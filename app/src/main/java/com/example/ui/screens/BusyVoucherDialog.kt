package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.core.formatInr
import com.example.core.safeDouble
import com.example.core.todayIsoDate
import com.example.data.model.BillRefType
import com.example.data.model.BusyAccount
import com.example.data.model.BusyAccountGroup
import com.example.data.model.BusyItem
import com.example.data.model.BusyLineItem
import com.example.data.model.BusyVoucherType
import com.example.data.model.LeadSource
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalSolarColors

@Composable
fun BusyVoucherDialog(
    initialType: BusyVoucherType,
    accounts: List<BusyAccount>,
    items: List<BusyItem>,
    onDismiss: () -> Unit,
    onPost: (
        type: BusyVoucherType,
        date: String,
        party: BusyAccount,
        contra: BusyAccount,
        costCenter: LeadSource,
        isIgst: Boolean,
        taxable: Double,
        gstPct: Double,
        billRefType: BillRefType,
        billRefNo: String,
        utrNo: String,
        narration: String,
        lineItems: List<BusyLineItem>
    ) -> Boolean
) {
    val solarColors = LocalSolarColors.current
    var type by remember { mutableStateOf(initialType) }
    var date by remember { mutableStateOf(todayIsoDate()) }

    val defaultParty = remember(type, accounts) {
        when (type) {
            BusyVoucherType.SALES, BusyVoucherType.RECEIPT ->
                accounts.firstOrNull { it.group == BusyAccountGroup.SUNDRY_DEBTORS } ?: accounts.firstOrNull()
            BusyVoucherType.PURCHASE, BusyVoucherType.PAYMENT ->
                accounts.firstOrNull { it.group == BusyAccountGroup.SUNDRY_CREDITORS } ?: accounts.firstOrNull()
            BusyVoucherType.CONTRA ->
                accounts.firstOrNull { it.group == BusyAccountGroup.CASH_IN_HAND } ?: accounts.firstOrNull()
            BusyVoucherType.JOURNAL ->
                accounts.firstOrNull { it.group == BusyAccountGroup.INDIRECT_EXPENSES } ?: accounts.firstOrNull()
        }
    }
    val defaultContra = remember(type, accounts) {
        when (type) {
            BusyVoucherType.SALES ->
                accounts.firstOrNull { it.group == BusyAccountGroup.SALES_ACCOUNTS } ?: accounts.firstOrNull()
            BusyVoucherType.PURCHASE ->
                accounts.firstOrNull { it.group == BusyAccountGroup.PURCHASE_ACCOUNTS } ?: accounts.firstOrNull()
            BusyVoucherType.PAYMENT, BusyVoucherType.RECEIPT, BusyVoucherType.CONTRA ->
                accounts.firstOrNull { it.group == BusyAccountGroup.BANK_ACCOUNTS } ?: accounts.firstOrNull()
            BusyVoucherType.JOURNAL ->
                accounts.firstOrNull { it.group == BusyAccountGroup.SUNDRY_DEBTORS } ?: accounts.lastOrNull()
        }
    }

    var partyAcc by remember(type) { mutableStateOf(defaultParty) }
    var contraAcc by remember(type) { mutableStateOf(defaultContra) }
    var costCenter by remember { mutableStateOf(partyAcc?.defaultCostCenter ?: LeadSource.NB_DIRECT) }
    var isIgst by remember { mutableStateOf(false) }
    var manualAmount by remember { mutableStateOf("") }
    var gstRate by remember { mutableStateOf(if (type == BusyVoucherType.SALES || type == BusyVoucherType.PURCHASE) "13.8" else "0") }
    var billRefType by remember {
        mutableStateOf(
            if (type == BusyVoucherType.SALES || type == BusyVoucherType.PURCHASE) BillRefType.NEW_REF
            else BillRefType.AGAINST_REF
        )
    }
    var billRefNo by remember { mutableStateOf("") }
    var utrNo by remember { mutableStateOf("") }
    var narration by remember { mutableStateOf("") }

    // Line items for Sales (F8) & Purchase (F9)
    val lineItems = remember { mutableStateListOf<BusyLineItem>() }
    var selectedItemToAdd by remember { mutableStateOf(items.firstOrNull()) }
    var itemQty by remember { mutableStateOf("1") }
    var itemRate by remember { mutableStateOf((items.firstOrNull()?.defaultSaleRate ?: 0.0).toString()) }

    val isTaxVoucher = type == BusyVoucherType.SALES || type == BusyVoucherType.PURCHASE
    val isBankCashVoucher = type == BusyVoucherType.PAYMENT || type == BusyVoucherType.RECEIPT || type == BusyVoucherType.CONTRA

    val computedTaxable = if (lineItems.isNotEmpty()) lineItems.sumOf { it.taxableAmount } else safeDouble(manualAmount)
    val computedGst = if (isTaxVoucher) {
        if (lineItems.isNotEmpty()) lineItems.sumOf { it.gstAmount }
        else computedTaxable * (safeDouble(gstRate) / 100.0)
    } else 0.0
    val grandTotal = computedTaxable + computedGst

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("BUSY 21 Voucher Entry", style = MaterialTheme.typography.titleLarge)
                Text(
                    "${type.label} Voucher (${type.shortcut})",
                    style = MaterialTheme.typography.labelMedium,
                    color = solarColors.solarAmber
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Voucher Type Switcher
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(BusyVoucherType.entries) { vt ->
                        FilterChip(
                            selected = type == vt,
                            onClick = { type = vt },
                            label = { Text("${vt.label} (${vt.shortcut})") }
                        )
                    }
                }

                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Voucher Date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Party / Debit / Credit Account Picker
                Text(
                    when (type) {
                        BusyVoucherType.SALES -> "Party / Customer Account (Dr)"
                        BusyVoucherType.PURCHASE -> "Supplier / Vendor Account (Cr)"
                        BusyVoucherType.PAYMENT -> "Paid To Account (Dr)"
                        BusyVoucherType.RECEIPT -> "Received From Party (Cr)"
                        BusyVoucherType.JOURNAL -> "Debit Account (Dr)"
                        BusyVoucherType.CONTRA -> "Deposit Into Bank/Cash (Dr)"
                    },
                    style = MaterialTheme.typography.labelMedium
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(accounts) { acc ->
                        FilterChip(
                            selected = partyAcc?.id == acc.id,
                            onClick = {
                                partyAcc = acc
                                costCenter = acc.defaultCostCenter
                            },
                            label = { Text(acc.name) }
                        )
                    }
                }

                Text(
                    when (type) {
                        BusyVoucherType.SALES -> "Sales Ledger (Cr)"
                        BusyVoucherType.PURCHASE -> "Purchase Ledger (Dr)"
                        BusyVoucherType.PAYMENT -> "Paid Via Bank / Cash (Cr)"
                        BusyVoucherType.RECEIPT -> "Deposited Into Bank / Cash (Dr)"
                        BusyVoucherType.JOURNAL -> "Credit Account (Cr)"
                        BusyVoucherType.CONTRA -> "Withdraw From Bank/Cash (Cr)"
                    },
                    style = MaterialTheme.typography.labelMedium
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(accounts) { acc ->
                        FilterChip(
                            selected = contraAcc?.id == acc.id,
                            onClick = { contraAcc = acc },
                            label = { Text(acc.name) }
                        )
                    }
                }

                // Cost Center Tagging
                Text("Cost Center", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(LeadSource.entries) { cc ->
                        FilterChip(
                            selected = costCenter == cc,
                            onClick = { costCenter = cc },
                            label = { Text(cc.shortLabel) }
                        )
                    }
                }

                // Line Item Builder for Sales (F8) & Purchase (F9)
                if (isTaxVoucher) {
                    HorizontalDivider()
                    Text("Item Selection (Auto-Updates Stock)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(items) { itm ->
                            FilterChip(
                                selected = selectedItemToAdd?.id == itm.id,
                                onClick = {
                                    selectedItemToAdd = itm
                                    itemRate = (if (type == BusyVoucherType.SALES) itm.defaultSaleRate else itm.defaultPurchaseRate).toString()
                                },
                                label = { Text("${itm.name} (${itm.gstRatePercent.toInt()}%)") }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = itemQty,
                            onValueChange = { itemQty = it },
                            label = { Text("Qty") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(0.7f)
                        )
                        OutlinedTextField(
                            value = itemRate,
                            onValueChange = { itemRate = it },
                            label = { Text("Rate ₹") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = {
                                val itm = selectedItemToAdd ?: return@OutlinedButton
                                val q = safeDouble(itemQty, 1.0).coerceAtLeast(0.01)
                                val r = safeDouble(itemRate, 0.0).coerceAtLeast(0.0)
                                lineItems.add(
                                    BusyLineItem(
                                        itemId = itm.id,
                                        itemName = itm.name,
                                        hsnCode = itm.hsnCode,
                                        quantity = q,
                                        unit = itm.unit,
                                        rate = r,
                                        gstRatePercent = itm.gstRatePercent
                                    )
                                )
                            }
                        ) { Text("+ Add") }
                    }

                    lineItems.forEachIndexed { idx, li ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${li.itemName} × ${li.quantity} @ ${formatInr(li.rate)} (+${li.gstRatePercent}% GST)",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { lineItems.removeAt(idx) }) { Text("Remove") }
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isIgst, onCheckedChange = { isIgst = it })
                        Text("Inter-State Supply (IGST instead of CGST + SGST)", style = MaterialTheme.typography.bodySmall)
                    }
                }

                // Manual amount entry (if no line items added or for Payment/Receipt/Journal/Contra)
                if (lineItems.isEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = manualAmount,
                            onValueChange = { manualAmount = it },
                            label = { Text(if (isTaxVoucher) "Taxable Amount (₹) *" else "Amount (₹) *") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1.3f)
                        )
                        if (isTaxVoucher) {
                            OutlinedTextField(
                                value = gstRate,
                                onValueChange = { gstRate = it },
                                label = { Text("GST %") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(0.7f)
                            )
                        }
                    }
                }

                // Bill-by-Bill Reference Adjustment + UTR / Cheque No.
                if (isBankCashVoucher || isTaxVoucher) {
                    Text("Bill-by-Bill Reference Adjustment", style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(BillRefType.entries) { ref ->
                            FilterChip(
                                selected = billRefType == ref,
                                onClick = { billRefType = ref },
                                label = { Text(ref.label) }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = billRefNo,
                            onValueChange = { billRefNo = it },
                            label = { Text("Bill Ref No.") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        if (isBankCashVoucher) {
                            OutlinedTextField(
                                value = utrNo,
                                onValueChange = { utrNo = it },
                                label = { Text("UTR / Cheque No.") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = narration,
                    onValueChange = { narration = it },
                    label = { Text("Narration") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Live GST & Grand Total Preview Strip
                Surface(
                    color = solarColors.gridHeader,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, solarColors.hairlineBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        if (isTaxVoucher) {
                            val taxDetail = if (isIgst) {
                                "IGST: ${formatInr(computedGst, true)}"
                            } else {
                                "CGST: ${formatInr(computedGst / 2, true)}  |  SGST: ${formatInr(computedGst / 2, true)}"
                            }
                            Text(taxDetail, style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Voucher Total: ${formatInr(grandTotal, true)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = solarColors.debitBlue
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = partyAcc ?: return@Button
                    val c = contraAcc ?: return@Button
                    val posted = onPost(
                        type,
                        date,
                        p,
                        c,
                        costCenter,
                        isIgst,
                        computedTaxable,
                        safeDouble(gstRate, 0.0),
                        billRefType,
                        billRefNo,
                        utrNo,
                        narration,
                        lineItems.toList()
                    )
                    if (posted) onDismiss()
                },
                enabled = partyAcc != null && contraAcc != null && grandTotal > 0.0
            ) {
                Text("Save Voucher (${type.shortcut})")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
