package com.example.ui.screens

import android.net.Uri
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.core.RolePermissions
import com.example.core.formatInr
import com.example.core.safeDouble
import com.example.data.SyncConnectionState
import com.example.data.SyncStatus
import com.example.data.model.AppHealthReport
import com.example.data.model.ProjectDivision
import com.example.data.model.StaffRole
import com.example.data.model.StaffUser
import com.example.data.model.TeamChatMessage
import com.example.ui.components.CompanyCircularLogo
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LimeGreen
import com.example.ui.theme.LimeSoft
import com.example.ui.theme.LocalSolarColors
import com.example.ui.theme.MaroonPrimary

@Composable
fun AdminTeamScreen(
    currentUser: StaffUser,
    staffUsers: List<StaffUser>,
    teamChat: List<TeamChatMessage>,
    syncStatus: SyncStatus,
    healthReport: AppHealthReport? = null,
    isRunningSelfTest: Boolean = false,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onForceSync: () -> Unit,
    onRunSelfTest: () -> Unit = {},
    onAddStaffUser: (loginId: String, pin: String, displayName: String, role: StaffRole, assignedDivision: ProjectDivision?) -> Unit,
    onDeleteStaffUser: (userId: String) -> Unit,
    onTopUpImprest: (staffId: String, amount: Double) -> Unit,
    onSendChatMessage: (text: String, videoUri: Uri?) -> Unit,
    onShowToast: (String) -> Unit,
    onLogout: () -> Unit
) {
    val solarColors = LocalSolarColors.current
    val clipboard = LocalClipboardManager.current
    val canManageUsers = RolePermissions.canManageUsers(currentUser.role)
    val canManageImprest = RolePermissions.canManageImprest(currentUser.role)
    var activeTab by remember { mutableIntStateOf(0) }
    var showAddStaffDialog by remember { mutableStateOf(false) }
    var topUpTargetStaff by remember { mutableStateOf<StaffUser?>(null) }
    var sqlCopied by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = activeTab) {
            Tab(
                selected = activeTab == 0,
                onClick = { activeTab = 0 },
                text = { Text("Team, Imprest & Sync") }
            )
            Tab(
                selected = activeTab == 1,
                onClick = { activeTab = 1 },
                text = { Text("Team Chat & Live Call (${teamChat.size})") }
            )
        }

        if (activeTab == 1) {
            TeamChatSection(
                currentUser = currentUser,
                messages = teamChat,
                onSendMessage = onSendChatMessage,
                onShowToast = onShowToast
            )
            return@Column
        }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Active Profile Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaroonPrimary),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CompanyCircularLogo(size = 44.dp, borderColor = LimeGreen)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(currentUser.displayName, style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                                Text(
                                    "${currentUser.loginId} • ${currentUser.role.label} • ${currentUser.assignedDivision?.shortLabel ?: "All Divisions"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    color = Color(0xFFFFDAD8)
                                )
                            }
                        }
                        IconButton(onClick = onToggleTheme) {
                            Icon(
                                if (isDarkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                                contentDescription = "Toggle Dark/Light Theme",
                                tint = Color.White
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        currentUser.role.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onLogout,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5A110F), contentColor = Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("logout_switch_role_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Sign Out / Switch Staff Role")
                    }
                }
            }
        }

        // Supervisor Imprest Cash Wallets
        val supervisors = staffUsers.filter { it.role == StaffRole.SUPERVISOR }
        if (canManageImprest || currentUser.role == StaffRole.SUPERVISOR) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, solarColors.hairlineBorder),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, tint = LimeGreen, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Supervisor Imprest Cash Wallets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "Automatically deducted when field supervisors log on-site material purchases via Imprest Cash.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val visibleSupervisors = if (canManageImprest) supervisors else supervisors.filter { it.id == currentUser.id }
                        visibleSupervisors.forEach { sup ->
                            Surface(
                                color = LimeSoft,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(sup.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFF1F3B0B))
                                        Text(
                                            "Balance: ${formatInr(sup.imprestBalance)}",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = if (sup.imprestBalance < 0) solarColors.creditRed else Color(0xFF2B4D12)
                                        )
                                    }
                                    if (canManageImprest) {
                                        Button(
                                            onClick = { topUpTargetStaff = sup },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.testTag("topup_imprest_btn_${sup.id}")
                                        ) {
                                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Top-Up")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Supabase Real-Time Sync Diagnostics Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, solarColors.hairlineBorder),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Supabase Real-Time Cloud Sync", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Surface(
                            color = when (syncStatus.state) {
                                SyncConnectionState.LIVE -> solarColors.emeraldContainer
                                SyncConnectionState.SYNCING -> solarColors.solarAmberContainer
                                SyncConnectionState.OFFLINE -> MaterialTheme.colorScheme.errorContainer
                            },
                            shape = RoundedCornerShape(99.dp)
                        ) {
                            Text(
                                text = syncStatus.state.name,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Text(
                        "Table: nb_solar_store • Last Sync: ${syncStatus.lastSyncedAt}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(syncStatus.detail, style = MaterialTheme.typography.bodySmall)
                    if (syncStatus.needsRlsGrant) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    "Supabase SQL Fix Required (42501)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    SUPABASE_GRANT_SQL,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                OutlinedButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(SUPABASE_GRANT_SQL))
                                        sqlCopied = true
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (sqlCopied) "SQL Copied — Run in Supabase SQL Editor" else "Copy SQL Fix for Supabase")
                                }
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = onForceSync,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("force_cloud_sync_btn")
                    ) {
                        Icon(Icons.Filled.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (syncStatus.queuedCount > 0) {
                                "Sync Now (${syncStatus.queuedCount} Offline Queued)"
                            } else {
                                "Sync Now with Supabase Cloud"
                            }
                        )
                    }
                }
            }
        }

        // Owner-Only Automated App Self-Test & Health Check
        if (currentUser.role == StaffRole.OWNER) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.5.dp, MaroonPrimary.copy(alpha = 0.65f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("owner_health_check_card")
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.HealthAndSafety, contentDescription = null, tint = MaroonPrimary, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        "QA Self-Test & System Health Check",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A)
                                    )
                                    Text(
                                        "Verifies Supabase R/W, Division Isolation, Costing, BUSY 21 & Proposal PDFs",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF475569)
                                    )
                                }
                            }
                            if (healthReport != null) {
                                Surface(
                                    color = if (healthReport.allPassed) LimeSoft else MaterialTheme.colorScheme.errorContainer,
                                    shape = RoundedCornerShape(99.dp)
                                ) {
                                    Text(
                                        text = "${healthReport.passedCount}/${healthReport.checks.size} PASS",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (healthReport.allPassed) Color(0xFF2B4D12) else MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = onRunSelfTest,
                            enabled = !isRunningSelfTest,
                            colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary, contentColor = Color.White),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("run_app_self_test_btn")
                        ) {
                            if (isRunningSelfTest) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Running Automated Self-Test…")
                            } else {
                                Icon(Icons.Filled.HealthAndSafety, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("🩺 Run App Self-Test & Health Check", fontWeight = FontWeight.Bold)
                            }
                        }

                        if (healthReport != null) {
                            Text(
                                text = "Last Checked: ${healthReport.ranAt}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF64748B)
                            )
                            healthReport.checks.forEach { chk ->
                                Surface(
                                    color = if (chk.passed) Color(0xFFF1F8E9) else Color(0xFFFFF1F2),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        if (chk.passed) LimeGreen.copy(alpha = 0.55f) else Color(0xFFFCA5A5)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(9.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Icon(
                                            imageVector = if (chk.passed) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                                            contentDescription = if (chk.passed) "PASS" else "FAIL",
                                            tint = if (chk.passed) Color(0xFF2B4D12) else Color(0xFFB91C1C),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = chk.name,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF0F172A),
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Text(
                                                    text = if (chk.passed) "PASS" else "FAIL",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = if (chk.passed) Color(0xFF2B4D12) else Color(0xFFB91C1C)
                                                )
                                            }
                                            Text(
                                                text = chk.detail,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFF334155)
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

        // Owner-Only Team & Login ID Management
        if (canManageUsers) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("STAFF LOGIN IDs (${staffUsers.size})", style = MaterialTheme.typography.labelSmall)
                    Button(
                        onClick = { showAddStaffDialog = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("add_staff_user_btn")
                    ) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add Staff")
                    }
                }
            }

            items(staffUsers, key = { it.id }) { staff ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, solarColors.hairlineBorder),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(staff.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "ID: ${staff.loginId} • PIN: ${staff.pin} • ${staff.role.label} • ${staff.assignedDivision?.shortLabel ?: "All"}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFontFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!staff.isBuiltIn) {
                            IconButton(onClick = { onDeleteStaffUser(staff.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Remove staff", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }

        // Official Company & Bank Identity Footer
        item {
            HorizontalDivider(color = solarColors.gridBorderStrong)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, solarColors.gridBorderStrong),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        "NB ENGINEERING WORKS AND ELECTRICALS",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaroonPrimary
                    )
                    Text(
                        "Rama Phosphates Ltd. Plant, Ground Floor, Shed No. 1, Opp. Rama Phosphates Ltd, Udaipur, Rajasthan",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "GSTIN: 08CSZPK9277N2Z8 • Tel.: 9929214883 • nbengineering87@gmail.com",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = solarColors.hairlineBorder)
                    Text(
                        "HDFC BANK • Branch: KANPUR, UDAIPUR (RAJ)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "A/c No.: 50200079191430 • IFSC: HDFC0004861",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
    }

    if (showAddStaffDialog) {
        AddStaffUserDialog(
            onDismiss = { showAddStaffDialog = false },
            onSave = { id, pin, name, role, div ->
                onAddStaffUser(id, pin, name, role, div)
                showAddStaffDialog = false
            }
        )
    }

    topUpTargetStaff?.let { target ->
        TopUpImprestDialog(
            staff = target,
            onDismiss = { topUpTargetStaff = null },
            onConfirm = { amount ->
                onTopUpImprest(target.id, amount)
                topUpTargetStaff = null
            }
        )
    }
}

@Composable
private fun TopUpImprestDialog(
    staff: StaffUser,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var amountText by remember { mutableStateOf("5000") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Top-Up Imprest Wallet") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Supervisor: ${staff.displayName} (Current: ${formatInr(staff.imprestBalance)})")
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Add Cash Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            val amt = safeDouble(amountText, 0.0)
            Button(
                onClick = { onConfirm(amt) },
                enabled = amt > 0.0,
                colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary)
            ) {
                Text("Add Imprest Cash")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddStaffUserDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String, StaffRole, ProjectDivision?) -> Unit
) {
    var loginId by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(StaffRole.SUPERVISOR) }
    var assignedDivision by remember { mutableStateOf<ProjectDivision?>(ProjectDivision.SOLAR) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Staff Login ID") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Staff Full Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = loginId,
                    onValueChange = { loginId = it },
                    label = { Text("Login ID (e.g., raju@nb) *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 6) pin = it.filter { c -> c.isDigit() } },
                    label = { Text("4-Digit PIN *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Assign Role & Privacy Level", style = MaterialTheme.typography.labelMedium)
                StaffRole.entries.forEach { r ->
                    FilterChip(
                        selected = role == r,
                        onClick = {
                            role = r
                            if (r == StaffRole.OWNER || r == StaffRole.ACCOUNTS) assignedDivision = null
                        },
                        label = { Text(r.label) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (role != StaffRole.OWNER && role != StaffRole.ACCOUNTS) {
                    Text("Division Isolation", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        FilterChip(
                            selected = assignedDivision == ProjectDivision.SOLAR,
                            onClick = { assignedDivision = ProjectDivision.SOLAR },
                            label = { Text("Solar Only") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = assignedDivision == ProjectDivision.WIND,
                            onClick = { assignedDivision = ProjectDivision.WIND },
                            label = { Text("Wind Only") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = assignedDivision == null,
                            onClick = { assignedDivision = null },
                            label = { Text("All") },
                            modifier = Modifier.weight(0.7f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(loginId, pin, displayName, role, assignedDivision) },
                enabled = loginId.isNotBlank() && pin.length >= 4
            ) { Text("Create & Sync") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private const val SUPABASE_GRANT_SQL = """GRANT USAGE ON SCHEMA public TO anon, authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.nb_solar_store TO anon, authenticated;
ALTER TABLE public.nb_solar_store ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "allow_nb_solar_store_all" ON public.nb_solar_store;
CREATE POLICY "allow_nb_solar_store_all" ON public.nb_solar_store
  FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);
ALTER PUBLICATION supabase_realtime ADD TABLE public.nb_solar_store;"""
