package com.example.data

import com.example.core.todayIsoDate
import com.example.data.model.BulkLoanPartyRow
import com.example.data.model.BusyAccount
import com.example.data.model.BusyAccountGroup
import com.example.data.model.LeadSource
import com.example.data.model.LeadStatus
import com.example.data.model.PartyProject
import com.example.data.model.ProjectDivision
import com.example.data.model.SiteLogEntry
import com.example.data.model.SolarSystemType
import com.example.data.model.defaultWorkflowStages
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

data class BulkLoanSummary(
    val totalRows: Int,
    val selectedCount: Int,
    val duplicateCount: Int,
    val totalFirstLoanReceivedInr: Double,
    val totalSecondLoanPendingInr: Double
)

/**
 * Multi-format Bulk Loan Parties Importer supporting:
 * 1) CSV & OOXML Excel (.xlsx) & legacy HTML/TSV .xls files (pure-JVM, zero bloated deps)
 * 2) Sample CSV template generation
 * 3) Raw WhatsApp / ledger text multi-line parsing
 * 4) Duplicate detection & batch conversion to PartyProject + BUSY Sundry Debtors
 */
object BulkLoanImporter {

    fun buildSampleLoanPartiesCsv(): String = buildString {
        appendLine(
            "Party Name,Mobile,Site/City,kW Capacity,Referrer,Commission %,Total Contract,Sanctioned Loan," +
                "1st Loan Received,1st Loan Done,1st Loan Date,2nd Loan Received,2nd Loan Done,2nd Loan Date," +
                "Installation Done,RSEB File Applied,MCO OK,Meter Installed,Subsidy Received"
        )
        appendLine(
            "Ramesh Choudhary,9829012345,Dabok Udaipur,5,Thakur Ji,3,265000,200000,100000,Yes,2026-11-12,100000,No,,Yes,Yes,No,No,No"
        )
        appendLine(
            "Mahesh Patel,9414167890,Mavli Udaipur,3,Self,0,185000,140000,70000,Yes,2026-11-05,70000,Yes,2026-11-20,Yes,Yes,Yes,Yes,No"
        )
    }

    fun computeBatchSummary(rows: List<BulkLoanPartyRow>): BulkLoanSummary {
        val selectedRows = rows.filter { it.selected }
        return BulkLoanSummary(
            totalRows = rows.size,
            selectedCount = selectedRows.size,
            duplicateCount = rows.count { it.isDuplicateInDb },
            totalFirstLoanReceivedInr = selectedRows.sumOf { row ->
                if (row.loan1Done || row.loan1Amount > 0.0) row.loan1Amount else 0.0
            },
            totalSecondLoanPendingInr = selectedRows.sumOf { it.effectivePending2ndLoanInr }
        )
    }

    fun markDuplicates(
        rows: List<BulkLoanPartyRow>,
        existingParties: List<PartyProject>
    ): List<BulkLoanPartyRow> {
        val existingNames = existingParties.map { normalizeName(it.partyName) }.filter { it.isNotBlank() }.toSet()
        val existingMobiles = existingParties.map { normalizePhone(it.mobile) }.filter { it.length >= 10 }.toSet()
        val seenNamesInBatch = mutableSetOf<String>()
        val seenMobilesInBatch = mutableSetOf<String>()

        return rows.map { row ->
            val normName = normalizeName(row.partyName)
            val normPhone = normalizePhone(row.mobile)
            val reasons = mutableListOf<String>()
            if (normName.isNotBlank() && normName in existingNames) {
                reasons += "Already in DB"
            } else if (normName.isNotBlank() && !seenNamesInBatch.add(normName)) {
                reasons += "Duplicate in batch"
            }
            if (normPhone.length >= 10) {
                if (normPhone in existingMobiles) {
                    reasons += "Mobile in DB"
                } else if (!seenMobilesInBatch.add(normPhone)) {
                    reasons += "Duplicate mobile"
                }
            }
            val isDup = reasons.isNotEmpty()
            row.copy(
                isDuplicateInDb = isDup,
                duplicateReason = reasons.joinToString(" • "),
                selected = if (isDup) false else row.selected
            )
        }
    }

    fun parseCsvLoanParties(
        csvText: String,
        existingParties: List<PartyProject> = emptyList()
    ): List<BulkLoanPartyRow> {
        val clean = csvText.removePrefix("\uFEFF").trim()
        if (clean.isBlank()) return emptyList()
        val firstLine = clean.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
        val delimiter = detectDelimiter(firstLine)
        val table = parseDelimitedTable(clean, delimiter)
        return mapTableToLoanRows(table, existingParties)
    }

