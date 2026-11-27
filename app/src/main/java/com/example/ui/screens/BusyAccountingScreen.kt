package com.example.ui.screens

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.ExportPrintHelper
import com.example.core.RolePermissions
import com.example.core.formatBusyDrCr
import com.example.core.formatInr
import com.example.core.safeDouble
import com.example.data.BusyAccountingEngine
import com.example.data.model.BillRefType
import com.example.data.model.BusyAccount
import com.example.data.model.BusyAccountGroup
import com.example.data.model.BusyItem
import com.example.data.model.BusyLineItem
import com.example.data.model.BusyVoucher
import com.example.data.model.BusyVoucherType
import com.example.data.model.DcrPanel
import com.example.data.model.LeadSource
import com.example.data.model.ParsedPurchaseBill
import com.example.data.model.PartyProject
import com.example.data.model.RawMaterial
import com.example.data.model.SiteKharchaEntry
import com.example.data.model.SolarMeter
import com.example.data.model.StaffRole
import com.example.ui.theme.LimeGreen
import com.example.ui.theme.LimeSoft
import com.example.ui.theme.LocalSolarColors
import com.example.ui.theme.MaroonPrimary
import com.example.ui.theme.TabularNumberStyle
import com.example.ui.viewmodel.AiScanUiState
import java.util.Locale

private enum class BusyMenuSection(val label: String) {
    VOUCHERS("Vouchers"),
    DISPLAY("Display Reports"),
    GST("GST & GSTR"),
    MASTERS("Masters"),
    AI_IMPORT("AI Auto-Import")
}

private enum class BusyReportTab(val label: String) {
    LEDGER("Account Ledger (Alt+L)"),
    DAY_BOOK("Day Book"),
    DAY_BY_DAY("Day-by-Day"),
    TRIAL_BALANCE("Trial Balance & P&L"),
    PARTY_COSTING("Party Costing"),
    AGING("Receivable Aging"),
    COST_CENTERS("Cost Centers"),
    REFERRALS("Referrals")
}

