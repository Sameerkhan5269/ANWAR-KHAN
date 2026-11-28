package com.example.data

import com.example.core.ImageCompressor
import com.example.data.model.JsonCodec
import com.example.data.model.LoanMcoRecord
import com.example.data.model.LoanReminderRule
import com.example.data.model.PartyProject
import com.example.data.model.ProjectDivision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HardeningVerificationTest {

    @Test
    fun `supabase sync uses six separated module key-rows`() {
        val expectedKeys = setOf(
            "nb_parties",
            "nb_inventory",
            "nb_busy_vouchers",
            "nb_loans_mco",
            "nb_chat",
            "nb_security_config"
        )
        assertEquals(expectedKeys, SupabaseSyncRepository.ALL_CANONICAL_KEYS.toSet())
    }

    @Test
    fun `loan mco overlay prevents simultaneous office and field overwrite`() {
        // Office edits customer mobile/city in nb_parties (where mcoOk is still false in Office's copy)
        val officePartyEdit = PartyProject(
            id = "p_ram_1",
            partyName = "Ramesh Choudhary",
            mobile = "9829011223",
            cityOrLocation = "Dabok, Udaipur",
            capacityKw = 5.0,
            sanctionedLoanAmount = 200_000.0,
            loan1Amount = 100_000.0,
            loan1Received = false,
            installationDoneDate = "2026-11-26",
            mcoOk = false
        )

        // Meanwhile Field Supervisor marks MCO OK & 1st Loan Done in nb_loans_mco
        val fieldMcoUpdate = LoanMcoRecord(
            partyId = "p_ram_1",
            sanctionedLoanAmount = 200_000.0,
            loan1Amount = 100_000.0,
            loan2Amount = 100_000.0,
            loan1Received = true,
            loan1Date = "2026-11-25",
            installationDoneDate = "2026-11-26",
            rsebFileApplied = true,
            mcoOk = true
        )

        val merged = JsonCodec.mergeLoanMcoOverlay(
            parties = listOf(officePartyEdit),
            records = listOf(fieldMcoUpdate)
        ).first()

        // Both Office's mobile/city update AND Field Supervisor's MCO OK + 1st Loan Done survive!
        assertEquals("9829011223", merged.mobile)
        assertEquals("Dabok, Udaipur", merged.cityOrLocation)
        assertTrue(merged.isMcoOk)
        assertTrue(merged.isLoan1Done)
        assertEquals("2026-11-25", merged.loan1Date)

        // Extracting back produces clean LoanMcoRecord matching merged party
        val extracted = JsonCodec.extractLoanMcoRecords(listOf(merged)).first()
        assertEquals("p_ram_1", extracted.partyId)
        assertTrue(extracted.mcoOk)
    }

    @Test
    fun `image compressor scales oversized camera photos to max 1280px width cleanly`() {
        val (w1, h1) = ImageCompressor.computeScaledDimensions(4000, 3000, ImageCompressor.DEFAULT_MAX_WIDTH)
        assertEquals(1280, w1)
        assertEquals(960, h1)

        val (w2, h2) = ImageCompressor.computeScaledDimensions(800, 600, ImageCompressor.DEFAULT_MAX_WIDTH)
        assertEquals(800, w2)
        assertEquals(600, h2)

        val sampleLarge = ImageCompressor.computeInSampleSize(4032, 3024, ImageCompressor.DEFAULT_MAX_WIDTH)
        assertTrue(sampleLarge >= 2)

        val sampleSmall = ImageCompressor.computeInSampleSize(800, 600, ImageCompressor.DEFAULT_MAX_WIDTH)
        assertEquals(1, sampleSmall)
    }

    @Test
    fun `smart reminder summary groups all five alert rules accurately`() {
        val parties = listOf(
            // 1. Pending 1st loan
            PartyProject(
                id = "p1",
                partyName = "Suresh Patel",
                division = ProjectDivision.SOLAR,
                capacityKw = 3.0,
                sanctionedLoanAmount = 140_000.0,
                loan1Amount = 70_000.0,
                loan1Received = false
            ),
            // 2. 1st Loan done 3 days ago -> 2-Day Installation overdue + RSEB MCO pending
            PartyProject(
                id = "p2",
                partyName = "Mahesh Joshi",
                division = ProjectDivision.SOLAR,
                capacityKw = 5.0,
                sanctionedLoanAmount = 200_000.0,
                loan1Received = true,
                loan1Date = "2026-11-24",
                installationDoneDate = "",
                rsebFileApplied = true,
                mcoOk = false
            ),
            // 3. Meter installed -> 2nd Loan due, plus MCO OK already ticked
            PartyProject(
                id = "p3",
                partyName = "Kailash Meena",
                division = ProjectDivision.SOLAR,
                capacityKw = 5.0,
                sanctionedLoanAmount = 200_000.0,
                loan1Amount = 100_000.0,
                loan2Amount = 100_000.0,
                loan1Received = true,
                installationDoneDate = "2026-11-20",
                meterInstalled = true,
                loan2Received = false,
                mcoOk = true
            ),
            // 4. 2nd Loan done 6 days ago -> 5-Day Subsidy Alert
            PartyProject(
                id = "p4",
                partyName = "Dinesh Paliwal",
                division = ProjectDivision.SOLAR,
                capacityKw = 3.0,
                sanctionedLoanAmount = 140_000.0,
                loan1Received = true,
                installationDoneDate = "2026-11-15",
                meterInstalled = true,
                loan2Received = true,
                loan2Date = "2026-11-21",
                mcoOk = true,
                subsidyReceived = false
            )
        )

        val reminders = LoanReminderEngine.computeReminders(parties, todayIso = "2026-11-27")
        val rulesPresent = reminders.map { it.rule }.toSet()

        assertTrue(LoanReminderRule.FIRST_LOAN_PENDING in rulesPresent)
        assertTrue(LoanReminderRule.INSTALLATION_2DAY_ALERT in rulesPresent)
        assertTrue(LoanReminderRule.METER_TO_SECOND_LOAN in rulesPresent)
        assertTrue(LoanReminderRule.SECOND_LOAN_TO_SUBSIDY in rulesPresent)
        assertTrue(LoanReminderRule.PERSISTENT_RSEB_MCO in rulesPresent)

        // p3 has MCO OK = true so must NOT appear under PERSISTENT_RSEB_MCO
        assertFalse(reminders.any { it.partyId == "p3" && it.rule == LoanReminderRule.PERSISTENT_RSEB_MCO })
        // p2 installation & p4 subsidy must be urgent
        assertTrue(reminders.first { it.partyId == "p2" && it.rule == LoanReminderRule.INSTALLATION_2DAY_ALERT }.isUrgent)
        assertTrue(reminders.first { it.partyId == "p4" && it.rule == LoanReminderRule.SECOND_LOAN_TO_SUBSIDY }.isUrgent)
    }
}
