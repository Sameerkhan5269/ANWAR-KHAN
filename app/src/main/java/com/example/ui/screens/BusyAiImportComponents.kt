package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.formatInr
import com.example.data.model.BusyAccount
import com.example.data.model.BusyAccountGroup
import com.example.data.model.BusyVoucherType
import com.example.data.model.LeadSource
import com.example.data.model.ParsedPurchaseBill
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalSolarColors
import com.example.ui.viewmodel.AiScanUiState
import java.util.UUID

@Composable
fun BusyAiAutoImportSection(
    aiState: AiScanUiState,
    accounts: List<BusyAccount>,
    onScanBankUri: (Uri, String) -> Unit,
    onScanBankText: (String, String) -> Unit,
    onToggleBankRow: (String) -> Unit,
    onPostReviewedBank: (String) -> Unit,
    onScanPurchaseBillUri: (Uri) -> Unit,
    onPostReviewedBill: (ParsedPurchaseBill, LeadSource, String) -> Unit,
    onResetAi: () -> Unit
) {
    val solarColors = LocalSolarColors.current
    val bankAccounts = remember(accounts) {
        accounts.filter { it.group == BusyAccountGroup.BANK_ACCOUNTS || it.group == BusyAccountGroup.CASH_IN_HAND }
            .ifEmpty { accounts }
    }
    var selectedBankId by remember(bankAccounts) { mutableStateOf(bankAccounts.firstOrNull()?.id.orEmpty()) }
    var pastedBankCsv by remember { mutableStateOf("") }
    var billCostCenter by remember { mutableStateOf(LeadSource.NB_DIRECT) }

    val bankPhotoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) onScanBankUri(uri, selectedBankId) }

    val bankDocPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) onScanBankUri(uri, selectedBankId) }

    val billPhotoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) onScanPurchaseBillUri(uri) }

    val billPdfPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) onScanPurchaseBillUri(uri) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // AI State Feedback Banner (Loading / Error / Review)
        when (aiState) {
            is AiScanUiState.Scanning -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = solarColors.solarAmberContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 3.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(aiState.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "Powered by Gemini AI • Extracting structured BUSY 21 vouchers…",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            is AiScanUiState.Error -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            "AI Scan Error",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            aiState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        TextButton(onClick = onResetAi) { Text("Dismiss") }
                    }
                }
            }

            is AiScanUiState.BankReview -> {
                val validSelectedCount = aiState.rows.count { it.selected && !it.isDuplicateUtr }
                val dupCount = aiState.rows.count { it.isDuplicateUtr }
                var isPostingBank by remember(aiState) { mutableStateOf(false) }
                val bankTxnUuid = remember(aiState) { UUID.randomUUID().toString() }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(2.dp, solarColors.emerald),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Bank Statement Review (${aiState.rows.size} rows)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(onClick = onResetAi) { Text("Cancel") }
                        }
                        if (dupCount > 0) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "$dupCount duplicate UTR(s) automatically blocked from posting.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }

                        aiState.rows.forEach { row ->
                            Surface(
                                color = if (row.isDuplicateUtr) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = row.selected && !row.isDuplicateUtr,
                                        enabled = !row.isDuplicateUtr,
                                        onCheckedChange = { onToggleBankRow(row.id) }
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                "${row.voucherType.label} (${row.voucherType.shortcut}) • ${row.partyName}",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                formatInr(row.amount, true),
                                                style = MaterialTheme.typography.labelLarge,
                                                fontFamily = JetBrainsMonoFontFamily,
                                                color = if (row.voucherType == BusyVoucherType.RECEIPT) solarColors.emerald else solarColors.creditRed
                                            )
                                        }
                                        Text(
                                            "${row.date} • UTR: ${row.utrNo.ifBlank { "N/A" }}" +
                                                if (row.isDuplicateUtr) " [DUPLICATE BLOCKED]" else "",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (row.isDuplicateUtr) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(row.narration, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = {
                                if (isPostingBank) return@Button
                                isPostingBank = true
                                onPostReviewedBank(bankTxnUuid)
                            },
                            enabled = validSelectedCount > 0 && !isPostingBank,
                            colors = ButtonDefaults.buttonColors(containerColor = solarColors.emerald),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("post_ai_bank_vouchers_btn")
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (isPostingBank) "Posting..." else "Post $validSelectedCount Vouchers in 1 Click")
                        }
                    }
                }
            }

            is AiScanUiState.BillReview -> {
                val bill = aiState.bill
                var isPostingBill by remember(aiState) { mutableStateOf(false) }
                val billTxnUuid = remember(aiState) { UUID.randomUUID().toString() }
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(2.dp, solarColors.solarAmber),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Scanned Purchase Bill (F9)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            TextButton(onClick = onResetAi) { Text("Cancel") }
                        }
                        Text("Supplier: ${bill.supplierName}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "GSTIN: ${bill.supplierGstin.ifBlank { "Unregistered" }} • Inv: ${bill.invoiceNo} • Date: ${bill.invoiceDate}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider()
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Taxable: ${formatInr(bill.taxableAmount, true)}", style = MaterialTheme.typography.bodySmall)
                            Text("CGST: ${formatInr(bill.cgstAmount, true)}", style = MaterialTheme.typography.bodySmall)
                            Text("SGST: ${formatInr(bill.sgstAmount, true)}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (bill.igstAmount > 0) {
                            Text("IGST: ${formatInr(bill.igstAmount, true)}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            "Grand Total: ${formatInr(bill.totalAmount, true)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = solarColors.debitBlue
                        )
                        if (bill.lineItems.isNotEmpty()) {
                            Text("Extracted Line Items (${bill.lineItems.size}):", style = MaterialTheme.typography.labelMedium)
                            bill.lineItems.forEach { li ->
                                Text(
                                    "• ${li.itemName} (${li.quantity} ${li.unit} @ ${formatInr(li.rate)} • ${li.gstRatePercent}% GST)",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        if (bill.detectedDcrSerials.isNotEmpty()) {
                            Surface(
                                color = solarColors.emeraldContainer,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "Detected ${bill.detectedDcrSerials.size} DCR Panel Serials (${bill.detectedPanelBrand} ${bill.detectedPanelWattage}W) — will auto-add to Inventory!",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }

                        Text("Assign Cost Center", style = MaterialTheme.typography.labelSmall)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(LeadSource.entries) { cc ->
                                FilterChip(
                                    selected = billCostCenter == cc,
                                    onClick = { billCostCenter = cc },
                                    label = { Text(cc.shortLabel) }
                                )
                            }
                        }

                        Button(
                            onClick = {
                                if (isPostingBill) return@Button
                                isPostingBill = true
                                onPostReviewedBill(bill, billCostCenter, billTxnUuid)
                            },
                            enabled = !isPostingBill,
                            colors = ButtonDefaults.buttonColors(containerColor = DeepNavy),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("post_ai_purchase_bill_btn")
                        ) {
                            Text(
                                if (isPostingBill) "Posting..." else "Post Purchase (F9) + Sync Inventory",
                                color = Color.White
                            )
                        }
                    }
                }
            }

            AiScanUiState.Idle -> Unit
        }

        // CARD 1: Bank Statement Auto-Import (PDF / CSV / Excel / Image)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, solarColors.hairlineBorder),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AccountBalance, contentDescription = null, tint = solarColors.debitBlue)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("1. Bank Statement AI Auto-Import", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Auto-parse F5 Payment & F6 Receipt vouchers, block duplicate UTRs & match party ledgers.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text("Target Bank Ledger:", style = MaterialTheme.typography.labelSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(bankAccounts) { b ->
                        FilterChip(
                            selected = selectedBankId == b.id,
                            onClick = { selectedBankId = b.id },
                            label = { Text(b.name) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            bankDocPicker.launch(arrayOf("application/pdf", "text/csv", "text/plain", "image/*"))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ai_bank_upload_file_btn")
                    ) {
                        Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("PDF / CSV")
                    }
                    OutlinedButton(
                        onClick = {
                            bankPhotoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ai_bank_upload_image_btn")
                    ) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Photo / Img")
                    }
                }

                OutlinedTextField(
                    value = pastedBankCsv,
                    onValueChange = { pastedBankCsv = it },
                    label = { Text("Or paste Bank Statement / CSV rows") },
                    placeholder = { Text("e.g., 2026-11-20 NEFT/UTR4092184 Ramesh Sharma Cr 85000") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = { onScanBankText(pastedBankCsv, selectedBankId) },
                    enabled = pastedBankCsv.isNotBlank() && aiState !is AiScanUiState.Scanning,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Parse Pasted Statement with AI")
                }
            }
        }

        // CARD 2: Purchase Bill Photo / PDF Auto-Scanner
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, solarColors.hairlineBorder),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.ReceiptLong, contentDescription = null, tint = solarColors.solarAmber)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("2. Purchase Bill Photo / PDF Auto-Scanner", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Extract Supplier, GSTIN, Invoice No., CGST/SGST, Line Items & DCR Serials → Post F9 + Inventory.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            billPhotoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DeepNavy),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ai_bill_photo_btn")
                    ) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Bill Photo", color = Color.White)
                    }
                    OutlinedButton(
                        onClick = { billPdfPicker.launch(arrayOf("application/pdf", "image/*")) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ai_bill_pdf_btn")
                    ) {
                        Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Bill PDF")
                    }
                }
            }
        }
    }
}
