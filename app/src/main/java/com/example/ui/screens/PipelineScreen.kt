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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SolarPower
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.RolePermissions
import com.example.core.formatInr
import com.example.data.BackupRestoreEngine
import com.example.data.model.LeadSource
import com.example.data.model.LeadStatus
import com.example.data.model.LoanReminderItem
import com.example.data.model.LoanReminderRule
import com.example.data.model.ParsedLekhaJokhaLine
import com.example.data.model.ParsedWhatsAppUpdate
import com.example.data.model.PartyProject
import com.example.data.model.ProjectDivision
import com.example.data.model.RoofStructureType
import com.example.data.model.StaffRole
import com.example.ui.theme.LimeGreen
import com.example.ui.theme.LimeSoft
import com.example.ui.theme.LocalSolarColors
import com.example.ui.theme.MaroonContainerLight
import com.example.ui.theme.MaroonPrimary
import com.example.ui.theme.TabularNumberStyle

@Composable
fun PipelineScreen(
    role: StaffRole,
    parties: List<PartyProject>,
    selectedSource: LeadSource?,
    selectedDivision: ProjectDivision?,
    searchQuery: String,
    onSelectSource: (LeadSource?) -> Unit,
    onSelectDivision: (ProjectDivision?) -> Unit,
    onSearchChange: (String) -> Unit,
    onOpenParty: (String) -> Unit,
    onCreateParty: (
        name: String,
        mobile: String,
        address: String,
        cityOrLocation: String,
        consumerNo: String,
        capacityKw: Double,
        leadSource: LeadSource,
        referrerName: String,
        referrerPhone: String,
        leadDate: String,
        roofStructureType: RoofStructureType,
        quotedRatePerKw: Double,
        nextFollowUpDate: String,
        siteSurveyNotes: String,
        leadStatus: LeadStatus,
        projectValue: Double,
        loan1: Double,
        loan2: Double,
        subsidy: Double,
        partnerPct: Double,
        division: ProjectDivision,
        txnId: String
    ) -> Unit,
    onDeleteParty: (String) -> Unit,
    onPreviewWhatsApp: (String) -> ParsedWhatsAppUpdate,
    onApplyWhatsApp: (ParsedWhatsAppUpdate, String?, String) -> Unit,
    onPreviewLekhaJokha: (String) -> List<ParsedLekhaJokhaLine>,
    onApplyLekhaJokha: (List<ParsedLekhaJokhaLine>, String) -> Unit,
    canBulkImportLoanParties: Boolean = false,
    canViewReminders: Boolean = false,
    reminders: List<LoanReminderItem> = emptyList(),
    onOpenBulkLoanImporter: () -> Unit = {},
    onOpenRemindersDrawer: () -> Unit = {},
    onTickMcoOk: (String) -> Unit = {}
) {
    val canSeeMoney = RolePermissions.canViewFinancials(role)
    val canSwitchDivision = RolePermissions.canSwitchDivision(role)
    val canCreate = RolePermissions.canCreateParty(role)
    val canDelete = RolePermissions.canDeleteRecords(role)
    val solarColors = LocalSolarColors.current

    var showNewPartyDialog by remember { mutableStateOf(false) }
    var showWhatsAppDialog by remember { mutableStateOf(false) }
    var showLekhaJokhaDialog by remember { mutableStateOf(false) }

    val filteredParties = remember(parties, selectedSource, selectedDivision, searchQuery) {
        parties.filter { p ->
            val matchesSource = selectedSource == null || p.leadSource == selectedSource
            val matchesDivision = selectedDivision == null || p.division == selectedDivision
            val q = searchQuery.trim().lowercase()
            val matchesQuery = q.isEmpty() ||
                p.partyName.lowercase().contains(q) ||
                p.address.lowercase().contains(q) ||
                p.cityOrLocation.lowercase().contains(q) ||
                p.consumerNumber.lowercase().contains(q) ||
                p.mobile.contains(q) ||
                p.currentStageTitle.lowercase().contains(q)
            matchesSource && matchesDivision && matchesQuery
        }
    }

    var visibleLimit by remember(selectedSource, selectedDivision, searchQuery) {
        mutableIntStateOf(BackupRestoreEngine.DEFAULT_PAGE_SIZE)
    }
    val pagedParties = remember(filteredParties, visibleLimit) {
        BackupRestoreEngine.paginateSlice(filteredParties, visibleLimit)
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
                    if (canSwitchDivision) {
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            item {
                                FilterChip(
                                    selected = selectedDivision == null,
                                    onClick = { onSelectDivision(null) },
                                    label = { Text("All Divisions (${parties.size})") }
                                )
                            }
                            items(ProjectDivision.entries) { div ->
                                val divCount = parties.count { it.division == div }
                                FilterChip(
                                    selected = selectedDivision == div,
                                    onClick = { onSelectDivision(div) },
                                    label = { Text("${div.label} ($divCount)") }
                                )
                            }
                        }
                    }
                }
            }

            // 2. Search Bar + WhatsApp & Lekha Jokha Parser Buttons
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchChange,
                        placeholder = { Text("Search party, K-No, mobile, city, stage…") },
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
                            .fillMaxWidth()
                            .testTag("pipeline_search_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            onClick = { showWhatsAppDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            color = LimeGreen,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("whatsapp_parser_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Filled.Chat,
                                    contentDescription = "WhatsApp Stage Parser",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "WA Stage Parse",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Surface(
                            onClick = { showLekhaJokhaDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            color = MaroonPrimary,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("lekha_jokha_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Filled.ReceiptLong,
                                    contentDescription = "Lekha Jokha Kharcha Parser",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Lekha Jokha",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Solar Pipeline Top Action Bar: Bulk Import Loan Parties & Live Reminders Drawer
                    val showBulkImportBtn = canBulkImportLoanParties && selectedDivision != ProjectDivision.WIND
                    if (showBulkImportBtn || canViewReminders) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (showBulkImportBtn) {
                                Surface(
                                    onClick = onOpenBulkLoanImporter,
                                    shape = RoundedCornerShape(12.dp),
                                    color = solarColors.navyHeader,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("bulk_import_loan_parties_button")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            Icons.Filled.UploadFile,
                                            contentDescription = "Bulk Import Loan Parties",
                                            tint = Color(0xFFFDE68A),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "Bulk Import Loans",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            if (canViewReminders) {
                                val urgentCount = reminders.count { it.isUrgent }
                                val bellBg = if (urgentCount > 0) Color(0xFFB91C1C) else solarColors.solarAmber
                                Surface(
                                    onClick = onOpenRemindersDrawer,
                                    shape = RoundedCornerShape(12.dp),
                                    color = bellBg,
                                    modifier = Modifier
                                        .weight(if (showBulkImportBtn) 0.9f else 1f)
                                        .height(48.dp)
                                        .testTag("live_reminders_drawer_button")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            Icons.Filled.NotificationsActive,
                                            contentDescription = "Live Action and Payment Reminders",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "Summary (${reminders.size})",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = Color.White,
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
                items(pagedParties, key = { it.id }) { party ->
                    PartyPipelineCard(
                        party = party,
                        showMoney = canSeeMoney,
                        canDelete = canDelete,
                        onClick = { onOpenParty(party.id) },
                        onDelete = { onDeleteParty(party.id) }
                    )
                }

                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, solarColors.hairlineBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Showing ${pagedParties.size} of ${filteredParties.size} parties",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (pagedParties.size < filteredParties.size) {
                                val remaining = filteredParties.size - pagedParties.size
                                OutlinedButton(
                                    onClick = { visibleLimit += BackupRestoreEngine.DEFAULT_PAGE_SIZE },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("pipeline_load_more_btn")
                                ) {
                                    Text(
                                        text = "Load More (25 per page) • $remaining remaining",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (canCreate) {
            FloatingActionButton(
                onClick = { showNewPartyDialog = true },
                containerColor = MaroonPrimary,
                contentColor = Color.White,
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
            initialDivision = selectedDivision ?: ProjectDivision.SOLAR,
            onDismiss = { showNewPartyDialog = false },
            onSave = { name, mob, addr, city, kno, kw, src, refName, refPhone, leadDate, roofType, rate, followUp, surveyNotes, leadSt, valInr, l1, l2, sub, pct, div, txnId ->
                onCreateParty(
                    name, mob, addr, city, kno, kw, src, refName, refPhone, leadDate,
                    roofType, rate, followUp, surveyNotes, leadSt, valInr, l1, l2, sub, pct, div, txnId
                )
                showNewPartyDialog = false
            }
        )
    }

    if (showWhatsAppDialog) {
        WhatsAppParserDialog(
            parties = parties,
            onPreview = onPreviewWhatsApp,
            onApply = { parsed, partyId, txnId ->
                onApplyWhatsApp(parsed, partyId, txnId)
                showWhatsAppDialog = false
            },
            onDismiss = { showWhatsAppDialog = false }
        )
    }

    if (showLekhaJokhaDialog) {
        LekhaJokhaBulkDialog(
            onPreview = onPreviewLekhaJokha,
            onApply = { lines, txnId ->
                onApplyLekhaJokha(lines, txnId)
                showLekhaJokhaDialog = false
            },
            onDismiss = { showLekhaJokhaDialog = false }
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
        LeadSource.NB_DIRECT -> MaroonPrimary
        LeadSource.THAKUR_JI -> Color(0xFF7F1D1D)
        LeadSource.PERSONAL_REF -> LimeGreen
    }

    Card(
        onClick = onClick,
        modifier = Modifier
            .width(196.dp)
            .testTag("source_card_${source.name.lowercase()}"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) accent else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) LimeGreen else solarColors.gridBorderStrong
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
                        style = MaterialTheme.typography.titleLarge.merge(TabularNumberStyle),
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
                            style = MaterialTheme.typography.labelLarge.merge(TabularNumberStyle),
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
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, solarColors.gridBorderStrong)
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
                            shape = RoundedCornerShape(4.dp)
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
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = solarColors.gridHeader,
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, solarColors.gridBorderStrong)
                        ) {
                            Text(
                                text = party.division.shortLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Surface(
                            color = MaroonContainerLight,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = party.leadSource.shortLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaroonPrimary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Surface(
                            color = LimeSoft,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = party.leadStatus.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF2B4D12),
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        val locText = party.cityOrLocation.ifBlank { party.address }
                        if (locText.isNotBlank()) {
                            Text(
                                text = locText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (party.nextFollowUpDate.isNotBlank() || party.sitePurchases.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = party.roofStructureType.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (party.nextFollowUpDate.isNotBlank()) {
                                Text(
                                    text = "Follow-Up: ${party.nextFollowUpDate}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaroonPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            if (party.sitePurchases.isNotEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.ShoppingCart,
                                        contentDescription = null,
                                        tint = LimeGreen,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(Modifier.width(3.dp))
                                    Text(
                                        text = "${party.sitePurchases.size} Site Bills",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = LimeGreen,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
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
                color = if (pct == 100) solarColors.emerald else MaroonPrimary,
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
                            style = MaterialTheme.typography.labelSmall.merge(TabularNumberStyle),
                            color = solarColors.debitBlue
                        )
                        Text(
                            text = "Due: ${formatInr(party.pendingAmount)}",
                            style = MaterialTheme.typography.labelSmall.merge(TabularNumberStyle),
                            color = if (party.pendingAmount > 0) solarColors.creditRed else solarColors.emerald
                        )
                    }
                }
            }
        }
    }
}


