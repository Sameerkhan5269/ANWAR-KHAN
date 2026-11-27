package com.example.data

import com.example.core.LedgerPrintRow
import com.example.data.model.BusyAccount
import com.example.data.model.BusyAccountGroup
import com.example.data.model.BusyVoucher
import com.example.data.model.BusyVoucherType
import com.example.data.model.DcrPanel
import com.example.data.model.LeadSource
import com.example.data.model.PartyProject
import com.example.data.model.RawMaterial
import com.example.data.model.SiteKharchaEntry
import com.example.data.model.SolarMeter

data class AccountLedgerResult(
    val account: BusyAccount,
    val openingBalance: Double, // +Dr, -Cr
    val rows: List<LedgerPrintRow>,
    val totalDebit: Double,
    val totalCredit: Double,
    val closingBalance: Double
)

data class TrialBalanceGroupRow(
    val group: BusyAccountGroup,
    val debitTotal: Double,
    val creditTotal: Double
)

data class TrialBalanceSummary(
    val groups: List<TrialBalanceGroupRow>,
    val grandDebit: Double,
    val grandCredit: Double,
    val totalSalesIncome: Double,
    val totalPurchaseAndExpenses: Double,
    val netProfit: Double
)

data class GstSummaryReport(
    val outputTaxable: Double,
    val outputCgst: Double,
    val outputSgst: Double,
    val outputIgst: Double,
    val inputTaxable: Double,
    val inputCgst: Double,
    val inputSgst: Double,
    val inputIgst: Double,
    val gstr1Sales: List<BusyVoucher>,
    val gstr2bPurchases: List<BusyVoucher>
) {
    val totalOutputGst: Double get() = outputCgst + outputSgst + outputIgst
    val totalInputGst: Double get() = inputCgst + inputSgst + inputIgst
    val netGstPayable: Double get() = totalOutputGst - totalInputGst
}

data class CostCenterFinancialRow(
    val source: LeadSource,
    val partyCount: Int,
    val totalSales: Double,
    val totalReceipts: Double,
    val totalDirectCosts: Double,
    val grossProfit: Double,
    val partnerSplitAmount: Double,
    val nbNetProfit: Double
)

data class PartyAgingRow(
    val partyId: String,
    val partyName: String,
    val source: LeadSource,
    val projectValue: Double,
    val receivedAmount: Double,
    val pendingDues: Double,
    val loan1Amount: Double,
    val loan1Received: Boolean,
    val loan2Amount: Double,
    val loan2Received: Boolean,
    val isOverdueAlert: Boolean
)

data class DayByDayFinancialRow(
    val date: String,
    val voucherCount: Int,
    val salesBilled: Double,
    val receiptsCollected: Double,
    val purchasesBilled: Double,
    val paymentsAndKharcha: Double
) {
    val netCashFlow: Double get() = receiptsCollected - paymentsAndKharcha
}

data class PartyCostingRow(
    val party: PartyProject,
    val dealValue: Double,
    val mainMaterialCost: Double,
    val onSitePurchaseCost: Double,
    val dailyKharchaCost: Double,
    val referralCommission: Double
) {
    val totalDirectCost: Double get() = mainMaterialCost + onSitePurchaseCost + dailyKharchaCost + referralCommission
    val netProfit: Double get() = dealValue - totalDirectCost
    val marginPct: Double get() = if (dealValue > 0.0) (netProfit / dealValue) * 100.0 else 0.0
}

data class ReferralCommissionRow(
    val partyId: String,
    val partyName: String,
    val leadSource: LeadSource,
    val referrerName: String,
    val referrerPhone: String,
    val dealValue: Double,
    val commissionPct: Double,
    val commissionAmount: Double,
    val isPaid: Boolean,
    val payoutDate: String
)

/**
 * Pure double-entry accounting calculator replicating BUSY 21 report outputs.
 */
object BusyAccountingEngine {

