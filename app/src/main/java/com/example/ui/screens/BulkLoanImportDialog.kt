package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.ExportPrintHelper
import com.example.core.formatInr
import com.example.core.safeDouble
import com.example.data.BulkLoanImporter
import com.example.data.model.BulkLoanPartyRow
import com.example.data.model.LoanReminderItem
import com.example.data.model.LoanReminderRule
import com.example.ui.theme.LimeGreen
import com.example.ui.theme.LimeSoft
import com.example.ui.theme.LocalSolarColors
import com.example.ui.theme.MaroonContainerLight
import com.example.ui.theme.MaroonPrimary
import com.example.ui.theme.TabularNumberStyle
import com.example.ui.viewmodel.BulkLoanScanUiState
import java.util.UUID

@Composable
fun BulkLoanImportDialog(
    scanState: BulkLoanScanUiState,
    onDismiss: () -> Unit,
    onPickFile: (Uri) -> Unit,
    onParseRawText: (rawText: String, useAi: Boolean) -> Unit,
    onUpdateRow: (String, (BulkLoanPartyRow) -> BulkLoanPartyRow) -> Unit,
    onAddBlankRow: () -> Unit,
    onRemoveRow: (String) -> Unit,
    onCommitParties: (String) -> Unit,
    onResetScan: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val solarColors = LocalSolarColors.current
    var selectedMethodTab by remember { mutableIntStateOf(0) }
    var rawTextInput by remember { mutableStateOf("") }

    val spreadsheetPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onPickFile(uri)
    }

    val pdfOrDocPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onPickFile(uri)
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onPickFile(uri)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Surface(color = MaroonPrimary, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Bulk Import Loan Parties",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Excel / CSV • Register Photo / PDF (Gemini AI) • WhatsApp Text",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFDCEDC8)
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("bulk_loan_close_button")
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                }

                // 3 Instant Import Method Tabs
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(solarColors.gridHeader)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedMethodTab == 0,
                            onClick = { selectedMethodTab = 0 },
                            leadingIcon = { Icon(Icons.Filled.TableChart, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text("1. Excel / CSV") },
                            modifier = Modifier.testTag("bulk_tab_excel_csv")
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedMethodTab == 1,
                            onClick = { selectedMethodTab = 1 },
                            leadingIcon = { Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text("2. Photo / PDF (AI)") },
                            modifier = Modifier.testTag("bulk_tab_photo_pdf")
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedMethodTab == 2,
                            onClick = { selectedMethodTab = 2 },
                            leadingIcon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text("3. WhatsApp / Text") },
                            modifier = Modifier.testTag("bulk_tab_whatsapp_text")
                        )
                    }
                }

                HorizontalDivider(color = solarColors.gridBorderStrong)

                // Body Area
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Input Source Panel (collapsible / compact when reviewing)
                    if (scanState !is BulkLoanScanUiState.Review) {
                        when (selectedMethodTab) {
                            0 -> {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, solarColors.gridBorderStrong)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = "Upload Excel (.xlsx / .xls) or CSV Loan Sheet",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Automatically detects Party Name, Mobile, Site, kW, Referrer/Commission, Contract Value, Sanctioned Loan, 1st/2nd Loan Done & Dates, Installation, RSEB File, MCO OK, Meter & Subsidy.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    spreadsheetPicker.launch(
                                                        arrayOf(
                                                            "text/csv",
                                                            "text/comma-separated-values",
                                                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                                            "application/vnd.ms-excel",
                                                            "*/*"
                                                        )
                                                    )
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(48.dp)
                                                    .testTag("upload_excel_csv_button")
                                            ) {
                                                Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("Pick Excel / CSV", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    ExportPrintHelper.exportSampleLoanPartiesCsv(context).fold(
                                                        onSuccess = { file -> onShowMessage("Template ready: ${file.name}") },
                                                        onFailure = { err -> onShowMessage(err.message ?: "Export failed.") }
                                                    )
                                                },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(48.dp)
                                                    .testTag("download_sample_csv_button")
                                            ) {
                                                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("Sample CSV", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                        OutlinedButton(
                                            onClick = onAddBlankRow,
                                            modifier = Modifier.fillMaxWidth().height(48.dp)
                                        ) {
                                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Start With Blank Editable Grid")
                                        }
                                    }
                                }
                            }

                            1 -> {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, solarColors.gridBorderStrong)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = "Scan Register Photo or Loan Statement PDF (Gemini AI)",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Upload a photo of your handwritten/printed NB Solar Loan Register or bank/party PDF statement. Gemini extracts all 13+ rows into the review grid.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    photoPicker.launch(
                                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                    )
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(48.dp)
                                                    .testTag("upload_register_photo_button")
                                            ) {
                                                Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("Register Photo", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                            Button(
                                                onClick = {
                                                    pdfOrDocPicker.launch(arrayOf("application/pdf", "image/*"))
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = LimeGreen),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(48.dp)
                                                    .testTag("upload_register_pdf_button")
                                            ) {
                                                Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("Upload PDF", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }
                                }
                            }

                            2 -> {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, solarColors.gridBorderStrong)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Paste Raw WhatsApp / Ledger Text List",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        OutlinedTextField(
                                            value = rawTextInput,
                                            onValueChange = { rawTextInput = it },
                                            label = { Text("One party per line (Name, kW, Deal, 1st/2nd Loan, Status)") },
                                            placeholder = {
                                                Text(
                                                    "1. Ramesh Choudhary | 9829012345 | Dabok | 5kW | Ref: Thakur Ji 3% | Deal 2.65L | Sanctioned 2L | 1st Loan 1L Done 2026-11-10 | 2nd Loan 1L Pending | Inst Done | RSEB Applied\n" +
                                                        "2. Suresh Mehta 9414055667 Mavli 3kW Deal 1.85L Sanctioned 1.4L 1st 70k Done 2026-11-18 2nd 70k Pending Inst Done Meter Installed"
                                                )
                                            },
                                            minLines = 5,
                                            maxLines = 8,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("bulk_loan_raw_text_input")
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = { onParseRawText(rawTextInput, false) },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(48.dp)
                                                    .testTag("quick_parse_text_button")
                                            ) {
                                                Text("Quick Parse", maxLines = 1)
                                            }
                                            Button(
                                                onClick = { onParseRawText(rawTextInput, true) },
                                                colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(48.dp)
                                                    .testTag("ai_parse_text_button")
                                            ) {
                                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("AI Smart Parse", maxLines = 1)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Scan Progress / Error / Review Table
                    when (scanState) {
                        is BulkLoanScanUiState.Idle -> {
                            Surface(
                                color = solarColors.gridHeader,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, solarColors.gridBorderStrong),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Tip: Upload your spreadsheet, ledger photo/PDF, or paste a WhatsApp list above to preview & edit all parties before saving to live Supabase.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        }

                        is BulkLoanScanUiState.Parsing -> {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaroonPrimary.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(20.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    CircularProgressIndicator(color = MaroonPrimary, modifier = Modifier.size(28.dp))
                                    Column {
                                        Text(scanState.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text("Detecting loan tranches, dates, installation, MCO & subsidy statuses…", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }

                        is BulkLoanScanUiState.Error -> {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                                border = BorderStroke(1.dp, solarColors.creditRed)
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Warning, contentDescription = null, tint = solarColors.creditRed)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Import Notice", fontWeight = FontWeight.Bold, color = solarColors.creditRed)
                                    }
                                    Text(scanState.message, style = MaterialTheme.typography.bodySmall, color = Color(0xFF7F1D1D))
                                    OutlinedButton(onClick = onResetScan, modifier = Modifier.height(48.dp)) {
                                        Text("Try Another File / Method")
                                    }
                                }
                            }
                        }

                        is BulkLoanScanUiState.Review -> {
                            val summary = remember(scanState.rows) {
                                BulkLoanImporter.computeBatchSummary(scanState.rows)
                            }

                            // Summary Totals Bar + Re-import action
                            Surface(
                                color = MaroonContainerLight,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, MaroonPrimary.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${scanState.sourceLabel} • Review & Edit Before Saving",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaroonPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedButton(
                                            onClick = onResetScan,
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.height(36.dp)
                                        ) {
                                            Text("Re-Upload", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        SummaryMetricBox(
                                            label = "Total Parties",
                                            value = "${summary.selectedCount}/${summary.totalRows}" +
                                                if (summary.duplicateCount > 0) " (${summary.duplicateCount} Dup)" else "",
                                            highlightColor = if (summary.duplicateCount > 0) solarColors.solarAmber else MaroonPrimary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        SummaryMetricBox(
                                            label = "1st Loan Recv'd",
                                            value = formatInr(summary.totalFirstLoanReceivedInr),
                                            highlightColor = LimeGreen,
                                            modifier = Modifier.weight(1f)
                                        )
                                        SummaryMetricBox(
                                            label = "2nd Loan Pending",
                                            value = formatInr(summary.totalSecondLoanPendingInr),
                                            highlightColor = solarColors.creditRed,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }

                            // Horizontally Scrollable Editable Multi-Row Review Table
                            val horizScroll = rememberScrollState()
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, solarColors.gridBorderStrong),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Row(
                                        modifier = Modifier
                                            .horizontalScroll(horizScroll)
                                            .background(solarColors.navyHeader)
                                            .padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        GridHeaderCell("Sel", 44.dp)
                                        GridHeaderCell("Party Name", 150.dp)
                                        GridHeaderCell("Mobile", 108.dp)
                                        GridHeaderCell("Site / City", 115.dp)
                                        GridHeaderCell("kW", 62.dp)
                                        GridHeaderCell("Referrer", 110.dp)
                                        GridHeaderCell("Comm%", 62.dp)
                                        GridHeaderCell("Contract (₹)", 98.dp)
                                        GridHeaderCell("Sanctioned (₹)", 102.dp)
                                        GridHeaderCell("1st Loan (₹)", 96.dp)
                                        GridHeaderCell("1st Done", 64.dp)
                                        GridHeaderCell("1st Date", 96.dp)
                                        GridHeaderCell("2nd Loan (₹)", 96.dp)
                                        GridHeaderCell("2nd Done", 64.dp)
                                        GridHeaderCell("2nd Date", 96.dp)
                                        GridHeaderCell("Inst", 52.dp)
                                        GridHeaderCell("RSEB", 52.dp)
                                        GridHeaderCell("MCO", 52.dp)
                                        GridHeaderCell("Meter", 54.dp)
                                        GridHeaderCell("Sub", 50.dp)
                                        GridHeaderCell("Del", 46.dp)
                                    }

                                    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                        items(scanState.rows, key = { it.id }) { row ->
                                        val rowBg = when {
                                            row.isDuplicateInDb -> Color(0xFFFFF7ED)
                                            !row.selected -> Color(0xFFF8FAFC)
                                            else -> MaterialTheme.colorScheme.surface
                                        }
                                        Row(
                                            modifier = Modifier
                                                .horizontalScroll(horizScroll)
                                                .background(rowBg)
                                                .border(0.5.dp, solarColors.gridBorderStrong),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(modifier = Modifier.width(44.dp), contentAlignment = Alignment.Center) {
                                                Checkbox(
                                                    checked = row.selected,
                                                    onCheckedChange = { chk -> onUpdateRow(row.id) { it.copy(selected = chk) } }
                                                )
                                            }
                                            Column(
                                                modifier = Modifier
                                                    .width(150.dp)
                                                    .padding(horizontal = 4.dp, vertical = 4.dp)
                                            ) {
                                                CompactGridTextField(
                                                    value = row.partyName,
                                                    onValueChange = { v -> onUpdateRow(row.id) { it.copy(partyName = v) } }
                                                )
                                                if (row.isDuplicateInDb) {
                                                    Text(
                                                        text = "DUP: ${row.duplicateReason}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = solarColors.creditRed,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                            GridTextCell(row.mobile, 108.dp, KeyboardType.Phone) { v ->
                                                onUpdateRow(row.id) { it.copy(mobile = v) }
                                            }
                                            GridTextCell(row.siteCity, 115.dp) { v ->
                                                onUpdateRow(row.id) { it.copy(siteCity = v) }
                                            }
                                            GridNumberCell(row.capacityKw, 62.dp) { v ->
                                                onUpdateRow(row.id) { it.copy(capacityKw = safeDouble(v, it.capacityKw)) }
                                            }
                                            GridTextCell(row.referrerName, 110.dp) { v ->
                                                onUpdateRow(row.id) { it.copy(referrerName = v) }
                                            }
                                            GridNumberCell(row.commissionPct, 62.dp) { v ->
                                                onUpdateRow(row.id) { it.copy(commissionPct = safeDouble(v, 0.0)) }
                                            }
                                            GridNumberCell(row.totalContractInr, 98.dp) { v ->
                                                onUpdateRow(row.id) { it.copy(totalContractInr = safeDouble(v, 0.0)) }
                                            }
                                            GridNumberCell(row.sanctionedLoanInr, 102.dp) { v ->
                                                onUpdateRow(row.id) { it.copy(sanctionedLoanInr = safeDouble(v, 0.0)) }
                                            }
                                            GridNumberCell(row.loan1Amount, 96.dp) { v ->
                                                onUpdateRow(row.id) { it.copy(loan1Amount = safeDouble(v, 0.0)) }
                                            }
                                            GridCheckCell(row.loan1Done, 64.dp) { chk ->
                                                onUpdateRow(row.id) { it.copy(loan1Done = chk) }
                                            }
                                            GridTextCell(row.loan1Date, 96.dp) { v ->
                                                onUpdateRow(row.id) { it.copy(loan1Date = v) }
                                            }
                                            GridNumberCell(row.loan2Amount, 96.dp) { v ->
                                                onUpdateRow(row.id) { it.copy(loan2Amount = safeDouble(v, 0.0)) }
                                            }
                                            GridCheckCell(row.loan2Done, 64.dp) { chk ->
                                                onUpdateRow(row.id) { it.copy(loan2Done = chk) }
                                            }
                                            GridTextCell(row.loan2Date, 96.dp) { v ->
                                                onUpdateRow(row.id) { it.copy(loan2Date = v) }
                                            }
                                            GridCheckCell(row.installationDone, 52.dp) { chk ->
                                                onUpdateRow(row.id) { it.copy(installationDone = chk) }
                                            }
                                            GridCheckCell(row.rsebFileApplied, 52.dp) { chk ->
                                                onUpdateRow(row.id) { it.copy(rsebFileApplied = chk) }
                                            }
                                            GridCheckCell(row.mcoOk, 52.dp) { chk ->
                                                onUpdateRow(row.id) { it.copy(mcoOk = chk) }
                                            }
                                            GridCheckCell(row.meterInstalled, 54.dp) { chk ->
                                                onUpdateRow(row.id) { it.copy(meterInstalled = chk) }
                                            }
                                            GridCheckCell(row.subsidyReceived, 50.dp) { chk ->
                                                onUpdateRow(row.id) { it.copy(subsidyReceived = chk) }
                                            }
                                            IconButton(
                                                onClick = { onRemoveRow(row.id) },
                                                modifier = Modifier.width(46.dp)
                                            ) {
                                                Icon(
                                                    Icons.Filled.Delete,
                                                    contentDescription = "Remove Row",
                                                    tint = solarColors.creditRed,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                    }
                                }
                            }

                            // Bottom Action Bar
                            var isCommitting by remember(scanState.rows.size) { mutableStateOf(false) }
                            val commitTxnUuid = remember(scanState.rows.size) { UUID.randomUUID().toString() }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onAddBlankRow,
                                    enabled = !isCommitting,
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Row")
                                }
                                Button(
                                    onClick = {
                                        if (isCommitting) return@Button
                                        isCommitting = true
                                        onCommitParties(commitTxnUuid)
                                    },
                                    enabled = summary.selectedCount > 0 && !isCommitting,
                                    colors = ButtonDefaults.buttonColors(containerColor = LimeGreen),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("bulk_loan_commit_button")
                                ) {
                                    Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = if (isCommitting) "Importing..." else "Import & Save ${summary.selectedCount} Parties to Live Cloud",
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LiveRemindersDrawerDialog(
    reminders: List<LoanReminderItem>,
    canBulkImport: Boolean,
    onDismiss: () -> Unit,
    onOpenBulkImport: () -> Unit,
    onTickMcoOk: (String) -> Unit,
    onOpenParty: (String) -> Unit
) {
    val solarColors = LocalSolarColors.current
    var selectedRuleFilter by remember { mutableStateOf<LoanReminderRule?>(null) }

    val filteredReminders = remember(reminders, selectedRuleFilter) {
        if (selectedRuleFilter == null) reminders else reminders.filter { it.rule == selectedRuleFilter }
    }
    val mcoPendingCount = remember(reminders) {
        reminders.count { it.rule == LoanReminderRule.PERSISTENT_RSEB_MCO }
    }
    val urgentCount = remember(reminders) {
        reminders.count { it.isUrgent }
    }
    val pendingLoanAndSubsidyInr = remember(reminders) {
        reminders.filter {
            it.rule == LoanReminderRule.FIRST_LOAN_PENDING ||
                it.rule == LoanReminderRule.METER_TO_SECOND_LOAN ||
                it.rule == LoanReminderRule.SECOND_LOAN_TO_SUBSIDY
        }.sumOf { it.amountInr }
    }

    val orderedRules = remember {
        listOf(
            LoanReminderRule.PERSISTENT_RSEB_MCO,
            LoanReminderRule.INSTALLATION_2DAY_ALERT,
            LoanReminderRule.METER_TO_SECOND_LOAN,
            LoanReminderRule.SECOND_LOAN_TO_SUBSIDY,
            LoanReminderRule.FIRST_LOAN_PENDING
        )
    }
    val groupedSections = remember(filteredReminders, selectedRuleFilter) {
        val rulesToRender = selectedRuleFilter?.let { listOf(it) } ?: orderedRules
        rulesToRender.mapNotNull { rule ->
            val list = filteredReminders.filter { it.rule == rule }
            if (list.isEmpty()) null else rule to list
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Surface(color = solarColors.navyHeader, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                Icons.Filled.NotificationsActive,
                                contentDescription = null,
                                tint = Color(0xFFFDE68A),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Today's Action & Payment Summary (${reminders.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Grouped: Inst (2d), 2nd Loan, Subsidy (5d), RSEB MCO & 1st Loan",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFCBD5E1),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("reminders_drawer_close")
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Close Summary", tint = Color.White)
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Combined KPI Summary Strip
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SummaryMetricBox(
                            label = "Urgent / Due",
                            value = "$urgentCount / ${reminders.size}",
                            highlightColor = if (urgentCount > 0) solarColors.creditRed else LimeGreen,
                            modifier = Modifier.weight(1f)
                        )
                        SummaryMetricBox(
                            label = "MCO Pending",
                            value = "$mcoPendingCount Sites",
                            highlightColor = if (mcoPendingCount > 0) solarColors.creditRed else LimeGreen,
                            modifier = Modifier.weight(1f)
                        )
                        SummaryMetricBox(
                            label = "Pending Amt",
                            value = formatInr(pendingLoanAndSubsidyInr),
                            highlightColor = MaroonPrimary,
                            modifier = Modifier.weight(1.15f)
                        )
                    }

                    if (canBulkImport) {
                        Button(
                            onClick = {
                                onDismiss()
                                onOpenBulkImport()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("drawer_bulk_import_loan_button")
                        ) {
                            Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Bulk Import Loan Parties (Excel / CSV / Photo / Text)", fontWeight = FontWeight.Bold)
                        }
                    }

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            FilterChip(
                                selected = selectedRuleFilter == null,
                                onClick = { selectedRuleFilter = null },
                                label = { Text("All (${reminders.size})") }
                            )
                        }
                        items(orderedRules) { rule ->
                            val cnt = reminders.count { it.rule == rule }
                            FilterChip(
                                selected = selectedRuleFilter == rule,
                                onClick = { selectedRuleFilter = if (selectedRuleFilter == rule) null else rule },
                                label = { Text("${rule.badge} ($cnt)") }
                            )
                        }
                    }

                    if (filteredReminders.isEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, solarColors.gridBorderStrong)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = LimeGreen,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text("All Caught Up!", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    "No pending Loan, Installation, Meter, Subsidy, or RSEB MCO alerts right now.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            groupedSections.forEach { (rule, sectionItems) ->
                                val sectionAccent = when (rule) {
                                    LoanReminderRule.PERSISTENT_RSEB_MCO -> solarColors.creditRed
                                    LoanReminderRule.INSTALLATION_2DAY_ALERT -> solarColors.solarAmber
                                    LoanReminderRule.METER_TO_SECOND_LOAN -> MaroonPrimary
                                    LoanReminderRule.SECOND_LOAN_TO_SUBSIDY -> LimeGreen
                                    LoanReminderRule.FIRST_LOAN_PENDING -> solarColors.debitBlue
                                }
                                val sectionHeading = when (rule) {
                                    LoanReminderRule.PERSISTENT_RSEB_MCO -> "RSEB MCO Pending (${sectionItems.size})"
                                    LoanReminderRule.INSTALLATION_2DAY_ALERT -> "2-Day Installation Due After 1st Loan (${sectionItems.size})"
                                    LoanReminderRule.METER_TO_SECOND_LOAN -> "2nd Loan Due After Meter Installed (${sectionItems.size})"
                                    LoanReminderRule.SECOND_LOAN_TO_SUBSIDY -> "5-Day Subsidy Check After 2nd Loan (${sectionItems.size})"
                                    LoanReminderRule.FIRST_LOAN_PENDING -> "1st Loan Tranche Pending (${sectionItems.size})"
                                }

                                item(key = "header_${rule.name}") {
                                    Surface(
                                        color = sectionAccent.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, sectionAccent.copy(alpha = 0.35f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = sectionHeading,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = sectionAccent
                                            )
                                            val secAmt = sectionItems.sumOf { it.amountInr }
                                            if (secAmt > 0.0) {
                                                Text(
                                                    text = formatInr(secAmt),
                                                    style = MaterialTheme.typography.labelSmall.merge(TabularNumberStyle),
                                                    fontWeight = FontWeight.Bold,
                                                    color = sectionAccent
                                                )
                                            }
                                        }
                                    }
                                }

                                items(sectionItems, key = { it.id }) { rem ->
                                    val accent = when (rem.rule) {
                                        LoanReminderRule.PERSISTENT_RSEB_MCO -> solarColors.creditRed
                                        LoanReminderRule.INSTALLATION_2DAY_ALERT -> if (rem.isUrgent) solarColors.creditRed else solarColors.solarAmber
                                        LoanReminderRule.METER_TO_SECOND_LOAN -> MaroonPrimary
                                        LoanReminderRule.SECOND_LOAN_TO_SUBSIDY -> if (rem.isUrgent) solarColors.creditRed else LimeGreen
                                        LoanReminderRule.FIRST_LOAN_PENDING -> solarColors.debitBlue
                                    }
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        border = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "${rem.partyName} • ${rem.capacityKw} kW",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Surface(
                                                    color = accent.copy(alpha = 0.14f),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = rem.rule.badge,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = accent,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = rem.title,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = accent
                                            )
                                            Text(
                                                text = rem.detail,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (rem.amountInr > 0.0) {
                                                    Text(
                                                        text = "Amt: ${formatInr(rem.amountInr)}",
                                                        style = MaterialTheme.typography.labelMedium.merge(TabularNumberStyle),
                                                        fontWeight = FontWeight.Bold,
                                                        color = accent
                                                    )
                                                }
                                                Spacer(Modifier.weight(1f))
                                                if (rem.rule == LoanReminderRule.PERSISTENT_RSEB_MCO) {
                                                    Button(
                                                        onClick = { onTickMcoOk(rem.partyId) },
                                                        colors = ButtonDefaults.buttonColors(containerColor = LimeGreen),
                                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                        modifier = Modifier
                                                            .height(40.dp)
                                                            .testTag("tick_mco_ok_${rem.partyId}")
                                                    ) {
                                                        Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                                        Spacer(Modifier.width(4.dp))
                                                        Text("Tick MCO OK", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                                OutlinedButton(
                                                    onClick = {
                                                        onDismiss()
                                                        onOpenParty(rem.partyId)
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                    modifier = Modifier.height(40.dp)
                                                ) {
                                                    Text("Open", style = MaterialTheme.typography.labelMedium)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryMetricBox(
    label: String,
    value: String,
    highlightColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, highlightColor.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge.merge(TabularNumberStyle),
                fontWeight = FontWeight.Bold,
                color = highlightColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun GridHeaderCell(text: String, width: Dp) {
    Box(
        modifier = Modifier
            .width(width)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun GridTextCell(
    value: String,
    width: Dp,
    keyboardType: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .width(width)
            .padding(horizontal = 4.dp, vertical = 6.dp)
    ) {
        CompactGridTextField(value = value, keyboardType = keyboardType, onValueChange = onChange)
    }
}

@Composable
private fun GridNumberCell(
    value: Double,
    width: Dp,
    onChange: (String) -> Unit
) {
    val formatted = remember(value) {
        if (value == 0.0) "0" else if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
    }
    var localText by remember(value) { mutableStateOf(formatted) }
    Box(
        modifier = Modifier
            .width(width)
            .padding(horizontal = 4.dp, vertical = 6.dp)
    ) {
        CompactGridTextField(
            value = localText,
            keyboardType = KeyboardType.Decimal,
            onValueChange = { next ->
                localText = next
                onChange(next)
            }
        )
    }
}

@Composable
private fun GridCheckCell(checked: Boolean, width: Dp, onChecked: (Boolean) -> Unit) {
    Box(modifier = Modifier.width(width), contentAlignment = Alignment.Center) {
        Checkbox(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun CompactGridTextField(
    value: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    onValueChange: (String) -> Unit
) {
    val solarColors = LocalSolarColors.current
    Surface(
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, solarColors.gridBorderStrong),
        color = MaterialTheme.colorScheme.surface
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp)
        )
    }
}
