package com.example.data

import com.example.data.model.AppDatabaseSnapshot
import com.example.data.model.BusyAccount
import com.example.data.model.BusyAccountGroup
import com.example.data.model.BusyItem
import com.example.data.model.BusyVoucher
import com.example.data.model.BusyVoucherType
import com.example.data.model.DcrPanel
import com.example.data.model.DiscomTestingStatus
import com.example.data.model.JsonCodec
import com.example.data.model.KharchaCategory
import com.example.data.model.LeadSource
import com.example.data.model.MaterialIssueLog
import com.example.data.model.MeterType
import com.example.data.model.PanelStatus
import com.example.data.model.PartyProject
import com.example.data.model.ProjectDivision
import com.example.data.model.RawMaterial
import com.example.data.model.SiteKharchaEntry
import com.example.data.model.SolarMeter
import com.example.data.model.StaffRole
import com.example.data.model.StaffUser
import com.example.data.model.TeamChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class ProductionSafetyFeaturesTest {

    private fun sampleSnapshot(): AppDatabaseSnapshot {
        val party1 = PartyProject(
            id = "p_1",
            partyName = "Ramesh Chandra Sharma",
            mobile = "9829012345",
            cityOrLocation = "Hiran Magri, Udaipur",
            consumerNumber = "K1100223344",
            capacityKw = 5.0,
            division = ProjectDivision.SOLAR,
            leadSource = LeadSource.THAKUR_JI,
            referrerName = "Thakur Ji",
            partnerCommissionPct = 10.0,
            projectValue = 280_000.0,
            receivedAmount = 140_000.0,
            sanctionedLoanAmount = 200_000.0,
            loan1Amount = 100_000.0,
            loan1Received = true,
            loan1Date = "2026-11-10",
            loan2Amount = 100_000.0,
            loan2Received = false,
            installationDoneDate = "2026-11-12",
            rsebFileApplied = true,
            mcoOk = false,
            meterInstalled = false,
            subsidyAmount = 78_000.0
        )
        val panel1 = DcrPanel(
            id = "dcr_1",
            serialNumber = "WAA540-001",
            brand = "Waaree",
            wattage = 540,
            supplierName = "Waaree Energies",
            purchaseInvoiceNo = "PUR-101",
            status = PanelStatus.ALLOCATED,
            linkedPartyId = "p_1",
            linkedPartyName = "Ramesh Chandra Sharma",
            addedDate = "2026-11-05"
        )
        val meter1 = SolarMeter(
            id = "m_1",
            serialNumber = "LT-889901",
            meterType = MeterType.BI_DIRECTIONAL,
            makeAndPhase = "L&T 3-Phase",
            testingStatus = DiscomTestingStatus.TESTED_PASSED,
            installedPartyId = "p_1",
            installedPartyName = "Ramesh Chandra Sharma"
        )
        val raw1 = RawMaterial(
            id = "rm_1",
            name = "GI Pipe Structure",
            category = "Structure",
            unit = "kg",
            supplierName = "Udaipur Steels",
            purchasedQty = 500.0,
            usedQty = 120.0,
            minStockAlert = 50.0,
            unitCost = 88.0,
            issueLogs = listOf(
                MaterialIssueLog(
                    id = "iss_1",
                    partyId = "p_1",
                    partyName = "Ramesh Chandra Sharma",
                    quantity = 120.0,
                    date = "2026-11-11",
                    issuedBy = "Owner"
                )
            )
        )
        val acc1 = BusyAccount(
            id = "acc_1",
            name = "Ramesh Chandra Sharma",
            group = BusyAccountGroup.SUNDRY_DEBTORS,
            linkedPartyId = "p_1"
        )
        val accBank = BusyAccount(
            id = "acc_bank",
            name = "HDFC Current Account",
            group = BusyAccountGroup.BANK_ACCOUNTS
        )
        val item1 = BusyItem(
            id = "itm_1",
            name = "Waaree 540W DCR Panel",
            hsnCode = "85414300",
            unit = "Nos",
            gstRatePercent = 12.0,
            defaultSaleRate = 14500.0,
            stockQty = 18.0
        )
        val vch1 = BusyVoucher(
            id = "vch_1",
            type = BusyVoucherType.RECEIPT,
            voucherNo = "RCP/26-27/001",
            date = "2026-11-10",
            partyAccountId = "acc_1",
            partyAccountName = "Ramesh Chandra Sharma",
            contraAccountId = "acc_bank",
            contraAccountName = "HDFC Current Account",
            costCenter = LeadSource.THAKUR_JI,
            taxableAmount = 140_000.0,
            totalAmount = 140_000.0,
            utrOrChequeNo = "UTR99887766",
            paidBy = "Ramesh Online",
            narration = "1st loan tranche receipt"
        )
        val kh1 = SiteKharchaEntry(
            id = "kh_1",
            partyId = "p_1",
            partyName = "Ramesh Chandra Sharma",
            category = KharchaCategory.SITE_KHARCHA,
            workerOrTitle = "Tea & Structure Welding",
            amount = 850.0,
            date = "2026-11-12",
            loggedBy = "Supervisor"
        )
        val chat1 = TeamChatMessage(
            id = "c_1",
            senderLoginId = "owner@nb",
            senderName = "Owner",
            senderRole = StaffRole.OWNER,
            text = "Ramesh site structure done",
            timestamp = "2026-11-12 10:30",
            division = ProjectDivision.SOLAR
        )
        val staff1 = StaffUser(
            id = "u_1",
            loginId = "owner@nb",
            pin = "992921",
            displayName = "NB Owner",
            role = StaffRole.OWNER,
            isBuiltIn = true
        )
        val parties = listOf(party1)
        return AppDatabaseSnapshot(
            parties = parties,
            loanMcoRecords = JsonCodec.extractLoanMcoRecords(parties),
            dcrPanels = listOf(panel1),
            solarMeters = listOf(meter1),
            rawMaterials = listOf(raw1),
            siteKharcha = listOf(kh1),
            teamChat = listOf(chat1),
            staffUsers = listOf(staff1),
            busyAccounts = listOf(acc1, accBank),
            busyItems = listOf(item1),
            busyVouchers = listOf(vch1)
        )
    }

    @Test
    fun `owner master security key 992921 validates strictly`() {
        assertTrue(BackupRestoreEngine.verifyOwnerMasterKey("992921"))
        assertTrue(BackupRestoreEngine.verifyOwnerMasterKey(" 992921 "))
        assertFalse(BackupRestoreEngine.verifyOwnerMasterKey("123456"))
        assertFalse(BackupRestoreEngine.verifyOwnerMasterKey(""))
    }

    @Test
    fun `empty backup json returns clean validation error instead of crashing`() {
        val emptyRes = BackupRestoreEngine.parseAndPreviewBackupJson("   ")
        assertTrue(emptyRes.isFailure)
        assertTrue(emptyRes.exceptionOrNull()?.message?.contains("empty", ignoreCase = true) == true)
    }

    @Test
    fun `backup overdue reminder triggers when never backed up or older than 7 days`() {
        val never = BackupRestoreEngine.computeBackupOverdueStatus("", todayIso = "2026-11-27")
        assertTrue(never.isOverdue)
        assertNull(never.daysSinceLastBackup)
        assertNotNull(never.bannerMessage)

        val recent = BackupRestoreEngine.computeBackupOverdueStatus("2026-11-22", todayIso = "2026-11-27")
        assertFalse(recent.isOverdue)
        assertEquals(5, recent.daysSinceLastBackup)
        assertNull(recent.bannerMessage)

        val exactSeven = BackupRestoreEngine.computeBackupOverdueStatus("2026-11-20", todayIso = "2026-11-27")
        assertFalse(exactSeven.isOverdue)
        assertEquals(7, exactSeven.daysSinceLastBackup)

        val overdue = BackupRestoreEngine.computeBackupOverdueStatus("2026-11-18", todayIso = "2026-11-27")
        assertTrue(overdue.isOverdue)
        assertEquals(9, overdue.daysSinceLastBackup)
        assertNotNull(overdue.bannerMessage)
    }

    @Test
    fun `master excel workbook xlsx builds valid multi-sheet zip archive and csv`() {
        val snap = sampleSnapshot()
        val xlsxBytes = BackupRestoreEngine.buildMasterExcelWorkbookXlsx(snap)

        // ZIP magic header PK\x03\x04
        assertTrue(xlsxBytes.size > 500)
        assertEquals('P'.code.toByte(), xlsxBytes[0])
        assertEquals('K'.code.toByte(), xlsxBytes[1])

        val entryNames = mutableListOf<String>()
        var sheet1Content = ""
        var sheet2Content = ""
        var sheet3Content = ""
        var sheet4Content = ""
        ZipInputStream(ByteArrayInputStream(xlsxBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entryNames.add(entry.name)
                when (entry.name) {
                    "xl/worksheets/sheet1.xml" -> sheet1Content = zis.readBytes().toString(Charsets.UTF_8)
                    "xl/worksheets/sheet2.xml" -> sheet2Content = zis.readBytes().toString(Charsets.UTF_8)
                    "xl/worksheets/sheet3.xml" -> sheet3Content = zis.readBytes().toString(Charsets.UTF_8)
                    "xl/worksheets/sheet4.xml" -> sheet4Content = zis.readBytes().toString(Charsets.UTF_8)
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        assertTrue(entryNames.contains("[Content_Types].xml"))
        assertTrue(entryNames.contains("xl/workbook.xml"))
        for (idx in 1..6) {
            assertTrue("Missing sheet$idx.xml", entryNames.contains("xl/worksheets/sheet$idx.xml"))
        }
        assertTrue(sheet1Content.contains("Ramesh Chandra Sharma"))
        assertTrue(sheet2Content.contains("100000.0"))
        assertTrue(sheet3Content.contains("WAA540-001"))
        assertTrue(sheet4Content.contains("RCP/26-27/001"))

        val csv = BackupRestoreEngine.buildMasterSummaryCsv(snap)
        assertTrue(csv.contains("SECTION,COL_1,COL_2"))
        assertTrue(csv.contains("PARTY,Ramesh Chandra Sharma"))
        assertTrue(csv.contains("VOUCHER_F6"))
        assertTrue(csv.contains("RCP/26-27/001"))
        assertTrue(csv.contains("DCR_PANEL,WAA540-001"))
        assertTrue(csv.contains("RAW_STOCK,GI Pipe Structure"))
    }

    @Test
    fun `duplicate transaction guard blocks rapid double taps and releases on validation failure`() {
        val guard = DuplicateTransactionGuard(burstWindowMs = 2_000L)
        val fp = "create_party|ramesh|9829012345|5.0"

        // First claim succeeds
        assertTrue(guard.tryClaim("uuid-1", fp, nowMs = 1_000L))
        // Immediate double tap with same UUID fails
        assertFalse(guard.tryClaim("uuid-1", fp, nowMs = 1_050L))
        // Even with different UUID, identical payload burst within window fails
        assertFalse(guard.tryClaim("uuid-2", fp, nowMs = 1_500L))
        // After burst window passes, a fresh UUID succeeds
        assertTrue(guard.tryClaim("uuid-3", fp, nowMs = 3_200L))

        // Validation failure releases lock so user can correct input and retry with same UUID/payload
        val retryUuid = "uuid-retry"
        val retryFp = "voucher|utr-dup"
        assertTrue(guard.tryClaim(retryUuid, retryFp, nowMs = 5_000L))
        guard.releaseOnValidationFailure(retryUuid, retryFp)
        assertTrue(guard.tryClaim(retryUuid, retryFp, nowMs = 5_100L))
    }

    @Test
    fun `low-ram pagination slices lists strictly at 25 per page`() {
        val empty = BackupRestoreEngine.paginateSlice(emptyList<Int>(), 25)
        assertEquals(0, empty.size)

        val sixty = (1..60).toList()
        val page1 = BackupRestoreEngine.paginateSlice(sixty, 25)
        assertEquals(25, page1.size)
        assertEquals(1, page1.first())
        assertEquals(25, page1.last())

        val page2 = BackupRestoreEngine.paginateSlice(sixty, 50)
        assertEquals(50, page2.size)
        assertEquals(50, page2.last())

        val page3 = BackupRestoreEngine.paginateSlice(sixty, 75)
        assertEquals(60, page3.size)
        assertEquals(60, page3.last())
    }
}