    fun parseXlsxLoanParties(
        bytes: ByteArray,
        existingParties: List<PartyProject> = emptyList()
    ): List<BulkLoanPartyRow> {
        if (bytes.isEmpty()) return emptyList()
        var sharedStringsXml: ByteArray? = null
        val worksheetEntries = mutableListOf<Pair<String, ByteArray>>()

        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val entryName = entry.name.replace('\\', '/')
                if (!entry.isDirectory) {
                    when {
                        entryName.equals("xl/sharedStrings.xml", ignoreCase = true) -> {
                            sharedStringsXml = zis.readBytes()
                        }
                        entryName.startsWith("xl/worksheets/sheet", ignoreCase = true) &&
                            entryName.endsWith(".xml", ignoreCase = true) -> {
                            worksheetEntries += entryName to zis.readBytes()
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val sheetBytes = worksheetEntries
            .sortedBy { it.first }
            .firstOrNull()?.second
            ?: return emptyList()

        val sharedStrings = sharedStringsXml?.let { parseSharedStringsXml(it) } ?: emptyList()
        val table = parseWorksheetXml(sheetBytes, sharedStrings)
        return mapTableToLoanRows(table, existingParties)
    }

    fun parseLegacyXlsOrText(
        bytes: ByteArray,
        existingParties: List<PartyProject> = emptyList()
    ): List<BulkLoanPartyRow> {
        if (bytes.isEmpty()) return emptyList()
        // If someone renamed an .xlsx as .xls, ZIP magic header PK\x03\x04 detects it cleanly
        if (bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
            val xlsxRows = runCatching { parseXlsxLoanParties(bytes, existingParties) }.getOrNull()
            if (!xlsxRows.isNullOrEmpty()) return xlsxRows
        }
        val asText = bytes.toString(Charsets.UTF_8)
        if (asText.contains("<tr", ignoreCase = true) && asText.contains("<td", ignoreCase = true)) {
            val htmlTable = parseHtmlTable(asText)
            val mapped = mapTableToLoanRows(htmlTable, existingParties)
            if (mapped.isNotEmpty()) return mapped
        }
        if (asText.count { it == ',' || it == '\t' || it == '|' } >= 3) {
            val csvRows = parseCsvLoanParties(asText, existingParties)
            if (csvRows.isNotEmpty()) return csvRows
        }
        return emptyList()
    }

    fun parseRawTextLoanParties(
        rawText: String,
        existingParties: List<PartyProject> = emptyList()
    ): List<BulkLoanPartyRow> {
        val clean = rawText.trim()
        if (clean.isBlank()) return emptyList()

        // First check if pasted text is actually CSV/TSV with header row
        val lines = clean.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.size >= 2) {
            val firstLower = lines.first().lowercase()
            if ((firstLower.contains("party") || firstLower.contains("name")) &&
                (firstLower.contains(",") || firstLower.contains("\t") || firstLower.contains("|"))
            ) {
                val delim = detectDelimiter(lines.first())
                val table = parseDelimitedTable(clean, delim)
                val mapped = mapTableToLoanRows(table, existingParties)
                if (mapped.isNotEmpty()) return mapped
            }
        }

        val parsed = lines.mapNotNull { line -> parseSingleRawLoanLine(line) }
        return markDuplicates(parsed, existingParties)
    }

    private fun parseSingleRawLoanLine(rawLine: String): BulkLoanPartyRow? {
        var line = rawLine.trim()
            .replace(Regex("""^\d+[.)\]]\s*"""), "")
            .trim()
        if (line.length < 3) return null

        val lower = line.lowercase()
        val mobileMatch = Regex("""(?<!\d)([6-9]\d{9})(?!\d)""").find(line)
        val mobile = mobileMatch?.groupValues?.getOrNull(1).orEmpty()

        val kwMatch = Regex("""(\d+(?:\.\d+)?)\s*kw\b""", RegexOption.IGNORE_CASE).find(line)
        val capacityKw = kwMatch?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.coerceIn(1.0, 500.0) ?: 3.0

        // Structured pipe/comma parts if delimited
        val tokens = if (line.contains("|")) {
            line.split("|").map { it.trim() }
        } else if (line.count { it == ',' } >= 2) {
            line.split(",").map { it.trim() }
        } else {
            emptyList()
        }

        var partyName = ""
        var siteCity = ""
        var referrerName = ""
        var commPct = 0.0
        var totalContract = 0.0
        var sanctionedLoan = 0.0
        var loan1Amt = 0.0
        var loan1Done = false
        var loan1Date = ""
        var loan2Amt = 0.0
        var loan2Done = false
        var loan2Date = ""

        if (tokens.isNotEmpty()) {
            partyName = tokens.first()
                .replace(Regex("""(?<!\d)[6-9]\d{9}(?!\d)"""), "")
                .replace(Regex("""\d+(?:\.\d+)?\s*kw\b""", RegexOption.IGNORE_CASE), "")
                .trim(' ', '-', ':', ',')
            for (tok in tokens.drop(1)) {
                val tl = tok.lowercase()
                when {
                    tl.contains("ref") || tl.contains("comm") || tl.contains("thakur") -> {
                        val pctMatch = Regex("""(\d+(?:\.\d+)?)\s*%""").find(tok)
                        if (pctMatch != null) commPct = pctMatch.groupValues[1].toDoubleOrNull() ?: 0.0
                        referrerName = tok
                            .replace(Regex("""ref(?:errer)?\s*:?""", RegexOption.IGNORE_CASE), "")
                            .replace(Regex("""\d+(?:\.\d+)?\s*%"""), "")
                            .trim(' ', '-', ':')
                    }
                    tl.contains("deal") || tl.contains("contract") || tl.contains("total") -> {
                        totalContract = extractCurrencyNumber(tok)
                    }
                    tl.contains("sanction") -> {
                        sanctionedLoan = extractCurrencyNumber(tok)
                    }
                    tl.contains("1st") || tl.contains("first") || tl.contains("loan 1") || tl.contains("loan1") -> {
                        loan1Amt = extractCurrencyNumber(tok)
                        loan1Done = hasPositiveStatus(tl)
                        loan1Date = extractDateFromSegment(tok)
                    }
                    tl.contains("2nd") || tl.contains("second") || tl.contains("loan 2") || tl.contains("loan2") -> {
                        loan2Amt = extractCurrencyNumber(tok)
                        loan2Done = hasPositiveStatus(tl)
                        loan2Date = extractDateFromSegment(tok)
                    }
                    !tl.contains("kw") && !Regex("""^[6-9]\d{9}$""").matches(tok) &&
                        !hasFlagKeyword(tl) && siteCity.isBlank() -> {
                        siteCity = tok
                    }
                }
            }
        } else {
            // Free-form line parsing
            val stopMatch = Regex(
                """(?i)\b(\d+(?:\.\d+)?\s*kw|deal|contract|sanction|1st|first|2nd|second|loan|inst|rseb|mco|meter|subsidy)\b|(?<!\d)[6-9]\d{9}(?!\d)"""
            ).find(line)
            val prefix = if (stopMatch != null) line.substring(0, stopMatch.range.first) else line
            partyName = prefix.trim(' ', '-', ':', ',', '|')

            Regex("""(?i)(?:deal|contract|total)\s*[:=]?\s*(₹?\s*\d+(?:[.,]\d+)?\s*(?:l|lac|lakh|k)?)""")
                .find(line)?.let { totalContract = parseIndianMoney(it.groupValues[1]) }

            Regex("""(?i)sanction(?:ed)?(?:\s*loan)?\s*[:=]?\s*(₹?\s*\d+(?:[.,]\d+)?\s*(?:l|lac|lakh|k)?)""")
                .find(line)?.let { sanctionedLoan = parseIndianMoney(it.groupValues[1]) }

            Regex("""(?i)(?:1st|first|l1)\s*(?:loan|tranche)?\s*[:=]?\s*(₹?\s*\d+(?:[.,]\d+)?\s*(?:l|lac|lakh|k)?)([^,|]*)""")
                .find(line)?.let {
                    loan1Amt = parseIndianMoney(it.groupValues[1])
                    val tail = it.groupValues[2].lowercase()
                    loan1Done = hasPositiveStatus(tail)
                    loan1Date = extractDateFromSegment(tail)
                }

            Regex("""(?i)(?:2nd|second|l2)\s*(?:loan|tranche)?\s*[:=]?\s*(₹?\s*\d+(?:[.,]\d+)?\s*(?:l|lac|lakh|k)?)([^,|]*)""")
                .find(line)?.let {
                    loan2Amt = parseIndianMoney(it.groupValues[1])
                    val tail = it.groupValues[2].lowercase()
                    loan2Done = hasPositiveStatus(tail)
                    loan2Date = extractDateFromSegment(tail)
                }

            if (lower.contains("thakur")) {
                referrerName = "Thakur Ji"
                Regex("""(\d+(?:\.\d+)?)\s*%""").find(line)?.let {
                    commPct = it.groupValues[1].toDoubleOrNull() ?: 0.0
                }
            }
        }

        if (partyName.isBlank()) return null

        val instDone = Regex("""(?i)\b(inst(?:allation)?\s*(?:done|ok|completed|yes)|work\s*done)\b""").containsMatchIn(lower)
        val rsebApplied = Regex("""(?i)\b(rseb|discom|file)\s*(?:applied|done|ok|submitted|yes)\b""").containsMatchIn(lower)
        val mcoOk = Regex("""(?i)\bmco\s*(?:ok|done|approved|yes)\b""").containsMatchIn(lower)
        val meterDone = Regex("""(?i)\bmeter\s*(?:installed|done|ok|yes)\b""").containsMatchIn(lower)
        val subsidyDone = Regex("""(?i)\bsubsidy\s*(?:received|credited|disbursed|done|ok|yes)\b""").containsMatchIn(lower)

        if (loan1Done && loan1Date.isBlank()) loan1Date = extractDateFromSegment(line)

        return BulkLoanPartyRow(
            partyName = partyName,
            mobile = mobile,
            siteCity = siteCity.ifBlank { "Udaipur" },
            capacityKw = capacityKw,
            referrerName = referrerName,
            commissionPct = commPct,
            totalContractInr = totalContract,
            sanctionedLoanInr = sanctionedLoan,
            loan1Amount = loan1Amt,
            loan1Done = loan1Done,
            loan1Date = loan1Date,
            loan2Amount = loan2Amt,
            loan2Done = loan2Done,
            loan2Date = loan2Date,
            installationDone = instDone,
            rsebFileApplied = rsebApplied,
            mcoOk = mcoOk,
            meterInstalled = meterDone,
            subsidyReceived = subsidyDone
        )
    }

    fun toPartyProjects(
        rows: List<BulkLoanPartyRow>,
        actorName: String,
        todayIso: String = todayIsoDate()
    ): Pair<List<PartyProject>, List<BusyAccount>> {
        val parties = mutableListOf<PartyProject>()
        val accounts = mutableListOf<BusyAccount>()

        rows.filter { it.selected && it.partyName.isNotBlank() }.forEach { row ->
            val partyId = UUID.randomUUID().toString()
            val cleanName = row.partyName.trim()
            val cleanCity = row.siteCity.trim().ifBlank { "Udaipur, Rajasthan" }
            val kw = row.capacityKw.coerceAtLeast(1.0)
            val source = when {
                row.referrerName.contains("thakur", ignoreCase = true) -> LeadSource.THAKUR_JI
                row.referrerName.isNotBlank() -> LeadSource.PERSONAL_REF
                else -> LeadSource.NB_DIRECT
            }
            val subsidyAmt = RealCostingCalculator.computePmSuryaGharSubsidy(kw, SolarSystemType.ON_GRID_DCR)
            val receivedInr = (if (row.loan1Done) row.loan1Amount else 0.0) +
                (if (row.loan2Done) row.loan2Amount else 0.0)
            val normL1Date = LoanReminderEngine.normalizeDateToIso(row.loan1Date)
            val normL2Date = LoanReminderEngine.normalizeDateToIso(row.loan2Date)

            val stages = defaultWorkflowStages().map { st ->
                val done = when (st.id) {
                    "LEAD_FILE_1", "LEAD_FILE_2" -> row.rsebFileApplied || row.installationDone || row.loan1Done || row.sanctionedLoanInr > 0
                    "LEAD_FILE_3" -> row.rsebFileApplied
                    "LOAN_1" -> row.sanctionedLoanInr > 0 || row.loan1Done || row.loan2Done
                    "LOAN_2" -> row.loan1Done
                    "LOAN_3" -> row.loan2Done
                    "INSTALLATION_1", "INSTALLATION_2", "INSTALLATION_3" -> row.installationDone
                    "NET_METERING_1" -> row.meterInstalled || row.mcoOk
                    "NET_METERING_2" -> row.meterInstalled
                    "NET_METERING_3" -> row.mcoOk
                    "SUBSIDY_CLOSE_1" -> row.subsidyReceived || row.mcoOk
                    "SUBSIDY_CLOSE_2" -> row.subsidyReceived
                    else -> false
                }
                if (!done) st else {
                    val ts = when (st.id) {
                        "LOAN_2" -> normL1Date.ifBlank { todayIso }
                        "LOAN_3" -> normL2Date.ifBlank { todayIso }
                        else -> todayIso
                    }
                    st.copy(completed = true, completedAt = ts, completedBy = actorName)
                }
            }

            parties += PartyProject(
                id = partyId,
                partyName = cleanName,
                mobile = row.mobile.trim(),
                address = cleanCity,
                cityOrLocation = cleanCity,
                capacityKw = kw,
                division = ProjectDivision.SOLAR,
                leadSource = source,
                referrerName = row.referrerName.trim(),
                leadDate = todayIso,
                quotedRatePerKw = if (row.totalContractInr > 0.0) row.totalContractInr / kw else 0.0,
                leadStatus = LeadStatus.CONVERTED,
                projectValue = row.totalContractInr.coerceAtLeast(0.0),
                receivedAmount = receivedInr.coerceAtLeast(0.0),
                sanctionedLoanAmount = row.sanctionedLoanInr.coerceAtLeast(0.0),
                loan1Amount = row.loan1Amount.coerceAtLeast(0.0),
                loan1Received = row.loan1Done,
                loan1Date = normL1Date,
                loan2Amount = row.loan2Amount.coerceAtLeast(0.0),
                loan2Received = row.loan2Done,
                loan2Date = normL2Date,
                installationDoneDate = if (row.installationDone) todayIso else "",
                rsebFileApplied = row.rsebFileApplied,
                mcoOk = row.mcoOk,
                meterInstalled = row.meterInstalled,
                subsidyReceived = row.subsidyReceived,
                subsidyAmount = subsidyAmt,
                partnerCommissionPct = row.commissionPct.coerceIn(0.0, 50.0),
                createdDate = todayIso,
                stages = stages,
                siteLogs = listOf(
                    SiteLogEntry(
                        timestamp = todayIso,
                        author = actorName,
                        message = "Bulk imported loan party (${kw}kW, Contract ₹${row.totalContractInr.toLong()}, Sanctioned ₹${row.sanctionedLoanInr.toLong()}, 1st Loan ${if (row.loan1Done) "Done" else "Pending"}, 2nd Loan ${if (row.loan2Done) "Done" else "Pending"})"
                    )
                )
            )

            accounts += BusyAccount(
                name = cleanName,
                group = BusyAccountGroup.SUNDRY_DEBTORS,
                openingBalance = 0.0,
                mobile = row.mobile.trim(),
                linkedPartyId = partyId,
                defaultCostCenter = source
            )
        }
        return parties to accounts
    }

    // --- TABLE MAPPING & PARSING HELPERS ---

    private enum class LoanCol {
        PARTY_NAME, MOBILE, SITE_CITY, KW_CAPACITY, REFERRER, COMMISSION_PCT,
        TOTAL_CONTRACT, SANCTIONED_LOAN, LOAN1_AMOUNT, LOAN1_DONE, LOAN1_DATE,
        LOAN2_AMOUNT, LOAN2_DONE, LOAN2_DATE, INSTALLATION_DONE, RSEB_FILE,
        MCO_OK, METER_INSTALLED, SUBSIDY_RECEIVED
    }

    private fun mapTableToLoanRows(
        table: List<List<String>>,
        existingParties: List<PartyProject>
    ): List<BulkLoanPartyRow> {
        val nonEmptyRows = table.filter { row -> row.any { it.isNotBlank() } }
        if (nonEmptyRows.isEmpty()) return emptyList()

        val headerIndex = nonEmptyRows.indexOfFirst { row ->
            row.any { cell ->
                val l = cell.lowercase()
                l.contains("party") || l.contains("customer") || l.contains("name") || l.contains("loan")
            }
        }.coerceAtLeast(0)

        val headers = nonEmptyRows[headerIndex]
        val colMap = mutableMapOf<Int, LoanCol>()
        headers.forEachIndexed { idx, header ->
            matchHeaderColumn(header)?.let { colMap[idx] = it }
        }

        // Positional fallback if no recognized headers were found
        val dataStart = if (colMap.isNotEmpty()) headerIndex + 1 else 0
        if (colMap.isEmpty()) {
            LoanCol.entries.forEachIndexed { idx, col -> colMap[idx] = col }
        }

        val parsed = buildList {
            for (rIdx in dataStart until nonEmptyRows.size) {
                val cells = nonEmptyRows[rIdx]
                var partyName = ""
                var mobile = ""
                var siteCity = ""
                var kw = 3.0
                var referrer = ""
                var commPct = 0.0
                var totalContract = 0.0
                var sanctioned = 0.0
                var l1Amt = 0.0
                var l1Done = false
                var l1Date = ""
                var l2Amt = 0.0
                var l2Done = false
                var l2Date = ""
                var instDone = false
                var rsebDone = false
                var mcoOk = false
                var meterDone = false
                var subsidyDone = false

                colMap.forEach { (colIdx, field) ->
                    val rawVal = cells.getOrNull(colIdx)?.trim().orEmpty()
                    if (rawVal.isBlank()) return@forEach
                    when (field) {
                        LoanCol.PARTY_NAME -> partyName = rawVal
                        LoanCol.MOBILE -> mobile = normalizePhone(rawVal).ifBlank { rawVal }
                        LoanCol.SITE_CITY -> siteCity = rawVal
                        LoanCol.KW_CAPACITY -> kw = rawVal.replace(Regex("""[^\d.]"""), "").toDoubleOrNull()?.coerceIn(1.0, 500.0) ?: 3.0
                        LoanCol.REFERRER -> {
                            referrer = rawVal
                            Regex("""(\d+(?:\.\d+)?)\s*%""").find(rawVal)?.let { m ->
                                commPct = m.groupValues[1].toDoubleOrNull() ?: commPct
                            }
                        }
                        LoanCol.COMMISSION_PCT -> commPct = rawVal.replace("%", "").trim().toDoubleOrNull() ?: commPct
                        LoanCol.TOTAL_CONTRACT -> totalContract = parseIndianMoney(rawVal)
                        LoanCol.SANCTIONED_LOAN -> sanctioned = parseIndianMoney(rawVal)
                        LoanCol.LOAN1_AMOUNT -> {
                            l1Amt = parseIndianMoney(rawVal)
                            if (hasPositiveStatus(rawVal.lowercase())) l1Done = true
                        }
                        LoanCol.LOAN1_DONE -> l1Done = parseBooleanFlag(rawVal)
                        LoanCol.LOAN1_DATE -> l1Date = LoanReminderEngine.normalizeDateToIso(rawVal)
                        LoanCol.LOAN2_AMOUNT -> {
                            l2Amt = parseIndianMoney(rawVal)
                            if (hasPositiveStatus(rawVal.lowercase())) l2Done = true
                        }
                        LoanCol.LOAN2_DONE -> l2Done = parseBooleanFlag(rawVal)
                        LoanCol.LOAN2_DATE -> l2Date = LoanReminderEngine.normalizeDateToIso(rawVal)
                        LoanCol.INSTALLATION_DONE -> instDone = parseBooleanFlag(rawVal)
                        LoanCol.RSEB_FILE -> rsebDone = parseBooleanFlag(rawVal)
                        LoanCol.MCO_OK -> mcoOk = parseBooleanFlag(rawVal)
                        LoanCol.METER_INSTALLED -> meterDone = parseBooleanFlag(rawVal)
                        LoanCol.SUBSIDY_RECEIVED -> subsidyDone = parseBooleanFlag(rawVal)
                    }
                }

                if (partyName.isNotBlank() && !partyName.equals("total", ignoreCase = true)) {
                    add(
                        BulkLoanPartyRow(
                            partyName = partyName,
                            mobile = mobile,
                            siteCity = siteCity,
                            capacityKw = kw,
                            referrerName = referrer,
                            commissionPct = commPct,
                            totalContractInr = totalContract,
                            sanctionedLoanInr = sanctioned,
                            loan1Amount = l1Amt,
                            loan1Done = l1Done,
                            loan1Date = l1Date,
                            loan2Amount = l2Amt,
                            loan2Done = l2Done,
                            loan2Date = l2Date,
                            installationDone = instDone,
                            rsebFileApplied = rsebDone,
                            mcoOk = mcoOk,
                            meterInstalled = meterDone,
                            subsidyReceived = subsidyDone
                        )
                    )
                }
            }
        }
        return markDuplicates(parsed, existingParties)
    }

    private fun matchHeaderColumn(header: String): LoanCol? {
        val h = header.lowercase().trim()
        return when {
            h.contains("1st") && h.contains("date") || h.contains("first") && h.contains("date") || h.contains("loan 1 date") -> LoanCol.LOAN1_DATE
            h.contains("2nd") && h.contains("date") || h.contains("second") && h.contains("date") || h.contains("loan 2 date") -> LoanCol.LOAN2_DATE
            h.contains("1st") && (h.contains("done") || h.contains("status")) ||
                h.contains("first") && (h.contains("done") || h.contains("status")) ||
                h.contains("loan 1 done") -> LoanCol.LOAN1_DONE
            h.contains("2nd") && (h.contains("done") || h.contains("status")) ||
                h.contains("second") && (h.contains("done") || h.contains("status")) ||
                h.contains("loan 2 done") -> LoanCol.LOAN2_DONE
            h.contains("1st") || h.contains("first loan") || h.contains("loan 1") || h.contains("tranche 1") -> LoanCol.LOAN1_AMOUNT
            h.contains("2nd") || h.contains("second loan") || h.contains("loan 2") || h.contains("tranche 2") -> LoanCol.LOAN2_AMOUNT
            h.contains("sanction") -> LoanCol.SANCTIONED_LOAN
            h.contains("contract") || h.contains("deal") || h.contains("project val") || h.contains("total amount") -> LoanCol.TOTAL_CONTRACT
            h.contains("commission") || h.contains("comm %") || h == "%" -> LoanCol.COMMISSION_PCT
            h.contains("referrer") || h.contains("reference") || h.contains("broker") || h.contains("agent") -> LoanCol.REFERRER
            h.contains("kw") || h.contains("capacity") || h.contains("size") -> LoanCol.KW_CAPACITY
            h.contains("mobile") || h.contains("phone") || h.contains("contact") -> LoanCol.MOBILE
            h.contains("site") || h.contains("city") || h.contains("location") || h.contains("address") || h.contains("village") -> LoanCol.SITE_CITY
            h.contains("install") -> LoanCol.INSTALLATION_DONE
            h.contains("rseb") || h.contains("discom") || h.contains("file applied") -> LoanCol.RSEB_FILE
            h.contains("mco") -> LoanCol.MCO_OK
            h.contains("meter") -> LoanCol.METER_INSTALLED
            h.contains("subsidy") -> LoanCol.SUBSIDY_RECEIVED
            h.contains("party") || h.contains("customer") || h.contains("name") || h.contains("beneficiary") || h.contains("client") -> LoanCol.PARTY_NAME
            else -> null
        }
    }

    private fun parseSharedStringsXml(bytes: ByteArray): List<String> {
        val doc = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
        }.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
        val siNodes = doc.getElementsByTagName("si")
        return buildList {
            for (i in 0 until siNodes.length) {
                val elem = siNodes.item(i) as? Element ?: continue
                val tNodes = elem.getElementsByTagName("t")
                val text = buildString {
                    for (j in 0 until tNodes.length) {
                        append(tNodes.item(j).textContent.orEmpty())
                    }
                }
                add(text)
            }
        }
    }

    private fun parseWorksheetXml(bytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val doc = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
        }.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
        val rowNodes = doc.getElementsByTagName("row")
        val rawRows = mutableListOf<MutableList<String>>()

        for (i in 0 until rowNodes.length) {
            val rowElem = rowNodes.item(i) as? Element ?: continue
            val cellNodes = rowElem.getElementsByTagName("c")
            val cells = mutableListOf<String>()
            var nextImplicitCol = 0

            for (j in 0 until cellNodes.length) {
                val cElem = cellNodes.item(j) as? Element ?: continue
                val ref = cElem.getAttribute("r")
                val colIdx = if (ref.isNotBlank()) colRefToIndex(ref) else nextImplicitCol
                nextImplicitCol = colIdx + 1
                while (cells.size <= colIdx) cells.add("")

                val cellType = cElem.getAttribute("t")
                val styleIdx = cElem.getAttribute("s")
                val value = when (cellType) {
                    "s" -> {
                        val idx = cElem.getElementsByTagName("v").item(0)?.textContent?.trim()?.toIntOrNull()
                        if (idx != null && idx in sharedStrings.indices) sharedStrings[idx] else ""
                    }
                    "inlineStr" -> {
                        val tNodes = cElem.getElementsByTagName("t")
                        buildString {
                            for (k in 0 until tNodes.length) append(tNodes.item(k).textContent.orEmpty())
                        }
                    }
                    "b" -> {
                        val b = cElem.getElementsByTagName("v").item(0)?.textContent?.trim()
                        if (b == "1") "Yes" else "No"
                    }
                    else -> {
                        cElem.getElementsByTagName("v").item(0)?.textContent?.trim().orEmpty()
                    }
                }
                // Check Excel serial date conversion later in date columns if numeric ~40000..60000
                cells[colIdx] = maybeConvertExcelSerialDate(value, colIdx, rawRows.firstOrNull(), styleIdx)
            }
            rawRows += cells
        }
        return rawRows
    }

