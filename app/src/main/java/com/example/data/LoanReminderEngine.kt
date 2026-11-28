package com.example.data

import com.example.core.formatInr
import com.example.core.todayIsoDate
import com.example.data.model.LoanReminderItem
import com.example.data.model.LoanReminderRule
import com.example.data.model.PartyProject
import com.example.data.model.ProjectDivision
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Automated 5-Rule Loan, Installation, Meter, Subsidy & Persistent RSEB MCO Reminder Engine.
 */
object LoanReminderEngine {

    fun computeReminders(
        parties: List<PartyProject>,
        todayIso: String = todayIsoDate()
    ): List<LoanReminderItem> {
        val items = mutableListOf<LoanReminderItem>()

        parties.filter { it.division == ProjectDivision.SOLAR || it.division == ProjectDivision.HYBRID }
            .forEach { party ->
                val isLoanCase = party.sanctionedLoanAmount > 0.0 ||
                    party.loan1Amount > 0.0 ||
                    party.loan2Amount > 0.0 ||
                    party.isLoan1Done ||
                    party.isLoan2Done ||
                    party.stages.firstOrNull { it.id == "LOAN_1" }?.completed == true

                val loan1Amt = if (party.loan1Amount > 0.0) {
                    party.loan1Amount
                } else if (party.sanctionedLoanAmount > 0.0) {
                    party.sanctionedLoanAmount * 0.5
                } else 0.0

                val loan2Amt = if (party.loan2Amount > 0.0) {
                    party.loan2Amount
                } else {
                    (party.sanctionedLoanAmount - loan1Amt).coerceAtLeast(0.0)
                }

                // Rule 1: 1st Loan Pending
                if (isLoanCase && !party.isLoan1Done) {
                    items += LoanReminderItem(
                        id = "${party.id}_rule1",
                        partyId = party.id,
                        partyName = party.partyName,
                        mobile = party.mobile,
                        capacityKw = party.capacityKw,
                        rule = LoanReminderRule.FIRST_LOAN_PENDING,
                        title = "1st Loan Tranche Pending",
                        detail = if (loan1Amt > 0) {
                            "Follow up with Bank for 1st Tranche (${formatInr(loan1Amt)})."
                        } else {
                            "Bank loan file pending 1st Tranche disbursement."
                        },
                        amountInr = loan1Amt,
                        isUrgent = party.isRsebFileApplied
                    )
                }

                // Rule 2: 1st Loan Done -> Installation 2-Day Alert
                if (party.isLoan1Done && !party.isInstallationDone) {
                    val days = daysBetweenIsoDates(party.loan1Date, todayIso)
                    val overdue = days >= 2
                    items += LoanReminderItem(
                        id = "${party.id}_rule2",
                        partyId = party.id,
                        partyName = party.partyName,
                        mobile = party.mobile,
                        capacityKw = party.capacityKw,
                        rule = LoanReminderRule.INSTALLATION_2DAY_ALERT,
                        title = if (overdue) {
                            "Installation Overdue ($days days since 1st Loan)"
                        } else {
                            "Schedule Site Installation (Within 2 Days of 1st Loan)"
                        },
                        detail = buildString {
                            append("1st Loan received")
                            if (party.loan1Date.isNotBlank()) append(" on ${party.loan1Date}")
                            append(". Complete GI structure, panel & inverter installation immediately.")
                        },
                        amountInr = loan1Amt,
                        isUrgent = overdue,
                        daysElapsed = days
                    )
                }

                // Rule 3: Meter Installed -> Collect 2nd Loan
                if (isLoanCase && party.isMeterInstalled && !party.isLoan2Done) {
                    items += LoanReminderItem(
                        id = "${party.id}_rule3",
                        partyId = party.id,
                        partyName = party.partyName,
                        mobile = party.mobile,
                        capacityKw = party.capacityKw,
                        rule = LoanReminderRule.METER_TO_SECOND_LOAN,
                        title = "Net Meter Installed -> Claim 2nd Loan",
                        detail = if (loan2Amt > 0) {
                            "Submit meter & geotag report to bank for 2nd Tranche (${formatInr(loan2Amt)})."
                        } else {
                            "Net meter installed — follow up bank for 2nd Loan disbursement."
                        },
                        amountInr = loan2Amt,
                        isUrgent = true
                    )
                }

                // Rule 4: 2nd Loan Done -> 5-Day Subsidy Alert
                if (party.isLoan2Done && !party.isSubsidyReceived) {
                    val days = daysBetweenIsoDates(party.loan2Date, todayIso)
                    val overdue = days >= 5
                    val subAmt = if (party.subsidyAmount > 0.0) {
                        party.subsidyAmount
                    } else {
                        RealCostingCalculator.computePmSuryaGharSubsidy(
                            party.capacityKw,
                            com.example.data.model.SolarSystemType.ON_GRID_DCR
                        )
                    }
                    items += LoanReminderItem(
                        id = "${party.id}_rule4",
                        partyId = party.id,
                        partyName = party.partyName,
                        mobile = party.mobile,
                        capacityKw = party.capacityKw,
                        rule = LoanReminderRule.SECOND_LOAN_TO_SUBSIDY,
                        title = if (overdue) {
                            "5-Day Subsidy Follow-Up Overdue ($days days)"
                        } else {
                            "PM Surya Ghar Subsidy Claim Due (5-Day Track)"
                        },
                        detail = buildString {
                            append("2nd Loan received")
                            if (party.loan2Date.isNotBlank()) append(" on ${party.loan2Date}")
                            append(". Verify DBT Subsidy (${formatInr(subAmt)}) credit.")
                        },
                        amountInr = subAmt,
                        isUrgent = overdue,
                        daysElapsed = days
                    )
                }

                // Rule 5: Persistent RSEB MCO Status Popup until "MCO OK" is ticked
                if ((party.isInstallationDone || party.isRsebFileApplied || party.isMeterInstalled) && !party.isMcoOk) {
                    items += LoanReminderItem(
                        id = "${party.id}_rule5",
                        partyId = party.id,
                        partyName = party.partyName,
                        mobile = party.mobile,
                        capacityKw = party.capacityKw,
                        rule = LoanReminderRule.PERSISTENT_RSEB_MCO,
                        title = "RSEB MCO Pending — Tick 'MCO OK' When Approved",
                        detail = buildString {
                            append(if (party.isInstallationDone) "Installation Done" else "RSEB File Applied")
                            if (party.consumerNumber.isNotBlank()) append(" • K-No: ${party.consumerNumber}")
                            append(" • Persistent alert stays active until MCO OK is ticked.")
                        },
                        isUrgent = party.isInstallationDone
                    )
                }
            }

        return items.sortedWith(
            compareByDescending<LoanReminderItem> { it.rule == LoanReminderRule.PERSISTENT_RSEB_MCO && it.isUrgent }
                .thenByDescending { it.isUrgent }
                .thenBy { it.rule.ordinal }
                .thenBy { it.partyName.lowercase() }
        )
    }

