package com.example.data

import com.example.core.todayIsoDate
import com.example.data.model.AppDatabaseSnapshot
import com.example.data.model.BusyVoucherType
import com.example.data.model.JsonCodec
import com.example.data.model.ProjectDivision
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Production safety engine handling:
 * 1. Owner Master Security Key ("992921") protected Full Company JSON Backup & Restore
 *    across all 6 Supabase module key-rows (`nb_parties`, `nb_inventory`,
 *    `nb_busy_vouchers`, `nb_loans_mco`, `nb_chat`, `nb_security_config`).
 * 2. Pure-Kotlin OOXML Master Excel (.xlsx) Multi-Sheet Workbook & Master CSV generation.
 * 3. Backup Overdue (>7 days) reminder tracking.
 * 4. Double-Tap Duplicate Submission Guard via unique transaction UUIDs.
 * 5. Low-RAM 25-per-page pagination slicing helpers.
 */
object BackupRestoreEngine {

    const val OWNER_MASTER_SECURITY_KEY = "992921"
    const val BACKUP_OVERDUE_DAYS = 7
    const val DEFAULT_PAGE_SIZE = 25

    data class BackupPreview(
        val companyName: String,
        val gstin: String,
        val schemaVersion: Int,
        val exportedAt: String,
        val exportedDate: String,
        val exportedBy: String,
        val partiesCount: Int,
        val leadsCount: Int,
        val loansMcoCount: Int,
        val dcrPanelsCount: Int,
        val solarMetersCount: Int,
        val rawMaterialsCount: Int,
        val challanIssueLogsCount: Int,
        val busyVouchersCount: Int,
        val receiptsCount: Int,
        val busyAccountsCount: Int,
        val busyItemsCount: Int,
        val siteKharchaCount: Int,
        val commissionsCount: Int,
        val chatCount: Int,
        val staffUsersCount: Int,
        val snapshot: AppDatabaseSnapshot
    )

    data class BackupOverdueStatus(
        val isOverdue: Boolean,
        val daysSinceLastBackup: Int?,
        val bannerMessage: String?
    )

    fun verifyOwnerMasterKey(enteredKey: String): Boolean {
        return enteredKey.trim() == OWNER_MASTER_SECURITY_KEY
    }

    fun <T> paginateSlice(items: List<T>, visibleLimit: Int): List<T> {
        val safeLimit = visibleLimit.coerceAtLeast(DEFAULT_PAGE_SIZE)
        return if (items.size <= safeLimit) items else items.subList(0, safeLimit)
    }

    fun formatByteSize(bytes: Long): String {
        val safe = bytes.coerceAtLeast(0L)
        return when {
            safe < 1024L -> "$safe B"
            safe < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", safe / 1024.0)
            else -> String.format(Locale.US, "%.2f MB", safe / (1024.0 * 1024.0))
        }
    }

    fun computeBackupOverdueStatus(
        lastBackupDateIso: String?,
        todayIso: String = todayIsoDate()
    ): BackupOverdueStatus {
        val clean = lastBackupDateIso?.trim().orEmpty()
        if (clean.isBlank()) {
            return BackupOverdueStatus(
                isOverdue = true,
                daysSinceLastBackup = null,
                bannerMessage = "No company backup taken yet. Download a Full Company Backup (.json) and Master Excel (.xlsx) now."
            )
        }
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
        val lastMillis = runCatching { sdf.parse(clean)?.time }.getOrNull()
        val todayMillis = runCatching { sdf.parse(todayIso)?.time }.getOrNull()
        if (lastMillis == null || todayMillis == null) {
            return BackupOverdueStatus(
                isOverdue = true,
                daysSinceLastBackup = null,
                bannerMessage = "Backup schedule check needed. Please save a fresh Full Company Backup (.json)."
            )
        }
        val diffDays = TimeUnit.MILLISECONDS.toDays((todayMillis - lastMillis).coerceAtLeast(0L)).toInt()
        return if (diffDays > BACKUP_OVERDUE_DAYS) {
            BackupOverdueStatus(
                isOverdue = true,
                daysSinceLastBackup = diffDays,
                bannerMessage = "Company Backup Overdue ($diffDays days since $clean). Weekly backup (>7 days) recommended."
            )
        } else {
            BackupOverdueStatus(
                isOverdue = false,
                daysSinceLastBackup = diffDays,
                bannerMessage = null
            )
        }
    }