    private fun maybeConvertExcelSerialDate(
        rawValue: String,
        colIdx: Int,
        headerRow: List<String>?,
        @Suppress("UNUSED_PARAMETER") styleIdx: String
    ): String {
        val header = headerRow?.getOrNull(colIdx)?.lowercase().orEmpty()
        if (!header.contains("date")) return rawValue
        val serial = rawValue.toDoubleOrNull() ?: return rawValue
        if (serial < 35000 || serial > 70000) return rawValue
        return try {
            val cal = Calendar.getInstance().apply {
                set(1899, Calendar.DECEMBER, 30, 0, 0, 0)
                add(Calendar.DAY_OF_YEAR, serial.toInt())
            }
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
        } catch (_: Exception) {
            rawValue
        }
    }

    private fun colRefToIndex(ref: String): Int {
        var idx = 0
        for (ch in ref) {
            if (ch in 'A'..'Z') {
                idx = idx * 26 + (ch - 'A' + 1)
            } else if (ch in 'a'..'z') {
                idx = idx * 26 + (ch - 'a' + 1)
            } else {
                break
            }
        }
        return (idx - 1).coerceAtLeast(0)
    }

    private fun parseHtmlTable(html: String): List<List<String>> {
        val rowRegex = Regex("""(?is)<tr[^>]*>(.*?)</tr>""")
        val cellRegex = Regex("""(?is)<t[dh][^>]*>(.*?)</t[dh]>""")
        val tagStrip = Regex("""<[^>]+>""")
        return rowRegex.findAll(html).map { rm ->
            cellRegex.findAll(rm.groupValues[1]).map { cm ->
                cm.groupValues[1]
                    .replace(tagStrip, " ")
                    .replace("&amp;", "&")
                    .replace("&nbsp;", " ")
                    .replace(Regex("""\s+"""), " ")
                    .trim()
            }.toList()
        }.toList()
    }

