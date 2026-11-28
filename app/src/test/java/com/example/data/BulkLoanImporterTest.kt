package com.example.data

import com.example.core.RolePermissions
import com.example.data.model.LoanReminderRule
import com.example.data.model.PartyProject
import com.example.data.model.ProjectDivision
import com.example.data.model.StaffRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BulkLoanImporterTest {

    @Test
    fun `csv import parses 13 loan parties computes totals and flags duplicates`() {
        val header =
            "Party Name,Mobile,Site/City,kW Capacity,Referrer,Commission %,Total Contract,Sanctioned Loan," +
                "1st Loan Received,1st Loan Done,1st Loan Date,2nd Loan Received,2nd Loan Done,2nd Loan Date," +
                "Installation Done,RSEB File Applied,MCO OK,Meter Installed,Subsidy Received"
        val csv = buildString {
            appendLine(header)
            for (i in 1..13) {
                val l1Done = if (i <= 10) "Yes" else "No"
                val l1Date = if (i <= 10) "2026-11-10" else ""
                val l2Done = if (i <= 4) "Yes" else "No"
                val l2Date = if (i <= 4) "2026-11-20" else ""
                val inst = if (i <= 8) "Yes" else "No"
                val rseb = if (i <= 11) "Yes" else "No"
                val mco = if (i <= 4) "Yes" else "No"
                val meter = if (i <= 6) "Yes" else "No"
                val sub = if (i <= 2) "Yes" else "No"
                appendLine(
                    "Loan Party $i,98290000${i.toString().padStart(2, '0')},Udaipur,5,Thakur Ji,3,265000,200000," +
                        "100000,$l1Done,$l1Date,100000,$l2Done,$l2Date,$inst,$rseb,$mco,$meter,$sub"
                )
            }
        }

        val existing = listOf(PartyProject(partyName = "Loan Party 1", mobile = "9829000001"))
        val rows = BulkLoanImporter.parseCsvLoanParties(csv, existing)

        assertEquals(13, rows.size)
        assertTrue(rows.first().isDuplicateInDb)
        assertFalse(rows.first().selected)
        assertFalse(rows[1].isDuplicateInDb)
        assertTrue(rows[1].selected)

        val summary = BulkLoanImporter.computeBatchSummary(rows)
        assertEquals(13, summary.totalRows)
        assertEquals(12, summary.selectedCount)
        assertEquals(1, summary.duplicateCount)
        // Selected rows 2..13 all have 1st Loan column = 100000 (rows 11..13 still have 100k in column or let's verify exact sum)
        assertEquals(1_200_000.0, summary.totalFirstLoanReceivedInr, 0.01)
        // Rows 2..4 have 2nd loan done (3 rows), rows 5..13 have 2nd loan pending (9 rows * 100k = 900k)
        assertEquals(900_000.0, summary.totalSecondLoanPendingInr, 0.01)
    }

    @Test
    fun `xlsx ooxml parser extracts shared strings and worksheet cells accurately`() {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("xl/sharedStrings.xml"))
            zos.write(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <si><t>Party Name</t></si>
                  <si><t>kW Capacity</t></si>
                  <si><t>Sanctioned Loan</t></si>
                  <si><t>1st Loan Received</t></si>
                  <si><t>1st Loan Done</t></si>
                  <si><t>MCO OK</t></si>
                  <si><t>Mahendra Singh</t></si>
                  <si><t>Yes</t></si>
                  <si><t>No</t></si>
                </sst>
                """.trimIndent().toByteArray(Charsets.UTF_8)
            )
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            zos.write(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <sheetData>
                    <row r="1">
                      <c r="A1" t="s"><v>0</v></c>
                      <c r="B1" t="s"><v>1</v></c>
                      <c r="C1" t="s"><v>2</v></c>
                      <c r="D1" t="s"><v>3</v></c>
                      <c r="E1" t="s"><v>4</v></c>
                      <c r="F1" t="s"><v>5</v></c>
                    </row>
                    <row r="2">
                      <c r="A2" t="s"><v>6</v></c>
                      <c r="B2"><v>5</v></c>
                      <c r="C2"><v>210000</v></c>
                      <c r="D2"><v>105000</v></c>
                      <c r="E2" t="s"><v>7</v></c>
                      <c r="F2" t="s"><v>8</v></c>
                    </row>
                  </sheetData>
                </worksheet>
                """.trimIndent().toByteArray(Charsets.UTF_8)
            )
            zos.closeEntry()
        }

        val rows = BulkLoanImporter.parseXlsxLoanParties(baos.toByteArray())
        assertEquals(1, rows.size)
        val r = rows.first()
        assertEquals("Mahendra Singh", r.partyName)
        assertEquals(5.0, r.capacityKw, 0.01)
        assertEquals(210_000.0, r.sanctionedLoanInr, 0.01)
        assertEquals(105_000.0, r.loan1Amount, 0.01)
        assertTrue(r.loan1Done)
        assertFalse(r.mcoOk)
    }

    @Test
    fun `raw whatsapp text parser parses multi-line rupee shorthands and flags`() {
        val raw = """
            1. Suresh Jain | 9829112233 | Dabok | 3kW | Ref: Thakur Ji 2.5% | Deal 1.85L | Sanctioned 1.40L | 1st Loan 70k Done 2026-11-10 | 2nd Loan 70k Pending | Inst Done | RSEB Applied
            2. Kavita Verma 9414223344 5kW Deal 2.65L Sanctioned 2L 1st 1L Done 12/11/2026 2nd 1L Done 20/11/2026 Inst Done Meter Installed Subsidy Received
        """.trimIndent()

        val rows = BulkLoanImporter.parseRawTextLoanParties(raw)
        assertEquals(2, rows.size)

        val r1 = rows[0]
        assertEquals("Suresh Jain", r1.partyName)
        assertEquals("9829112233", r1.mobile)
        assertEquals(3.0, r1.capacityKw, 0.01)
        assertEquals(2.5, r1.commissionPct, 0.01)
        assertEquals(185_000.0, r1.totalContractInr, 0.01)
        assertEquals(140_000.0, r1.sanctionedLoanInr, 0.01)
        assertEquals(70_000.0, r1.loan1Amount, 0.01)
        assertTrue(r1.loan1Done)
        assertEquals("2026-11-10", r1.loan1Date)
        assertEquals(70_000.0, r1.loan2Amount, 0.01)
        assertFalse(r1.loan2Done)
        assertTrue(r1.installationDone)
        assertTrue(r1.rsebFileApplied)

        val r2 = rows[1]
        assertEquals("Kavita Verma", r2.partyName)
        assertEquals(5.0, r2.capacityKw, 0.01)
        assertTrue(r2.loan2Done)
        assertTrue(r2.meterInstalled)
        assertTrue(r2.subsidyReceived)
    }

    @Test
    fun `5 rule automated reminder engine triggers alerts and clears persistent mco when ticked`() {
        val sampleCsv = BulkLoanImporter.buildSampleLoanPartiesCsv()
        val parsed = BulkLoanImporter.parseCsvLoanParties(sampleCsv)
        val (parties, accounts) = BulkLoanImporter.toPartyProjects(parsed, "Owner / CEO", todayIso = "2026-11-27")
        assertEquals(2, parties.size)
        assertEquals(2, accounts.size)

        // Add one party awaiting 1st loan and one installed-after-1st-loan (>2d) + meter-installed-awaiting-2nd-loan + 2nd-loan-5d-subsidy
        val pLoan1Pending = PartyProject(
            partyName = "Loan 1 Due Party",
            sanctionedLoanAmount = 150_000.0,
            loan1Amount = 75_000.0,
            loan1Received = false,
            rsebFileApplied = true
        )
        val pInst2Day = PartyProject(
            partyName = "Inst 2Day Party",
            sanctionedLoanAmount = 200_000.0,
            loan1Amount = 100_000.0,
            loan1Received = true,
            loan1Date = "2026-11-24",
            installationDoneDate = ""
        )
        val pMeterToLoan2 = PartyProject(
            partyName = "Meter To Loan2 Party",
            sanctionedLoanAmount = 200_000.0,
            loan1Amount = 100_000.0,
            loan1Received = true,
            loan1Date = "2026-11-10",
            installationDoneDate = "2026-11-12",
            meterInstalled = true,
            loan2Received = false,
            mcoOk = false
        )
        val pSubsidy5Day = PartyProject(
            partyName = "Subsidy 5Day Party",
            sanctionedLoanAmount = 200_000.0,
            loan1Received = true,
            loan2Received = true,
            loan2Date = "2026-11-20",
            installationDoneDate = "2026-11-15",
            mcoOk = true,
            subsidyReceived = false
        )

        val reminders = LoanReminderEngine.computeReminders(
            listOf(pLoan1Pending, pInst2Day, pMeterToLoan2, pSubsidy5Day),
            todayIso = "2026-11-27"
        )

        assertTrue(reminders.any { it.rule == LoanReminderRule.FIRST_LOAN_PENDING && it.partyName == "Loan 1 Due Party" })
        assertTrue(reminders.any { it.rule == LoanReminderRule.INSTALLATION_2DAY_ALERT && it.partyName == "Inst 2Day Party" && it.isUrgent })
        assertTrue(reminders.any { it.rule == LoanReminderRule.METER_TO_SECOND_LOAN && it.partyName == "Meter To Loan2 Party" })
        assertTrue(reminders.any { it.rule == LoanReminderRule.SECOND_LOAN_TO_SUBSIDY && it.partyName == "Subsidy 5Day Party" && it.isUrgent })
        assertTrue(reminders.any { it.rule == LoanReminderRule.PERSISTENT_RSEB_MCO && it.partyName == "Meter To Loan2 Party" })

        // Once MCO OK is ticked, persistent MCO reminder vanishes
        val afterMcoTicked = LoanReminderEngine.computeReminders(
            listOf(pMeterToLoan2.copy(mcoOk = true)),
            todayIso = "2026-11-27"
        )
        assertFalse(afterMcoTicked.any { it.rule == LoanReminderRule.PERSISTENT_RSEB_MCO })
    }

    @Test
    fun `rbac restricts bulk loan import to owner accounts and solar office only`() {
        assertTrue(RolePermissions.canBulkImportLoanParties(StaffRole.OWNER, null))
        assertTrue(RolePermissions.canBulkImportLoanParties(StaffRole.ACCOUNTS, null))
        assertTrue(RolePermissions.canBulkImportLoanParties(StaffRole.OFFICE, ProjectDivision.SOLAR))

        assertFalse(RolePermissions.canBulkImportLoanParties(StaffRole.OFFICE, ProjectDivision.WIND))
        assertFalse(RolePermissions.canBulkImportLoanParties(StaffRole.SUPERVISOR, ProjectDivision.SOLAR))
        assertFalse(RolePermissions.canBulkImportLoanParties(StaffRole.HELPER, ProjectDivision.SOLAR))
    }
}
