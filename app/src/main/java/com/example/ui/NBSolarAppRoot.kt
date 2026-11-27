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
import androidx.compose.material.icons.filled.Architecture
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
import com.example.data.model.StaffRole
import com.example.ui.components.CompanyCircularLogo
import com.example.ui.screens.AdminTeamScreen
import com.example.ui.screens.BusyAccountingScreen
import com.example.ui.screens.InventoryScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.PartyDetailScreen
import com.example.ui.screens.PipelineScreen
import com.example.ui.screens.SiteSurvey3DCostingScreen
import com.example.ui.theme.LimeGreen
import com.example.ui.theme.LocalSolarColors
import com.example.ui.theme.MaroonPrimary
import com.example.ui.theme.NBSolarTheme
import com.example.ui.viewmodel.MainNavTab
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NBSolarAppRoot(viewModel: MainViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val useDark = state.darkThemeOverride ?: false
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

        val visibleTabs = remember(currentUser.role) {
            buildList {
                add(MainNavTab.PIPELINE)
                add(MainNavTab.SURVEY_3D)
                add(MainNavTab.INVENTORY)
                // STRICT PRIVACY: Accounting tab ONLY rendered for Owner & Accounts Manager!
                if (RolePermissions.canAccessAccounting(currentUser.role)) {
                    add(MainNavTab.BUSY_ACCOUNTING)
                }
                add(MainNavTab.TEAM_SYNC)
            }
        }

        val visibleParties = state.visibleParties
        val visibleTeamChat = state.visibleTeamChat

        val selectedParty = remember(state.selectedPartyId, visibleParties) {
            state.selectedPartyId?.let { id -> visibleParties.firstOrNull { it.id == id } }
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
                                containerColor = MaroonPrimary,
                                titleContentColor = Color.White
                            ),
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CompanyCircularLogo(size = 38.dp, borderColor = LimeGreen)
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "NB Solar & Wind ERP",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "${currentUser.role.badge} • NB Engineering Works",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFFDCEDC8)
                                        )
                                    }
                                }
                            },
                            actions = {
                                // Live Supabase Sync Pill (Lime Green #7CB342 Live when connected)
                                val pillBg = when (state.syncStatus.state) {
                                    SyncConnectionState.LIVE -> LimeGreen
                                    SyncConnectionState.SYNCING -> Color(0xFF6B1412)
                                    SyncConnectionState.OFFLINE -> Color(0xFF4A1210)
                                }
                                val borderColor = when (state.syncStatus.state) {
                                    SyncConnectionState.LIVE -> Color(0xFFDCEDC8)
                                    SyncConnectionState.SYNCING -> Color(0xFFFDE68A)
                                    SyncConnectionState.OFFLINE -> Color(0xFFFCA5A5)
                                }
                                val dotColor = when (state.syncStatus.state) {
                                    SyncConnectionState.LIVE -> Color.White
                                    SyncConnectionState.SYNCING -> Color(0xFFFDE68A)
                                    SyncConnectionState.OFFLINE -> Color(0xFFF87171)
                                }
                                val label = when (state.syncStatus.state) {
                                    SyncConnectionState.LIVE -> "Live"
                                    SyncConnectionState.SYNCING -> "Sync…"
                                    SyncConnectionState.OFFLINE -> "Offline"
                                }
                                Surface(
                                    color = pillBg,
                                    shape = RoundedCornerShape(999.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
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
                                            fontWeight = FontWeight.Bold,
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
                            val imprestBal = if (currentUser.role == StaffRole.SUPERVISOR) {
                                currentUser.imprestBalance
                            } else {
                                state.snapshot.staffUsers.firstOrNull { it.role == StaffRole.SUPERVISOR }?.imprestBalance ?: 0.0
                            }
                            PartyDetailScreen(
                                party = selectedParty,
                                role = currentUser.role,
                                currentUserImprestBalance = imprestBal,
                                allocatedPanels = state.snapshot.dcrPanels.filter { it.linkedPartyId == selectedParty.id },
                                allocatedMeters = state.snapshot.solarMeters.filter { it.installedPartyId == selectedParty.id },
                                rawMaterials = state.snapshot.rawMaterials,
                                partyKharcha = state.snapshot.siteKharcha.filter { it.partyId == selectedParty.id },
                                busyVouchers = state.snapshot.busyVouchers,
                                busyItems = state.snapshot.busyItems,
                                aiPhotoState = state.aiPhotoState,
                                onPickRooftopPhoto = viewModel::setRooftopBeforePhoto,
                                onPickInverterPhoto = viewModel::setInverterBeforePhoto,
                                onGenerateRooftopAi = viewModel::generateAiRooftopPlacement,
                                onGenerateInverterAi = viewModel::generateAiInverterPlacement,
                                onClearAiError = viewModel::clearAiPhotoErrors,
                                onApplyQuoteToParty = viewModel::applyQuoteFromSurvey,
                                onApproveAndConvertOrder = viewModel::approveProposalAndConvertOrder,
                                onShowMessage = viewModel::showToast,
                                onBack = { viewModel.openPartyDetail(null) },
                                onToggleStage = { stageId -> viewModel.toggleWorkflowStage(selectedParty.id, stageId) },
                                onAddSiteNote = { note -> viewModel.addPartySiteNote(selectedParty.id, note) },
                                onUploadDocument = { uri, title, cat, notes ->
                                    viewModel.uploadPartyDocument(selectedParty.id, uri, title, cat, notes)
                                },
                                onUpdateLeadDetails = { mobile, city, kno, kw, src, refName, refPhone, leadDate, roofType, rate, dealVal, followUp, surveyNotes, leadSt, div ->
                                    viewModel.updatePartyLeadDetails(
                                        selectedParty.id, mobile, city, kno, kw, src, refName, refPhone,
                                        leadDate, roofType, rate, dealVal, followUp, surveyNotes, leadSt, div
                                    )
                                },
                                onAddSitePurchase = { desc, qty, vendor, amt, source, photoUri ->
                                    viewModel.addSiteMaterialPurchase(
                                        selectedParty.id, desc, qty, vendor, amt, source, photoUri
                                    )
                                }
                            )
                        } else {
                            when (state.selectedTab) {
                                MainNavTab.PIPELINE -> PipelineScreen(
                                    role = currentUser.role,
                                    parties = visibleParties,
                                    selectedSource = state.selectedSourceFilter,
                                    selectedDivision = state.selectedDivisionFilter,
                                    searchQuery = state.pipelineSearchQuery,
                                    onSelectSource = viewModel::setSourceFilter,
                                    onSelectDivision = viewModel::setDivisionFilter,
                                    onSearchChange = viewModel::setPipelineSearch,
                                    onOpenParty = viewModel::openPartyDetail,
                                    onCreateParty = { name, mob, addr, city, kno, kw, src, refName, refPhone, leadDate, roofType, rate, followUp, surveyNotes, leadSt, valInr, l1, l2, sub, pct, div ->
                                        viewModel.createParty(
                                            name, mob, addr, city, kno, kw, src, refName, refPhone, leadDate,
                                            roofType, rate, followUp, surveyNotes, leadSt, valInr, l1, l2, sub, pct, div
                                        )
                                    },
                                    onDeleteParty = viewModel::deleteParty,
                                    onPreviewWhatsApp = viewModel::previewWhatsAppMessage,
                                    onApplyWhatsApp = viewModel::applyWhatsAppUpdate,
                                    onPreviewLekhaJokha = viewModel::previewBulkLekhaJokha,
                                    onApplyLekhaJokha = viewModel::applyBulkLekhaJokha
                                )

                                MainNavTab.SURVEY_3D -> SiteSurvey3DCostingScreen(
                                    role = currentUser.role,
                                    parties = visibleParties,
                                    busyVouchers = state.snapshot.busyVouchers,
                                    busyItems = state.snapshot.busyItems,
                                    linkedParty = null,
                                    aiPhotoState = state.aiPhotoState,
                                    onPickRooftopPhoto = viewModel::setRooftopBeforePhoto,
                                    onPickInverterPhoto = viewModel::setInverterBeforePhoto,
                                    onGenerateRooftopAi = viewModel::generateAiRooftopPlacement,
                                    onGenerateInverterAi = viewModel::generateAiInverterPlacement,
                                    onClearAiError = viewModel::clearAiPhotoErrors,
                                    onApplyQuoteToParty = viewModel::applyQuoteFromSurvey,
                                    onApproveAndConvertOrder = viewModel::approveProposalAndConvertOrder,
                                    onShowMessage = viewModel::showToast
                                )

                                MainNavTab.INVENTORY -> InventoryScreen(
                                    role = currentUser.role,
                                    searchQuery = state.inventorySearchQuery,
                                    panels = state.snapshot.dcrPanels,
                                    meters = state.snapshot.solarMeters,
                                    rawMaterials = state.snapshot.rawMaterials,
                                    parties = visibleParties,
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
                                    dcrPanels = state.snapshot.dcrPanels,
                                    solarMeters = state.snapshot.solarMeters,
                                    rawMaterials = state.snapshot.rawMaterials,
                                    siteKharcha = state.snapshot.siteKharcha,
                                    aiState = state.aiScanState,
                                    onAddAccount = viewModel::addBusyAccount,
                                    onAddItem = viewModel::addBusyItem,
                                    onPostVoucher = { vt, d, p, c, cc, igst, tax, gst, refT, refNo, utr, paidBy, nar, li ->
                                        viewModel.postBusyVoucher(
                                            type = vt,
                                            date = d,
                                            partyAccount = p,
                                            contraAccount = c,
                                            costCenter = cc,
                                            isInterStateIgst = igst,
                                            taxableAmount = tax,
                                            gstPercent = gst,
                                            billRefType = refT,
                                            billRefNo = refNo,
                                            utrOrChequeNo = utr,
                                            paidBy = paidBy,
                                            narration = nar,
                                            lineItems = li
                                        )
                                    },
                                    onDeleteVoucher = viewModel::deleteVoucher,
                                    onMarkReferralPaid = viewModel::markReferralPaid,
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
                                    teamChat = visibleTeamChat,
                                    syncStatus = state.syncStatus,
                                    healthReport = state.healthReport,
                                    isRunningSelfTest = state.isRunningSelfTest,
                                    isDarkTheme = useDark,
                                    onToggleTheme = { viewModel.toggleDarkTheme(useDark) },
                                    onForceSync = viewModel::forceCloudSync,
                                    onRunSelfTest = viewModel::runAppSelfTest,
                                    onAddStaffUser = viewModel::addStaffUser,
                                    onDeleteStaffUser = viewModel::deleteStaffUser,
                                    onTopUpImprest = viewModel::topUpSupervisorImprest,
                                    onSendChatMessage = viewModel::sendTeamChatMessage,
                                    onShowToast = viewModel::showToast,
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
    MainNavTab.SURVEY_3D -> Icons.Filled.Architecture
    MainNavTab.INVENTORY -> Icons.Filled.Inventory2
    MainNavTab.BUSY_ACCOUNTING -> Icons.Filled.AccountBalanceWallet
    MainNavTab.TEAM_SYNC -> Icons.Filled.Groups
}