    fun buildFullBackupJson(
        snapshot: AppDatabaseSnapshot,
        exportedBy: String,
        exportedAt: String,
        exportedDate: String = todayIsoDate()
    ): String {
        val modulesObj = JSONObject().apply {
            for (key in SupabaseSyncRepository.ALL_CANONICAL_KEYS) {
                val payload = SupabaseSyncRepository.encodePayloadForKey(snapshot, key)
                if (payload != null) {
                    put(key, payload)
                }
            }
        }
        val root = JSONObject().apply {
            put("app", "NB Solar & Wind ERP")
            put("company", "NB ENGINEERING WORKS AND ELECTRICALS")
            put("gstin", "08CSZPK9277N2Z8")
            put("schemaVersion", 2)
            put("exportedAt", exportedAt)
            put("exportedDate", exportedDate)
            put("exportedBy", exportedBy)
            put("recordCounts", JSONObject().apply {
                put("parties", snapshot.parties.size)
                put("dcrPanels", snapshot.dcrPanels.size)
                put("solarMeters", snapshot.solarMeters.size)
                put("rawMaterials", snapshot.rawMaterials.size)
                put("busyAccounts", snapshot.busyAccounts.size)
                put("busyItems", snapshot.busyItems.size)
                put("busyVouchers", snapshot.busyVouchers.size)
                put("siteKharcha", snapshot.siteKharcha.size)
                put("teamChat", snapshot.teamChat.size)
                put("staffUsers", snapshot.staffUsers.size)
            })
            put("modules", modulesObj)
        }
        return root.toString(2)
    }