@Composable
fun BusyAccountingScreen(
    role: StaffRole,
    accounts: List<BusyAccount>,
    items: List<BusyItem>,
    vouchers: List<BusyVoucher>,
    parties: List<PartyProject>,
    dcrPanels: List<DcrPanel>,
    solarMeters: List<SolarMeter>,
    rawMaterials: List<RawMaterial>,
    siteKharcha: List<SiteKharchaEntry>,
    aiState: AiScanUiState,
    onAddAccount: (String, BusyAccountGroup, Double, String, LeadSource) -> Unit,
    onAddItem: (String, String, String, Double, Double, Double, Double) -> Unit,
    onPostVoucher: (
        BusyVoucherType, String, BusyAccount, BusyAccount, LeadSource, Boolean,
        Double, Double, BillRefType, String, String, String, String, List<BusyLineItem>
    ) -> Boolean,
    onDeleteVoucher: (String) -> Unit,
    onMarkReferralPaid: (String, Boolean) -> Unit,
    onScanBankUri: (Uri, String) -> Unit,
    onScanBankText: (String, String) -> Unit,
    onToggleBankRow: (String) -> Unit,
    onPostReviewedBank: () -> Unit,
    onScanPurchaseBillUri: (Uri) -> Unit,
    onPostReviewedBill: (ParsedPurchaseBill, LeadSource) -> Unit,
    onResetAi: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    // Hard privacy firewall — should never be reached by non-accounting roles
    if (!RolePermissions.canAccessAccounting(role)) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Access restricted to Owner & Accounts Manager.")
        }
        return
    }

    val context = LocalContext.current
    val solarColors = LocalSolarColors.current
    val canDelete = RolePermissions.canDeleteRecords(role)
    val canSeePartnerSplit = RolePermissions.canViewPartnerSplits(role)

    var activeMenu by remember { mutableStateOf(BusyMenuSection.VOUCHERS) }
    var activeReport by remember { mutableStateOf(BusyReportTab.LEDGER) }
    var voucherDialogType by remember { mutableStateOf<BusyVoucherType?>(null) }
    var showAddAccountDialog by remember { mutableStateOf(false) }
    var showAddItemDialog by remember { mutableStateOf(false) }

    // Ledger report filters
    var selectedLedgerAccId by remember(accounts) { mutableStateOf(accounts.firstOrNull()?.id.orEmpty()) }
    var fromDate by remember { mutableStateOf("") }
    var toDate by remember { mutableStateOf("") }
    var dayBookFilter by remember { mutableStateOf<BusyVoucherType?>(null) }
    var gstSubTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown) {
                    when {
                        ev.key == Key.F8 -> { voucherDialogType = BusyVoucherType.SALES; true }
                        ev.key == Key.F9 -> { voucherDialogType = BusyVoucherType.PURCHASE; true }
                        ev.key == Key.F5 -> { voucherDialogType = BusyVoucherType.PAYMENT; true }
                        ev.key == Key.F6 -> { voucherDialogType = BusyVoucherType.RECEIPT; true }
                        ev.key == Key.F7 -> { voucherDialogType = BusyVoucherType.JOURNAL; true }
                        ev.key == Key.F4 -> { voucherDialogType = BusyVoucherType.CONTRA; true }
                        ev.isAltPressed && ev.key == Key.L -> {
                            activeMenu = BusyMenuSection.DISPLAY
                            activeReport = BusyReportTab.LEDGER
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        // Classic BUSY 21 Top Ribbon Header
        Surface(color = MaroonPrimary, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "BUSY 21 ACCOUNTING SUITE",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color(0xFFDCEDC8),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "FY 2026-27 • NB ENGINEERING WORKS AND ELECTRICALS (GSTIN: 08CSZPK9277N2Z8)",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFFDAD8)
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            val res = ExportPrintHelper.exportBusy21Csv(context, vouchers)
                            res.fold(
                                onSuccess = { onShowMessage("Exported $it for BUSY 21 Import") },
                                onFailure = { onShowMessage("Export failed: ${it.message}") }
                            )
                        },
                        border = BorderStroke(1.dp, Color(0xFFDCEDC8)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("busy_export_csv_btn")
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = "Export BUSY 21 CSV", tint = Color(0xFFDCEDC8), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("BUSY CSV", color = Color(0xFFDCEDC8), style = MaterialTheme.typography.labelSmall)
                    }
                }

                // Quick Shortcut Strip (F8, F9, F5, F6, F7, F4, Alt+L)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(BusyVoucherType.entries) { vt ->
                        Surface(
                            onClick = { voucherDialogType = vt },
                            color = Color(0xFF6B1412),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color(0xFFDCEDC8).copy(alpha = 0.55f)),
                            modifier = Modifier.testTag("shortcut_${vt.shortcut.lowercase()}")
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
                                Text(
                                    vt.shortcut,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFDCEDC8),
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(vt.label, style = MaterialTheme.typography.labelSmall, color = Color.White)
                            }
                        }
                    }
                    item {
                        Surface(
                            onClick = {
                                activeMenu = BusyMenuSection.DISPLAY
                                activeReport = BusyReportTab.LEDGER
                            },
                            color = solarColors.debitBlue,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                "Alt+L Ledger",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }

        // BUSY 21 Menu Bar
        ScrollableTabRow(
            selectedTabIndex = BusyMenuSection.entries.indexOf(activeMenu),
            edgePadding = 8.dp
        ) {
            BusyMenuSection.entries.forEach { section ->
                Tab(
                    selected = activeMenu == section,
                    onClick = { activeMenu = section },
                    text = { Text(section.label, fontWeight = if (activeMenu == section) FontWeight.Bold else FontWeight.Normal) }
                )
            }
        }

        // Main Content Body
        val aging = remember(parties) { BusyAccountingEngine.computePartyAging(parties) }
        val ccRows = remember(parties, vouchers) {
            BusyAccountingEngine.computeCostCenterSummary(parties, vouchers)
        }
        val gst = remember(vouchers) { BusyAccountingEngine.computeGstSummary(vouchers) }
        val dayByDayRows = remember(vouchers, siteKharcha) {
            BusyAccountingEngine.computeDayByDayReport(vouchers, siteKharcha)
        }
        val partyCostingRows = remember(parties, dcrPanels, solarMeters, rawMaterials, siteKharcha) {
            BusyAccountingEngine.computePartyCostingReport(parties, dcrPanels, solarMeters, rawMaterials, siteKharcha)
        }
        val referralRows = remember(parties) {
            BusyAccountingEngine.computeReferralCommissions(parties)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (activeMenu) {
                BusyMenuSection.VOUCHERS -> {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("VOUCHER REGISTER (${vouchers.size})", style = MaterialTheme.typography.labelSmall)
                            Button(
                                onClick = { voucherDialogType = BusyVoucherType.SALES },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("New Voucher")
                            }
                        }
                    }

                    if (vouchers.isEmpty()) {
                        item {
                            BusyEmptyNotice("No vouchers posted yet. Tap F8 Sales, F9 Purchase, F5 Payment, or use AI Auto-Import to post vouchers instantly.")
                        }
                    } else {
                        items(vouchers, key = { it.id }) { v ->
                            val linkedParty = parties.firstOrNull {
                                it.partyName.equals(v.partyAccountName, ignoreCase = true)
                            }
                            BusyVoucherRowCard(
                                voucher = v,
                                canDelete = canDelete,
                                onPrint = { ExportPrintHelper.printTaxInvoiceA4(context, v) },
                                onPrintChallan = if (v.type == BusyVoucherType.SALES) {
                                    {
                                        ExportPrintHelper.printDeliveryChallanA4(
                                            context = context,
                                            partyName = v.partyAccountName,
                                            partyAddress = linkedParty?.cityOrLocation?.ifBlank { linkedParty.address } ?: "Udaipur, Rajasthan",
                                            partyPhone = linkedParty?.mobile.orEmpty(),
                                            challanNo = "DC-${v.voucherNo.replace("/", "-")}",
                                            date = v.date,
                                            items = v.lineItems,
                                            remarks = v.narration
                                        )
                                    }
                                } else null,
                                onDelete = { onDeleteVoucher(v.id) }
                            )
                        }
                    }
                }

                BusyMenuSection.DISPLAY -> {
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(BusyReportTab.entries) { rt ->
                                FilterChip(
                                    selected = activeReport == rt,
                                    onClick = { activeReport = rt },
                                    label = { Text(rt.label) }
                                )
                            }
                        }
                    }

                    when (activeReport) {
                        BusyReportTab.LEDGER -> {
                            item {
                                val selectedAcc = accounts.firstOrNull { it.id == selectedLedgerAccId } ?: accounts.firstOrNull()
                                if (selectedAcc != null) {
                                    val ledger = remember(selectedAcc, vouchers, fromDate, toDate) {
                                        BusyAccountingEngine.computeAccountLedger(selectedAcc, vouchers, fromDate, toDate)
                                    }
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text("Select Account Ledger:", style = MaterialTheme.typography.labelMedium)
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            items(accounts) { acc ->
                                                FilterChip(
                                                    selected = acc.id == selectedAcc.id,
                                                    onClick = { selectedLedgerAccId = acc.id },
                                                    label = { Text(acc.name) }
                                                )
                                            }
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedTextField(
                                                value = fromDate,
                                                onValueChange = { fromDate = it },
                                                label = { Text("From (YYYY-MM-DD)") },
                                                singleLine = true,
                                                modifier = Modifier.weight(1f)
                                            )
                                            OutlinedTextField(
                                                value = toDate,
                                                onValueChange = { toDate = it },
                                                label = { Text("To (YYYY-MM-DD)") },
                                                singleLine = true,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }

                                        val (openTxt, openDr) = formatBusyDrCr(ledger.openingBalance)
                                        val (closeTxt, closeDr) = formatBusyDrCr(ledger.closingBalance)

                                        Card(
                                            shape = RoundedCornerShape(8.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                            border = BorderStroke(1.dp, solarColors.gridBorderStrong)
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column {
                                                        Text(selectedAcc.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                                        Text(
                                                            "${selectedAcc.group.label} • Opening: $openTxt",
                                                            style = MaterialTheme.typography.labelSmall.merge(TabularNumberStyle),
                                                            color = if (openDr) solarColors.debitBlue else solarColors.creditRed
                                                        )
                                                    }
                                                    OutlinedButton(
                                                        onClick = {
                                                            ExportPrintHelper.printAccountLedgerA4(
                                                                context,
                                                                selectedAcc,
                                                                fromDate.ifBlank { "Start" },
                                                                toDate.ifBlank { "Today" },
                                                                ledger.rows,
                                                                ledger.closingBalance
                                                            )
                                                        },
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                                    ) {
                                                        Icon(Icons.Filled.Print, contentDescription = "Print Ledger", modifier = Modifier.size(16.dp))
                                                        Spacer(Modifier.width(4.dp))
                                                        Text("Print A4")
                                                    }
                                                }

                                                Spacer(Modifier.height(10.dp))
                                                // Dense BUSY-style Tabular Ledger Grid
                                                Column(
                                                    modifier = Modifier
                                                        .horizontalScroll(rememberScrollState())
                                                        .background(MaterialTheme.colorScheme.surface)
                                                ) {
                                                    BusyTableHeaderRow(
                                                        listOf("Date" to 88, "Vch No" to 96, "Particulars" to 150, "Debit (Dr)" to 96, "Credit (Cr)" to 96, "Balance" to 116)
                                                    )
                                                    if (ledger.rows.isEmpty()) {
                                                        Text(
                                                            "No entries in selected date range.",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            modifier = Modifier.padding(12.dp)
                                                        )
                                                    } else {
                                                        ledger.rows.forEach { r ->
                                                            val (balStr, isDr) = formatBusyDrCr(r.runningBalance)
                                                            Row(
                                                                modifier = Modifier.padding(vertical = 6.dp, horizontal = 6.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Text(r.date, style = MaterialTheme.typography.labelSmall.merge(TabularNumberStyle), modifier = Modifier.width(88.dp))
                                                                Text(r.voucherNo, style = MaterialTheme.typography.labelSmall.merge(TabularNumberStyle), modifier = Modifier.width(96.dp))
                                                                Column(modifier = Modifier.width(150.dp)) {
                                                                    Text(r.particulars, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                                    if (r.narration.isNotBlank()) {
                                                                        Text(r.narration, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                                    }
                                                                }
                                                                Text(
                                                                    if (r.debit > 0) formatInr(r.debit, true) else "-",
                                                                    style = MaterialTheme.typography.labelSmall.merge(TabularNumberStyle),
                                                                    color = solarColors.debitBlue,
                                                                    textAlign = TextAlign.End,
                                                                    modifier = Modifier.width(96.dp)
                                                                )
                                                                Text(
                                                                    if (r.credit > 0) formatInr(r.credit, true) else "-",
                                                                    style = MaterialTheme.typography.labelSmall.merge(TabularNumberStyle),
                                                                    color = solarColors.creditRed,
                                                                    textAlign = TextAlign.End,
                                                                    modifier = Modifier.width(96.dp)
                                                                )
                                                                Text(
                                                                    balStr,
                                                                    style = MaterialTheme.typography.labelSmall.merge(TabularNumberStyle),
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = if (isDr) solarColors.debitBlue else solarColors.creditRed,
                                                                    textAlign = TextAlign.End,
                                                                    modifier = Modifier.width(116.dp)
                                                                )
                                                            }
                                                            HorizontalDivider(color = solarColors.gridBorderStrong)
                                                        }
                                                    }
                                                }

                                                Spacer(Modifier.height(10.dp))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text("Closing Balance:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                                    Text(
                                                        closeTxt,
                                                        style = MaterialTheme.typography.titleMedium.merge(TabularNumberStyle),
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (closeDr) solarColors.debitBlue else solarColors.creditRed
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        BusyReportTab.DAY_BOOK -> {
                            item {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    item {
                                        FilterChip(
                                            selected = dayBookFilter == null,
                                            onClick = { dayBookFilter = null },
                                            label = { Text("All (${vouchers.size})") }
                                        )
                                    }
                                    items(BusyVoucherType.entries) { vt ->
                                        FilterChip(
                                            selected = dayBookFilter == vt,
                                            onClick = { dayBookFilter = if (dayBookFilter == vt) null else vt },
                                            label = { Text(vt.label) }
                                        )
                                    }
                                }
                            }
                            val dayRows = vouchers.filter { dayBookFilter == null || it.type == dayBookFilter }
                                .sortedByDescending { it.date }
                            if (dayRows.isEmpty()) {
                                item { BusyEmptyNotice("No vouchers recorded for selected Day Book filter.") }
                            } else {
                                items(dayRows, key = { it.id }) { v ->
                                    val linkedParty = parties.firstOrNull {
                                        it.partyName.equals(v.partyAccountName, ignoreCase = true)
                                    }
                                    BusyVoucherRowCard(
                                        voucher = v,
                                        canDelete = canDelete,
                                        onPrint = { ExportPrintHelper.printTaxInvoiceA4(context, v) },
                                        onPrintChallan = if (v.type == BusyVoucherType.SALES) {
                                            {
                                                ExportPrintHelper.printDeliveryChallanA4(
                                                    context = context,
                                                    partyName = v.partyAccountName,
                                                    partyAddress = linkedParty?.cityOrLocation?.ifBlank { linkedParty.address } ?: "Udaipur, Rajasthan",
                                                    partyPhone = linkedParty?.mobile.orEmpty(),
                                                    challanNo = "DC-${v.voucherNo.replace("/", "-")}",
                                                    date = v.date,
                                                    items = v.lineItems,
                                                    remarks = v.narration
                                                )
                                            }
                                        } else null,
                                        onDelete = { onDeleteVoucher(v.id) }
                                    )
                                }
                            }
                        }

                        BusyReportTab.DAY_BY_DAY -> {
                            item {
                                Card(
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, solarColors.gridBorderStrong),
                                    modifier = Modifier.testTag("day_by_day_report_card")
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            "Day-by-Day Financial Register",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "Daily Sales, Collections, Purchases, Payments & Site Kharcha Cash Flow",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (dayByDayRows.isEmpty()) {
                                            Text("No daily entries posted yet.", style = MaterialTheme.typography.bodySmall)
                                        } else {
                                            Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                                BusyTableHeaderRow(
                                                    listOf(
                                                        "Date" to 92,
                                                        "Txns" to 44,
                                                        "Sales (₹)" to 100,
                                                        "Receipts (₹)" to 100,
                                                        "Purchase (₹)" to 100,
                                                        "Pay/Exp (₹)" to 100,
                                                        "Net Cash (₹)" to 106
                                                    )
                                                )
                                                dayByDayRows.forEach { d ->
                                                    Row(
                                                        modifier = Modifier.padding(vertical = 6.dp, horizontal = 6.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(d.date, style = MaterialTheme.typography.labelSmall.merge(TabularNumberStyle), modifier = Modifier.width(92.dp))
                                                        Text("${d.voucherCount}", style = MaterialTheme.typography.labelSmall.merge(TabularNumberStyle), modifier = Modifier.width(44.dp))
                                                        Text(formatInr(d.salesBilled), style = MaterialTheme.typography.labelSmall.merge(TabularNumberStyle), color = solarColors.debitBlue, textAlign = TextAlign.End, modifier = Modifier.width(100.dp))
                                                        Text(formatInr(d.receiptsCollected), style = MaterialTheme.typography.labelSmall.merge(TabularNumberStyle), color = solarColors.emerald, textAlign = TextAlign.End, modifier = Modifier.width(100.dp))
                                                        Text(formatInr(d.purchasesBilled), style = MaterialTheme.typography.labelSmall.merge(TabularNumberStyle), color = solarColors.creditRed, textAlign = TextAlign.End, modifier = Modifier.width(100.dp))
                                                        Text(formatInr(d.paymentsAndKharcha), style = MaterialTheme.typography.labelSmall.merge(TabularNumberStyle), color = solarColors.creditRed, textAlign = TextAlign.End, modifier = Modifier.width(100.dp))
                                                        Text(
                                                            formatInr(d.netCashFlow),
                                                            style = MaterialTheme.typography.labelSmall.merge(TabularNumberStyle),
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (d.netCashFlow >= 0) solarColors.emerald else solarColors.creditRed,
                                                            textAlign = TextAlign.End,
                                                            modifier = Modifier.width(106.dp)
                                                        )
                                                    }
                                                    HorizontalDivider(color = solarColors.gridBorderStrong)
                                                }
                                                Row(
                                                    modifier = Modifier
                                                        .background(solarColors.gridHeader)
                                                        .padding(vertical = 8.dp, horizontal = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text("TOTAL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(136.dp))
                                                    Text(formatInr(dayByDayRows.sumOf { it.salesBilled }), style = MaterialTheme.typography.labelSmall.merge(TabularNumberStyle), fontWeight = FontWeight.Bold, color = solarColors.debitBlue, textAlign = TextAlign.End, modifier = Modifier.width(100.dp))
                                                    Text(formatInr(dayByDayRows.sumOf { it.receiptsCollected }), style = MaterialTheme.typography.labelSmall.merge(TabularNumberStyle), fontWeight = FontWeight.Bold, color = solarColors.emerald, textAlign = TextAlign.End, modifier = Modifier.width(100.dp))
                                                    Text(formatInr(dayByDayRows.sumOf { it.purchasesBilled }), style = MaterialTheme.typography.labelSmall.merge(TabularNumberStyle), fontWeight = FontWeight.Bold, color = solarColors.creditRed, textAlign = TextAlign.End, modifier = Modifier.width(100.dp))
                                                    Text(formatInr(dayByDayRows.sumOf { it.paymentsAndKharcha }), style = MaterialTheme.typography.labelSmall.merge(TabularNumberStyle), fontWeight = FontWeight.Bold, color = solarColors.creditRed, textAlign = TextAlign.End, modifier = Modifier.width(100.dp))
                                                    val totalCash = dayByDayRows.sumOf { it.netCashFlow }
                                                    Text(formatInr(totalCash), style = MaterialTheme.typography.labelSmall.merge(TabularNumberStyle), fontWeight = FontWeight.Bold, color = if (totalCash >= 0) solarColors.emerald else solarColors.creditRed, textAlign = TextAlign.End, modifier = Modifier.width(106.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        BusyReportTab.TRIAL_BALANCE -> {
                            item {
                                val tb = remember(accounts, vouchers) {
                                    BusyAccountingEngine.computeTrialBalance(accounts, vouchers)
                                }
                                Card(
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, solarColors.gridBorderStrong)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("Trial Balance (Group-Wise Dr / Cr)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        BusyTableHeaderRow(listOf("Account Group" to 165, "Debit (Dr)" to 92, "Credit (Cr)" to 92))
                                        tb.groups.forEach { g ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp, horizontal = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(g.group.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1.4f))
                                                Text(
                                                    if (g.debitTotal > 0) formatInr(g.debitTotal) else "-",
                                                    style = MaterialTheme.typography.labelSmall.merge(TabularNumberStyle),
                                                    color = solarColors.debitBlue,
                                                    textAlign = TextAlign.End,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Text(
                                                    if (g.creditTotal > 0) formatInr(g.creditTotal) else "-",
                                                    style = MaterialTheme.typography.labelSmall.merge(TabularNumberStyle),
                                                    color = solarColors.creditRed,
                                                    textAlign = TextAlign.End,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                            HorizontalDivider(color = solarColors.gridBorderStrong)
                                        }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(solarColors.gridHeader)
                                                .padding(8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("TOTAL", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                            Text("Dr ${formatInr(tb.grandDebit)}", style = MaterialTheme.typography.labelSmall.merge(TabularNumberStyle), color = solarColors.debitBlue)
                                            Text("Cr ${formatInr(tb.grandCredit)}", style = MaterialTheme.typography.labelSmall.merge(TabularNumberStyle), color = solarColors.creditRed)
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        Text("P&L / Balance Sheet Snapshot", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Sales Income: ${formatInr(tb.totalSalesIncome)}", style = MaterialTheme.typography.bodySmall.merge(TabularNumberStyle), color = solarColors.emerald)
                                            Text("Purchases & Exp: ${formatInr(tb.totalPurchaseAndExpenses)}", style = MaterialTheme.typography.bodySmall.merge(TabularNumberStyle), color = solarColors.creditRed)
                                        }
                                        Text(
                                            "Net Profit / Surplus: ${formatInr(tb.netProfit)}",
                                            style = MaterialTheme.typography.titleMedium.merge(TabularNumberStyle),
                                            color = if (tb.netProfit >= 0) solarColors.emerald else solarColors.creditRed
                                        )
                                    }
                                }
                            }
                        }

                        BusyReportTab.PARTY_COSTING -> {
                            if (partyCostingRows.isEmpty()) {
                                item { BusyEmptyNotice("No parties in pipeline for Party Costing & Profit Report.") }
                            } else {
                                items(partyCostingRows, key = { it.party.id }) { row ->
                                    val p = row.party
                                    val pPanels = remember(dcrPanels, p.id) { dcrPanels.filter { it.linkedPartyId == p.id } }
                                    val pMeters = remember(solarMeters, p.id) { solarMeters.filter { it.installedPartyId == p.id } }
                                    Card(
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        border = BorderStroke(1.dp, solarColors.gridBorderStrong),
                                        modifier = Modifier.testTag("party_costing_card_${p.id}")
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(p.partyName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                                    Text(
                                                        "${p.division.label} • ${p.capacityKw} kW • ${p.leadSource.shortLabel}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Surface(
                                                    color = if (row.netProfit >= 0) LimeSoft else MaterialTheme.colorScheme.errorContainer,
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = String.format(Locale.US, "%.1f%% Margin", row.marginPct),
                                                        style = MaterialTheme.typography.labelSmall.merge(TabularNumberStyle),
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (row.netProfit >= 0) Color(0xFF2B4D12) else solarColors.creditRed,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                    )
                                                }
                                            }
                                            HorizontalDivider(color = solarColors.gridBorderStrong)
                                            CostingLineRow("Contract Deal Value", formatInr(row.dealValue, true), solarColors.debitBlue)
                                            CostingLineRow("(-) Main Solar Hardware (${pPanels.size} Panels, ${pMeters.size} Meters, BOS)", "- ${formatInr(row.mainMaterialCost, true)}", solarColors.creditRed)
                                            CostingLineRow("(-) On-Site Purchases (${p.sitePurchases.size} Bills)", "- ${formatInr(row.onSitePurchaseCost, true)}", solarColors.creditRed)
                                            CostingLineRow("(-) Daily Kharcha & Labour", "- ${formatInr(row.dailyKharchaCost, true)}", solarColors.creditRed)
                                            CostingLineRow("(-) Referral Commission", "- ${formatInr(row.referralCommission, true)}", solarColors.creditRed)
                                            HorizontalDivider(color = solarColors.gridBorderStrong)
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    "Net Profit: ${formatInr(row.netProfit, true)}",
                                                    style = MaterialTheme.typography.titleSmall.merge(TabularNumberStyle),
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (row.netProfit >= 0) LimeGreen else solarColors.creditRed
                                                )
                                                OutlinedButton(
                                                    onClick = {
                                                        ExportPrintHelper.printPartyCostingReportA4(context, row, pPanels, pMeters)
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                                ) {
                                                    Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("Costing PDF", style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        BusyReportTab.AGING -> {
                            if (aging.isEmpty()) {
                                item { BusyEmptyNotice("No parties in pipeline for Receivable Aging.") }
                            } else {
                                items(aging, key = { it.partyId }) { row ->
                                    Card(
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        border = BorderStroke(
                                            1.dp,
                                            if (row.isOverdueAlert) MaterialTheme.colorScheme.error else solarColors.gridBorderStrong
                                        )
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(row.partyName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                                if (row.isOverdueAlert) {
                                                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(4.dp)) {
                                                        Text(
                                                            "OVERDUE ALERT",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("Total: ${formatInr(row.projectValue)}", style = MaterialTheme.typography.labelSmall.merge(TabularNumberStyle))
                                                Text("Recd: ${formatInr(row.receivedAmount)}", style = MaterialTheme.typography.labelSmall.merge(TabularNumberStyle), color = solarColors.emerald)
                                                Text("Pending: ${formatInr(row.pendingDues)}", style = MaterialTheme.typography.labelSmall.merge(TabularNumberStyle), color = solarColors.creditRed)
                                            }
                                            Text(
                                                "Loan 1st Tranche: ${formatInr(row.loan1Amount)} (${if (row.loan1Received) "Received" else "Pending"}) • Loan 2nd: ${formatInr(row.loan2Amount)} (${if (row.loan2Received) "Received" else "Pending"})",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        BusyReportTab.COST_CENTERS -> {
                            items(ccRows, key = { it.source.name }) { cc ->
                                Card(
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, solarColors.gridBorderStrong)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(cc.source.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                            Text("${cc.partyCount} Projects", style = MaterialTheme.typography.labelSmall, color = solarColors.solarAmber)
                                        }
                                        HorizontalDivider(color = solarColors.gridBorderStrong)
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Revenue / Sales", style = MaterialTheme.typography.bodySmall)
                                            Text(formatInr(cc.totalSales), style = MaterialTheme.typography.labelSmall.merge(TabularNumberStyle), color = solarColors.debitBlue)
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Cash Receipts", style = MaterialTheme.typography.bodySmall)
                                            Text(formatInr(cc.totalReceipts), style = MaterialTheme.typography.labelSmall.merge(TabularNumberStyle), color = solarColors.emerald)
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Purchases & Direct Costs", style = MaterialTheme.typography.bodySmall)
                                            Text(formatInr(cc.totalDirectCosts), style = MaterialTheme.typography.labelSmall.merge(TabularNumberStyle), color = solarColors.creditRed)
                                        }
                                        if (canSeePartnerSplit) {
                                            HorizontalDivider(color = solarColors.gridBorderStrong)
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("Partner / Reference Split (Owner Only)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                                Text(formatInr(cc.partnerSplitAmount), style = MaterialTheme.typography.labelSmall.merge(TabularNumberStyle), color = solarColors.solarAmber)
                                            }
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("NB Engineering Net Margin", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                                Text(formatInr(cc.nbNetProfit), style = MaterialTheme.typography.titleSmall.merge(TabularNumberStyle), color = solarColors.emerald)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        BusyReportTab.REFERRALS -> {
                            item {
                                val totalComm = referralRows.sumOf { it.commissionAmount }
                                val paidComm = referralRows.filter { it.isPaid }.sumOf { it.commissionAmount }
                                val pendingComm = totalComm - paidComm
                                Card(
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, solarColors.gridBorderStrong)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("Referral Commission Ledger", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Total: ${formatInr(totalComm)}", style = MaterialTheme.typography.labelSmall.merge(TabularNumberStyle), color = solarColors.debitBlue)
                                            Text("Paid: ${formatInr(paidComm)}", style = MaterialTheme.typography.labelSmall.merge(TabularNumberStyle), color = LimeGreen)
                                            Text("Pending: ${formatInr(pendingComm)}", style = MaterialTheme.typography.labelSmall.merge(TabularNumberStyle), color = solarColors.creditRed)
                                        }
                                    }
                                }
                            }
                            if (referralRows.isEmpty()) {
                                item { BusyEmptyNotice("No referral or Thakur Ji partner commission leads yet.") }
                            } else {
                                items(referralRows, key = { it.partyId }) { ref ->
                                    Card(
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        border = BorderStroke(1.dp, solarColors.gridBorderStrong)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(ref.partyName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                                    Text(
                                                        "${ref.leadSource.label} • ${ref.referrerName}" +
                                                            if (ref.referrerPhone.isNotBlank()) " (${ref.referrerPhone})" else "",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Surface(
                                                    color = if (ref.isPaid) LimeSoft else MaterialTheme.colorScheme.errorContainer,
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = if (ref.isPaid) "PAID" else "PENDING",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (ref.isPaid) Color(0xFF2B4D12) else solarColors.creditRed,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                    )
                                                }
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text(
                                                        "Deal: ${formatInr(ref.dealValue)} • ${String.format(Locale.US, "%.1f", ref.commissionPct)}% = ${formatInr(ref.commissionAmount)}",
                                                        style = MaterialTheme.typography.labelMedium.merge(TabularNumberStyle),
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaroonPrimary
                                                    )
                                                    if (ref.isPaid && ref.payoutDate.isNotBlank()) {
                                                        Text("Settled on ${ref.payoutDate}", style = MaterialTheme.typography.labelSmall, color = LimeGreen)
                                                    }
                                                }
                                                OutlinedButton(
                                                    onClick = { onMarkReferralPaid(ref.partyId, !ref.isPaid) },
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                                ) {
                                                    Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text(if (ref.isPaid) "Mark Pending" else "Mark Paid", style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                BusyMenuSection.GST -> {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = gstSubTab == 0, onClick = { gstSubTab = 0 }, label = { Text("GST Summary") })
                            FilterChip(selected = gstSubTab == 1, onClick = { gstSubTab = 1 }, label = { Text("GSTR-1 (${gst.gstr1Sales.size})") })
                            FilterChip(selected = gstSubTab == 2, onClick = { gstSubTab = 2 }, label = { Text("GSTR-2B (${gst.gstr2bPurchases.size})") })
                        }
                    }
                    when (gstSubTab) {
                        0 -> item {
                            Card(
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, solarColors.gridBorderStrong)
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Input vs Output GST Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    BusyTableHeaderRow(listOf("Tax Head" to 120, "Output GST" to 100, "Input ITC" to 100))
                                    GstCompareRow("CGST", gst.outputCgst, gst.inputCgst)
                                    GstCompareRow("SGST", gst.outputSgst, gst.inputSgst)
                                    GstCompareRow("IGST", gst.outputIgst, gst.inputIgst)
                                    HorizontalDivider(color = solarColors.gridBorderStrong)
                                    GstCompareRow("TOTAL GST", gst.totalOutputGst, gst.totalInputGst)
                                    Surface(
                                        color = if (gst.netGstPayable > 0) MaterialTheme.colorScheme.errorContainer else solarColors.emeraldContainer,
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = if (gst.netGstPayable >= 0) {
                                                "Net GST Payable (Challan): ${formatInr(gst.netGstPayable, true)}"
                                            } else {
                                                "Excess Input Tax Credit (ITC): ${formatInr(-gst.netGstPayable, true)}"
                                            },
                                            style = MaterialTheme.typography.titleSmall.merge(TabularNumberStyle),
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                }
                            }
                        }

                        1 -> {
                            if (gst.gstr1Sales.isEmpty()) {
                                item { BusyEmptyNotice("No Sales (F8) invoices for GSTR-1 yet.") }
                            } else {
                                items(gst.gstr1Sales, key = { it.id }) { v ->
                                    GstRegisterCard(v, "GSTR-1 Sales")
                                }
                            }
                        }

                        else -> {
                            if (gst.gstr2bPurchases.isEmpty()) {
                                item { BusyEmptyNotice("No Purchase (F9) bills for GSTR-2B yet.") }
                            } else {
                                items(gst.gstr2bPurchases, key = { it.id }) { v ->
                                    GstRegisterCard(v, "GSTR-2B Purchase ITC")
                                }
                            }
                        }
                    }
                }

                BusyMenuSection.MASTERS -> {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { showAddAccountDialog = true }, modifier = Modifier.weight(1f)) {
                                Text("+ Account Master")
                            }
                            OutlinedButton(onClick = { showAddItemDialog = true }, modifier = Modifier.weight(1f)) {
                                Text("+ Item Master")
                            }
                        }
                    }

                    item {
                        Text("ACCOUNT MASTERS (${accounts.size})", style = MaterialTheme.typography.labelSmall)
                    }
                    items(accounts, key = { it.id }) { acc ->
                        val (openStr, isDr) = formatBusyDrCr(acc.openingBalance)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, solarColors.hairlineBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedLedgerAccId = acc.id
                                    activeMenu = BusyMenuSection.DISPLAY
                                    activeReport = BusyReportTab.LEDGER
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(acc.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text(
                                        "${acc.group.label} • CC: ${acc.defaultCostCenter.shortLabel}" +
                                            if (acc.gstin.isNotBlank()) " • GSTIN: ${acc.gstin}" else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    openStr,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isDr) solarColors.debitBlue else solarColors.creditRed
                                )
                            }
                        }
                    }

                    item {
                        Spacer(Modifier.height(8.dp))
                        Text("ITEM MASTERS & TAX CATEGORIES (${items.size})", style = MaterialTheme.typography.labelSmall)
                    }
                    items(items, key = { it.id }) { itm ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, solarColors.hairlineBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(itm.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text(
                                        "HSN: ${itm.hsnCode} • GST ${itm.gstRatePercent}% • Sale ${formatInr(itm.defaultSaleRate)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    "Stock: ${itm.stockQty} ${itm.unit}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = solarColors.emerald
                                )
                            }
                        }
                    }
                }

                BusyMenuSection.AI_IMPORT -> {
                    item {
                        BusyAiAutoImportSection(
                            aiState = aiState,
                            accounts = accounts,
                            onScanBankUri = onScanBankUri,
                            onScanBankText = onScanBankText,
                            onToggleBankRow = onToggleBankRow,
                            onPostReviewedBank = onPostReviewedBank,
                            onScanPurchaseBillUri = onScanPurchaseBillUri,
                            onPostReviewedBill = onPostReviewedBill,
                            onResetAi = onResetAi
                        )
                    }
                }
            }
        }
    }

    // Voucher Entry Dialog
    voucherDialogType?.let { vt ->
        BusyVoucherDialog(
            initialType = vt,
            accounts = accounts,
            items = items,
            onDismiss = { voucherDialogType = null },
            onPost = onPostVoucher
        )
    }

    // Add Account Master Dialog
    if (showAddAccountDialog) {
        AddAccountMasterDialog(
            onDismiss = { showAddAccountDialog = false },
            onSave = { name, grp, opBal, gstin, cc ->
                onAddAccount(name, grp, opBal, gstin, cc)
                showAddAccountDialog = false
            }
        )
    }

    // Add Item Master Dialog
    if (showAddItemDialog) {
        AddItemMasterDialog(
            onDismiss = { showAddItemDialog = false },
            onSave = { name, hsn, unit, gst, sRate, pRate, stock ->
                onAddItem(name, hsn, unit, gst, sRate, pRate, stock)
                showAddItemDialog = false
            }
        )
    }
}

@Composable
private fun BusyTableHeaderRow(columns: List<Pair<String, Int>>) {
    val solarColors = LocalSolarColors.current
    Row(
        modifier = Modifier
            .background(solarColors.gridHeader)
            .padding(vertical = 7.dp, horizontal = 6.dp)
    ) {
        columns.forEach { (title, widthDp) ->
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(widthDp.dp)
            )
        }
    }
}

@Composable
private fun BusyVoucherRowCard(
    voucher: BusyVoucher,
    canDelete: Boolean,
    onPrint: () -> Unit,
    onPrintChallan: (() -> Unit)? = null,
    onDelete: () -> Unit
) {
    val solarColors = LocalSolarColors.current
    val isReceiptOrSale = voucher.type == BusyVoucherType.SALES || voucher.type == BusyVoucherType.RECEIPT

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, solarColors.gridBorderStrong),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = if (isReceiptOrSale) solarColors.emeraldContainer else solarColors.solarAmberContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            "${voucher.type.label} (${voucher.type.shortcut})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(voucher.voucherNo, style = MaterialTheme.typography.labelSmall.merge(TabularNumberStyle))
                }
                Text(
                    formatInr(voucher.totalAmount, true),
                    style = MaterialTheme.typography.titleSmall.merge(TabularNumberStyle),
                    fontWeight = FontWeight.Bold,
                    color = if (isReceiptOrSale) solarColors.debitBlue else solarColors.creditRed
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${voucher.partyAccountName}  ⇄  ${voucher.contraAccountName}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "${voucher.date} • CC: ${voucher.costCenter.shortLabel} • ${voucher.billRefType.label}" +
                    if (voucher.utrOrChequeNo.isNotBlank()) " • UTR: ${voucher.utrOrChequeNo}" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (voucher.narration.isNotBlank()) {
                Text(voucher.narration, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onPrintChallan != null) {
                    TextButton(onClick = onPrintChallan) {
                        Icon(Icons.Filled.LocalShipping, contentDescription = "Delivery Challan PDF", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Challan")
                    }
                }
                val printLabel = when (voucher.type) {
                    BusyVoucherType.SALES -> "Tax Invoice"
                    BusyVoucherType.RECEIPT -> "Receipt PDF"
                    else -> "Print A4"
                }
                TextButton(onClick = onPrint) {
                    Icon(
                        if (voucher.type == BusyVoucherType.RECEIPT) Icons.Filled.ReceiptLong else Icons.Filled.Print,
                        contentDescription = printLabel,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(printLabel)
                }
                if (canDelete) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete Voucher", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CostingLineRow(label: String, amount: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(
            amount,
            style = MaterialTheme.typography.labelMedium.merge(TabularNumberStyle),
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun GstCompareRow(label: String, output: Double, input: Double) {
    val solarColors = LocalSolarColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(120.dp))
        Text(formatInr(output, true), style = MaterialTheme.typography.labelSmall.merge(TabularNumberStyle), color = solarColors.creditRed, modifier = Modifier.width(100.dp))
        Text(formatInr(input, true), style = MaterialTheme.typography.labelSmall.merge(TabularNumberStyle), color = solarColors.emerald, modifier = Modifier.width(100.dp))
    }
}

@Composable
private fun GstRegisterCard(v: BusyVoucher, tag: String) {
    val solarColors = LocalSolarColors.current
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, solarColors.gridBorderStrong),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${v.voucherNo} • ${v.partyAccountName}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(formatInr(v.totalAmount, true), style = MaterialTheme.typography.labelLarge.merge(TabularNumberStyle))
            }
            Text(
                "$tag • ${v.date} • GSTIN: ${v.partyGstin.ifBlank { "URD / B2C" }}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Taxable: ${formatInr(v.taxableAmount, true)} | CGST: ${formatInr(v.cgstAmount, true)} | SGST: ${formatInr(v.sgstAmount, true)} | IGST: ${formatInr(v.igstAmount, true)}",
                style = MaterialTheme.typography.labelSmall.merge(TabularNumberStyle),
                color = solarColors.debitBlue
            )
        }
    }
}

@Composable
private fun BusyEmptyNotice(text: String) {
    val solarColors = LocalSolarColors.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, solarColors.hairlineBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(20.dp)
        )
    }
}

@Composable
private fun AddAccountMasterDialog(
    onDismiss: () -> Unit,
    onSave: (String, BusyAccountGroup, Double, String, LeadSource) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var group by remember { mutableStateOf(BusyAccountGroup.SUNDRY_DEBTORS) }
    var opBal by remember { mutableStateOf("0") }
    var gstin by remember { mutableStateOf("") }
    var costCenter by remember { mutableStateOf(LeadSource.NB_DIRECT) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add BUSY Account Master") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Account Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Account Group", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(BusyAccountGroup.entries) { grp ->
                        FilterChip(
                            selected = group == grp,
                            onClick = { group = grp },
                            label = { Text(grp.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = opBal,
                    onValueChange = { opBal = it },
                    label = { Text("Opening Bal (+Dr / -Cr)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = gstin,
                    onValueChange = { gstin = it },
                    label = { Text("GSTIN (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Default Cost Center", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(LeadSource.entries) { cc ->
                        FilterChip(
                            selected = costCenter == cc,
                            onClick = { costCenter = cc },
                            label = { Text(cc.shortLabel) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, group, safeDouble(opBal, 0.0), gstin, costCenter) },
                enabled = name.isNotBlank()
            ) { Text("Save Account") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddItemMasterDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String, Double, Double, Double, Double) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var hsn by remember { mutableStateOf("85414300") }
    var unit by remember { mutableStateOf("Nos") }
    var gst by remember { mutableStateOf("18") }
    var saleRate by remember { mutableStateOf("0") }
    var purRate by remember { mutableStateOf("0") }
    var stock by remember { mutableStateOf("0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add BUSY Item Master") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Item Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = hsn,
                        onValueChange = { hsn = it },
                        label = { Text("HSN") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("Unit") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text("Tax Category (GST %)", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("5", "12", "18", "28").forEach { rate ->
                        FilterChip(
                            selected = gst == rate,
                            onClick = { gst = rate },
                            label = { Text("$rate%") }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = saleRate,
                        onValueChange = { saleRate = it },
                        label = { Text("Sale Rate ₹") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = purRate,
                        onValueChange = { purRate = it },
                        label = { Text("Pur. Rate ₹") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = stock,
                    onValueChange = { stock = it },
                    label = { Text("Opening Stock Qty") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        name,
                        hsn,
                        unit,
                        safeDouble(gst, 18.0),
                        safeDouble(saleRate, 0.0),
                        safeDouble(purRate, 0.0),
                        safeDouble(stock, 0.0)
                    )
                },
                enabled = name.isNotBlank()
            ) { Text("Save Item") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
