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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SolarPower
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.ui.screens.BulkLoanImportDialog
import com.example.ui.screens.BusyAccountingScreen
import com.example.ui.screens.InventoryScreen
import com.example.ui.screens.LiveRemindersDrawerDialog
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
                                if (RolePermissions.canViewLoanSection(currentUser.role)) {
                                    val alertCount = state.liveReminders.size
                                    IconButton(
                                        onClick = { viewModel.setRemindersDrawerVisible(true) },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("topbar_reminders_bell")
                                    ) {
                                        BadgedBox(
                                            badge = {
                                                if (alertCount > 0) {
                                                    Badge(
                                                        containerColor = Color(0xFFF59E0B),
                                                        contentColor = Color.Black
                                                    ) {
                                                        Text(
                                                            text = if (alertCount > 99) "99+" else "$alertCount",
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Notifications,
                                                contentDescription = "Today's Action & Payment Summary ($alertCount)",
                                                tint = if (alertCount > 0) Color(0xFFFDE68A) else Color.White
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(4.dp))
                                }

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
                                onApproveAndConvertOrder = { existingPartyId, customerName, customerMobile, siteLocation, consumerNo, capacityKw, roofType, quotedRatePerKw, dealValue, subsidyAmount, surveyNotes, txnId ->
                                    viewModel.approveProposalAndConvertOrder(
                                        existingPartyId, customerName, customerMobile, siteLocation, consumerNo,
                                        capacityKw, roofType, quotedRatePerKw, dealValue, subsidyAmount, surveyNotes, txnId
                                    )
                                },
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
                                onAddSitePurchase = { desc, qty, vendor, amt, source, photoUri, txnId ->
                                    viewModel.addSiteMaterialPurchase(
                                        selectedParty.id, desc, qty, vendor, amt, source, photoUri, txnId
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
                                    onCreateParty = { name, mob, addr, city, kno, kw, src, refName, refPhone, leadDate, roofType, rate, followUp, surveyNotes, leadSt, valInr, l1, l2, sub, pct, div, txnId ->
                                        viewModel.createParty(
                                            name, mob, addr, city, kno, kw, src, refName, refPhone, leadDate,
                                            roofType, rate, followUp, surveyNotes, leadSt, valInr, l1, l2, sub, pct, div, txnId
                                        )
                                    },
                                    onDeleteParty = viewModel::deleteParty,
                                    onPreviewWhatsApp = viewModel::previewWhatsAppMessage,
                                    onApplyWhatsApp = { parsed, partyId, txnId ->
                                        viewModel.applyWhatsAppUpdate(parsed, partyId, txnId)
                                    },
                                    onPreviewLekhaJokha = viewModel::previewBulkLekhaJokha,
                                    onApplyLekhaJokha = { lines, txnId ->
                                        viewModel.applyBulkLekhaJokha(lines, txnId)
                                    },
                                    canBulkImportLoanParties = RolePermissions.canBulkImportLoanParties(
                                        currentUser.role,
                                        currentUser.assignedDivision
                                    ),
                                    canViewReminders = RolePermissions.canViewLoanSection(currentUser.role),
                                    reminders = state.liveReminders,
                                    onOpenBulkLoanImporter = viewModel::openBulkLoanImporter,
                                    onOpenRemindersDrawer = { viewModel.setRemindersDrawerVisible(true) },
                                    onTickMcoOk = viewModel::tickPartyMcoOk
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
                                    onApproveAndConvertOrder = { existingPartyId, customerName, customerMobile, siteLocation, consumerNo, capacityKw, roofType, quotedRatePerKw, dealValue, subsidyAmount, surveyNotes, txnId ->
                                        viewModel.approveProposalAndConvertOrder(
                                            existingPartyId, customerName, customerMobile, siteLocation, consumerNo,
                                            capacityKw, roofType, quotedRatePerKw, dealValue, subsidyAmount, surveyNotes, txnId
                                        )
                                    },
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
                                    onAddDcrPanelsBatch = { serials, brand, watt, sup, inv, txnId ->
                                        viewModel.addDcrPanelsBatch(serials, brand, watt, sup, inv, txnId)
                                    },
                                    onAllocatePanel = { panelId, party, txnId ->
                                        viewModel.allocatePanelToParty(panelId, party, txnId)
                                    },
                                    onAddMeter = { serial, type, makePhase, status, txnId ->
                                        viewModel.addSolarMeter(serial, type, makePhase, status, txnId)
                                    },
                                    onUpdateMeter = { meterId, status, party, txnId ->
                                        viewModel.updateMeterAllocation(meterId, status, party, txnId)
                                    },
                                    onAddRawStock = { matId, name, cat, unit, sup, qty, min, txnId ->
                                        viewModel.addRawMaterialStock(matId, name, cat, unit, sup, qty, min, txnId)
                                    },
                                    onIssueRawMaterial = { matId, party, qty, txnId ->
                                        viewModel.issueRawMaterialToSite(matId, party, qty, txnId)
                                    }
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
                                    onAddAccount = { name, grp, opBal, gstin, cc, txnId ->
                                        viewModel.addBusyAccount(name, grp, opBal, gstin, cc, txnId)
                                    },
                                    onAddItem = { name, hsn, unit, gst, sRate, pRate, stock, txnId ->
                                        viewModel.addBusyItem(name, hsn, unit, gst, sRate, pRate, stock, txnId)
                                    },
                                    onPostVoucher = { vt, d, p, c, cc, igst, tax, gst, refT, refNo, utr, paidBy, nar, li, txnId ->
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
                                            lineItems = li,
                                            txnId = txnId
                                        )
                                    },
                                    onDeleteVoucher = viewModel::deleteVoucher,
                                    onMarkReferralPaid = viewModel::markReferralPaid,
                                    onScanBankUri = viewModel::scanBankStatementFromUri,
                                    onScanBankText = viewModel::scanBankStatementFromText,
                                    onToggleBankRow = viewModel::toggleBankRowSelection,
                                    onPostReviewedBank = { txnId -> viewModel.postReviewedBankVouchers(txnId) },
                                    onScanPurchaseBillUri = viewModel::scanPurchaseBillFromUri,
                                    onPostReviewedBill = { bill, cc, txnId ->
                                        viewModel.postReviewedPurchaseBill(bill, cc, txnId)
                                    },
                                    onResetAi = viewModel::resetAiScanner,
                                    onShowMessage = viewModel::showToast
                                )

                                MainNavTab.TEAM_SYNC -> AdminTeamScreen(
                                    currentUser = currentUser,
                                    staffUsers = state.snapshot.staffUsers,
                                    teamChat = visibleTeamChat,
                                    syncStatus = state.syncStatus,
                                    storageHealthStats = state.storageHealthStats,
                                    lastBackupDateIso = state.lastBackupDateIso,
                                    backupOverdueBanner = state.backupOverdueBanner,
                                    backupMasterUnlocked = state.backupMasterUnlocked,
                                    isBackupBusy = state.isBackupBusy,
                                    isFlushingQueue = state.isFlushingQueue,
                                    pendingRestorePreview = state.pendingRestorePreview,
                                    healthReport = state.healthReport,
                                    isRunningSelfTest = state.isRunningSelfTest,
                                    isDarkTheme = useDark,
                                    onToggleTheme = { viewModel.toggleDarkTheme(useDark) },
                                    onForceSync = viewModel::forceCloudSync,
                                    onFlushQueueNow = viewModel::flushOfflineQueueNow,
                                    onClearLocalCache = viewModel::clearLocalCacheCloudSafe,
                                    onUnlockBackupKey = viewModel::unlockBackupWithMasterKey,
                                    onLockBackupPanel = viewModel::lockBackupPanel,
                                    onDownloadFullBackupJson = viewModel::downloadFullCompanyBackupJson,
                                    onExportMasterExcel = viewModel::exportMasterExcelWorkbook,
                                    onExportMasterCsv = viewModel::exportMasterSummaryCsv,
                                    onPickRestoreFile = viewModel::previewRestoreFromBackupUri,
                                    onConfirmRestoreBackup = viewModel::confirmRestoreFromBackup,
                                    onDismissRestorePreview = viewModel::dismissRestorePreview,
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

            if (state.showBulkLoanImporter) {
                BulkLoanImportDialog(
                    scanState = state.bulkLoanScanState,
                    onDismiss = viewModel::dismissBulkLoanImporter,
                    onPickFile = viewModel::importLoanPartiesFromFile,
                    onParseRawText = viewModel::importLoanPartiesFromText,
                    onUpdateRow = viewModel::updateBulkLoanRow,
                    onAddBlankRow = viewModel::addBlankBulkLoanRow,
                    onRemoveRow = viewModel::removeBulkLoanRow,
                    onCommitParties = { txnId -> viewModel.commitBulkLoanParties(txnId) },
                    onResetScan = viewModel::resetBulkLoanScanState,
                    onShowMessage = viewModel::showToast
                )
            }

            if (state.showRemindersDrawer) {
                LiveRemindersDrawerDialog(
                    reminders = state.liveReminders,
                    canBulkImport = RolePermissions.canBulkImportLoanParties(
                        currentUser.role,
                        currentUser.assignedDivision
                    ),
                    onDismiss = { viewModel.setRemindersDrawerVisible(false) },
                    onOpenBulkImport = viewModel::openBulkLoanImporter,
                    onTickMcoOk = viewModel::tickPartyMcoOk,
                    onOpenParty = viewModel::openPartyDetail
                )
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