    private fun detectDelimiter(line: String): Char {
        val counts = listOf(',' to line.count { it == ',' }, '\t' to line.count { it == '\t' }, ';' to line.count { it == ';' }, '|' to line.count { it == '|' })
        return counts.maxByOrNull { it.second }?.takeIf { it.second > 0 }?.first ?: ','
    }

    private fun parseDelimitedTable(text: String, delimiter: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val currentRow = mutableListOf<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < text.length && text[i + 1] == '"') {
                        cell.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == delimiter && !inQuotes -> {
                    currentRow.add(cell.toString().trim())
                    cell.clear()
                }
                (c == '\n' || c == '\r') && !inQuotes -> {
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    currentRow.add(cell.toString().trim())
                    cell.clear()
                    if (currentRow.any { it.isNotBlank() }) rows.add(currentRow.toList())
                    currentRow.clear()
                }
                else -> cell.append(c)
            }
            i++
        }
        currentRow.add(cell.toString().trim())
        if (currentRow.any { it.isNotBlank() }) rows.add(currentRow.toList())
        return rows
    }

    private fun parseBooleanFlag(raw: String): Boolean {
        val clean = raw.trim().lowercase()
        return clean in setOf(
            "yes", "y", "true", "1", "done", "ok", "received", "credited",
            "disbursed", "paid", "completed", "applied", "installed", "✓", "✔"
        ) || clean.startsWith("done") || clean.startsWith("yes") || clean.startsWith("rec") || clean.startsWith("ok")
    }

    private fun hasPositiveStatus(lower: String): Boolean {
        return lower.contains("done") || lower.contains("rec") || lower.contains("yes") ||
            lower.contains("ok") || lower.contains("credited") || lower.contains("paid") ||
            lower.contains("completed")
    }

    private fun hasFlagKeyword(lower: String): Boolean =
        lower.contains("inst") || lower.contains("rseb") || lower.contains("mco") ||
            lower.contains("meter") || lower.contains("subsidy")

    private fun extractCurrencyNumber(segment: String): Double {
        val cleaned = segment
            .replace(Regex("""\d{4}[-/]\d{1,2}[-/]\d{1,2}|\d{1,2}[-/.]\d{1,2}[-/.]\d{2,4}"""), " ")
            .replace(Regex("""(?i)\b(?:1st|2nd|3rd|first|second)\b"""), " ")
            .replace(Regex("""(?i)\b(?:loan|tranche)\s*[12]\b"""), " ")
        val m = Regex("""₹?\s*(\d+(?:[.,]\d+)?\s*(?:lakh|lac|l|k)?)""", RegexOption.IGNORE_CASE).find(cleaned)
        return m?.let { parseIndianMoney(it.groupValues[1]) } ?: 0.0
    }

    fun parseIndianMoney(raw: String): Double {
        val s = raw.trim().lowercase().replace("₹", "").replace(",", "").replace("rs.", "").replace("rs", "").trim()
        if (s.isBlank()) return 0.0
        val multiplier = when {
            s.endsWith("lakh") -> 100_000.0
            s.endsWith("lac") -> 100_000.0
            s.endsWith("l") -> 100_000.0
            s.endsWith("k") -> 1_000.0
            else -> 1.0
        }
        val numStr = s.replace(Regex("""[^\d.]"""), "")
        val base = numStr.toDoubleOrNull() ?: return 0.0
        return (base * multiplier).takeIf { !it.isNaN() && !it.isInfinite() } ?: 0.0
    }

    private fun extractDateFromSegment(segment: String): String {
        val m = Regex("""(\d{4}[-/]\d{1,2}[-/]\d{1,2}|\d{1,2}[-/.]\d{1,2}[-/.]\d{2,4})""").find(segment)
            ?: return ""
        return LoanReminderEngine.normalizeDateToIso(m.value)
    }

    private fun normalizeName(name: String): String =
        name.trim().lowercase().replace(Regex("""\s+"""), " ")

    private fun normalizePhone(phone: String): String =
        phone.filter { it.isDigit() }.takeLast(10)
}
