package com.example.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.ExportPrintHelper
import com.example.core.RolePermissions
import com.example.core.formatInr
import com.example.core.safeDouble
import com.example.data.RealCostingCalculator
import com.example.data.model.BusyItem
import com.example.data.model.BusyVoucher
import com.example.data.model.CostingStructureType
import com.example.data.model.PartyProject
import com.example.data.model.ProposalFormat
import com.example.data.model.RealCostingBreakdown
import com.example.data.model.RoofStructureType
import com.example.data.model.SiteSurveyDimensions
import com.example.data.model.SolarSystemType
import com.example.data.model.StaffRole
import com.example.ui.components.InteractiveStructure3DCard
import com.example.ui.theme.LimeGreen
import com.example.ui.theme.LimeSoft
import com.example.ui.theme.LocalSolarColors
import com.example.ui.theme.MaroonContainerLight
import com.example.ui.theme.MaroonPrimary
import com.example.ui.theme.TabularNumberStyle
import com.example.ui.viewmodel.AiPhotoPlacementUiState
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Composable
fun SiteSurvey3DCostingScreen(
    role: StaffRole,
    parties: List<PartyProject>,
    busyVouchers: List<BusyVoucher>,
    busyItems: List<BusyItem>,
    linkedParty: PartyProject?,
    aiPhotoState: AiPhotoPlacementUiState,
    onPickRooftopPhoto: (Uri) -> Unit,
    onPickInverterPhoto: (Uri) -> Unit,
    onGenerateRooftopAi: (
        capacityKw: Double,
        panelCount: Int,
        structureType: CostingStructureType,
        systemType: SolarSystemType
    ) -> Unit,
    onGenerateInverterAi: (
        capacityKw: Double,
        systemType: SolarSystemType
    ) -> Unit,
    onClearAiError: () -> Unit,
    onApplyQuoteToParty: (
        partyId: String,
        capacityKw: Double,
        roofType: RoofStructureType,
        quotedRatePerKw: Double,
        dealValue: Double,
        surveyNotes: String
    ) -> Unit,
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
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val solarColors = LocalSolarColors.current
    val canSeeProfitability = RolePermissions.canViewPartyProfitability(role)
    val canSeeMoney = RolePermissions.canViewFinancials(role)

    var selectedPartyId by remember(linkedParty?.id) { mutableStateOf(linkedParty?.id) }
    val activeParty = remember(linkedParty, selectedPartyId, parties) {
        linkedParty ?: parties.firstOrNull { it.id == selectedPartyId }
    }

    var customerName by remember(activeParty?.id) { mutableStateOf(activeParty?.partyName.orEmpty()) }
    var customerMobile by remember(activeParty?.id) { mutableStateOf(activeParty?.mobile.orEmpty()) }
    var siteLocation by remember(activeParty?.id) {
        mutableStateOf(activeParty?.cityOrLocation?.ifBlank { activeParty.address } ?: "Udaipur, Rajasthan")
    }
    var consumerNo by remember(activeParty?.id) { mutableStateOf(activeParty?.consumerNumber.orEmpty()) }

    var systemType by remember { mutableStateOf(SolarSystemType.ON_GRID_DCR) }
    var structureType by remember(activeParty?.id) {
        mutableStateOf(
            activeParty?.let { CostingStructureType.fromRoofType(it.roofStructureType) }
                ?: CostingStructureType.STANDARD_RCC
        )
    }
    var capacityKwText by remember(activeParty?.id) {
        mutableStateOf(
            (activeParty?.capacityKw ?: 3.0).let {
                if (it % 1.0 == 0.0) it.toInt().toString() else it.toString()
            }
        )
    }

    // Rooftop & Wiring Survey dimensions
    var roofLengthText by remember { mutableStateOf("9.5") }
    var roofWidthText by remember { mutableStateOf("6.0") }
    var inverterDistText by remember { mutableStateOf("12") }
    var earthingDistText by remember { mutableStateOf("15") }
    var panelWpText by remember { mutableStateOf("540") }
    var tiltText by remember(structureType) { mutableStateOf(structureType.defaultTiltDeg.roundToInt().toString()) }
    var azimuthText by remember { mutableStateOf("180") }

    // BUSY 21 Live Purchase Rates + editable overrides
    val busyRates = remember(busyVouchers, busyItems, structureType) {
        RealCostingCalculator.extractLatestPurchaseRates(busyVouchers, busyItems, structureType)
    }
    var dcrRateText by remember(busyRates) { mutableStateOf(busyRates.dcrPanelRatePerWatt.toString()) }
    var invRateText by remember(busyRates) { mutableStateOf(busyRates.inverterRatePerKw.roundToInt().toString()) }
    var structRateText by remember(structureType) { mutableStateOf(structureType.defaultRatePerKw.roundToInt().toString()) }
    var marginPctText by remember(busyRates) { mutableStateOf(busyRates.profitMarginPercent.roundToInt().toString()) }
    var gstPctText by remember(busyRates) { mutableStateOf(busyRates.gstPercent.toString()) }
    var includeOwnerCostingInPdf by remember { mutableStateOf(false) }
    var selectedProposalFormat by remember { mutableStateOf(ProposalFormat.PM_SURYA_GHAR_2PG) }
    var showProposalPreview by remember { mutableStateOf(false) }

    LaunchedEffect(structureType) {
        structRateText = structureType.defaultRatePerKw.roundToInt().toString()
        tiltText = structureType.defaultTiltDeg.roundToInt().toString()
    }

    val capacityKw = safeDouble(capacityKwText, 3.0).coerceIn(1.0, 250.0)
    val dimensions = remember(
        roofLengthText, roofWidthText, inverterDistText, earthingDistText, panelWpText, tiltText, azimuthText
    ) {
        SiteSurveyDimensions(
            roofLengthM = safeDouble(roofLengthText, 9.5).coerceIn(2.0, 100.0),
            roofWidthM = safeDouble(roofWidthText, 6.0).coerceIn(2.0, 100.0),
            panelWattageWp = safeDouble(panelWpText, 540.0).roundToInt().coerceIn(250, 750),
            inverterDistanceM = safeDouble(inverterDistText, 12.0).coerceIn(1.0, 150.0),
            earthingDistanceM = safeDouble(earthingDistText, 15.0).coerceIn(1.0, 150.0),
            tiltDegrees = safeDouble(tiltText, 24.0).toFloat().coerceIn(5f, 45f),
            azimuthDegrees = safeDouble(azimuthText, 180.0).toFloat().coerceIn(0f, 360f)
        )
    }

    val effectiveRates = remember(
        busyRates, dcrRateText, invRateText, structRateText, marginPctText, gstPctText
    ) {
        busyRates.copy(
            dcrPanelRatePerWatt = safeDouble(dcrRateText, busyRates.dcrPanelRatePerWatt).coerceAtLeast(10.0),
            inverterRatePerKw = safeDouble(invRateText, busyRates.inverterRatePerKw).coerceAtLeast(2000.0),
            structureRatePerKw = safeDouble(structRateText, structureType.defaultRatePerKw).coerceAtLeast(1000.0),
            profitMarginPercent = safeDouble(marginPctText, busyRates.profitMarginPercent).coerceIn(0.0, 60.0),
            gstPercent = safeDouble(gstPctText, busyRates.gstPercent).coerceIn(0.0, 28.0)
        )
    }

    val breakdown = remember(capacityKw, systemType, structureType, dimensions, effectiveRates) {
        RealCostingCalculator.computeRealCosting(
            capacityKw = capacityKw,
            systemType = systemType,
            structureType = structureType,
            dimensions = dimensions,
            rates = effectiveRates
        )
    }

    val comparisonBreakdowns = remember(capacityKw, structureType, dimensions, effectiveRates) {
        SolarSystemType.entries.map { sys ->
            RealCostingCalculator.computeRealCosting(
                capacityKw = capacityKw,
                systemType = sys,
                structureType = structureType,
                dimensions = dimensions,
                rates = effectiveRates
            )
        }
    }

    // Zero-permission Photo Pickers (Play Policy compliant)
    val rooftopPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onPickRooftopPhoto(uri)
    }
    val inverterPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onPickInverterPhoto(uri)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 0. Header / Customer Binding Card
        item {
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, solarColors.gridBorderStrong),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "AI Site Photo, 3D Structure & Real Costing",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaroonPrimary
                            )
                            Text(
                                text = "Instant 'Kitne Ka Padega' EPC Calculator + PM Surya Ghar Subsidy",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(color = LimeSoft, shape = RoundedCornerShape(6.dp)) {
                            Text(
                                text = "${capacityKw} kW",
                                style = MaterialTheme.typography.labelLarge.merge(TabularNumberStyle),
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2B4D12),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (linkedParty == null && parties.isNotEmpty()) {
                        Text(
                            text = "LINK EXISTING PARTY / LEAD (OPTIONAL)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            item {
                                FilterChip(
                                    selected = selectedPartyId == null,
                                    onClick = { selectedPartyId = null },
                                    label = { Text("Walk-In Quote") }
                                )
                            }
                            items(parties.take(15), key = { it.id }) { p ->
                                FilterChip(
                                    selected = selectedPartyId == p.id,
                                    onClick = { selectedPartyId = p.id },
                                    label = { Text("${p.partyName} (${p.capacityKw}kW)") }
                                )
                            }
                        }
                    }

                    if (linkedParty == null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = customerName,
                                onValueChange = { customerName = it },
                                label = { Text("Customer / Party Name") },
                                singleLine = true,
                                modifier = Modifier.weight(1.2f)
                            )
                            OutlinedTextField(
                                value = customerMobile,
                                onValueChange = { customerMobile = it },
                                label = { Text("Mobile") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                singleLine = true,
                                modifier = Modifier.weight(0.9f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = siteLocation,
                                onValueChange = { siteLocation = it },
                                label = { Text("Site City / Address") },
                                singleLine = true,
                                modifier = Modifier.weight(1.2f)
                            )
                            OutlinedTextField(
                                value = consumerNo,
                                onValueChange = { consumerNo = it },
                                label = { Text("K-Number") },
                                singleLine = true,
                                modifier = Modifier.weight(0.8f)
                            )
                        }
                    } else {
                        Surface(
                            color = MaroonContainerLight,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Linked Party: ${linkedParty.partyName} • ${linkedParty.cityOrLocation.ifBlank { linkedParty.address }} • K-No: ${linkedParty.consumerNumber.ifBlank { "-" }}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaroonPrimary,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        }

        // 1. AI Site Photo Placement Card (Gemini Image Generation)
        item {
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, solarColors.gridBorderStrong),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "AI Site Photo Placement (Before → After)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = "Upload customer rooftop & wall photos — Gemini AI places ${breakdown.wiring.panelCount} panels & inverter.",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF475569)
                            )
                        }
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaroonPrimary)
                    }

                    // Rooftop Photo Section
                    Text(
                        text = "1. ROOFTOP SOLAR ARRAY PLACEMENT (${structureType.shortLabel})",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaroonPrimary
                    )
                    BeforeAfterPhotoRow(
                        beforeBitmap = aiPhotoState.rooftopBefore,
                        afterBitmap = aiPhotoState.rooftopAfter,
                        isLoading = aiPhotoState.rooftopLoading,
                        errorMessage = aiPhotoState.rooftopError,
                        beforePlaceholder = "Tap 'Pick Rooftop Photo'",
                        afterPlaceholder = "AI Placed ${breakdown.wiring.panelCount} Panels",
                        loadingLabel = "Gemini AI placing solar array (30-60s)…",
                        onClearError = onClearAiError
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                rooftopPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pick_rooftop_photo_btn")
                        ) {
                            Icon(Icons.Filled.AddAPhoto, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (aiPhotoState.rooftopBefore == null) "Pick Rooftop Photo" else "Change Photo")
                        }
                        Button(
                            onClick = {
                                onGenerateRooftopAi(capacityKw, breakdown.wiring.panelCount, structureType, systemType)
                            },
                            enabled = aiPhotoState.rooftopBefore != null && !aiPhotoState.rooftopLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("generate_rooftop_ai_btn")
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (aiPhotoState.rooftopLoading) "Generating…" else "AI Place Panels")
                        }
                    }

                    HorizontalDivider(color = solarColors.hairlineBorder)

                    // Inverter Wall Section
                    Text(
                        text = "2. INVERTER, ACDB/DCDB & CONDUIT WALL PLACEMENT",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaroonPrimary
                    )
                    BeforeAfterPhotoRow(
                        beforeBitmap = aiPhotoState.inverterBefore,
                        afterBitmap = aiPhotoState.inverterAfter,
                        isLoading = aiPhotoState.inverterLoading,
                        errorMessage = aiPhotoState.inverterError,
                        beforePlaceholder = "Tap 'Pick Wall Photo'",
                        afterPlaceholder = "AI Placed Inverter + ACDB/DCDB",
                        loadingLabel = "Gemini AI mounting inverter & conduits…",
                        onClearError = onClearAiError
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                inverterPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pick_inverter_photo_btn")
                        ) {
                            Icon(Icons.Filled.AddAPhoto, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (aiPhotoState.inverterBefore == null) "Pick Wall Photo" else "Change Photo")
                        }
                        Button(
                            onClick = { onGenerateInverterAi(capacityKw, systemType) },
                            enabled = aiPhotoState.inverterBefore != null && !aiPhotoState.inverterLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = LimeGreen, contentColor = Color.White),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("generate_inverter_ai_btn")
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (aiPhotoState.inverterLoading) "Generating…" else "AI Place Inverter")
                        }
                    }
                }
            }
        }

        // 2. Real-Time Rooftop Dimension Survey Map & Calculated Wiring Measurements
        item {
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, solarColors.gridBorderStrong),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Rooftop Survey Map & Wiring Measurements",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = "Roof ${dimensions.roofLengthM}m × ${dimensions.roofWidthM}m (${breakdown.wiring.roofAreaSqM} m²) • Needs ${breakdown.wiring.requiredPanelAreaSqM} m²",
                                style = MaterialTheme.typography.labelSmall.merge(TabularNumberStyle),
                                color = if (breakdown.wiring.fitsOnRoof) Color(0xFF2B4D12) else Color(0xFFB91C1C)
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                val query = Uri.encode(siteLocation.ifBlank { "Udaipur, Rajasthan" })
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:24.5854,73.7125?q=$query"))
                                try {
                                    context.startActivity(intent)
                                } catch (_: ActivityNotFoundException) {
                                    onShowMessage("No Maps application available on this device.")
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Filled.LocationOn, contentDescription = "Open Maps", modifier = Modifier.size(16.dp), tint = MaroonPrimary)
                            Spacer(Modifier.width(4.dp))
                            Text("Site Map", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    // Scaled Top-Down Rooftop CAD Blueprint Canvas
                    RooftopBlueprintMapCanvas(
                        roofLengthM = dimensions.roofLengthM,
                        roofWidthM = dimensions.roofWidthM,
                        panelCount = breakdown.wiring.panelCount,
                        fitsOnRoof = breakdown.wiring.fitsOnRoof
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = roofLengthText,
                            onValueChange = { roofLengthText = it },
                            label = { Text("Roof L (m)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = roofWidthText,
                            onValueChange = { roofWidthText = it },
                            label = { Text("Roof W (m)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = inverterDistText,
                            onValueChange = { inverterDistText = it },
                            label = { Text("Inv Dist (m)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = earthingDistText,
                            onValueChange = { earthingDistText = it },
                            label = { Text("Earth Pit (m)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = panelWpText,
                            onValueChange = { panelWpText = it },
                            label = { Text("Panel Wp") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = tiltText,
                            onValueChange = { tiltText = it },
                            label = { Text("Tilt (°)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(0.8f)
                        )
                        OutlinedTextField(
                            value = azimuthText,
                            onValueChange = { azimuthText = it },
                            label = { Text("Azim (°)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(0.9f)
                        )
                    }

                    // 4 Calculated Wiring Measurement Tiles
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        WiringMetricTile("DC Cable", "${breakdown.wiring.dcCableMeters} m", Color(0xFFB91C1C), Modifier.weight(1f))
                        WiringMetricTile("AC Cable", "${breakdown.wiring.acCableMeters} m", Color(0xFF1D4ED8), Modifier.weight(1f))
                        WiringMetricTile("Earthing", "${breakdown.wiring.earthingWireMeters} m", Color(0xFF2B4D12), Modifier.weight(1f))
                        WiringMetricTile("PVC Conduit", "${breakdown.wiring.pvcConduitMeters} m", Color(0xFF475569), Modifier.weight(1f))
                    }
                }
            }
        }

        // 3. Interactive 3D Mounting Structure Diagram Card
        item {
            InteractiveStructure3DCard(
                structureType = structureType,
                tiltDegrees = dimensions.tiltDegrees,
                panelCount = breakdown.wiring.panelCount,
                estimatedStructureKg = breakdown.wiring.estimatedStructureKg
            )
        }

        // 4. PROMINENT LIVE "💰 Real Costing & Customer Net Price ('Kitne Ka Padega')" CARD
        item {
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.5.dp, MaroonPrimary),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("real_costing_card")
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Calculate, contentDescription = null, tint = MaroonPrimary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "💰 Real Costing & Customer Net Price ('Kitne Ka Padega')",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaroonPrimary
                            )
                            Text(
                                text = "Switch System Type, Structure or kW to compute real EPC cost & PM Surya Ghar net rate.",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF475569)
                            )
                        }
                    }

                    // Live System Type Chips
                    Text("SYSTEM TYPE", style = MaterialTheme.typography.labelSmall, color = Color(0xFF475569))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(SolarSystemType.entries) { sys ->
                            FilterChip(
                                selected = systemType == sys,
                                onClick = { systemType = sys },
                                label = { Text(sys.label) },
                                modifier = Modifier.testTag("sys_type_${sys.name.lowercase()}")
                            )
                        }
                    }

                    // Live Structure Type Chips
                    Text("MOUNTING STRUCTURE TYPE", style = MaterialTheme.typography.labelSmall, color = Color(0xFF475569))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(CostingStructureType.entries) { st ->
                            FilterChip(
                                selected = structureType == st,
                                onClick = { structureType = st },
                                label = { Text(st.label) },
                                modifier = Modifier.testTag("struct_type_${st.name.lowercase()}")
                            )
                        }
                    }

                    // Capacity Quick Chips (3kW, 5kW, 10kW) + Custom kW
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("3", "5", "10").forEach { kwPreset ->
                            FilterChip(
                                selected = capacityKwText.trim() == kwPreset,
                                onClick = { capacityKwText = kwPreset },
                                label = { Text("${kwPreset}kW") },
                                modifier = Modifier.testTag("kw_preset_$kwPreset")
                            )
                        }
                        OutlinedTextField(
                            value = capacityKwText,
                            onValueChange = { capacityKwText = it },
                            label = { Text("Custom kW") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("custom_kw_input")
                        )
                    }

                    if (!canSeeMoney) {
                        // STRICT PRIVACY FIREWALL FOR SUPERVISOR & HELPER
                        Surface(
                            color = MaroonContainerLight,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaroonPrimary.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Lock, contentDescription = null, tint = MaroonPrimary)
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Field Privacy Mode Active (${role.badge})",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaroonPrimary
                                    )
                                    Text(
                                        text = "Purchase costing, profit margins, and customer pricing are restricted to Owner, Accounts & Office.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF334155)
                                    )
                                }
                            }
                        }
                    } else {
                        // OWNER & ACCOUNTS PRIVATE COSTING BOX
                        if (canSeeProfitability) {
                            Surface(
                                color = Color(0xFFFFF8F7),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, MaroonPrimary.copy(alpha = 0.45f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("owner_private_costing_box")
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "OWNER & ACCOUNTS PRIVATE REAL COSTING",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaroonPrimary
                                        )
                                        TextButton(
                                            onClick = {
                                                dcrRateText = busyRates.dcrPanelRatePerWatt.toString()
                                                invRateText = busyRates.inverterRatePerKw.roundToInt().toString()
                                                structRateText = structureType.defaultRatePerKw.roundToInt().toString()
                                                marginPctText = busyRates.profitMarginPercent.roundToInt().toString()
                                                gstPctText = busyRates.gstPercent.toString()
                                            },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                        ) {
                                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("BUSY Rates", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }

                                    Text(
                                        text = "Rate Source: ${busyRates.sourceLabel}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF475569)
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        OutlinedTextField(
                                            value = dcrRateText,
                                            onValueChange = { dcrRateText = it },
                                            label = { Text("DCR ₹/W") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = invRateText,
                                            onValueChange = { invRateText = it },
                                            label = { Text("Inv ₹/kW") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = structRateText,
                                            onValueChange = { structRateText = it },
                                            label = { Text("Struct ₹/kW") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        OutlinedTextField(
                                            value = marginPctText,
                                            onValueChange = { marginPctText = it },
                                            label = { Text("Margin %") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = gstPctText,
                                            onValueChange = { gstPctText = it },
                                            label = { Text("GST %") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    HorizontalDivider(color = solarColors.hairlineBorder)

                                    // Item-wise Landing Cost Grid
                                    breakdown.lineItems.forEach { li ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(li.title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
                                                Text(li.spec, style = MaterialTheme.typography.labelSmall, color = Color(0xFF475569))
                                            }
                                            Text(
                                                text = formatInr(li.landingCostInr),
                                                style = MaterialTheme.typography.labelMedium.merge(TabularNumberStyle),
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF0F172A)
                                            )
                                        }
                                    }

                                    HorizontalDivider(color = solarColors.gridBorderStrong)

                                    CostSummaryLine("Total EPC Real Costing (Landing)", formatInr(breakdown.totalLandingCostInr), Color(0xFF0F172A))
                                    CostSummaryLine(
                                        "Profit Margin (${String.format(Locale.US, "%.1f", breakdown.profitMarginPct)}%)",
                                        "+ ${formatInr(breakdown.profitMarginInr)}",
                                        Color(0xFF2B4D12)
                                    )
                                    CostSummaryLine(
                                        "Composite Solar GST (${effectiveRates.gstPercent}%)",
                                        "+ ${formatInr(breakdown.gstAmountInr)}",
                                        Color(0xFF475569)
                                    )
                                    CostSummaryLine("Gross Quoted Selling Price", formatInr(breakdown.grossQuotedPriceInr), MaroonPrimary)

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { includeOwnerCostingInPdf = !includeOwnerCostingInPdf }
                                    ) {
                                        Checkbox(
                                            checked = includeOwnerCostingInPdf,
                                            onCheckedChange = { includeOwnerCostingInPdf = it }
                                        )
                                        Text(
                                            text = "Include Owner Internal Costing Page in PDF",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF334155)
                                        )
                                    }
                                }
                            }
                        }

                        // CUSTOMER-FACING NET PRICE BOX ("Party Ko Kitne Ka Padega")
                        Surface(
                            color = LimeSoft,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.5.dp, LimeGreen),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("customer_net_price_box")
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "CUSTOMER NET PRICE (\"PARTY KO KITNE KA PADEGA\")",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1F3B0B)
                                )
                                CostSummaryLine(
                                    "Gross Quoted Turnkey Price (Incl. GST)",
                                    formatInr(breakdown.grossQuotedPriceInr),
                                    Color(0xFF0F172A)
                                )
                                CostSummaryLine(
                                    if (systemType.isSubsidyEligible) "(-) PM Surya Ghar Central Subsidy" else "(-) PM Surya Ghar Subsidy (N/A Off-Grid)",
                                    "- ${formatInr(breakdown.pmSuryaGharSubsidyInr)}",
                                    Color(0xFF2B4D12)
                                )
                                HorizontalDivider(color = LimeGreen)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "FINAL NET COST AFTER SUBSIDY",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1F3B0B)
                                    )
                                    Text(
                                        text = formatInr(breakdown.finalNetCostAfterSubsidyInr),
                                        style = MaterialTheme.typography.titleLarge.merge(TabularNumberStyle),
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaroonPrimary
                                    )
                                }
                                Text(
                                    text = "Est. ~${breakdown.estimatedMonthlyUnits} Units/mo • Savings ~${formatInr(breakdown.estimatedMonthlySavingsInr)}/mo • Payback ~${breakdown.estimatedPaybackYears} Yrs",
                                    style = MaterialTheme.typography.labelSmall.merge(TabularNumberStyle),
                                    color = Color(0xFF2B4D12)
                                )
                            }
                        }

                        // CHOOSE PROPOSAL FORMAT / TEMPLATE (4 VARIETIES)
                        Text(
                            text = "CHOOSE PROPOSAL FORMAT / TEMPLATE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaroonPrimary
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(ProposalFormat.entries) { fmt ->
                                FilterChip(
                                    selected = selectedProposalFormat == fmt,
                                    onClick = { selectedProposalFormat = fmt },
                                    label = { Text(fmt.shortTitle) },
                                    modifier = Modifier.testTag("proposal_format_${fmt.name.lowercase()}")
                                )
                            }
                        }
                        Text(
                            text = "${selectedProposalFormat.pageBadge} • ${selectedProposalFormat.subtitle}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF475569)
                        )

                        // Live Proposal Preview + Direct Print / Share Actions
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { showProposalPreview = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary, contentColor = Color.White),
                                modifier = Modifier
                                    .weight(1.3f)
                                    .height(48.dp)
                                    .testTag("preview_proposal_btn")
                            ) {
                                Icon(Icons.Filled.Preview, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Live Preview & Export", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = {
                                    ExportPrintHelper.printProposalWithCostingA4(
                                        context = context,
                                        format = selectedProposalFormat,
                                        customerName = customerName.ifBlank { activeParty?.partyName ?: "Valued Solar Consumer" },
                                        customerMobile = customerMobile.ifBlank { activeParty?.mobile.orEmpty() },
                                        siteLocation = siteLocation.ifBlank { activeParty?.cityOrLocation ?: "Udaipur, Rajasthan" },
                                        consumerNo = consumerNo.ifBlank { activeParty?.consumerNumber.orEmpty() },
                                        breakdown = breakdown,
                                        comparisonBreakdowns = comparisonBreakdowns,
                                        dimensions = dimensions,
                                        rooftopBeforeBitmap = aiPhotoState.rooftopBefore,
                                        rooftopAfterBitmap = aiPhotoState.rooftopAfter,
                                        inverterBeforeBitmap = aiPhotoState.inverterBefore,
                                        inverterAfterBitmap = aiPhotoState.inverterAfter,
                                        includeOwnerInternalCosting = canSeeProfitability && includeOwnerCostingInPdf
                                    )
                                },
                                modifier = Modifier
                                    .weight(0.9f)
                                    .height(48.dp)
                                    .testTag("generate_proposal_pdf_btn")
                            ) {
                                Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaroonPrimary)
                                Spacer(Modifier.width(4.dp))
                                Text("Print PDF")
                            }
                        }

                        if (activeParty != null) {
                            OutlinedButton(
                                onClick = {
                                    val ratePerKw = (breakdown.grossQuotedPriceInr / breakdown.capacityKw).roundToInt().toDouble()
                                    val surveySummary = buildString {
                                        append("${breakdown.capacityKw} kW ${systemType.shortLabel} • ${structureType.shortLabel}")
                                        append(" | Roof ${dimensions.roofLengthM}x${dimensions.roofWidthM}m")
                                        append(" | DC ${breakdown.wiring.dcCableMeters}m, AC ${breakdown.wiring.acCableMeters}m, Earth ${breakdown.wiring.earthingWireMeters}m")
                                        append(" | Net After Subsidy ${formatInr(breakdown.finalNetCostAfterSubsidyInr)}")
                                    }
                                    onApplyQuoteToParty(
                                        activeParty.id,
                                        breakdown.capacityKw,
                                        structureType.toRoofType,
                                        ratePerKw,
                                        breakdown.grossQuotedPriceInr,
                                        surveySummary
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("apply_quote_to_party_btn")
                            ) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = LimeGreen, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Save Survey, kW & Quoted Rate to ${activeParty.partyName}")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showProposalPreview && canSeeMoney) {
        val resolvedName = customerName.ifBlank { activeParty?.partyName ?: "Valued Solar Consumer" }
        val resolvedMobile = customerMobile.ifBlank { activeParty?.mobile.orEmpty() }
        val resolvedLocation = siteLocation.ifBlank { activeParty?.cityOrLocation ?: "Udaipur, Rajasthan" }
        val resolvedKNo = consumerNo.ifBlank { activeParty?.consumerNumber.orEmpty() }

        LiveProposalPreviewDialog(
            selectedFormat = selectedProposalFormat,
            onSelectFormat = { selectedProposalFormat = it },
            customerName = resolvedName,
            customerMobile = resolvedMobile,
            siteLocation = resolvedLocation,
            consumerNo = resolvedKNo,
            breakdown = breakdown,
            comparisonBreakdowns = comparisonBreakdowns,
            dimensions = dimensions,
            rooftopBeforeBitmap = aiPhotoState.rooftopBefore,
            rooftopAfterBitmap = aiPhotoState.rooftopAfter,
            inverterBeforeBitmap = aiPhotoState.inverterBefore,
            inverterAfterBitmap = aiPhotoState.inverterAfter,
            includeOwnerInternalCosting = canSeeProfitability && includeOwnerCostingInPdf,
            canApproveOrder = RolePermissions.canCreateParty(role),
            onDownloadPdf = {
                ExportPrintHelper.printProposalWithCostingA4(
                    context = context,
                    format = selectedProposalFormat,
                    customerName = resolvedName,
                    customerMobile = resolvedMobile,
                    siteLocation = resolvedLocation,
                    consumerNo = resolvedKNo,
                    breakdown = breakdown,
                    comparisonBreakdowns = comparisonBreakdowns,
                    dimensions = dimensions,
                    rooftopBeforeBitmap = aiPhotoState.rooftopBefore,
                    rooftopAfterBitmap = aiPhotoState.rooftopAfter,
                    inverterBeforeBitmap = aiPhotoState.inverterBefore,
                    inverterAfterBitmap = aiPhotoState.inverterAfter,
                    includeOwnerInternalCosting = canSeeProfitability && includeOwnerCostingInPdf
                )
            },
            onShareWhatsApp = {
                val res = ExportPrintHelper.shareProposalPdfOnWhatsApp(
                    context = context,
                    format = selectedProposalFormat,
                    customerName = resolvedName,
                    customerMobile = resolvedMobile,
                    siteLocation = resolvedLocation,
                    consumerNo = resolvedKNo,
                    breakdown = breakdown,
                    dimensions = dimensions,
                    rooftopAfterBitmap = aiPhotoState.rooftopAfter,
                    inverterAfterBitmap = aiPhotoState.inverterAfter
                )
                if (res.isFailure) {
                    onShowMessage("Share failed: ${res.exceptionOrNull()?.message ?: "unknown error"}")
                }
            },
            onApproveOrder = {
                val ratePerKw = (breakdown.grossQuotedPriceInr / breakdown.capacityKw).roundToInt().toDouble()
                val surveySummary = buildString {
                    append("${breakdown.capacityKw} kW ${systemType.shortLabel} • ${structureType.shortLabel}")
                    append(" | ${selectedProposalFormat.shortTitle}")
                    append(" | DC ${breakdown.wiring.dcCableMeters}m, AC ${breakdown.wiring.acCableMeters}m")
                    append(" | Net ${formatInr(breakdown.finalNetCostAfterSubsidyInr)}")
                }
                onApproveAndConvertOrder(
                    activeParty?.id,
                    resolvedName,
                    resolvedMobile,
                    resolvedLocation,
                    resolvedKNo,
                    breakdown.capacityKw,
                    structureType.toRoofType,
                    ratePerKw,
                    breakdown.grossQuotedPriceInr,
                    breakdown.pmSuryaGharSubsidyInr,
                    surveySummary
                )
                showProposalPreview = false
            },
            onDismiss = { showProposalPreview = false }
        )
    }
}

@Composable
private fun LiveProposalPreviewDialog(
    selectedFormat: ProposalFormat,
    onSelectFormat: (ProposalFormat) -> Unit,
    customerName: String,
    customerMobile: String,
    siteLocation: String,
    consumerNo: String,
    breakdown: RealCostingBreakdown,
    comparisonBreakdowns: List<RealCostingBreakdown>,
    dimensions: SiteSurveyDimensions,
    rooftopBeforeBitmap: Bitmap?,
    rooftopAfterBitmap: Bitmap?,
    inverterBeforeBitmap: Bitmap?,
    inverterAfterBitmap: Bitmap?,
    includeOwnerInternalCosting: Boolean,
    canApproveOrder: Boolean,
    onDownloadPdf: () -> Unit,
    onShareWhatsApp: () -> Unit,
    onApproveOrder: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val previewHtml = remember(
        selectedFormat, customerName, customerMobile, siteLocation, consumerNo,
        breakdown, comparisonBreakdowns, dimensions,
        rooftopBeforeBitmap, rooftopAfterBitmap, inverterBeforeBitmap, inverterAfterBitmap,
        includeOwnerInternalCosting
    ) {
        ExportPrintHelper.buildProposalHtml(
            context = context,
            format = selectedFormat,
            customerName = customerName,
            customerMobile = customerMobile,
            siteLocation = siteLocation,
            consumerNo = consumerNo,
            breakdown = breakdown,
            comparisonBreakdowns = comparisonBreakdowns,
            dimensions = dimensions,
            rooftopBeforeBitmap = rooftopBeforeBitmap,
            rooftopAfterBitmap = rooftopAfterBitmap,
            inverterBeforeBitmap = inverterBeforeBitmap,
            inverterAfterBitmap = inverterAfterBitmap,
            includeOwnerInternalCosting = includeOwnerInternalCosting
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Surface(color = MaroonPrimary, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Live A4 Proposal Preview",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "${selectedFormat.title} • $customerName",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFFFDAD8)
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Close Preview", tint = Color.White)
                        }
                    }
                }

                // Interactive Template Switcher Bar inside Preview
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.background(Color(0xFFF8FAFC))
                ) {
                    items(ProposalFormat.entries) { fmt ->
                        FilterChip(
                            selected = selectedFormat == fmt,
                            onClick = { onSelectFormat(fmt) },
                            label = { Text(fmt.shortTitle, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                HorizontalDivider(color = Color(0xFFCBD5E1))

                // Interactive WebView Preview
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            webViewClient = WebViewClient()
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true
                            loadDataWithBaseURL(null, previewHtml, "text/html", "UTF-8", null)
                        }
                    },
                    update = { webView ->
                        webView.loadDataWithBaseURL(null, previewHtml, "text/html", "UTF-8", null)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("proposal_preview_webview")
                )

                HorizontalDivider(color = Color(0xFFCBD5E1))

                // Action Bar: Download PDF, Share WhatsApp, Approve & Convert Order
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF8FAFC))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = onDownloadPdf,
                            colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary, contentColor = Color.White),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("preview_download_pdf_btn")
                        ) {
                            Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Download PDF")
                        }
                        OutlinedButton(
                            onClick = onShareWhatsApp,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("preview_share_whatsapp_btn")
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, tint = LimeGreen, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("WhatsApp PDF")
                        }
                    }
                    if (canApproveOrder) {
                        Button(
                            onClick = onApproveOrder,
                            colors = ButtonDefaults.buttonColors(containerColor = LimeGreen, contentColor = Color.White),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("preview_approve_convert_btn")
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Approve & Convert to Active Order", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BeforeAfterPhotoRow(
    beforeBitmap: Bitmap?,
    afterBitmap: Bitmap?,
    isLoading: Boolean,
    errorMessage: String?,
    beforePlaceholder: String,
    afterPlaceholder: String,
    loadingLabel: String,
    onClearError: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            PhotoPreviewBox(
                title = "BEFORE",
                bitmap = beforeBitmap,
                placeholder = beforePlaceholder,
                isLoading = false,
                loadingLabel = "",
                borderColor = Color(0xFFCBD5E1),
                modifier = Modifier.weight(1f)
            )
            PhotoPreviewBox(
                title = "AFTER (GEMINI AI)",
                bitmap = afterBitmap,
                placeholder = afterPlaceholder,
                isLoading = isLoading,
                loadingLabel = loadingLabel,
                borderColor = LimeGreen,
                modifier = Modifier.weight(1f)
            )
        }
        if (!errorMessage.isNullOrBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onClearError, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Dismiss error", modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoPreviewBox(
    title: String,
    bitmap: Bitmap?,
    placeholder: String,
    isLoading: Boolean,
    loadingLabel: String,
    borderColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier.height(132.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (!isLoading) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF64748B),
                    modifier = Modifier.padding(8.dp)
                )
            }
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.62f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(6.dp)) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                        Spacer(Modifier.height(6.dp))
                        Text(loadingLabel, style = MaterialTheme.typography.labelSmall, color = Color.White)
                    }
                }
            }
            Surface(
                color = Color(0xFF0F172A).copy(alpha = 0.78f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun RooftopBlueprintMapCanvas(
    roofLengthM: Double,
    roofWidthM: Double,
    panelCount: Int,
    fitsOnRoof: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(148.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0F172A))
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(14.dp)) {
            val aspect = (roofLengthM / roofWidthM.coerceAtLeast(1.0)).toFloat().coerceIn(0.6f, 2.5f)
            val availW = size.width * 0.72f
            val availH = size.height * 0.85f
            val boxW = if (availW / availH > aspect) availH * aspect else availW
            val boxH = boxW / aspect
            val roofLeft = 8f
            val roofTop = (size.height - boxH) / 2f

            // Parapet roof boundary
            drawRect(
                color = if (fitsOnRoof) Color(0xFF1E293B) else Color(0xFF450A0A),
                topLeft = Offset(roofLeft, roofTop),
                size = Size(boxW, boxH)
            )
            drawRect(
                color = if (fitsOnRoof) LimeGreen else Color(0xFFF87171),
                topLeft = Offset(roofLeft, roofTop),
                size = Size(boxW, boxH),
                style = Stroke(width = 3f)
            )

            // Solar panel grid inside roof
            val cols = ceil(sqrt(panelCount.toDouble() * 1.4)).toInt().coerceIn(2, 8)
            val rows = ceil(panelCount.toDouble() / cols).toInt().coerceIn(1, 5)
            val padX = boxW * 0.1f
            val padY = boxH * 0.14f
            val cellW = (boxW - padX * 2f) / cols
            val cellH = ((boxH - padY * 2f) / rows).coerceAtMost(cellW * 1.45f)

            var drawn = 0
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    if (drawn >= panelCount) break
                    val px = roofLeft + padX + c * cellW + 2f
                    val py = roofTop + padY + r * cellH + 2f
                    drawRect(
                        color = Color(0xFF2563EB),
                        topLeft = Offset(px, py),
                        size = Size((cellW - 4f).coerceAtLeast(4f), (cellH - 4f).coerceAtLeast(4f))
                    )
                    drawRect(
                        color = Color(0xFFBFDBFE),
                        topLeft = Offset(px, py),
                        size = Size((cellW - 4f).coerceAtLeast(4f), (cellH - 4f).coerceAtLeast(4f)),
                        style = Stroke(width = 1f)
                    )
                    drawn++
                }
            }

            // Inverter Wall & Earthing Pit Nodes on right side
            val invPos = Offset(size.width - 28f, size.height * 0.30f)
            val earthPos = Offset(size.width - 28f, size.height * 0.78f)
            val arrayTap = Offset(roofLeft + boxW - 6f, roofTop + boxH * 0.35f)

            // Red DC line
            drawLine(
                color = Color(0xFFEF4444),
                start = arrayTap,
                end = invPos,
                strokeWidth = 3f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))
            )
            // Blue AC line
            drawLine(
                color = Color(0xFF38BDF8),
                start = invPos,
                end = Offset(invPos.x - 42f, size.height * 0.56f),
                strokeWidth = 2.8f
            )
            // Green Earthing line
            drawLine(
                color = LimeGreen,
                start = invPos,
                end = earthPos,
                strokeWidth = 2.6f
            )

            drawRect(Color(0xFFF59E0B), topLeft = Offset(invPos.x - 10f, invPos.y - 10f), size = Size(20f, 20f))
            drawCircle(LimeGreen, radius = 9f, center = earthPos)
        }

        Text(
            text = if (fitsOnRoof) "CAD FIT OK • Red=DC • Blue=AC • Green=3-Pit Earth" else "WARNING: Roof area tight for $panelCount panels",
            style = MaterialTheme.typography.labelSmall,
            color = if (fitsOnRoof) Color(0xFFDCEDC8) else Color(0xFFFCA5A5),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun WiringMetricTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Filled.Cable, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
            Spacer(Modifier.height(2.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF475569))
            Text(value, style = MaterialTheme.typography.labelMedium.merge(TabularNumberStyle), fontWeight = FontWeight.Bold, color = accent)
        }
    }
}

@Composable
private fun CostSummaryLine(label: String, amountText: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Color(0xFF334155), modifier = Modifier.weight(1f))
        Text(
            text = amountText,
            style = MaterialTheme.typography.labelLarge.merge(TabularNumberStyle),
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}
