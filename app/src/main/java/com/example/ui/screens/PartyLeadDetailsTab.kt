package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.core.ExportPrintHelper
import com.example.core.RolePermissions
import com.example.core.formatInr
import com.example.core.safeDouble
import com.example.data.PartyCostingRow
import com.example.data.model.DcrPanel
import com.example.data.model.LeadSource
import com.example.data.model.LeadStatus
import com.example.data.model.PartyProject
import com.example.data.model.ProjectDivision
import com.example.data.model.RawMaterial
import com.example.data.model.RoofStructureType
import com.example.data.model.SiteKharchaEntry
import com.example.data.model.SolarMeter
import com.example.data.model.StaffRole
import com.example.ui.theme.LimeGreen
import com.example.ui.theme.LimeSoft
import com.example.ui.theme.LocalSolarColors
import com.example.ui.theme.MaroonContainerLight
import com.example.ui.theme.MaroonPrimary
import com.example.ui.theme.TabularNumberStyle
import java.util.Locale

@Composable
fun PartyLeadDetailsTab(
    party: PartyProject,
    role: StaffRole,
    allocatedPanels: List<DcrPanel>,
    allocatedMeters: List<SolarMeter>,
    rawMaterials: List<RawMaterial>,
    partyKharcha: List<SiteKharchaEntry>,
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
    ) -> Unit
) {
    val context = LocalContext.current
    val solarColors = LocalSolarColors.current
    val canSeeMoney = RolePermissions.canViewFinancials(role)
    val canSeeProfitability = RolePermissions.canViewPartyProfitability(role)
    val canEditLead = RolePermissions.canCreateParty(role)

    var showEditDialog by remember { mutableStateOf(false) }

    val mainSolarMaterialCost = remember(allocatedPanels, allocatedMeters, rawMaterials, party.id) {
        val panelCost = allocatedPanels.sumOf { it.unitCost }
        val meterCost = allocatedMeters.sumOf { it.unitCost }
        val bosCost = rawMaterials.sumOf { rm ->
            rm.issueLogs.filter { it.partyId == party.id }.sumOf { it.quantity * rm.unitCost }
        }
        panelCost + meterCost + bosCost
    }
    val onSitePurchasesCost = party.totalOnSitePurchases
    val dailyKharchaCost = remember(partyKharcha) { partyKharcha.sumOf { it.amount } }
    val commissionPct = if (party.partnerCommissionPct > 0.0) party.partnerCommissionPct else {
        if (party.leadSource == LeadSource.THAKUR_JI) 10.0 else 0.0
    }
    val referralCommission = party.projectValue * (commissionPct / 100.0)
    val costingRow = remember(party, mainSolarMaterialCost, onSitePurchasesCost, dailyKharchaCost, referralCommission) {
        PartyCostingRow(
            party = party,
            dealValue = party.projectValue,
            mainMaterialCost = mainSolarMaterialCost,
            onSitePurchaseCost = onSitePurchasesCost,
            dailyKharchaCost = dailyKharchaCost,
            referralCommission = referralCommission
        )
    }
    val netProfit = costingRow.netProfit
    val marginPct = costingRow.marginPct

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Live Party-Wise Profitability Summary (OWNER & ACCOUNTS ONLY)
        if (canSeeProfitability) {
            item {
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.5.dp, if (netProfit >= 0) LimeGreen else solarColors.creditRed),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("party_profitability_card")
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.TrendingUp, contentDescription = null, tint = LimeGreen, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Live Party Profitability Summary",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Surface(
                                color = if (netProfit >= 0) LimeSoft else MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = String.format(Locale.US, "%.1f%% Margin", marginPct),
                                    style = MaterialTheme.typography.labelSmall.merge(TabularNumberStyle),
                                    fontWeight = FontWeight.Bold,
                                    color = if (netProfit >= 0) Color(0xFF2B4D12) else solarColors.creditRed,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        HorizontalDivider(color = solarColors.gridBorderStrong)
                        ProfitRow("Contract Deal Value", formatInr(party.projectValue), solarColors.debitBlue)
                        ProfitRow(
                            "(-) Main Solar Material (${allocatedPanels.size} Panels, ${allocatedMeters.size} Meters, BOS)",
                            "- ${formatInr(mainSolarMaterialCost)}",
                            solarColors.creditRed
                        )
                        ProfitRow(
                            "(-) On-Site Material Purchases (${party.sitePurchases.size} Bills)",
                            "- ${formatInr(onSitePurchasesCost)}",
                            solarColors.creditRed
                        )
                        ProfitRow(
                            "(-) Daily Site Kharcha & Labour (${partyKharcha.size} Entries)",
                            "- ${formatInr(dailyKharchaCost)}",
                            solarColors.creditRed
                        )
                        ProfitRow(
                            "(-) Referral / Partner Commission (${String.format(Locale.US, "%.1f", commissionPct)}%)",
                            "- ${formatInr(referralCommission)}",
                            solarColors.creditRed
                        )
                        HorizontalDivider(color = solarColors.gridBorderStrong)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "Net Party Profit",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    formatInr(netProfit),
                                    style = MaterialTheme.typography.titleMedium.merge(TabularNumberStyle),
                                    fontWeight = FontWeight.Bold,
                                    color = if (netProfit >= 0) LimeGreen else solarColors.creditRed
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = {
                                        ExportPrintHelper.exportAndSharePartyCostingReportPdf(
                                            context,
                                            costingRow,
                                            allocatedPanels,
                                            allocatedMeters
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = LimeGreen, contentColor = Color.White),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("share_party_costing_pdf_btn")
                                ) {
                                    Icon(Icons.Filled.Share, contentDescription = "Share Costing PDF", modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Share PDF", style = MaterialTheme.typography.labelSmall)
                                }
                                OutlinedButton(
                                    onClick = {
                                        ExportPrintHelper.printPartyCostingReportA4(
                                            context,
                                            costingRow,
                                            allocatedPanels,
                                            allocatedMeters
                                        )
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("print_party_costing_pdf_btn")
                                ) {
                                    Icon(Icons.Filled.Print, contentDescription = "Print Costing PDF", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Complete Lead Details & Pre-Sale Tracker Card
        item {
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, solarColors.gridBorderStrong),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("lead_details_card")
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Lead Details & Pre-Sale Tracker",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (canEditLead) {
                            OutlinedButton(
                                onClick = { showEditDialog = true },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("edit_lead_details_btn")
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Edit Lead")
                            }
                        }
                    }

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            Surface(
                                color = solarColors.gridHeader,
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, solarColors.gridBorderStrong)
                            ) {
                                Text(
                                    text = party.division.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        item {
                            Surface(color = MaroonContainerLight, shape = RoundedCornerShape(4.dp)) {
                                Text(
                                    text = party.leadSource.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaroonPrimary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        item {
                            Surface(color = LimeSoft, shape = RoundedCornerShape(4.dp)) {
                                Text(
                                    text = party.leadStatus.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFF2B4D12),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = solarColors.gridBorderStrong)

                    if (party.leadSource != LeadSource.NB_DIRECT &&
                        (party.referrerName.isNotBlank() || party.referrerPhone.isNotBlank())
                    ) {
                        LeadDetailRow(
                            "Referrer",
                            listOf(party.referrerName, party.referrerPhone).filter { it.isNotBlank() }.joinToString(" • ")
                        )
                    }
                    LeadDetailRow("Lead Date", party.leadDate.ifBlank { party.createdDate.ifBlank { "-" } })
                    LeadDetailRow("Consumer Mobile", party.mobile.ifBlank { "-" })
                    LeadDetailRow("Site Location / City", party.cityOrLocation.ifBlank { party.address.ifBlank { "-" } })
                    LeadDetailRow("DISCOM K-Number", party.consumerNumber.ifBlank { "-" })
                    LeadDetailRow("Proposed Capacity", "${party.capacityKw} kW")
                    LeadDetailRow("Roof Structure Type", party.roofStructureType.label)

                    if (canSeeMoney) {
                        LeadDetailRow("Quoted Rate", if (party.quotedRatePerKw > 0) "${formatInr(party.quotedRatePerKw)} / kW" else "-")
                        LeadDetailRow("Deal Value", formatInr(party.projectValue))
                    }

                    LeadDetailRow("Next Follow-Up Date", party.nextFollowUpDate.ifBlank { "Not Scheduled" })
                    Column {
                        Text(
                            "Site Survey Notes",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(2.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = party.siteSurveyNotes.ifBlank { "No site survey notes recorded yet." },
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditDialog) {
        EditLeadDetailsDialog(
            party = party,
            canSeeMoney = canSeeMoney,
            onDismiss = { showEditDialog = false },
            onSave = { mobile, city, kno, kw, src, refName, refPhone, leadDate, roofType, rate, dealVal, followUp, surveyNotes, leadSt, div ->
                onUpdateLeadDetails(
                    mobile, city, kno, kw, src, refName, refPhone, leadDate,
                    roofType, rate, dealVal, followUp, surveyNotes, leadSt, div
                )
                showEditDialog = false
            }
        )
    }
}

@Composable
private fun LeadDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ProfitRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(
            value,
            style = TabularNumberStyle,
            color = valueColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun EditLeadDetailsDialog(
    party: PartyProject,
    canSeeMoney: Boolean,
    onDismiss: () -> Unit,
    onSave: (
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
    ) -> Unit
) {
    var mobile by remember { mutableStateOf(party.mobile) }
    var city by remember { mutableStateOf(party.cityOrLocation.ifBlank { party.address }) }
    var kno by remember { mutableStateOf(party.consumerNumber) }
    var kw by remember { mutableStateOf(party.capacityKw.toString()) }
    var division by remember { mutableStateOf(party.division) }
    var source by remember { mutableStateOf(party.leadSource) }
    var refName by remember { mutableStateOf(party.referrerName) }
    var refPhone by remember { mutableStateOf(party.referrerPhone) }
    var leadDate by remember { mutableStateOf(party.leadDate.ifBlank { party.createdDate }) }
    var roofType by remember { mutableStateOf(party.roofStructureType) }
    var rate by remember { mutableStateOf(if (party.quotedRatePerKw > 0) party.quotedRatePerKw.toLong().toString() else "") }
    var dealVal by remember { mutableStateOf(party.projectValue.toLong().toString()) }
    var followUp by remember { mutableStateOf(party.nextFollowUpDate) }
    var surveyNotes by remember { mutableStateOf(party.siteSurveyNotes) }
    var leadStatus by remember { mutableStateOf(party.leadStatus) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update Lead Details") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = mobile,
                        onValueChange = { mobile = it },
                        label = { Text("Consumer Mobile") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.weight(1.2f)
                    )
                    OutlinedTextField(
                        value = kw,
                        onValueChange = { kw = it },
                        label = { Text("Capacity kW") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(0.9f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = city,
                        onValueChange = { city = it },
                        label = { Text("Site Location / City") },
                        singleLine = true,
                        modifier = Modifier.weight(1.2f)
                    )
                    OutlinedTextField(
                        value = leadDate,
                        onValueChange = { leadDate = it },
                        label = { Text("Lead Date") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = kno,
                    onValueChange = { kno = it },
                    label = { Text("DISCOM K-Number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Project Division", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(ProjectDivision.entries) { div ->
                        FilterChip(
                            selected = division == div,
                            onClick = { division = div },
                            label = { Text(div.label) }
                        )
                    }
                }

                Text("Lead Source Priority", style = MaterialTheme.typography.labelMedium)
                LeadSource.entries.forEach { s ->
                    FilterChip(
                        selected = source == s,
                        onClick = { source = s },
                        label = { Text(s.label) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (source != LeadSource.NB_DIRECT) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = refName,
                            onValueChange = { refName = it },
                            label = { Text("Referrer Name") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = refPhone,
                            onValueChange = { refPhone = it },
                            label = { Text("Referrer Phone") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Text("Roof Structure Type", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(RoofStructureType.entries) { rt ->
                        FilterChip(
                            selected = roofType == rt,
                            onClick = { roofType = rt },
                            label = { Text(rt.label) }
                        )
                    }
                }

                Text("Lead Status", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(LeadStatus.entries) { st ->
                        FilterChip(
                            selected = leadStatus == st,
                            onClick = { leadStatus = st },
                            label = { Text(st.label) }
                        )
                    }
                }

                if (canSeeMoney) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = rate,
                            onValueChange = { rate = it },
                            label = { Text("Quoted ₹/kW") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = dealVal,
                            onValueChange = { dealVal = it },
                            label = { Text("Deal Value (₹)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                OutlinedTextField(
                    value = followUp,
                    onValueChange = { followUp = it },
                    label = { Text("Next Follow-Up Date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = surveyNotes,
                    onValueChange = { surveyNotes = it },
                    label = { Text("Site Survey Notes") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            var isSavingLead by remember { mutableStateOf(false) }
            Button(
                onClick = {
                    if (isSavingLead) return@Button
                    isSavingLead = true
                    onSave(
                        mobile,
                        city,
                        kno,
                        safeDouble(kw, party.capacityKw),
                        source,
                        refName,
                        refPhone,
                        leadDate,
                        roofType,
                        safeDouble(rate, party.quotedRatePerKw),
                        safeDouble(dealVal, party.projectValue),
                        followUp,
                        surveyNotes,
                        leadStatus,
                        division
                    )
                },
                enabled = !isSavingLead,
                colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                modifier = Modifier.testTag("save_lead_details_btn")
            ) {
                Text(if (isSavingLead) "Saving..." else "Save Lead")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
