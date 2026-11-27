package com.example.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.core.ExportPrintHelper
import com.example.core.RolePermissions
import com.example.core.formatInr
import com.example.data.model.BusyItem
import com.example.data.model.BusyVoucher
import com.example.data.model.CostingStructureType
import com.example.data.model.DcrPanel
import com.example.data.model.DocumentCategory
import com.example.data.model.LeadSource
import com.example.data.model.LeadStatus
import com.example.data.model.PartyDocument
import com.example.data.model.PartyProject
import com.example.data.model.ProjectDivision
import com.example.data.model.RawMaterial
import com.example.data.model.RoofStructureType
import com.example.data.model.SiteKharchaEntry
import com.example.data.model.SitePurchasePaymentSource
import com.example.data.model.SolarMeter
import com.example.data.model.SolarSystemType
import com.example.data.model.StaffRole
import com.example.data.model.WorkflowSection
import com.example.ui.theme.LimeGreen
import com.example.ui.theme.LocalSolarColors
import com.example.ui.theme.MaroonPrimary
import com.example.ui.theme.TabularNumberStyle
import com.example.ui.viewmodel.AiPhotoPlacementUiState
import java.io.File

@Composable
fun PartyDetailScreen(
    party: PartyProject,
    role: StaffRole,
    currentUserImprestBalance: Double,
    allocatedPanels: List<DcrPanel>,
    allocatedMeters: List<SolarMeter>,
    rawMaterials: List<RawMaterial>,
    partyKharcha: List<SiteKharchaEntry>,
    busyVouchers: List<BusyVoucher>,
    busyItems: List<BusyItem>,
    aiPhotoState: AiPhotoPlacementUiState,
    onPickRooftopPhoto: (Uri) -> Unit,
    onPickInverterPhoto: (Uri) -> Unit,
    onGenerateRooftopAi: (Double, Int, CostingStructureType, SolarSystemType) -> Unit,
    onGenerateInverterAi: (Double, SolarSystemType) -> Unit,
    onClearAiError: () -> Unit,
    onApplyQuoteToParty: (String, Double, RoofStructureType, Double, Double, String) -> Unit,
    onApproveAndConvertOrder: (
        existingPartyId: String?,
        customerName: String,
        customerMobile: String,
        siteLocation: String,
        consumerNo: String,
        capacityKw: Double,
        roofType: RoofStructureType,
        quotedRatePerKw: Double,
        dealValue: Double,
        subsidyAmount: Double,
        surveyNotes: String
    ) -> Unit = { _, _, _, _, _, _, _, _, _, _, _ -> },
    onShowMessage: (String) -> Unit,
    onBack: () -> Unit,
    onToggleStage: (String) -> Unit,
    onAddSiteNote: (String) -> Unit,
    onUploadDocument: (Uri, String, DocumentCategory, String) -> Unit,
    onUpdateLeadDetails: (
        mobile: String,
        cityOrLocation: String,
        consumerNo: String,
        capacityKw: Double,
        leadSource: LeadSource,
        referrerName: String,
        referrerPhone: String,
        leadDate: String,
        roofStructureType: RoofStructureType,
        quotedRatePerKw: Double,
        dealValue: Double,
        nextFollowUpDate: String,
        siteSurveyNotes: String,
        leadStatus: LeadStatus,
        division: ProjectDivision
    ) -> Unit,
    onAddSitePurchase: (
        itemDescription: String,
        quantity: String,
        vendorName: String,
        amount: Double,
        paymentSource: SitePurchasePaymentSource,
        billPhotoUri: Uri?
    ) -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val solarColors = LocalSolarColors.current
    val canSeeMoney = RolePermissions.canViewFinancials(role)
    val canSeeLoan = RolePermissions.canViewLoanSection(role)

    var activeSubTab by remember { mutableIntStateOf(0) }
    var docSearch by remember { mutableStateOf("") }
    var selectedDocCat by remember { mutableStateOf<DocumentCategory?>(null) }
    var pendingUploadUri by remember { mutableStateOf<Uri?>(null) }
    var pendingUploadDefaultCat by remember { mutableStateOf(DocumentCategory.GEOTAG_PHOTO) }
    var previewDoc by remember { mutableStateOf<PartyDocument?>(null) }
    var noteInput by remember { mutableStateOf("") }

    // Zero-permission Android Photo Picker (Google Play Policy compliant)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pendingUploadDefaultCat = DocumentCategory.GEOTAG_PHOTO
            pendingUploadUri = uri
        }
    }

    // Zero-permission SAF Document Picker for PDFs/Invoices/DCR
    val docPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingUploadUri = uri
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Party Header Strip
        Surface(
            color = solarColors.navyHeader,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("party_detail_back")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Pipeline",
                            tint = Color.White
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = party.partyName,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${party.division.shortLabel} • ${party.capacityKw} kW • ${party.leadSource.label}" +
                                if (party.consumerNumber.isNotBlank()) " • K-No: ${party.consumerNumber}" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFFDAD8)
                        )
                    }
                    Surface(
                        color = LimeGreen,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "${(party.progressFraction * 100).toInt()}%",
                            style = MaterialTheme.typography.labelLarge.merge(TabularNumberStyle),
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                // Financial KPI strip — strictly gated behind RolePermissions.canViewFinancials
                if (canSeeMoney) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MiniStat("Value", formatInr(party.projectValue), Color.White)
                        MiniStat("Received", formatInr(party.receivedAmount), Color(0xFFDCEDC8))
                        MiniStat("Pending", formatInr(party.pendingAmount), Color(0xFFFEE2E2))
                        MiniStat("Subsidy", formatInr(party.subsidyAmount), Color(0xFFFDE68A))
                    }
                }
            }
        }

        // 6 Sub-Tabs
        ScrollableTabRow(selectedTabIndex = activeSubTab, edgePadding = 8.dp) {
            Tab(
                selected = activeSubTab == 0,
                onClick = { activeSubTab = 0 },
                text = { Text("Lead Details") }
            )
            Tab(
                selected = activeSubTab == 1,
                onClick = { activeSubTab = 1 },
                text = { Text("3D, Photo & Cost") }
            )
            Tab(
                selected = activeSubTab == 2,
                onClick = { activeSubTab = 2 },
                text = { Text("5 Workflows") }
            )
            Tab(
                selected = activeSubTab == 3,
                onClick = { activeSubTab = 3 },
                text = { Text("🛒 On-Site Purchase (${party.sitePurchases.size})") }
            )
            Tab(
                selected = activeSubTab == 4,
                onClick = { activeSubTab = 4 },
                text = { Text("Doc Vault (${party.documents.size})") }
            )
            Tab(
                selected = activeSubTab == 5,
                onClick = { activeSubTab = 5 },
                text = { Text("Site & Hardware") }
            )
        }

        when (activeSubTab) {
            0 -> {
                PartyLeadDetailsTab(
                    party = party,
                    role = role,
                    allocatedPanels = allocatedPanels,
                    allocatedMeters = allocatedMeters,
                    rawMaterials = rawMaterials,
                    partyKharcha = partyKharcha,
                    onUpdateLeadDetails = onUpdateLeadDetails
                )
            }

            1 -> {
                SiteSurvey3DCostingScreen(
                    role = role,
                    parties = listOf(party),
                    busyVouchers = busyVouchers,
                    busyItems = busyItems,
                    linkedParty = party,
                    aiPhotoState = aiPhotoState,
                    onPickRooftopPhoto = onPickRooftopPhoto,
                    onPickInverterPhoto = onPickInverterPhoto,
                    onGenerateRooftopAi = onGenerateRooftopAi,
                    onGenerateInverterAi = onGenerateInverterAi,
                    onClearAiError = onClearAiError,
                    onApplyQuoteToParty = onApplyQuoteToParty,
                    onApproveAndConvertOrder = onApproveAndConvertOrder,
                    onShowMessage = onShowMessage
                )
            }

            2 -> {
                // 5 SEPARATE WORKFLOW SECTIONS
                val visibleSections = WorkflowSection.entries.filter { sec ->
                    sec != WorkflowSection.LOAN || canSeeLoan
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(visibleSections) { section ->
                        val sectionStages = party.stages.filter { it.section == section }
                        val canEdit = RolePermissions.canEditWorkflowSection(role, section)
                        val doneCount = sectionStages.count { it.completed }

                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, solarColors.hairlineBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = section.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (!canEdit) {
                                            Icon(
                                                Icons.Filled.Lock,
                                                contentDescription = "View only",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                        }
                                        Text(
                                            text = "$doneCount/${sectionStages.size}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (doneCount == sectionStages.size) solarColors.emerald else solarColors.solarAmber
                                        )
                                    }
                                }

                                // Show Loan 1 / Loan 2 amounts ONLY if authorized
                                if (section == WorkflowSection.LOAN && canSeeMoney) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = "Loan 1st Tranche: ${formatInr(party.loan1Amount)}  •  Loan 2nd Tranche: ${formatInr(party.loan2Amount)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = solarColors.debitBlue
                                    )
                                }

                                Spacer(Modifier.height(8.dp))
                                HorizontalDivider(color = solarColors.hairlineBorder)
                                Spacer(Modifier.height(6.dp))

                                sectionStages.forEach { st ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable(enabled = canEdit) { onToggleStage(st.id) }
                                            .padding(vertical = 8.dp, horizontal = 4.dp)
                                            .testTag("stage_${st.id}"),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (st.completed) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                            contentDescription = if (st.completed) "Completed" else "Pending",
                                            tint = if (st.completed) solarColors.emerald else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = st.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (st.completed) FontWeight.SemiBold else FontWeight.Normal
                                            )
                                            if (st.completed && st.completedAt.isNotBlank()) {
                                                Text(
                                                    text = "Done ${st.completedAt} by ${st.completedBy}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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

            3 -> {
                PartySitePurchaseTab(
                    party = party,
                    role = role,
                    supervisorImprestBalance = currentUserImprestBalance,
                    onAddSitePurchase = onAddSitePurchase
                )
            }

            4 -> {
                // SEARCHABLE DOCUMENT VAULT + DCR PDF GENERATOR
                val filteredDocs = remember(party.documents, docSearch, selectedDocCat) {
                    party.documents.filter { d ->
                        val catOk = selectedDocCat == null || d.category == selectedDocCat
                        val q = docSearch.trim().lowercase()
                        val textOk = q.isEmpty() ||
                            d.title.lowercase().contains(q) ||
                            d.fileName.lowercase().contains(q) ||
                            d.category.label.lowercase().contains(q) ||
                            d.notes.lowercase().contains(q)
                        catOk && textOk
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("vault_upload_photo_btn")
                                ) {
                                    Icon(Icons.Filled.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Geotag Photo")
                                }
                                OutlinedButton(
                                    onClick = {
                                        pendingUploadDefaultCat = DocumentCategory.KYC_DISCOM
                                        docPickerLauncher.launch(arrayOf("application/pdf", "image/*", "*/*"))
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("vault_upload_doc_btn")
                                ) {
                                    Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("PDF / KYC")
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        ExportPrintHelper.printDcrCertificatePdf(context, party, allocatedPanels)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = LimeGreen, contentColor = Color.White),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("generate_dcr_pdf_btn")
                                ) {
                                    Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Generate DCR PDF")
                                }
                                OutlinedButton(
                                    onClick = {
                                        pendingUploadDefaultCat = DocumentCategory.DCR_CERT
                                        docPickerLauncher.launch(arrayOf("application/pdf", "image/*"))
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("upload_oem_dcr_btn")
                                ) {
                                    Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("OEM DCR PDF")
                                }
                            }
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = docSearch,
                            onValueChange = { docSearch = it },
                            placeholder = { Text("Search vault docs by keyword…") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("vault_search_input")
                        )
                    }

                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            item {
                                FilterChip(
                                    selected = selectedDocCat == null,
                                    onClick = { selectedDocCat = null },
                                    label = { Text("All (${party.documents.size})") }
                                )
                            }
                            items(DocumentCategory.entries) { cat ->
                                FilterChip(
                                    selected = selectedDocCat == cat,
                                    onClick = { selectedDocCat = if (selectedDocCat == cat) null else cat },
                                    label = { Text(cat.label) }
                                )
                            }
                        }
                    }

                    if (filteredDocs.isEmpty()) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, solarColors.hairlineBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "No documents in vault yet. Upload Geotag Photos, DCR Certificates, Tax Invoices, or AVVNL KYC files.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(20.dp)
                                )
                            }
                        }
                    } else {
                        items(filteredDocs, key = { it.id }) { doc ->
                            VaultDocumentCard(
                                doc = doc,
                                onPreview = { previewDoc = doc },
                                onShare = { ExportPrintHelper.shareVaultDocument(context, party.partyName, doc) }
                            )
                        }
                    }
                }
            }

            5 -> {
                // ALLOCATED HARDWARE + LEKHA JOKHA KHARCHA + SITE LOGS
                val siteMaterialLogs = rawMaterials.flatMap { rm ->
                    rm.issueLogs.filter { it.partyId == party.id }.map { log ->
                        "${rm.name}: ${log.quantity} ${rm.unit} (${log.date} by ${log.issuedBy})"
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, solarColors.gridBorderStrong),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Allocated DCR Panels (${allocatedPanels.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    OutlinedButton(
                                        onClick = {
                                            ExportPrintHelper.printPartyHardwareChallanA4(
                                                context,
                                                party,
                                                allocatedPanels,
                                                allocatedMeters,
                                                rawMaterials
                                            )
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.testTag("party_delivery_challan_pdf_btn")
                                    ) {
                                        Icon(Icons.Filled.LocalShipping, contentDescription = "Delivery Challan PDF", modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Delivery Challan PDF", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                if (allocatedPanels.isEmpty()) {
                                    Text("No DCR panels allocated yet. Allocate from Inventory tab.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    allocatedPanels.forEach { p ->
                                        Text(
                                            text = "• ${p.serialNumber} (${p.brand} ${p.wattage}W)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                                Text("Installed Net / Solar Meters (${allocatedMeters.size})", style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(4.dp))
                                if (allocatedMeters.isEmpty()) {
                                    Text("No meters linked yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    allocatedMeters.forEach { m ->
                                        Text(
                                            text = "• ${m.serialNumber} — ${m.meterType.label} [${m.testingStatus.label}]",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                                Text("BOS Materials Issued (${siteMaterialLogs.size})", style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(4.dp))
                                if (siteMaterialLogs.isEmpty()) {
                                    Text("No raw materials issued yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    siteMaterialLogs.forEach { line ->
                                        Text("• $line", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                if (partyKharcha.isNotEmpty()) {
                                    Spacer(Modifier.height(10.dp))
                                    Text("Lekha Jokha Site Kharcha (${partyKharcha.size})", style = MaterialTheme.typography.titleSmall)
                                    Spacer(Modifier.height(4.dp))
                                    partyKharcha.forEach { kh ->
                                        Text(
                                            "• ${kh.workerOrTitle} (${kh.category.label}) — ${formatInr(kh.amount)} [${kh.date}]",
                                            style = MaterialTheme.typography.bodySmall.merge(TabularNumberStyle)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Add Site Note
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = noteInput,
                                onValueChange = { noteInput = it },
                                placeholder = { Text("Add site update / progress note…") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    if (noteInput.isNotBlank()) {
                                        onAddSiteNote(noteInput)
                                        noteInput = ""
                                    }
                                }
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Post site note", tint = solarColors.solarAmber)
                            }
                        }
                    }

                    items(party.siteLogs, key = { it.id }) { log ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, solarColors.hairlineBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = log.author + if (log.viaWhatsAppParser) " • WhatsApp" else "",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (log.viaWhatsAppParser) solarColors.emerald else MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = log.timestamp,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(log.message, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }

    // Metadata modal after picking photo or PDF
    pendingUploadUri?.let { uri ->
        UploadDocumentDialog(
            initialCategory = pendingUploadDefaultCat,
            onDismiss = { pendingUploadUri = null },
            onConfirm = { title, cat, notes ->
                onUploadDocument(uri, title, cat, notes)
                pendingUploadUri = null
            }
        )
    }

    // Preview Document Modal
    previewDoc?.let { doc ->
        AlertDialog(
            onDismissRequest = { previewDoc = null },
            title = { Text(doc.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${doc.category.label} • ${doc.fileName}", style = MaterialTheme.typography.labelMedium)
                    val localFile = doc.localFilePath.takeIf { it.isNotBlank() }?.let { File(it) }
                    if (localFile != null && localFile.exists() && doc.mimeType.startsWith("image/")) {
                        AsyncImage(
                            model = localFile,
                            contentDescription = doc.title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                    } else if (doc.base64Thumbnail.isNotBlank()) {
                        val bmp = remember(doc.base64Thumbnail) {
                            runCatching {
                                val bytes = Base64.decode(doc.base64Thumbnail, Base64.DEFAULT)
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            }.getOrNull()
                        }
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = doc.title,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                        }
                    }
                    Text("Uploaded: ${doc.uploadedAt} by ${doc.uploadedBy}", style = MaterialTheme.typography.bodySmall)
                    if (doc.notes.isNotBlank()) {
                        Text("Notes: ${doc.notes}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = { ExportPrintHelper.shareVaultDocument(context, party.partyName, doc) }) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Share / WhatsApp")
                }
            },
            dismissButton = {
                TextButton(onClick = { previewDoc = null }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun MiniStat(label: String, value: String, valueColor: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color(0xFFFFDAD8))
        Text(value, style = MaterialTheme.typography.labelLarge.merge(TabularNumberStyle), color = valueColor, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun VaultDocumentCard(
    doc: PartyDocument,
    onPreview: () -> Unit,
    onShare: () -> Unit
) {
    val solarColors = LocalSolarColors.current
    val isImg = doc.mimeType.startsWith("image/")

    Card(
        onClick = onPreview,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, solarColors.hairlineBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (doc.base64Thumbnail.isNotBlank()) {
                    val bmp = remember(doc.base64Thumbnail) {
                        runCatching {
                            val bytes = Base64.decode(doc.base64Thumbnail, Base64.DEFAULT)
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        }.getOrNull()
                    }
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = doc.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(Icons.Filled.Image, contentDescription = null, tint = solarColors.solarAmber)
                    }
                } else {
                    Icon(
                        if (isImg) Icons.Filled.Image else Icons.Filled.Description,
                        contentDescription = null,
                        tint = if (isImg) solarColors.solarAmber else solarColors.debitBlue
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(doc.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "${doc.category.label} • ${doc.uploadedAt}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Filled.Share, contentDescription = "Share to WhatsApp", tint = solarColors.emerald)
            }
        }
    }
}

@Composable
private fun UploadDocumentDialog(
    initialCategory: DocumentCategory = DocumentCategory.GEOTAG_PHOTO,
    onDismiss: () -> Unit,
    onConfirm: (String, DocumentCategory, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var category by remember(initialCategory) { mutableStateOf(initialCategory) }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save to Document Vault") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Document Title (e.g., Geotag Rooftop Photo)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Category", style = MaterialTheme.typography.labelMedium)
                DocumentCategory.entries.forEach { cat ->
                    FilterChip(
                        selected = category == cat,
                        onClick = { category = cat },
                        label = { Text(cat.label) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("GPS / Remarks (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(title, category, notes) }) { Text("Save & Sync") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