    fun daysBetweenIsoDates(fromDateRaw: String, toIsoDate: String): Int {
        val normFrom = normalizeDateToIso(fromDateRaw)
        if (normFrom.isBlank()) return 0
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
        return try {
            val d1 = sdf.parse(normFrom) ?: return 0
            val d2 = sdf.parse(toIsoDate) ?: return 0
            val diff = d2.time - d1.time
            TimeUnit.MILLISECONDS.toDays(diff).toInt().coerceAtLeast(0)
        } catch (_: Exception) {
            0
        }
    }

    fun normalizeDateToIso(raw: String): String {
        val cleaned = raw.trim()
        if (cleaned.isBlank()) return ""
        val isoRegex = Regex("""^(\d{4})[-/](\d{1,2})[-/](\d{1,2})$""")
        isoRegex.matchEntire(cleaned)?.let { m ->
            val (y, mo, d) = m.destructured
            return "${y.padStart(4, '0')}-${mo.padStart(2, '0')}-${d.padStart(2, '0')}"
        }
        val dmyRegex = Regex("""^(\d{1,2})[-/.](\d{1,2})[-/.](\d{2,4})$""")
        dmyRegex.matchEntire(cleaned)?.let { m ->
            val (d, mo, yRaw) = m.destructured
            val y = if (yRaw.length == 2) "20$yRaw" else yRaw.padStart(4, '0')
            return "$y-${mo.padStart(2, '0')}-${d.padStart(2, '0')}"
        }
        return ""
    }
}