    /**
     * Computes Account Ledger (Alt + L) with opening balance, debit/credit columns, and running Dr/Cr balance.
     * Double-entry convention:
     * - SALES (F8): Party Account is DEBITED (+total); Contra/Sales Account is CREDITED (-total)
     * - PURCHASE (F9): Party/Supplier Account is CREDITED (-total); Contra/Purchase Account is DEBITED (+total)
     * - PAYMENT (F5): Party/Expense Account is DEBITED (+total); Bank/Cash Contra Account is CREDITED (-total)
     * - RECEIPT (F6): Party/Customer Account is CREDITED (-total); Bank/Cash Contra Account is DEBITED (+total)
     * - JOURNAL (F7): Party Account is DEBITED (+total); Contra Account is CREDITED (-total)
     * - CONTRA (F4): Target Bank/Cash (Party field) is DEBITED (+total); Source Bank/Cash (Contra field) is CREDITED (-total)
     */
    fun computeAccountLedger(
        account: BusyAccount,
        vouchers: List<BusyVoucher>,
        fromDate: String = "",
        toDate: String = ""
    ): AccountLedgerResult {
        val sorted = vouchers.sortedWith(compareBy<BusyVoucher> { it.date }.thenBy { it.voucherNo })

        var running = account.openingBalance
        var totalDr = if (account.openingBalance > 0) account.openingBalance else 0.0
        var totalCr = if (account.openingBalance < 0) -account.openingBalance else 0.0
        val rows = mutableListOf<LedgerPrintRow>()

        for (v in sorted) {
            if (fromDate.isNotBlank() && v.date < fromDate) {
                val (dr, cr) = entryImpactForAccount(account, v)
                running += (dr - cr)
                continue
            }
            if (toDate.isNotBlank() && v.date > toDate) continue

            val (dr, cr) = entryImpactForAccount(account, v)
            if (dr == 0.0 && cr == 0.0) continue

            running += (dr - cr)
            totalDr += dr
            totalCr += cr

            val isPartySide = v.partyAccountId == account.id ||
                v.partyAccountName.equals(account.name, ignoreCase = true)
            val particulars = if (isPartySide) {
                v.contraAccountName.ifBlank { v.type.label }
            } else {
                v.partyAccountName.ifBlank { v.type.label }
            }

            rows.add(
                LedgerPrintRow(
                    date = v.date,
                    voucherType = "${v.type.label} (${v.type.shortcut})",
                    voucherNo = v.voucherNo,
                    particulars = particulars,
                    narration = buildString {
                        if (v.utrOrChequeNo.isNotBlank()) append("UTR/Ref: ${v.utrOrChequeNo}. ")
                        append(v.narration)
                    }.trim(),
                    debit = dr,
                    credit = cr,
                    runningBalance = running
                )
            )
        }

        return AccountLedgerResult(
            account = account,
            openingBalance = account.openingBalance,
            rows = rows,
            totalDebit = totalDr,
            totalCredit = totalCr,
            closingBalance = running
        )
    }

    private fun entryImpactForAccount(account: BusyAccount, v: BusyVoucher): Pair<Double, Double> {
        val matchesParty = v.partyAccountId == account.id ||
            (v.partyAccountId.isBlank() && v.partyAccountName.equals(account.name, ignoreCase = true))
        val matchesContra = v.contraAccountId == account.id ||
            (v.contraAccountId.isBlank() && v.contraAccountName.equals(account.name, ignoreCase = true))

        if (!matchesParty && !matchesContra) return 0.0 to 0.0
        val amt = v.totalAmount.coerceAtLeast(0.0)

        var dr = 0.0
        var cr = 0.0

        when (v.type) {
            BusyVoucherType.SALES, BusyVoucherType.PAYMENT, BusyVoucherType.JOURNAL, BusyVoucherType.CONTRA -> {
                if (matchesParty) dr += amt
                if (matchesContra) cr += amt
            }
            BusyVoucherType.PURCHASE, BusyVoucherType.RECEIPT -> {
                if (matchesParty) cr += amt
                if (matchesContra) dr += amt
            }
        }
        return dr to cr
    }

