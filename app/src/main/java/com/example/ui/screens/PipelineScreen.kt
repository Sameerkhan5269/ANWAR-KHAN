package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SolarPower
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.RolePermissions
import com.example.core.formatInr
import com.example.core.safeDouble
import com.example.data.model.LeadSource
import com.example.data.model.ParsedWhatsAppUpdate
import com.example.data.model.PartyProject
import com.example.data.model.StaffRole
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalSolarColors
import com.example.ui.theme.SolarAmber

@Composable
fun PipelineScreen(
    role: StaffRole,
    parties: List<PartyProject>,
    selectedSource: LeadSource?,
    searchQuery: String,
    onSelectSource: (LeadSource?) -> Unit,
    onSearchChange: (String) -> Unit,
    onOpenParty: (String) -> Unit,
    onCreateParty: (
        name: String,
        mobile: String,
        address: String,
        consumerNo: String,
        capacityKw: Double,
        leadSource: LeadSource,
        projectValue: Double,
        loan1: Double,
        loan2: Double,
        subsidy: Double,
        partnerPct: Double
    ) -> Unit,
    onDeleteParty: (String) -> Unit,
    onPreviewWhatsApp: (String) -> ParsedWhatsAppUpdate,
    onApplyWhatsApp: (ParsedWhatsAppUpdate, String?) -> Unit
) {
    val canSeeMoney = RolePermissions.canViewFinancials(role)
    val canCreate = RolePermissions.canCreateParty(role)
    val canDelete = RolePermissions.canDeleteRecords(role)
    val solarColors = LocalSolarColors.current

    var showNewPartyDialog by remember { mutableStateOf(false) }
    var showWhatsAppDialog by remember { mutableStateOf(false) }

    val filteredParties = remember(parties, selectedSource, searchQuery) {
        parties.filter { p ->
            val matchesSource = selectedSource == null || p.leadSource == selectedSource
            val q = searchQuery.trim().lowercase()
            val matchesQuery = q.isEmpty() ||
                p.partyName.lowercase().contains(q) ||
                p.address.lowercase().contains(q) ||
                p.consumerNumber.lowercase().contains(q) ||
                p.mobile.contains(q) ||
                p.currentStageTitle.lowercase().contains(q)
            matchesSource && matchesQuery
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Lead / Reference Source Priority Summary Cards (1-click filter)
            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "LEAD SOURCE PRIORITY",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (selectedSource != null) {
                            Text(
                                text = "Clear Filter",
                                style = MaterialTheme.typography.labelMedium,
                                color = solarColors.solarAmber,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable { onSelectSource(null) }
                                    .padding(4.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(LeadSource.entries) { src ->
                            val srcParties = parties.filter { it.leadSource == src }
                            val avgPct = if (srcParties.isEmpty()) 0 else {
                                (srcParties.map { it.progressFraction }.average() * 100).toInt()
                            }
                            val pendingSum = srcParties.sumOf { it.pendingAmount }
                            val isSelected = selectedSource == src

                            LeadSourceSummaryCard(
                                source = src,
                                count = srcParties.size,
                                avgProgressPct = avgPct,
                                pendingSettlement = pendingSum,
                                showMoney = canSeeMoney,
                                selected = isSelected,
                                onClick = { onSelectSource(src) }
                            )
                        }
                    }
                }
            }

            // 2. Search Bar + WhatsApp Group Message Parser Button
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchChange,
                        placeholder = { Text("Search party, K-No, mobile, stage…") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchChange("") }) {
                                    Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pipeline_search_input")
                    )

                    Surface(
                        onClick = { showWhatsAppDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        color = solarColors.emerald,
                        modifier = Modifier
                            .height(54.dp)
                            .testTag("whatsapp_parser_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Chat,
                                contentDescription = "WhatsApp Group Message Parser",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "WA Parse",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // Strict Privacy Banner for Field Staff
            if (!canSeeMoney) {
                item {
                    Surface(
                        color = solarColors.emeraldContainer,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, solarColors.emerald.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Field Mode Active (${role.label}): Financial values, loans & accounting are hidden. You can update site workflows & upload geotag photos.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            // 3. Party Cards List
            if (filteredParties.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, solarColors.hairlineBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Filled.SolarPower,
                                contentDescription = null,
                                tint = solarColors.solarAmber,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = if (parties.isEmpty()) "No Solar EPC Parties Yet" else "No Matching Parties",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = if (canCreate) {
                                    "Tap '+ New Party' or use 'WA Parse' to start tracking a rooftop solar installation across all 5 workflow stages."
                                } else {
                                    "Parties added by Office or Owner will sync here automatically in real time."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                }
            } else {
                items(filteredParties, key = { it.id }) { party ->
                    PartyPipelineCard(
                        party = party,
                        showMoney = canSeeMoney,
                        canDelete = canDelete,
                        onClick = { onOpenParty(party.id) },
                        onDelete = { onDeleteParty(party.id) }
                    )
                }
            }
        }

        if (canCreate) {
            FloatingActionButton(
                onClick = { showNewPartyDialog = true },
                containerColor = SolarAmber,
                contentColor = DeepNavy,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .testTag("add_party_fab")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add Party")
                    Spacer(Modifier.width(6.dp))
                    Text("New Party", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showNewPartyDialog) {
        AddPartyDialog(
            canSetPartnerSplit = RolePermissions.canViewPartnerSplits(role),
            onDismiss = { showNewPartyDialog = false },
            onSave = { name, mob, addr, kno, kw, src, valInr, l1, l2, sub, pct ->
                onCreateParty(name, mob, addr, kno, kw, src, valInr, l1, l2, sub, pct)
                showNewPartyDialog = false
            }
        )
    }

    if (showWhatsAppDialog) {
        WhatsAppParserDialog(
            parties = parties,
            onPreview = onPreviewWhatsApp,
            onApply = { parsed, partyId ->
                onApplyWhatsApp(parsed, partyId)
                showWhatsAppDialog = false
            },
            onDismiss = { showWhatsAppDialog = false }
        )
    }
}

@Composable
private fun LeadSourceSummaryCard(
    source: LeadSource,
    count: Int,
    avgProgressPct: Int,
    pendingSettlement: Double,
    showMoney: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    val solarColors = LocalSolarColors.current
    val accent = when (source) {
        LeadSource.NB_DIRECT -> DeepNavy
        LeadSource.THAKUR_JI -> solarColors.solarAmber
        LeadSource.PERSONAL_REF -> solarColors.emerald
    }

    Card(
        onClick = onClick,
        modifier = Modifier
            .width(196.dp)
            .testTag("source_card_${source.name.lowercase()}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) accent else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) SolarAmber else solarColors.hairlineBorder
        )
    ) {
        val textColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
        val subColor = if (selected) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant

        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = source.label,
                style = MaterialTheme.typography.titleSmall,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "$count Parties",
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = JetBrainsMonoFontFamily,
                        color = textColor
                    )
                    Text(
                        text = "$avgProgressPct% Avg Done",
                        style = MaterialTheme.typography.labelSmall,
                        color = subColor
                    )
                }
                if (showMoney) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Pending",
                            style = MaterialTheme.typography.labelSmall,
                            color = subColor
                        )
                        Text(
                            text = formatInr(pendingSettlement),
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = if (selected) Color(0xFFFDE68A) else solarColors.creditRed
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PartyPipelineCard(
    party: PartyProject,
    showMoney: Boolean,
    canDelete: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val solarColors = LocalSolarColors.current
    val pct = (party.progressFraction * 100).toInt()

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("party_card_${party.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, solarColors.hairlineBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = party.partyName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            color = solarColors.solarAmberContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.Bolt,
                                    contentDescription = null,
                                    tint = solarColors.solarAmber,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "${party.capacityKw} kW",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = party.leadSource.shortLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        if (party.address.isNotBlank()) {
                            Text(
                                text = party.address,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (canDelete) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete Party",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Open Party",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(10.dp))

            // Workflow progress bar + current stage
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Next: ${party.currentStageTitle}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${party.completedStagesCount}/${party.totalStagesCount} ($pct%)",
                    style = MaterialTheme.typography.labelSmall,
                    color = solarColors.emerald
                )
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { party.progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(99.dp)),
                color = if (pct == 100) solarColors.emerald else solarColors.solarAmber,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${party.documents.size} Vault Docs • ${party.siteLogs.size} Updates",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // STRICT PRIVACY CHECK: Only show ₹ figures if authorized!
                if (showMoney) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Val: ${formatInr(party.projectValue)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = solarColors.debitBlue
                        )
                        Text(
                            text = "Due: ${formatInr(party.pendingAmount)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (party.pendingAmount > 0) solarColors.creditRed else solarColors.emerald
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddPartyDialog(
    canSetPartnerSplit: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, Double, LeadSource, Double, Double, Double, Double, Double) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var consumerNo by remember { mutableStateOf("") }
    var capacityKw by remember { mutableStateOf("3.0") }
    var selectedSource by remember { mutableStateOf(LeadSource.NB_DIRECT) }
    var projectValue by remember { mutableStateOf("185000") }
    var loan1 by remember { mutableStateOf("100000") }
    var loan2 by remember { mutableStateOf("50000") }
    var subsidy by remember { mutableStateOf("78000") }
    var partnerPct by remember { mutableStateOf("0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Solar EPC Party") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Customer / Party Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = capacityKw,
                        onValueChange = { capacityKw = it },
                        label = { Text("Capacity (kW)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = mobile,
                        onValueChange = { mobile = it },
                        label = { Text("Mobile") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.weight(1.3f)
                    )
                }
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Site Address (Udaipur / District)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = consumerNo,
                    onValueChange = { consumerNo = it },
                    label = { Text("AVVNL K-Number / Consumer No.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Primary Lead / Reference Source *", style = MaterialTheme.typography.labelMedium)
                LeadSource.entries.forEach { src ->
                    FilterChip(
                        selected = selectedSource == src,
                        onClick = {
                            selectedSource = src
                            if (src == LeadSource.THAKUR_JI && partnerPct == "0") partnerPct = "10"
                        },
                        label = { Text(src.label) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = projectValue,
                        onValueChange = { projectValue = it },
                        label = { Text("Project Value (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = subsidy,
                        onValueChange = { subsidy = it },
                        label = { Text("Subsidy (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = loan1,
                        onValueChange = { loan1 = it },
                        label = { Text("Loan 1st Tranche") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = loan2,
                        onValueChange = { loan2 = it },
                        label = { Text("Loan 2nd Tranche") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (canSetPartnerSplit) {
                    OutlinedTextField(
                        value = partnerPct,
                        onValueChange = { partnerPct = it },
                        label = { Text("Partner Split / Commission % (Owner Only)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        name,
                        mobile,
                        address,
                        consumerNo,
                        safeDouble(capacityKw, 3.0),
                        selectedSource,
                        safeDouble(projectValue, 0.0),
                        safeDouble(loan1, 0.0),
                        safeDouble(loan2, 0.0),
                        safeDouble(subsidy, 0.0),
                        if (canSetPartnerSplit) safeDouble(partnerPct, 0.0) else 0.0
                    )
                },
                enabled = name.isNotBlank()
            ) {
                Text("Create Party")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun WhatsAppParserDialog(
    parties: List<PartyProject>,
    onPreview: (String) -> ParsedWhatsAppUpdate,
    onApply: (ParsedWhatsAppUpdate, String?) -> Unit,
    onDismiss: () -> Unit
) {
    var message by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<ParsedWhatsAppUpdate?>(null) }
    var overridePartyId by remember { mutableStateOf<String?>(null) }
    val solarColors = LocalSolarColors.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("WhatsApp Group Message Parser") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Paste a site update from the NB Solar WhatsApp group (e.g., \"Ramesh Sharma 5kW installation done\" or \"Meera Devi AVVNL net meter installed\").",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = message,
                    onValueChange = {
                        message = it
                        preview = if (it.isNotBlank()) onPreview(it) else null
                        overridePartyId = preview?.matchedPartyId
                    },
                    label = { Text("Paste WhatsApp Message") },
                    minLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("whatsapp_message_input")
                )

                preview?.let { p ->
                    Surface(
                        color = solarColors.emeraldContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Detected Party: ${p.matchedPartyName}" +
                                    if (p.matchedPartyId != null) " (Matched)" else " (New/Unmatched)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            if (p.detectedKw != null) {
                                Text("Detected Capacity: ${p.detectedKw} kW", style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                text = if (p.stagesToComplete.isNotEmpty()) {
                                    "Stages to mark done: ${p.stagesToComplete.joinToString(", ")}"
                                } else {
                                    "Will attach as timestamped site update note."
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    if (parties.isNotEmpty()) {
                        Text("Or link to existing party:", style = MaterialTheme.typography.labelSmall)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(parties.take(8)) { party ->
                                FilterChip(
                                    selected = overridePartyId == party.id,
                                    onClick = { overridePartyId = party.id },
                                    label = { Text(party.partyName) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = preview ?: onPreview(message)
                    onApply(p, overridePartyId)
                },
                enabled = message.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = solarColors.emerald)
            ) {
                Text("Apply & Sync")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
