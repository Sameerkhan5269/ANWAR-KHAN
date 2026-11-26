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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.VerifiedUser
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.core.RolePermissions
import com.example.data.SyncConnectionState
import com.example.data.SyncStatus
import com.example.data.model.StaffRole
import com.example.data.model.StaffUser
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalSolarColors

@Composable
fun AdminTeamScreen(
    currentUser: StaffUser,
    staffUsers: List<StaffUser>,
    syncStatus: SyncStatus,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onForceSync: () -> Unit,
    onAddStaffUser: (loginId: String, pin: String, displayName: String, role: StaffRole) -> Unit,
    onDeleteStaffUser: (userId: String) -> Unit,
    onLogout: () -> Unit
) {
    val solarColors = LocalSolarColors.current
    val canManageUsers = RolePermissions.canManageUsers(currentUser.role)
    var showAddStaffDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Active Profile Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DeepNavy),
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
                            Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = solarColors.solarAmber, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(currentUser.displayName, style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                                Text(
                                    "${currentUser.loginId} • ${currentUser.role.label}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    color = Color(0xFFCBD5E1)
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
                        color = Color(0xFFE2E8F0)
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onLogout,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
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
                    OutlinedButton(
                        onClick = onForceSync,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("force_cloud_sync_btn")
                    ) {
                        Icon(Icons.Filled.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Sync Now with Supabase Cloud")
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
                                "ID: ${staff.loginId} • PIN: ${staff.pin} • ${staff.role.label}",
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

        // Company Identity Footer
        item {
            HorizontalDivider()
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text("NB Engineering Works and Electricals", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Road No. 5, Madri Industrial Area, Udaipur, Rajasthan",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showAddStaffDialog) {
        AddStaffUserDialog(
            onDismiss = { showAddStaffDialog = false },
            onSave = { id, pin, name, role ->
                onAddStaffUser(id, pin, name, role)
                showAddStaffDialog = false
            }
        )
    }
}

@Composable
private fun AddStaffUserDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String, StaffRole) -> Unit
) {
    var loginId by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(StaffRole.SUPERVISOR) }

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
                        onClick = { role = r },
                        label = { Text(r.label) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(loginId, pin, displayName, role) },
                enabled = loginId.isNotBlank() && pin.length >= 4
            ) { Text("Create & Sync") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