    fun parseAndPreviewBackupJson(rawJson: String): Result<BackupPreview> {
        return try {
            val trimmed = rawJson.trim()
            if (trimmed.isBlank()) {
                return Result.failure(IllegalArgumentException("Backup file is empty."))
            }
            val root = JSONObject(trimmed)
            val modulesObj = root.optJSONObject("modules")
                ?: return Result.failure(
                    IllegalArgumentException("Invalid backup format: missing 'modules' section. Select an NB Solar & Wind ERP (.json) backup file.")
                )

            val hasKnownKey = SupabaseSyncRepository.ALL_CANONICAL_KEYS.any { modulesObj.has(it) }
            if (!hasKnownKey) {
                return Result.failure(
                    IllegalArgumentException("Unrecognized backup file: no NB Solar & Wind ERP module keys found.")
                )
            }

            var restored = AppDatabaseSnapshot()
            // Restore canonical keys in deterministic order (parties before loans overlay)
            val orderedKeys = listOf(
                SupabaseSyncRepository.KEY_SECURITY_CONFIG,
                SupabaseSyncRepository.KEY_PARTIES,
                SupabaseSyncRepository.KEY_LOANS_MCO,
                SupabaseSyncRepository.KEY_INVENTORY,
                SupabaseSyncRepository.KEY_BUSY_VOUCHERS,
                SupabaseSyncRepository.KEY_CHAT
            )
            for (key in orderedKeys) {
                val payload = modulesObj.opt(key) ?: continue
                restored = SupabaseSyncRepository.applyStoreKeyPayload(restored, key, payload)
            }
            if (restored.loanMcoRecords.isEmpty() && restored.parties.isNotEmpty()) {
                restored = restored.copy(loanMcoRecords = JsonCodec.extractLoanMcoRecords(restored.parties))
            }

            val challansCount = restored.rawMaterials.sumOf { it.issueLogs.size }
            val receiptsCount = restored.busyVouchers.count { it.type == BusyVoucherType.RECEIPT }
            val commissionsCount = restored.parties.count { it.partnerCommissionPct > 0.0 || it.referrerName.isNotBlank() }

            Result.success(
                BackupPreview(
                    companyName = root.optString("company").ifBlank { "NB ENGINEERING WORKS AND ELECTRICALS" },
                    gstin = root.optString("gstin").ifBlank { "08CSZPK9277N2Z8" },
                    schemaVersion = root.optInt("schemaVersion", 2),
                    exportedAt = root.optString("exportedAt").ifBlank { "Unknown Timestamp" },
                    exportedDate = root.optString("exportedDate"),
                    exportedBy = root.optString("exportedBy").ifBlank { "Owner" },
                    partiesCount = restored.parties.size,
                    leadsCount = restored.parties.count { it.leadStatus.name != "CONVERTED" },
                    loansMcoCount = restored.loanMcoRecords.size,
                    dcrPanelsCount = restored.dcrPanels.size,
                    solarMetersCount = restored.solarMeters.size,
                    rawMaterialsCount = restored.rawMaterials.size,
                    challanIssueLogsCount = challansCount,
                    busyVouchersCount = restored.busyVouchers.size,
                    receiptsCount = receiptsCount,
                    busyAccountsCount = restored.busyAccounts.size,
                    busyItemsCount = restored.busyItems.size,
                    siteKharchaCount = restored.siteKharcha.size,
                    commissionsCount = commissionsCount,
                    chatCount = restored.teamChat.size,
                    staffUsersCount = restored.staffUsers.size,
                    snapshot = restored
                )
            )
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("Unable to read backup JSON: ${e.message ?: "malformed file"}"))
        }
    }

    /**
     * Generates a genuine multi-sheet OOXML Excel (.xlsx) Workbook covering:
     * 1. Parties & Leads
     * 2. Loans, MCO & Subsidy
     * 3. Inventory & Delivery Challans
     * 4. BUSY 21 Vouchers & Receipts
     * 5. Commissions & Site Kharcha
     * 6. BUSY Ledgers & Item Stock
     */
    fun buildMasterExcelWorkbookXlsx(snapshot: AppDatabaseSnapshot): ByteArray {
        val sheets = listOf(
            "Parties_Leads" to buildPartiesSheetRows(snapshot),
            "Loans_MCO" to buildLoansMcoSheetRows(snapshot),
            "Inventory_Challans" to buildInventorySheetRows(snapshot),
            "Vouchers_Receipts" to buildVouchersSheetRows(snapshot),
            "Commissions_Kharcha" to buildCommissionsKharchaSheetRows(snapshot),
            "Ledgers_Stock" to buildLedgersAndItemsSheetRows(snapshot)
        )

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            // 1. [Content_Types].xml
            val sheetOverrides = sheets.indices.joinToString("\n") { idx ->
                """  <Override PartName="/xl/worksheets/sheet${idx + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>"""
            }
            writeZipEntry(
                zip,
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
$sheetOverrides
</Types>"""
            )

            // 2. _rels/.rels
            writeZipEntry(
                zip,
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""
            )

            // 3. xl/workbook.xml
            val sheetDefs = sheets.mapIndexed { idx, (name, _) ->
                """    <sheet name="${escapeXml(name.take(31))}" sheetId="${idx + 1}" r:id="rId${idx + 1}"/>"""
            }.joinToString("\n")
            writeZipEntry(
                zip,
                "xl/workbook.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
$sheetDefs
  </sheets>
</workbook>"""
            )

            // 4. xl/_rels/workbook.xml.rels
            val sheetRels = sheets.indices.joinToString("\n") { idx ->
                """  <Relationship Id="rId${idx + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${idx + 1}.xml"/>"""
            }
            writeZipEntry(
                zip,
                "xl/_rels/workbook.xml.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
$sheetRels
  <Relationship Id="rId${sheets.size + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""
            )

            // 5. xl/styles.xml (minimal bold header style at index 1)
            writeZipEntry(
                zip,
                "xl/styles.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="2">
    <font><sz val="10"/><name val="Calibri"/></font>
    <font><b/><sz val="10"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
  </fonts>
  <fills count="3">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF8E1E1B"/></patternFill></fill>
  </fills>
  <borders count="1">
    <border><left/><right/><top/><bottom/><diagonal/></border>
  </borders>
  <cellStyleXfs count="1">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
  </cellStyleXfs>
  <cellXfs count="2">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
  </cellXfs>
</styleSheet>"""
            )

            // 6. xl/worksheets/sheetN.xml
            sheets.forEachIndexed { idx, (_, rows) ->
                writeZipEntry(zip, "xl/worksheets/sheet${idx + 1}.xml", buildWorksheetXml(rows))
            }
        }
        return baos.toByteArray()
    }

    fun buildMasterSummaryCsv(snapshot: AppDatabaseSnapshot): String {
        val sb = StringBuilder()
        sb.appendLine("SECTION,COL_1,COL_2,COL_3,COL_4,COL_5,COL_6,COL_7,COL_8")
        snapshot.parties.forEach { p ->
            sb.appendLine(
                listOf(
                    "PARTY",
                    p.partyName,
                    p.mobile,
                    p.cityOrLocation.ifBlank { p.address },
                    "${p.capacityKw} kW (${p.division.shortLabel})",
                    p.leadSource.label,
                    p.leadStatus.label,
                    String.format(Locale.US, "%.2f", p.projectValue),
                    String.format(Locale.US, "%.2f", p.pendingAmount)
                ).joinToString(",") { csvCell(it) }
            )
        }
        snapshot.busyVouchers.forEach { v ->
            sb.appendLine(
                listOf(
                    "VOUCHER_${v.type.shortcut}",
                    v.date,
                    v.voucherNo,
                    v.partyAccountName,
                    v.contraAccountName,
                    v.costCenter.shortLabel,
                    v.utrOrChequeNo,
                    String.format(Locale.US, "%.2f", v.totalAmount),
                    v.paidBy.ifBlank { v.narration }
                ).joinToString(",") { csvCell(it) }
            )
        }
        snapshot.dcrPanels.forEach { dp ->
            sb.appendLine(
                listOf(
                    "DCR_PANEL",
                    dp.serialNumber,
                    dp.brand,
                    "${dp.wattage}W",
                    dp.status.label,
                    dp.linkedPartyName,
                    dp.supplierName,
                    dp.purchaseInvoiceNo,
                    dp.addedDate
                ).joinToString(",") { csvCell(it) }
            )
        }
        snapshot.rawMaterials.forEach { rm ->
            sb.appendLine(
                listOf(
                    "RAW_STOCK",
                    rm.name,
                    rm.category,
                    "${rm.remainingQty} ${rm.unit}",
                    "Used: ${rm.usedQty}",
                    "Logs: ${rm.issueLogs.size}",
                    rm.supplierName,
                    String.format(Locale.US, "%.2f", rm.unitCost),
                    ""
                ).joinToString(",") { csvCell(it) }
            )
        }
        return sb.toString()
    }

    private fun buildPartiesSheetRows(snapshot: AppDatabaseSnapshot): List<List<Any>> {
        val rows = mutableListOf<List<Any>>(
            listOf(
                "Party Name", "Mobile", "City / Site", "Consumer / K-No", "Capacity (kW)",
                "Division", "Lead Source", "Referrer", "Lead Status", "Deal Value (INR)",
                "Received (INR)", "Pending Due (INR)", "Next Stage", "Site Purchases (INR)", "Created Date"
            )
        )
        snapshot.parties.forEach { p ->
            rows += listOf(
                p.partyName,
                p.mobile,
                p.cityOrLocation.ifBlank { p.address },
                p.consumerNumber,
                p.capacityKw,
                p.division.label,
                p.leadSource.label,
                p.referrerName,
                p.leadStatus.label,
                p.projectValue,
                p.receivedAmount,
                p.pendingAmount,
                p.currentStageTitle,
                p.totalOnSitePurchases,
                p.createdDate
            )
        }
        return rows
    }

    private fun buildLoansMcoSheetRows(snapshot: AppDatabaseSnapshot): List<List<Any>> {
        val rows = mutableListOf<List<Any>>(
            listOf(
                "Party Name", "Mobile", "Capacity (kW)", "Sanctioned Loan (INR)",
                "1st Loan (INR)", "1st Loan Recd", "1st Loan Date", "Installation Date",
                "RSEB File Applied", "MCO OK", "Meter Installed",
                "2nd Loan (INR)", "2nd Loan Recd", "2nd Loan Date", "Subsidy (INR)", "Subsidy Recd"
            )
        )
        snapshot.parties.forEach { p ->
            rows += listOf(
                p.partyName,
                p.mobile,
                p.capacityKw,
                p.sanctionedLoanAmount,
                p.loan1Amount,
                if (p.isLoan1Done) "YES" else "NO",
                p.loan1Date,
                p.installationDoneDate,
                if (p.isRsebFileApplied) "YES" else "NO",
                if (p.isMcoOk) "YES" else "NO",
                if (p.isMeterInstalled) "YES" else "NO",
                p.loan2Amount,
                if (p.isLoan2Done) "YES" else "NO",
                p.loan2Date,
                p.subsidyAmount,
                if (p.isSubsidyReceived) "YES" else "NO"
            )
        }
        return rows
    }

    private fun buildInventorySheetRows(snapshot: AppDatabaseSnapshot): List<List<Any>> {
        val rows = mutableListOf<List<Any>>(
            listOf(
                "Inventory Category", "Item / Serial No", "Brand / Spec", "Purchased / Spec Qty",
                "Remaining / Status", "Linked Party / Site", "Date / Invoice", "Issued By / Cost (INR)"
            )
        )
        snapshot.dcrPanels.forEach { dp ->
            rows += listOf(
                "DCR Solar Panel",
                dp.serialNumber,
                "${dp.brand} ${dp.wattage}W",
                1.0,
                dp.status.label,
                dp.linkedPartyName,
                "${dp.addedDate} / ${dp.purchaseInvoiceNo}",
                dp.unitCost
            )
        }
        snapshot.solarMeters.forEach { m ->
            rows += listOf(
                "Net / Solar Meter",
                m.serialNumber,
                m.meterType.label,
                1.0,
                m.testingStatus.label,
                m.installedPartyName,
                m.makeAndPhase,
                m.unitCost
            )
        }
        snapshot.rawMaterials.forEach { rm ->
            rows += listOf(
                "Raw Material (${rm.category})",
                rm.name,
                rm.unit,
                rm.purchasedQty,
                "Remaining: ${rm.remainingQty} ${rm.unit}",
                rm.supplierName,
                "Used: ${rm.usedQty}",
                rm.unitCost
            )
            rm.issueLogs.forEach { log ->
                rows += listOf(
                    "Delivery Challan / Issue",
                    rm.name,
                    rm.unit,
                    log.quantity,
                    "ISSUED",
                    log.partyName,
                    log.date,
                    log.issuedBy
                )
            }
        }
        return rows
    }

    private fun buildVouchersSheetRows(snapshot: AppDatabaseSnapshot): List<List<Any>> {
        val rows = mutableListOf<List<Any>>(
            listOf(
                "Date", "Voucher Type", "Key", "Voucher No", "Party Account (Dr/Cr)",
                "Contra Bank/Ledger", "Cost Center", "Taxable (INR)", "CGST (INR)",
                "SGST (INR)", "IGST (INR)", "Total (INR)", "UTR / Ref No", "Paid By", "Narration"
            )
        )
        snapshot.busyVouchers.forEach { v ->
            rows += listOf(
                v.date,
                v.type.label,
                v.type.shortcut,
                v.voucherNo,
                v.partyAccountName,
                v.contraAccountName,
                v.costCenter.label,
                v.taxableAmount,
                v.cgstAmount,
                v.sgstAmount,
                v.igstAmount,
                v.totalAmount,
                v.utrOrChequeNo.ifBlank { v.billRefNo },
                v.paidBy,
                v.narration
            )
        }
        return rows
    }

    private fun buildCommissionsKharchaSheetRows(snapshot: AppDatabaseSnapshot): List<List<Any>> {
        val rows = mutableListOf<List<Any>>(
            listOf(
                "Record Type", "Party Name", "Lead Source / Payment Mode", "Referrer / Vendor / Worker",
                "Commission % / Qty", "Amount (INR)", "Status / Voucher No", "Date"
            )
        )
        snapshot.parties.filter { it.partnerCommissionPct > 0.0 || it.referrerName.isNotBlank() }.forEach { p ->
            val commAmt = p.projectValue * (p.partnerCommissionPct / 100.0)
            rows += listOf(
                "Referral Commission",
                p.partyName,
                p.leadSource.label,
                p.referrerName.ifBlank { p.leadSource.label },
                p.partnerCommissionPct,
                commAmt,
                if (p.referralPaid) "PAID" else "PENDING",
                p.referralPayoutDate
            )
        }
        snapshot.parties.forEach { p ->
            p.sitePurchases.forEach { sp ->
                rows += listOf(
                    "On-Site Material Purchase",
                    p.partyName,
                    sp.paymentSource.label,
                    sp.vendorName,
                    "${sp.itemDescription} (${sp.quantity})",
                    sp.amount,
                    sp.linkedVoucherNo,
                    sp.purchaseDate
                )
            }
        }
        snapshot.siteKharcha.forEach { kh ->
            rows += listOf(
                "Site Kharcha (${kh.category.label})",
                kh.partyName,
                kh.loggedBy,
                kh.workerOrTitle,
                kh.note,
                kh.amount,
                "POSTED",
                kh.date
            )
        }
        return rows
    }

    private fun buildLedgersAndItemsSheetRows(snapshot: AppDatabaseSnapshot): List<List<Any>> {
        val rows = mutableListOf<List<Any>>(
            listOf(
                "Master Type", "Name", "Group / HSN", "Cost Center / Unit",
                "Opening Bal / Stock Qty", "GSTIN / GST %", "Mobile / Sale Rate (INR)"
            )
        )
        snapshot.busyAccounts.forEach { acc ->
            rows += listOf(
                "Account Ledger",
                acc.name,
                acc.group.label,
                acc.defaultCostCenter.label,
                acc.openingBalance,
                acc.gstin,
                acc.mobile
            )
        }
        snapshot.busyItems.forEach { itm ->
            rows += listOf(
                "Item Master",
                itm.name,
                itm.hsnCode,
                itm.unit,
                itm.stockQty,
                "${itm.gstRatePercent}%",
                itm.defaultSaleRate
            )
        }
        snapshot.staffUsers.forEach { st ->
            rows += listOf(
                "Staff User",
                st.displayName,
                st.role.label,
                st.assignedDivision?.shortLabel ?: ProjectDivision.SOLAR.shortLabel + "+WIND",
                st.imprestBalance,
                st.loginId,
                if (st.isBuiltIn) "Built-In" else "Custom"
            )
        }
        return rows
    }

    private fun buildWorksheetXml(rows: List<List<Any>>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
        rows.forEachIndexed { rIdx, cols ->
            val rowNum = rIdx + 1
            val styleAttr = if (rIdx == 0) """ s="1"""" else ""
            sb.append("""<row r="$rowNum">""")
            cols.forEachIndexed { cIdx, value ->
                val ref = "${colLetter(cIdx)}$rowNum"
                when (value) {
                    is Number -> {
                        val d = value.toDouble()
                        if (d.isNaN() || d.isInfinite()) {
                            sb.append("""<c r="$ref" t="inlineStr"$styleAttr><is><t>0</t></is></c>""")
                        } else {
                            sb.append("""<c r="$ref" t="n"$styleAttr><v>$d</v></c>""")
                        }
                    }
                    else -> {
                        sb.append("""<c r="$ref" t="inlineStr"$styleAttr><is><t>${escapeXml(value.toString())}</t></is></c>""")
                    }
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun colLetter(index: Int): String {
        var n = index
        val sb = StringBuilder()
        while (n >= 0) {
            sb.append(('A'.code + (n % 26)).toChar())
            n = (n / 26) - 1
        }
        return sb.reverse().toString()
    }

    private fun writeZipEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun escapeXml(raw: String): String = buildString(raw.length) {
        for (ch in raw) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> {
                    // Strip invalid XML control chars
                    if (ch.code == 0x9 || ch.code == 0xA || ch.code == 0xD || ch.code >= 0x20) {
                        append(ch)
                    } else {
                        append(' ')
                    }
                }
            }
        }
    }

    private fun csvCell(raw: String): String {
        val clean = raw.replace("\r", " ").replace("\n", " ")
        return if (clean.contains(",") || clean.contains("\"")) {
            "\"${clean.replace("\"", "\"\"")}\""
        } else {
            clean
        }
    }
}

/**
 * Thread-safe Double-Tap Duplicate Submission Guard.
 * Rejects repeat submissions sharing a client transaction UUID (`txnId`),
 * and also suppresses accidental identical payload bursts within a short window.
 */
class DuplicateTransactionGuard(
    private val burstWindowMs: Long = 1_800L
) {
    private val claimedTxnIds = ConcurrentHashMap.newKeySet<String>()
    private val recentFingerprints = ConcurrentHashMap<String, Long>()

    fun tryClaim(
        txnId: String?,
        payloadFingerprint: String? = null,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        val cleanTxn = txnId?.trim().orEmpty()
        if (cleanTxn.isNotBlank()) {
            if (!claimedTxnIds.add(cleanTxn)) {
                return false
            }
        }
        val fp = payloadFingerprint?.trim().orEmpty()
        if (fp.isNotBlank()) {
            val prev = recentFingerprints[fp]
            if (prev != null && (nowMs - prev) < burstWindowMs) {
                if (cleanTxn.isNotBlank()) claimedTxnIds.remove(cleanTxn)
                return false
            }
            recentFingerprints[fp] = nowMs
            // Keep map bounded cleanly
            if (recentFingerprints.size > 200) {
                val cutoff = nowMs - burstWindowMs * 2
                recentFingerprints.entries.removeIf { it.value < cutoff }
            }
        }
        return true
    }

    fun releaseOnValidationFailure(txnId: String?, payloadFingerprint: String? = null) {
        val cleanTxn = txnId?.trim().orEmpty()
        if (cleanTxn.isNotBlank()) {
            claimedTxnIds.remove(cleanTxn)
        }
        val fp = payloadFingerprint?.trim().orEmpty()
        if (fp.isNotBlank()) {
            recentFingerprints.remove(fp)
        }
    }
}