    fun computeTrialBalance(
        accounts: List<BusyAccount>,
        vouchers: List<BusyVoucher>
    ): TrialBalanceSummary {
        val groupMap = BusyAccountGroup.entries.associateWith { Pair(0.0, 0.0) }.toMutableMap()
        var salesTotal = 0.0
        var expenseTotal = 0.0

        accounts.forEach { acc ->
            val ledger = computeAccountLedger(acc, vouchers)
            val bal = ledger.closingBalance
            val (curDr, curCr) = groupMap[acc.group] ?: (0.0 to 0.0)
            if (bal >= 0) {
                groupMap[acc.group] = (curDr + bal) to curCr
            } else {
                groupMap[acc.group] = curDr to (curCr + (-bal))
            }
            when (acc.group) {
                BusyAccountGroup.SALES_ACCOUNTS -> salesTotal += (-bal).coerceAtLeast(0.0)
                BusyAccountGroup.PURCHASE_ACCOUNTS,
                BusyAccountGroup.DIRECT_EXPENSES,
                BusyAccountGroup.INDIRECT_EXPENSES -> expenseTotal += bal.coerceAtLeast(0.0)
                else -> {}
            }
        }

        val rows = BusyAccountGroup.entries.map { grp ->
            val (d, c) = groupMap[grp] ?: (0.0 to 0.0)
            TrialBalanceGroupRow(grp, d, c)
        }
        return TrialBalanceSummary(
            groups = rows,
            grandDebit = rows.sumOf { it.debitTotal },
            grandCredit = rows.sumOf { it.creditTotal },
            totalSalesIncome = salesTotal,
            totalPurchaseAndExpenses = expenseTotal,
            netProfit = salesTotal - expenseTotal
        )
    }

    fun computeGstSummary(vouchers: List<BusyVoucher>): GstSummaryReport {
        val sales = vouchers.filter { it.type == BusyVoucherType.SALES }
        val purchases = vouchers.filter { it.type == BusyVoucherType.PURCHASE }

        return GstSummaryReport(
            outputTaxable = sales.sumOf { it.taxableAmount },
            outputCgst = sales.sumOf { it.cgstAmount },
            outputSgst = sales.sumOf { it.sgstAmount },
            outputIgst = sales.sumOf { it.igstAmount },
            inputTaxable = purchases.sumOf { it.taxableAmount },
            inputCgst = purchases.sumOf { it.cgstAmount },
            inputSgst = purchases.sumOf { it.sgstAmount },
            inputIgst = purchases.sumOf { it.igstAmount },
            gstr1Sales = sales.sortedByDescending { it.date },
            gstr2bPurchases = purchases.sortedByDescending { it.date }
        )
    }

    fun computeCostCenterSummary(
        parties: List<PartyProject>,
        vouchers: List<BusyVoucher>
    ): List<CostCenterFinancialRow> {
        return LeadSource.entries.map { src ->
            val srcParties = parties.filter { it.leadSource == src }
            val srcVouchers = vouchers.filter { it.costCenter == src }
            val sales = srcVouchers.filter { it.type == BusyVoucherType.SALES }.sumOf { it.totalAmount }
                .let { if (it > 0) it else srcParties.sumOf { p -> p.projectValue } }
            val receipts = srcVouchers.filter { it.type == BusyVoucherType.RECEIPT }.sumOf { it.totalAmount }
                .let { if (it > 0) it else srcParties.sumOf { p -> p.receivedAmount } }
            val costs = srcVouchers.filter {
                it.type == BusyVoucherType.PURCHASE || it.type == BusyVoucherType.PAYMENT
            }.sumOf { it.totalAmount }

            val grossProfit = (sales - costs).coerceAtLeast(0.0)
            // Compute partner commission/split based on party commission % (or Thakur Ji journal entries)
            val journalPartnerSplit = srcVouchers.filter {
                it.type == BusyVoucherType.JOURNAL &&
                    (it.partyAccountName.contains("Thakur", ignoreCase = true) ||
                        it.narration.contains("split", ignoreCase = true) ||
                        it.narration.contains("commission", ignoreCase = true))
            }.sumOf { it.totalAmount }

            val pctSplit = srcParties.sumOf { p ->
                val pct = if (p.partnerCommissionPct > 0) p.partnerCommissionPct else {
                    if (src == LeadSource.THAKUR_JI) 10.0 else 0.0
                }
                (p.projectValue * (pct / 100.0))
            }
            val partnerSplit = if (journalPartnerSplit > 0) journalPartnerSplit else pctSplit

            CostCenterFinancialRow(
                source = src,
                partyCount = srcParties.size,
                totalSales = sales,
                totalReceipts = receipts,
                totalDirectCosts = costs,
                grossProfit = grossProfit,
                partnerSplitAmount = partnerSplit,
                nbNetProfit = (grossProfit - partnerSplit)
            )
        }
    }

