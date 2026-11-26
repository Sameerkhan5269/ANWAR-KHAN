package com.example.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.SolarPower
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.RolePermissions
import com.example.core.configureAppCheck
import com.example.data.SyncConnectionState
import com.example.ui.screens.AdminTeamScreen
import com.example.ui.screens.BusyAccountingScreen
import com.example.ui.screens.InventoryScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.PartyDetailScreen
import com.example.ui.screens.PipelineScreen
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.LocalSolarColors
import com.example.ui.theme.NBSolarTheme
import com.example.ui.theme.SolarAmber
import com.example.ui.viewmodel.MainNavTab
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NBSolarAppRoot(viewModel: MainViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val useDark = state.darkThemeOverride ?: systemDark
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.toastMessage) {
        val msg = state.toastMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeToast()
        }
    }

    NBSolarTheme(darkTheme = useDark) {
        val currentUser = state.currentUser
        if (currentUser == null) {
            LoginScreen(
                staffUsers = state.snapshot.staffUsers,
                errorMessage = state.loginError,
                onLogin = viewModel::login
            )
            return@NBSolarTheme
        }

        val solarColors = LocalSolarColors.current
        val visibleTabs = remember(currentUser.role) {
            buildList {
                add(MainNavTab.PIPELINE)
                add(MainNavTab.INVENTORY)
                // STRICT PRIVACY: Accounting tab ONLY rendered for Owner & Accounts Manager!
                if (RolePermissions.canAccessAccounting(currentUser.role)) {
                    add(MainNavTab.BUSY_ACCOUNTING)
                }
                add(MainNavTab.TEAM_SYNC)
            }
        }

        val selectedParty = remember(state.selectedPartyId, state.snapshot.parties) {
            state.selectedPartyId?.let { id -> state.snapshot.parties.firstOrNull { it.id == id } }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isExpandedScreen = maxWidth >= 680.dp

            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    if (selectedParty == null) {
                        TopAppBar(
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = DeepNavy,
                                titleContentColor = Color.White
                            ),
                            title = {
                                Column {
                                    Text(
                                        text = "NB Solar ERP",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "${currentUser.role.badge} • Udaipur EPC",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SolarAmber
                                    )
                                }
                            },
                            actions = {
                                // Live Supabase Sync Pill
                                val dotColor = when (state.syncStatus.state) {
                                    SyncConnectionState.LIVE -> Color(0xFF34D399)
                                    SyncConnectionState.SYNCING -> SolarAmber
                                    SyncConnectionState.OFFLINE -> Color(0xFFF87171)
                                }
                                val label = when (state.syncStatus.state) {
                                    SyncConnectionState.LIVE -> "Live"
                                    SyncConnectionState.SYNCING -> "Sync…"
                                    SyncConnectionState.OFFLINE -> "Cache"
                                }
                                Surface(
                                    color = Color(0xFF1B2B4B),
                                    shape = RoundedCornerShape(999.dp),
                                    modifier = Modifier
                                        .padding(end = 10.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                        .clickable { viewModel.forceCloudSync() }
                                        .testTag("sync_status_pill")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(dotColor)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        )
                    }
                },
                bottomBar = {
                    if (!isExpandedScreen && selectedParty == null) {
                        NavigationBar {
                            visibleTabs.forEach { tab ->
                                NavigationBarItem(
                                    selected = state.selectedTab == tab,
                                    onClick = { viewModel.selectTab(tab) },
                                    icon = { Icon(tabIcon(tab), contentDescription = tab.label) },
                                    label = { Text(tab.label) },
                                    modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (isExpandedScreen && selectedParty == null) {
                        NavigationRail(modifier = Modifier.fillMaxHeight()) {
                            Spacer(Modifier.weight(0.2f))
                            visibleTabs.forEach { tab ->
                                NavigationRailItem(
                                    selected = state.selectedTab == tab,
                                    onClick = { viewModel.selectTab(tab) },
                                    icon = { Icon(tabIcon(tab), contentDescription = tab.label) },
                                    label = { Text(tab.label) },
                                    modifier = Modifier.testTag("rail_tab_${tab.name.lowercase()}")
                                )
                            }
                            Spacer(Modifier.weight(1f))
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        if (selectedParty != null) {
                            PartyDetailScreen(
                                party = selectedParty,
                                role = currentUser.role,
                                allocatedPanels = state.snapshot.dcrPanels.filter { it.linkedPartyId == selectedParty.id },
                                allocatedMeters = state.snapshot.solarMeters.filter { it.installedPartyId == selectedParty.id },
                                rawMaterials = state.snapshot.rawMaterials,
                                onBack = { viewModel.openPartyDetail(null) },
                                onToggleStage = { stageId -> viewModel.toggleWorkflowStage(selectedParty.id, stageId) },
                                onAddSiteNote = { note -> viewModel.addPartySiteNote(selectedParty.id, note) },
                                onUploadDocument = { uri, title, cat, notes ->
                                    viewModel.uploadPartyDocument(selectedParty.id, uri, title, cat, notes)
                                }
                            )
                        } else {
                            when (state.selectedTab) {
                                MainNavTab.PIPELINE -> PipelineScreen(
                                    role = currentUser.role,
                                    parties = state.snapshot.parties,
                                    selectedSource = state.selectedSourceFilter,
                                    searchQuery = state.pipelineSearchQuery,
                                    onSelectSource = viewModel::setSourceFilter,
                                    onSearchChange = viewModel::setPipelineSearch,
                                    onOpenParty = viewModel::openPartyDetail,
                                    onCreateParty = viewModel::createParty,
                                    onDeleteParty = viewModel::deleteParty,
                                    onPreviewWhatsApp = viewModel::previewWhatsAppMessage,
                                    onApplyWhatsApp = viewModel::applyWhatsAppUpdate
                                )

                                MainNavTab.INVENTORY -> InventoryScreen(
                                    role = currentUser.role,
                                    searchQuery = state.inventorySearchQuery,
                                    panels = state.snapshot.dcrPanels,
                                    meters = state.snapshot.solarMeters,
                                    rawMaterials = state.snapshot.rawMaterials,
                                    parties = state.snapshot.parties,
                                    onSearchChange = viewModel::setInventorySearch,
                                    onAddDcrPanelsBatch = viewModel::addDcrPanelsBatch,
                                    onAllocatePanel = viewModel::allocatePanelToParty,
                                    onAddMeter = viewModel::addSolarMeter,
                                    onUpdateMeter = viewModel::updateMeterAllocation,
                                    onAddRawStock = viewModel::addRawMaterialStock,
                                    onIssueRawMaterial = viewModel::issueRawMaterialToSite
                                )

                                MainNavTab.BUSY_ACCOUNTING -> BusyAccountingScreen(
                                    role = currentUser.role,
                                    accounts = state.snapshot.busyAccounts,
                                    items = state.snapshot.busyItems,
                                    vouchers = state.snapshot.busyVouchers,
                                    parties = state.snapshot.parties,
                                    aiState = state.aiScanState,
                                    onAddAccount = viewModel::addBusyAccount,
                                    onAddItem = viewModel::addBusyItem,
                                    onPostVoucher = viewModel::postBusyVoucher,
                                    onDeleteVoucher = viewModel::deleteVoucher,
                                    onScanBankUri = viewModel::scanBankStatementFromUri,
                                    onScanBankText = viewModel::scanBankStatementFromText,
                                    onToggleBankRow = viewModel::toggleBankRowSelection,
                                    onPostReviewedBank = viewModel::postReviewedBankVouchers,
                                    onScanPurchaseBillUri = viewModel::scanPurchaseBillFromUri,
                                    onPostReviewedBill = viewModel::postReviewedPurchaseBill,
                                    onResetAi = viewModel::resetAiScanner,
                                    onShowMessage = viewModel::showToast
                                )

                                MainNavTab.TEAM_SYNC -> AdminTeamScreen(
                                    currentUser = currentUser,
                                    staffUsers = state.snapshot.staffUsers,
                                    syncStatus = state.syncStatus,
                                    isDarkTheme = useDark,
                                    onToggleTheme = { viewModel.toggleDarkTheme(useDark) },
                                    onForceSync = viewModel::forceCloudSync,
                                    onAddStaffUser = viewModel::addStaffUser,
                                    onDeleteStaffUser = viewModel::deleteStaffUser,
                                    onLogout = viewModel::logout
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun tabIcon(tab: MainNavTab): ImageVector = when (tab) {
    MainNavTab.PIPELINE -> Icons.Filled.SolarPower
    MainNavTab.INVENTORY -> Icons.Filled.Inventory2
    MainNavTab.BUSY_ACCOUNTING -> Icons.Filled.AccountBalanceWallet
    MainNavTab.TEAM_SYNC -> Icons.Filled.Groups
}