    fun computePartyAging(parties: List<PartyProject>): List<PartyAgingRow> {
        return parties.map { p ->
            val installationDone = p.stages.any { it.id == "INSTALLATION_3" && it.completed }
            val isOverdue = p.pendingAmount > 0 && (installationDone || (!p.loan2Received && p.loan2Amount > 0))
            PartyAgingRow(
                partyId = p.id,
                partyName = p.partyName,
                source = p.leadSource,
                projectValue = p.projectValue,
                receivedAmount = p.receivedAmount,
                pendingDues = p.pendingAmount,
                loan1Amount = p.loan1Amount,
                loan1Received = p.loan1Received,
                loan2Amount = p.loan2Amount,
                loan2Received = p.loan2Received,
                isOverdueAlert = isOverdue
            )
        }.sortedByDescending { it.pendingDues }
    }

    fun computeDayByDayReport(
        vouchers: List<BusyVoucher>,
        siteKharcha: List<SiteKharchaEntry>
    ): List<DayByDayFinancialRow> {
        val allDates = (vouchers.map { it.date } + siteKharcha.map { it.date })
            .filter { it.isNotBlank() }
            .distinct()
            .sortedDescending()

        return allDates.map { date ->
            val dayVouchers = vouchers.filter { it.date == date }
            val dayKharcha = siteKharcha.filter { it.date == date && it.linkedVoucherNo.isBlank() }
            val sales = dayVouchers.filter { it.type == BusyVoucherType.SALES }.sumOf { it.totalAmount }
            val receipts = dayVouchers.filter { it.type == BusyVoucherType.RECEIPT }.sumOf { it.totalAmount }
            val purchases = dayVouchers.filter { it.type == BusyVoucherType.PURCHASE }.sumOf { it.totalAmount }
            val payments = dayVouchers.filter { it.type == BusyVoucherType.PAYMENT }.sumOf { it.totalAmount } +
                dayKharcha.sumOf { it.amount }
            DayByDayFinancialRow(
                date = date,
                voucherCount = dayVouchers.size + dayKharcha.size,
                salesBilled = sales,
                receiptsCollected = receipts,
                purchasesBilled = purchases,
                paymentsAndKharcha = payments
            )
        }
    }

    fun computePartyCostingReport(
        parties: List<PartyProject>,
        dcrPanels: List<DcrPanel>,
        solarMeters: List<SolarMeter>,
        rawMaterials: List<RawMaterial>,
        siteKharcha: List<SiteKharchaEntry>
    ): List<PartyCostingRow> {
        return parties.map { party ->
            val panelCost = dcrPanels.filter { it.linkedPartyId == party.id }.sumOf { it.unitCost }
            val meterCost = solarMeters.filter { it.installedPartyId == party.id }.sumOf { it.unitCost }
            val bosCost = rawMaterials.sumOf { rm ->
                rm.issueLogs.filter { it.partyId == party.id }.sumOf { it.quantity * rm.unitCost }
            }
            val mainMat = panelCost + meterCost + bosCost
            val siteMat = party.totalOnSitePurchases
            val kharcha = siteKharcha.filter { it.partyId == party.id }.sumOf { it.amount }
            val pct = if (party.partnerCommissionPct > 0.0) party.partnerCommissionPct else {
                if (party.leadSource == LeadSource.THAKUR_JI) 10.0 else 0.0
            }
            val refComm = party.projectValue * (pct / 100.0)
            PartyCostingRow(
                party = party,
                dealValue = party.projectValue,
                mainMaterialCost = mainMat,
                onSitePurchaseCost = siteMat,
                dailyKharchaCost = kharcha,
                referralCommission = refComm
            )
        }
    }

    fun computeReferralCommissions(parties: List<PartyProject>): List<ReferralCommissionRow> {
        return parties.mapNotNull { party ->
            val pct = if (party.partnerCommissionPct > 0.0) party.partnerCommissionPct else {
                if (party.leadSource == LeadSource.THAKUR_JI) 10.0 else 0.0
            }
            val isRef = party.leadSource != LeadSource.NB_DIRECT || pct > 0.0 || party.referrerName.isNotBlank()
            if (!isRef) return@mapNotNull null
            val amount = party.projectValue * (pct / 100.0)
            ReferralCommissionRow(
                partyId = party.id,
                partyName = party.partyName,
                leadSource = party.leadSource,
                referrerName = party.referrerName.ifBlank {
                    if (party.leadSource == LeadSource.THAKUR_JI) "Thakur Ji Partner" else "Personal Referrer"
                },
                referrerPhone = party.referrerPhone,
                dealValue = party.projectValue,
                commissionPct = pct,
                commissionAmount = amount,
                isPaid = party.referralPaid,
                payoutDate = party.referralPayoutDate
            )
        }
    }
}
