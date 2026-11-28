package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.ExportPrintHelper
import com.example.core.ImageCompressor
import com.example.core.RolePermissions
import com.example.core.nowTimestamp
import com.example.core.todayIsoDate
import com.example.data.BackupRestoreEngine
import com.example.data.BulkLoanImporter
import com.example.data.BusyAccountingEngine
import com.example.data.DuplicateTransactionGuard
import com.example.data.FirebaseAiPhotoRepository
import com.example.data.FirebaseAiScannerRepository
import com.example.data.LoanReminderEngine
import com.example.data.RealCostingCalculator
import com.example.data.StorageHealthStats
import com.example.data.SupabaseSyncRepository
import com.example.data.SyncStatus
import com.example.data.WhatsAppParser
import com.example.data.model.AppDatabaseSnapshot
import com.example.data.model.AppHealthReport
import com.example.data.model.BillRefType
import com.example.data.model.BulkLoanPartyRow
import com.example.data.model.BusyAccount
import com.example.data.model.BusyAccountGroup
import com.example.data.model.BusyItem
import com.example.data.model.BusyLineItem
import com.example.data.model.BusyVoucher
import com.example.data.model.BusyVoucherType
import com.example.data.model.CostingStructureType
import com.example.data.model.DcrPanel
import com.example.data.model.DiscomTestingStatus
import com.example.data.model.DocumentCategory
import com.example.data.model.HealthCheckItem
import com.example.data.model.LeadSource
import com.example.data.model.LeadStatus
import com.example.data.model.LoanReminderItem
import com.example.data.model.MaterialIssueLog
import com.example.data.model.MeterType
import com.example.data.model.PanelStatus
import com.example.data.model.ParsedBankRow
import com.example.data.model.ParsedLekhaJokhaLine
import com.example.data.model.ParsedPurchaseBill
import com.example.data.model.ParsedWhatsAppUpdate
import com.example.data.model.PartyDocument
import com.example.data.model.PartyProject
import com.example.data.model.ProjectDivision
import com.example.data.model.ProposalFormat
import com.example.data.model.RawMaterial
import com.example.data.model.RoofStructureType
import com.example.data.model.SiteKharchaEntry
import com.example.data.model.SiteLogEntry
import com.example.data.model.SiteMaterialPurchase
import com.example.data.model.SitePurchasePaymentSource
import com.example.data.model.SiteSurveyDimensions
import com.example.data.model.SolarMeter
import com.example.data.model.SolarSystemType
import com.example.data.model.StaffRole
import com.example.data.model.StaffUser
import com.example.data.model.TeamChatMessage
import com.example.data.model.defaultWorkflowStages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private val Context.sessionDataStore by preferencesDataStore(name = "nb_solar_session")
private val KEY_LOGGED_IN_ID = stringPreferencesKey("logged_in_login_id")
private val KEY_LAST_BACKUP_ISO = stringPreferencesKey("last_backup_date_iso")

enum class MainNavTab(val label: String) {
    PIPELINE("Pipeline"),
    SURVEY_3D("3D & Cost"),
    INVENTORY("Inventory"),
    BUSY_ACCOUNTING("BUSY 21"),
    TEAM_SYNC("Team & Sync")
}

data class AiPhotoPlacementUiState(
    val rooftopBefore: Bitmap? = null,
    val rooftopAfter: Bitmap? = null,
    val rooftopLoading: Boolean = false,
    val rooftopError: String? = null,
    val inverterBefore: Bitmap? = null,
    val inverterAfter: Bitmap? = null,
    val inverterLoading: Boolean = false,
    val inverterError: String? = null
)

sealed interface AiScanUiState {
    data object Idle : AiScanUiState
    data class Scanning(val label: String) : AiScanUiState
    data class BankReview(val rows: List<ParsedBankRow>, val bankAccountId: String) : AiScanUiState
    data class BillReview(val bill: ParsedPurchaseBill) : AiScanUiState
    data class Error(val message: String) : AiScanUiState
}

sealed interface BulkLoanScanUiState {
    data object Idle : BulkLoanScanUiState
    data class Parsing(val label: String) : BulkLoanScanUiState
    data class Review(val rows: List<BulkLoanPartyRow>, val sourceLabel: String = "") : BulkLoanScanUiState
    data class Error(val message: String) : BulkLoanScanUiState
}

data class MainUiState(
    val currentUser: StaffUser? = null,
    val loginError: String? = null,
    val selectedTab: MainNavTab = MainNavTab.PIPELINE,
    val selectedPartyId: String? = null,
    val selectedSourceFilter: LeadSource? = null,
    val selectedDivisionFilter: ProjectDivision? = null,
    val pipelineSearchQuery: String = "",
    val inventorySearchQuery: String = "",
    val snapshot: AppDatabaseSnapshot = AppDatabaseSnapshot(),
    val syncStatus: SyncStatus = SyncStatus(),
    val toastMessage: String? = null,
    val aiScanState: AiScanUiState = AiScanUiState.Idle,
    val aiPhotoState: AiPhotoPlacementUiState = AiPhotoPlacementUiState(),
    val darkThemeOverride: Boolean? = null,
    val healthReport: AppHealthReport? = null,
    val isRunningSelfTest: Boolean = false,
    val showBulkLoanImporter: Boolean = false,
    val bulkLoanScanState: BulkLoanScanUiState = BulkLoanScanUiState.Idle,
    val showRemindersDrawer: Boolean = false,
    val liveReminders: List<LoanReminderItem> = emptyList(),
    val storageHealthStats: StorageHealthStats = StorageHealthStats(),
    val lastBackupDateIso: String = "",
    val backupOverdueBanner: String? = null,
    val pendingRestorePreview: BackupRestoreEngine.BackupPreview? = null,
    val isBackupBusy: Boolean = false,
    val isFlushingQueue: Boolean = false,
    val backupMasterUnlocked: Boolean = false
) {
    val visibleParties: List<PartyProject>
        get() {
            val assigned = currentUser?.assignedDivision ?: return snapshot.parties
            return snapshot.parties.filter { it.division == assigned }
        }

    val visibleTeamChat: List<TeamChatMessage>
        get() {
            val assigned = currentUser?.assignedDivision ?: return snapshot.teamChat
            return snapshot.teamChat.filter { it.division == null || it.division == assigned }
        }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val syncRepo = SupabaseSyncRepository(application.applicationContext)
    private val aiScanner = FirebaseAiScannerRepository()
    private val aiPhotoRepo = FirebaseAiPhotoRepository()

    private val _currentUser = MutableStateFlow<StaffUser?>(null)
    private val _loginError = MutableStateFlow<String?>(null)
    private val _selectedTab = MutableStateFlow(MainNavTab.PIPELINE)
    private val _selectedPartyId = MutableStateFlow<String?>(null)
    private val _selectedSourceFilter = MutableStateFlow<LeadSource?>(null)
    private val _selectedDivisionFilter = MutableStateFlow<ProjectDivision?>(null)
    private val _pipelineSearch = MutableStateFlow("")
    private val _inventorySearch = MutableStateFlow("")
    private val _toastMessage = MutableStateFlow<String?>(null)
    private val _aiScanState = MutableStateFlow<AiScanUiState>(AiScanUiState.Idle)
    private val _aiPhotoState = MutableStateFlow(AiPhotoPlacementUiState())
    private val _darkThemeOverride = MutableStateFlow<Boolean?>(null)
    private val _healthReport = MutableStateFlow<AppHealthReport?>(null)
    private val _isRunningSelfTest = MutableStateFlow(false)
    private val _showBulkLoanImporter = MutableStateFlow(false)
    private val _bulkLoanScanState = MutableStateFlow<BulkLoanScanUiState>(BulkLoanScanUiState.Idle)
    private val _showRemindersDrawer = MutableStateFlow(false)
    private val _lastBackupDateIso = MutableStateFlow("")
    private val _pendingRestorePreview = MutableStateFlow<BackupRestoreEngine.BackupPreview?>(null)
    private val _isBackupBusy = MutableStateFlow(false)
    private val _isFlushingQueue = MutableStateFlow(false)
    private val _backupMasterUnlocked = MutableStateFlow(false)
    private val _healthRefreshTick = MutableStateFlow(0L)
    private val txnGuard = DuplicateTransactionGuard()
    @Volatile
    private var hasAutoShownLaunchSummaryForUserId: String? = null

    private val navAndFilterFlow = combine(
        _currentUser,
        _loginError,
        _selectedTab,
        _selectedPartyId,
        _selectedSourceFilter
    ) { user, err, tab, partyId, source ->
        NavFilterTuple(user, err, tab, partyId, source)
    }

    private val searchAndToastFlow = combine(
        _pipelineSearch,
        _inventorySearch,
        _toastMessage,
        _aiScanState,
        _darkThemeOverride,
        _selectedDivisionFilter
    ) { arr ->
        SearchToastTuple(
            pSearch = arr[0] as String,
            iSearch = arr[1] as String,
            toast = arr[2] as String?,
            aiState = arr[3] as AiScanUiState,
            darkOverride = arr[4] as Boolean?,
            division = arr[5] as ProjectDivision?
        )
    }

    private val diagnosticsFlow = combine(
        _aiPhotoState,
        _healthReport,
        _isRunningSelfTest,
        _showBulkLoanImporter,
        _bulkLoanScanState,
        _showRemindersDrawer
    ) { arr ->
        DiagnosticsBundle(
            photoState = arr[0] as AiPhotoPlacementUiState,
            healthReport = arr[1] as AppHealthReport?,
            isRunningSelfTest = arr[2] as Boolean,
            showBulkLoanImporter = arr[3] as Boolean,
            bulkLoanScanState = arr[4] as BulkLoanScanUiState,
            showRemindersDrawer = arr[5] as Boolean
        )
    }

    private val backupSafetyFlow = combine(
        _lastBackupDateIso,
        _pendingRestorePreview,
        _isBackupBusy,
        _isFlushingQueue,
        _backupMasterUnlocked
    ) { lastDate, restorePrev, backupBusy, flushing, unlocked ->
        BackupSafetyBundle(
            lastBackupDateIso = lastDate,
            pendingRestorePreview = restorePrev,
            isBackupBusy = backupBusy,
            isFlushingQueue = flushing,
            backupMasterUnlocked = unlocked
        )
    }

    private val coreSnapshotFlow = combine(
        syncRepo.snapshot,
        syncRepo.syncStatus,
        backupSafetyFlow,
        _healthRefreshTick
    ) { snap, sync, safety, tick ->
        CoreSnapshotTuple(snap, sync, safety, tick)
    }

    val uiState: StateFlow<MainUiState> = combine(
        navAndFilterFlow,
        searchAndToastFlow,
        diagnosticsFlow,
        coreSnapshotFlow
    ) { nav, extra, diag, core ->
        val snap = core.snapshot
        val sync = core.syncStatus
        val safety = core.safety

        // Enforce strict privacy: if current role cannot access accounting, force back to PIPELINE
        val safeTab = if (nav.tab == MainNavTab.BUSY_ACCOUNTING &&
            (nav.user == null || !RolePermissions.canAccessAccounting(nav.user.role))
        ) {
            MainNavTab.PIPELINE
        } else {
            nav.tab
        }

        // Keep logged-in user object fresh if updated in staff_users
        val refreshedUser = nav.user?.let { u ->
            snap.staffUsers.firstOrNull { it.loginId.equals(u.loginId, ignoreCase = true) } ?: u
        }

        // Enforce division isolation if user is assigned to Solar or Wind only
        val effectiveDivisionFilter = refreshedUser?.assignedDivision ?: extra.division

        val visibleForReminders = refreshedUser?.assignedDivision?.let { assigned ->
            snap.parties.filter { it.division == assigned }
        } ?: snap.parties
        val liveReminders = if (refreshedUser != null && RolePermissions.canViewLoanSection(refreshedUser.role)) {
            LoanReminderEngine.computeReminders(visibleForReminders)
        } else {
            emptyList()
        }

        // Show combined "Today's Action & Payment Summary" popup ONCE on app launch/login (no popup fatigue)
        if (refreshedUser != null &&
            liveReminders.isNotEmpty() &&
            hasAutoShownLaunchSummaryForUserId != refreshedUser.id
        ) {
            hasAutoShownLaunchSummaryForUserId = refreshedUser.id
            viewModelScope.launch {
                _showRemindersDrawer.value = true
            }
        }

        val overdueStatus = BackupRestoreEngine.computeBackupOverdueStatus(safety.lastBackupDateIso)
        val storageHealth = syncRepo.computeStorageHealth()

        MainUiState(
            currentUser = refreshedUser,
            loginError = nav.err,
            selectedTab = safeTab,
            selectedPartyId = nav.partyId,
            selectedSourceFilter = nav.source,
            selectedDivisionFilter = effectiveDivisionFilter,
            pipelineSearchQuery = extra.pSearch,
            inventorySearchQuery = extra.iSearch,
            snapshot = snap,
            syncStatus = sync,
            toastMessage = extra.toast,
            aiScanState = extra.aiState,
            aiPhotoState = diag.photoState,
            darkThemeOverride = extra.darkOverride,
            healthReport = diag.healthReport,
            isRunningSelfTest = diag.isRunningSelfTest,
            showBulkLoanImporter = diag.showBulkLoanImporter,
            bulkLoanScanState = diag.bulkLoanScanState,
            showRemindersDrawer = diag.showRemindersDrawer,
            liveReminders = liveReminders,
            storageHealthStats = storageHealth,
            lastBackupDateIso = safety.lastBackupDateIso,
            backupOverdueBanner = overdueStatus.bannerMessage,
            pendingRestorePreview = safety.pendingRestorePreview,
            isBackupBusy = safety.isBackupBusy,
            isFlushingQueue = safety.isFlushingQueue,
            backupMasterUnlocked = safety.backupMasterUnlocked && refreshedUser?.role == StaffRole.OWNER
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = MainUiState()
    )

    init {
        restoreSavedSession()
    }

    private fun restoreSavedSession() {
        viewModelScope.launch {
            try {
                val prefs = getApplication<Application>().sessionDataStore.data.first()
                _lastBackupDateIso.value = prefs[KEY_LAST_BACKUP_ISO].orEmpty()
                val savedLogin = prefs[KEY_LOGGED_IN_ID]
                if (!savedLogin.isNullOrBlank()) {
                    val user = syncRepo.snapshot.value.staffUsers.firstOrNull {
                        it.loginId.equals(savedLogin, ignoreCase = true)
                    }
                    if (user != null) {
                        _currentUser.value = user
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    // --- AUTH & NAVIGATION ---

    fun login(loginId: String, pin: String) {
        val cleanId = loginId.trim().lowercase()
        val cleanPin = pin.trim()
        val matched = syncRepo.snapshot.value.staffUsers.firstOrNull {
            it.loginId.trim().lowercase() == cleanId && it.pin.trim() == cleanPin
        }
        if (matched != null) {
            _currentUser.value = matched
            _loginError.value = null
            _selectedTab.value = MainNavTab.PIPELINE
            _selectedPartyId.value = null
            viewModelScope.launch {
                getApplication<Application>().sessionDataStore.edit {
                    it[KEY_LOGGED_IN_ID] = matched.loginId
                }
            }
        } else {
            _loginError.value = "Invalid Login ID or 4-digit PIN. Please check credentials."
        }
    }

    fun logout() {
        hasAutoShownLaunchSummaryForUserId = null
        _showRemindersDrawer.value = false
        _backupMasterUnlocked.value = false
        _pendingRestorePreview.value = null
        _currentUser.value = null
        _selectedPartyId.value = null
        _selectedTab.value = MainNavTab.PIPELINE
        _aiScanState.value = AiScanUiState.Idle
        viewModelScope.launch {
            getApplication<Application>().sessionDataStore.edit {
                it.remove(KEY_LOGGED_IN_ID)
            }
        }
    }

    fun selectTab(tab: MainNavTab) {
        val user = _currentUser.value ?: return
        if (tab == MainNavTab.BUSY_ACCOUNTING && !RolePermissions.canAccessAccounting(user.role)) {
            showToast("Access restricted: BUSY 21 Accounting is for Owner & Accounts only.")
            return
        }
        _selectedTab.value = tab
        _selectedPartyId.value = null
    }

    fun openPartyDetail(partyId: String?) {
        _selectedPartyId.value = partyId
    }

    fun setSourceFilter(source: LeadSource?) {
        _selectedSourceFilter.value = if (_selectedSourceFilter.value == source) null else source
    }

    fun setDivisionFilter(division: ProjectDivision?) {
        val actor = _currentUser.value ?: return
        if (!RolePermissions.canSwitchDivision(actor.role)) return
        _selectedDivisionFilter.value = if (_selectedDivisionFilter.value == division) null else division
    }

    fun setPipelineSearch(q: String) {
        _pipelineSearch.value = q
    }

    fun setInventorySearch(q: String) {
        _inventorySearch.value = q
    }

    fun toggleDarkTheme(currentlyDark: Boolean) {
        _darkThemeOverride.value = !currentlyDark
    }

    fun consumeToast() {
        _toastMessage.value = null
    }

    fun showToast(msg: String) {
        _toastMessage.value = msg
    }

    fun forceCloudSync() {
        viewModelScope.launch {
            val ok = syncRepo.pullAllFromCloud()
            val status = syncRepo.syncStatus.value
            val msg = when {
                ok -> "Live synced with Supabase (nb_solar_store)."
                status.needsRlsGrant -> "Supabase permission denied (42501). Open Team & Sync tab to copy the GRANT/RLS SQL."
                else -> "Unable to reach Supabase — retrying."
            }
            showToast(msg)
        }
    }

    // --- STAFF USER MANAGEMENT (OWNER ONLY) ---

    fun addStaffUser(
        loginId: String,
        pin: String,
        displayName: String,
        role: StaffRole,
        assignedDivision: ProjectDivision? = null
    ) {
        val actor = _currentUser.value ?: return
        if (!RolePermissions.canManageUsers(actor.role)) {
            showToast("Only Owner / CEO can create staff accounts.")
            return
        }
        val cleanId = loginId.trim().lowercase()
        if (cleanId.isBlank() || pin.length < 4) {
            showToast("Enter valid Login ID and 4-digit PIN.")
            return
        }
        val effectiveDivision = if (role == StaffRole.OWNER || role == StaffRole.ACCOUNTS) {
            null
        } else {
            assignedDivision
        }
        syncRepo.updateAndSyncKey(SupabaseSyncRepository.KEY_SECURITY_CONFIG) { snap ->
            val filtered = snap.staffUsers.filterNot { it.loginId.equals(cleanId, ignoreCase = true) }
            snap.copy(
                staffUsers = filtered + StaffUser(
                    loginId = cleanId,
                    pin = pin.trim(),
                    displayName = displayName.trim().ifBlank { cleanId },
                    role = role,
                    assignedDivision = effectiveDivision,
                    isBuiltIn = false
                )
            )
        }
        showToast("Staff account $cleanId created and synced.")
    }

    fun deleteStaffUser(userId: String) {
        val actor = _currentUser.value ?: return
        if (!RolePermissions.canManageUsers(actor.role)) return
        syncRepo.updateAndSyncKey(SupabaseSyncRepository.KEY_SECURITY_CONFIG) { snap ->
            snap.copy(staffUsers = snap.staffUsers.filterNot { it.id == userId && !it.isBuiltIn })
        }
        showToast("Staff user removed.")
    }

    fun topUpSupervisorImprest(staffId: String, amount: Double) {
        val actor = _currentUser.value ?: return
        if (!RolePermissions.canManageImprest(actor.role)) {
            showToast("Only Owner / Accounts can top up Imprest Cash.")
            return
        }
        if (amount <= 0.0) {
            showToast("Enter a positive Imprest top-up amount.")
            return
        }
        var targetName = ""
        syncRepo.updateAndSyncKey(SupabaseSyncRepository.KEY_SECURITY_CONFIG) { snap ->
            snap.copy(
                staffUsers = snap.staffUsers.map { u ->
                    if (u.id != staffId) u else {
                        targetName = u.displayName
                        u.copy(imprestBalance = u.imprestBalance + amount)
                    }
                }
            )
        }
        showToast("Added ₹${amount.toInt()} Imprest Cash to $targetName.")
    }

    // --- PARTY PIPELINE, LEAD DETAILS & 5 WORKFLOW SECTIONS ---

    fun createParty(
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
        loan1Amount: Double,
        loan2Amount: Double,
        subsidyAmount: Double,
        partnerCommissionPct: Double,
        division: ProjectDivision = ProjectDivision.SOLAR,
        txnId: String = ""
    ) {
        val actor = _currentUser.value ?: return
        if (!RolePermissions.canCreateParty(actor.role)) {
            showToast("Your role cannot create new party records.")
            return
        }
        if (name.isBlank()) {
            showToast("Party name is required.")
            return
        }
        val fp = "createParty|${name.trim().lowercase()}|${mobile.trim()}|$capacityKw"
        if (!txnGuard.tryClaim(txnId, fp)) return

        val partyId = UUID.randomUUID().toString()
        val resolvedValue = if (projectValue > 0.0) {
            projectValue
        } else if (quotedRatePerKw > 0.0) {
            quotedRatePerKw * capacityKw.coerceAtLeast(1.0)
        } else 0.0
        val cleanDate = leadDate.trim().ifBlank { todayIsoDate() }

        val resolvedDivision = actor.assignedDivision ?: division
        val newParty = PartyProject(
            id = partyId,
            partyName = name.trim(),
            mobile = mobile.trim(),
            address = address.trim().ifBlank { cityOrLocation.trim() },
            cityOrLocation = cityOrLocation.trim().ifBlank { address.trim() },
            consumerNumber = consumerNo.trim(),
            capacityKw = capacityKw.coerceAtLeast(1.0),
            division = resolvedDivision,
            leadSource = leadSource,
            referrerName = referrerName.trim(),
            referrerPhone = referrerPhone.trim(),
            leadDate = cleanDate,
            roofStructureType = roofStructureType,
            quotedRatePerKw = quotedRatePerKw.coerceAtLeast(0.0),
            nextFollowUpDate = nextFollowUpDate.trim(),
            siteSurveyNotes = siteSurveyNotes.trim(),
            leadStatus = leadStatus,
            projectValue = resolvedValue.coerceAtLeast(0.0),
            receivedAmount = 0.0,
            loan1Amount = loan1Amount.coerceAtLeast(0.0),
            loan2Amount = loan2Amount.coerceAtLeast(0.0),
            subsidyAmount = subsidyAmount.coerceAtLeast(0.0),
            partnerCommissionPct = partnerCommissionPct.coerceIn(0.0, 50.0),
            createdDate = cleanDate,
            stages = defaultWorkflowStages(),
            siteLogs = listOf(
                SiteLogEntry(
                    timestamp = nowTimestamp(),
                    author = actor.displayName,
                    message = "Lead/Party created (${leadStatus.label}) under ${leadSource.label} • ${capacityKw} kW ${roofStructureType.label}"
                )
            )
        )

        syncRepo.updateAndSyncKey(SupabaseSyncRepository.KEY_PARTIES) { snap ->
            snap.copy(parties = listOf(newParty) + snap.parties)
        }
        if (loan1Amount > 0.0 || loan2Amount > 0.0 || subsidyAmount > 0.0) {
            syncRepo.updateAndSyncKey(SupabaseSyncRepository.KEY_LOANS_MCO) { it }
        }

        // Automatically create a matching Sundry Debtor ledger in BUSY 21 Accounts!
        syncRepo.updateAndSyncKey(SupabaseSyncRepository.KEY_BUSY_VOUCHERS) { snap ->
            val exists = snap.busyAccounts.any { it.name.equals(newParty.partyName, ignoreCase = true) }
            if (exists) snap else snap.copy(
                busyAccounts = snap.busyAccounts + BusyAccount(
                    name = newParty.partyName,
                    group = BusyAccountGroup.SUNDRY_DEBTORS,
                    openingBalance = 0.0,
                    mobile = newParty.mobile,
                    linkedPartyId = partyId,
                    defaultCostCenter = leadSource
                )
            )
        }

        showToast("Party '${newParty.partyName}' created & synced.")
    }

    fun updatePartyLeadDetails(
        partyId: String,
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
        division: ProjectDivision? = null
    ) {
        val actor = _currentUser.value ?: return
        syncRepo.updateAndSyncKey(SupabaseSyncRepository.KEY_PARTIES) { snap ->
            snap.copy(
                parties = snap.parties.map { p ->
                    if (p.id != partyId) p else p.copy(
                        mobile = mobile.trim(),
                        cityOrLocation = cityOrLocation.trim(),
                        address = cityOrLocation.trim().ifBlank { p.address },
                        consumerNumber = consumerNo.trim(),
                        capacityKw = capacityKw.coerceAtLeast(1.0),
                        division = division ?: p.division,
                        leadSource = leadSource,
                        referrerName = referrerName.trim(),
                        referrerPhone = referrerPhone.trim(),
                        leadDate = leadDate.trim().ifBlank { p.leadDate },
                        roofStructureType = roofStructureType,
                        quotedRatePerKw = quotedRatePerKw.coerceAtLeast(0.0),
                        projectValue = if (RolePermissions.canViewFinancials(actor.role)) dealValue.coerceAtLeast(0.0) else p.projectValue,
                        nextFollowUpDate = nextFollowUpDate.trim(),
                        siteSurveyNotes = siteSurveyNotes.trim(),
                        leadStatus = leadStatus,
                        siteLogs = listOf(
                            SiteLogEntry(
                                timestamp = nowTimestamp(),
                                author = actor.displayName,
                                message = "Lead updated: ${leadStatus.label}" +
                                    if (nextFollowUpDate.isNotBlank()) " • Follow-up: $nextFollowUpDate" else ""
                            )
                        ) + p.siteLogs
                    )
                }
            )
        }
        showToast("Lead details saved & synced.")
    }

    fun applyQuoteFromSurvey(
        partyId: String,
        capacityKw: Double,
        roofType: RoofStructureType,
        quotedRatePerKw: Double,
        dealValue: Double,
        surveyNotes: String
    ) {
        val actor = _currentUser.value ?: return
        syncRepo.updateAndSyncKey(SupabaseSyncRepository.KEY_PARTIES) { snap ->
            snap.copy(
                parties = snap.parties.map { p ->
                    if (p.id != partyId) p else p.copy(
                        capacityKw = capacityKw.coerceAtLeast(1.0),
                        roofStructureType = roofType,
                        quotedRatePerKw = if (RolePermissions.canViewFinancials(actor.role)) quotedRatePerKw else p.quotedRatePerKw,
                        projectValue = if (RolePermissions.canViewFinancials(actor.role)) dealValue else p.projectValue,
                        siteSurveyNotes = surveyNotes,
                        siteLogs = listOf(
                            SiteLogEntry(
                                timestamp = nowTimestamp(),
                                author = actor.displayName,
                                message = "Survey & Real Costing Applied: $surveyNotes"
                            )
                        ) + p.siteLogs
                    )
                }
            )
        }
        showToast("Survey specs & real costing applied to party record.")
    }

    fun approveProposalAndConvertOrder(
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
        surveyNotes: String,
        txnId: String = ""
    ) {
        val actor = _currentUser.value ?: return
        val cleanName = customerName.trim().ifBlank { "Valued Solar Consumer" }
        val fp = "approveOrder|${existingPartyId.orEmpty()}|${cleanName.lowercase()}|$dealValue"
        if (!txnGuard.tryClaim(txnId, fp)) return
        val division = actor.assignedDivision ?: ProjectDivision.SOLAR

        if (!existingPartyId.isNullOrBlank()) {
            syncRepo.updateAndSyncKey(SupabaseSyncRepository.KEY_PARTIES) { snap ->
                snap.copy(
                    parties = snap.parties.map { p ->
                        if (p.id != existingPartyId) p else {
                            val updatedStages = p.stages.map { s ->
                                if (s.id == "REG_3" && !s.completed) {
                                    s.copy(
                                        completed = true,
                                        completedAt = nowTimestamp(),
                                        completedBy = actor.displayName
                                    )
                                } else s
                            }
                            p.copy(
                                mobile = customerMobile.trim().ifBlank { p.mobile },
                                cityOrLocation = siteLocation.trim().ifBlank { p.cityOrLocation },
                                address = siteLocation.trim().ifBlank { p.address },
                                consumerNumber = consumerNo.trim().ifBlank { p.consumerNumber },
                                capacityKw = capacityKw.coerceAtLeast(1.0),
                                roofStructureType = roofType,
                                quotedRatePerKw = if (RolePermissions.canViewFinancials(actor.role)) quotedRatePerKw else p.quotedRatePerKw,
                                projectValue = if (RolePermissions.canViewFinancials(actor.role)) dealValue else p.projectValue,
                                subsidyAmount = if (RolePermissions.canViewFinancials(actor.role)) subsidyAmount else p.subsidyAmount,
                                siteSurveyNotes = surveyNotes,
                                leadStatus = LeadStatus.CONVERTED,
                                stages = updatedStages,
                                siteLogs = listOf(
                                    SiteLogEntry(
                                        timestamp = nowTimestamp(),
                                        author = actor.displayName,
                                        message = "Proposal Approved & Converted to Active Order: $surveyNotes"
                                    )
                                ) + p.siteLogs
                            )
                        }
                    }
                )
            }
            showToast("Proposal approved! '${cleanName}' moved to Active Converted Orders.")
        } else {
            val newId = UUID.randomUUID().toString()
            val stages = defaultWorkflowStages().map { s ->
                if (s.id == "REG_3") {
                    s.copy(completed = true, completedAt = nowTimestamp(), completedBy = actor.displayName)
                } else s
            }
            val newParty = PartyProject(
                id = newId,
                partyName = cleanName,
                mobile = customerMobile.trim(),
                address = siteLocation.trim().ifBlank { "Udaipur, Rajasthan" },
                cityOrLocation = siteLocation.trim().ifBlank { "Udaipur, Rajasthan" },
                consumerNumber = consumerNo.trim(),
                capacityKw = capacityKw.coerceAtLeast(1.0),
                division = division,
                leadSource = LeadSource.NB_DIRECT,
                leadDate = todayIsoDate(),
                roofStructureType = roofType,
                quotedRatePerKw = if (RolePermissions.canViewFinancials(actor.role)) quotedRatePerKw else 0.0,
                siteSurveyNotes = surveyNotes,
                leadStatus = LeadStatus.CONVERTED,
                projectValue = if (RolePermissions.canViewFinancials(actor.role)) dealValue else 0.0,
                subsidyAmount = if (RolePermissions.canViewFinancials(actor.role)) subsidyAmount else 0.0,
                createdDate = todayIsoDate(),
                stages = stages,
                siteLogs = listOf(
                    SiteLogEntry(
                        timestamp = nowTimestamp(),
                        author = actor.displayName,
                        message = "Walk-in Proposal Approved & Converted to Active Order: $surveyNotes"
                    )
                )
            )
            syncRepo.updateAndSyncKey(SupabaseSyncRepository.KEY_PARTIES) { snap ->
                snap.copy(parties = listOf(newParty) + snap.parties)
            }
            syncRepo.updateAndSyncKey(SupabaseSyncRepository.KEY_BUSY_VOUCHERS) { snap ->
                val exists = snap.busyAccounts.any { it.name.equals(newParty.partyName, ignoreCase = true) }
                if (exists) snap else snap.copy(
                    busyAccounts = snap.busyAccounts + BusyAccount(
                        name = newParty.partyName,
                        group = BusyAccountGroup.SUNDRY_DEBTORS,
                        openingBalance = 0.0,
                        mobile = newParty.mobile,
                        linkedPartyId = newId,
                        defaultCostCenter = LeadSource.NB_DIRECT
                    )
                )
            }
            showToast("Created & Converted Active Order for '$cleanName'!")
        }
    }

    fun markReferralPaid(partyId: String, paid: Boolean) {
        val actor = _currentUser.value ?: return
        if (!RolePermissions.canAccessAccounting(actor.role)) {
            showToast("Only Owner / Accounts can settle referral commissions.")
            return
        }
        val today = todayIsoDate()
        syncRepo.updateAndSyncKey(SupabaseSyncRepository.KEY_PARTIES) { snap ->
            snap.copy(
                parties = snap.parties.map { p ->
                    if (p.id != partyId) p else p.copy(
                        referralPaid = paid,
                        referralPayoutDate = if (paid) today else ""
                    )
                }
            )
        }
        showToast(if (paid) "Referral commission marked PAID." else "Referral commission marked Pending.")
    }

    fun deleteParty(partyId: String) {
        val actor = _currentUser.value ?: return
        if (!RolePermissions.canDeleteRecords(actor.role)) {
            showToast("Only Owner / CEO has delete permission.")
            return
        }
        syncRepo.updateAndSyncKey(SupabaseSyncRepository.KEY_PARTIES) { snap ->
            snap.copy(parties = snap.parties.filterNot { it.id == partyId })
        }
        if (_selectedPartyId.value == partyId) _selectedPartyId.value = null
        showToast("Party record deleted.")
    }

    fun toggleWorkflowStage(partyId: String, stageId: String) {
        val actor = _currentUser.value ?: return
        val party = syncRepo.snapshot.value.parties.firstOrNull { it.id == partyId } ?: return
        val stage = party.stages.firstOrNull { it.id == stageId } ?: return

        if (!RolePermissions.canEditWorkflowSection(actor.role, stage.section)) {
            showToast("Your role cannot modify ${stage.section.title}.")
            return
        }

        val nowCompleted = !stage.completed
        val isLoanOrMcoStage = stageId in setOf(
            "LEAD_FILE_3",
            "LOAN_2",
            "LOAN_3",
            "INSTALLATION_3",
            "NET_METERING_2",
            "NET_METERING_3",
            "SUBSIDY_CLOSE_2"
        )
        val syncTargetKey = if (isLoanOrMcoStage) {
            SupabaseSyncRepository.KEY_LOANS_MCO
        } else {
            SupabaseSyncRepository.KEY_PARTIES
        }
        syncRepo.updateAndSyncKey(syncTargetKey) { snap ->
            snap.copy(
                parties = snap.parties.map { p ->
                    if (p.id != partyId) p else {
                        val updatedStages = p.stages.map { s ->
                            if (s.id != stageId) s else s.copy(
                                completed = nowCompleted,
                                completedAt = if (nowCompleted) nowTimestamp() else "",
                                completedBy = if (nowCompleted) actor.displayName else ""
                            )
                        }
                        val updatedLoan1 = if (stageId == "LOAN_2") nowCompleted else p.loan1Received
                        val updatedLoan2 = if (stageId == "LOAN_3") nowCompleted else p.loan2Received
                        val updatedRseb = if (stageId == "LEAD_FILE_3") nowCompleted else p.rsebFileApplied
                        val updatedMeter = if (stageId == "NET_METERING_2") nowCompleted else p.meterInstalled
                        val updatedMco = if (stageId == "NET_METERING_3") nowCompleted else p.mcoOk
                        val updatedSubsidy = if (stageId == "SUBSIDY_CLOSE_2") nowCompleted else p.subsidyReceived
                        val updatedL1Date = if (stageId == "LOAN_2" && nowCompleted && p.loan1Date.isBlank()) todayIsoDate() else p.loan1Date
                        val updatedL2Date = if (stageId == "LOAN_3" && nowCompleted && p.loan2Date.isBlank()) todayIsoDate() else p.loan2Date
                        val updatedInstDate = if (stageId == "INSTALLATION_3") {
                            if (nowCompleted) p.installationDoneDate.ifBlank { todayIsoDate() } else ""
                        } else p.installationDoneDate
                        val logMsg = if (nowCompleted) "Completed: ${stage.title}" else "Reopened: ${stage.title}"
                        p.copy(
                            stages = updatedStages,
                            loan1Received = updatedLoan1,
                            loan2Received = updatedLoan2,
                            loan1Date = updatedL1Date,
                            loan2Date = updatedL2Date,
                            installationDoneDate = updatedInstDate,
                            rsebFileApplied = updatedRseb,
                            meterInstalled = updatedMeter,
                            mcoOk = updatedMco,
                            subsidyReceived = updatedSubsidy,
                            siteLogs = listOf(
                                SiteLogEntry(
                                    timestamp = nowTimestamp(),
                                    author = actor.displayName,
                                    message = logMsg
                                )
                            ) + p.siteLogs
                        )
                    }
                }
            )
        }
    }

    fun setRemindersDrawerVisible(visible: Boolean) {
        _showRemindersDrawer.value = visible
    }

    fun tickPartyMcoOk(partyId: String) {
        val actor = _currentUser.value ?: return
        if (!RolePermissions.canEditWorkflowSection(actor.role, com.example.data.model.WorkflowSection.NET_METERING)) {
            showToast("Your role cannot update RSEB MCO status.")
            return
        }
        var targetName = ""
        // Sync strictly to `nb_loans_mco` so Office party/doc edits never get overwritten
        syncRepo.updateAndSyncKey(SupabaseSyncRepository.KEY_LOANS_MCO) { snap ->
            snap.copy(
                parties = snap.parties.map { p ->
                    if (p.id != partyId) p else {
                        targetName = p.partyName
                        val updatedStages = p.stages.map { s ->
                            if (s.id == "NET_METERING_3" && !s.completed) {
                                s.copy(completed = true, completedAt = nowTimestamp(), completedBy = actor.displayName)
                            } else s
                        }
                        p.copy(
                            mcoOk = true,
                            stages = updatedStages,
                            siteLogs = listOf(
                                SiteLogEntry(
                                    timestamp = nowTimestamp(),
                                    author = actor.displayName,
                                    message = "RSEB MCO OK ticked & synced"
                                )
                            ) + p.siteLogs
                        )
                    }
                }
            )
        }
        if (targetName.isNotBlank()) {
            showToast("Marked MCO OK for $targetName & synced to Supabase.")
        }
    }

    // --- BULK IMPORT LOAN PARTIES (EXCEL / CSV / PHOTO / PDF / WHATSAPP TEXT) ---

    fun openBulkLoanImporter() {
        val actor = _currentUser.value ?: return
        if (!RolePermissions.canBulkImportLoanParties(actor.role, actor.assignedDivision)) {
            showToast("Bulk Loan Import is restricted to Owner, Accounts, and Solar Office.")
            return
        }
        _bulkLoanScanState.value = BulkLoanScanUiState.Idle
        _showBulkLoanImporter.value = true
    }

    fun dismissBulkLoanImporter() {
        _showBulkLoanImporter.value = false
        _bulkLoanScanState.value = BulkLoanScanUiState.Idle
    }

    fun resetBulkLoanScanState() {
        _bulkLoanScanState.value = BulkLoanScanUiState.Idle
    }

    fun importLoanPartiesFromFile(uri: Uri) {
        val actor = _currentUser.value ?: return
        if (!RolePermissions.canBulkImportLoanParties(actor.role, actor.assignedDivision)) return
        viewModelScope.launch {
            val resolver = getApplication<Application>().contentResolver
            val fileName = withContext(Dispatchers.IO) { queryFileName(uri).orEmpty() }
            val mime = withContext(Dispatchers.IO) { resolver.getType(uri).orEmpty().lowercase() }
            val lowerName = fileName.lowercase()
            val existingParties = syncRepo.snapshot.value.parties

            when {
                lowerName.endsWith(".csv") || mime.contains("csv") || mime == "text/plain" -> {
                    _bulkLoanScanState.value = BulkLoanScanUiState.Parsing("Parsing CSV file ($fileName)…")
                    val rows = withContext(Dispatchers.IO) {
                        val txt = resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                        BulkLoanImporter.parseCsvLoanParties(txt, existingParties)
                    }
                    _bulkLoanScanState.value = if (rows.isEmpty()) {
                        BulkLoanScanUiState.Error("No valid customer loan rows found in $fileName. Check headers or download the sample CSV.")
                    } else {
                        BulkLoanScanUiState.Review(rows, sourceLabel = "CSV: $fileName")
                    }
                }
                lowerName.endsWith(".xlsx") || mime.contains("spreadsheetml") -> {
                    _bulkLoanScanState.value = BulkLoanScanUiState.Parsing("Reading Excel (.xlsx) workbook ($fileName)…")
                    val rows = withContext(Dispatchers.IO) {
                        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                        runCatching { BulkLoanImporter.parseXlsxLoanParties(bytes, existingParties) }.getOrDefault(emptyList())
                    }
                    _bulkLoanScanState.value = if (rows.isEmpty()) {
                        BulkLoanScanUiState.Error("Could not extract rows from $fileName. Try saving as CSV or uploading a PDF/screenshot.")
                    } else {
                        BulkLoanScanUiState.Review(rows, sourceLabel = "Excel: $fileName")
                    }
                }
                lowerName.endsWith(".xls") || mime.contains("ms-excel") -> {
                    _bulkLoanScanState.value = BulkLoanScanUiState.Parsing("Reading Excel (.xls) file ($fileName)…")
                    val (directRows, rawTextFallback) = withContext(Dispatchers.IO) {
                        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                        val parsed = runCatching { BulkLoanImporter.parseLegacyXlsOrText(bytes, existingParties) }.getOrDefault(emptyList())
                        val text = if (parsed.isEmpty()) bytes.toString(Charsets.UTF_8).take(40_000) else ""
                        parsed to text
                    }
                    if (directRows.isNotEmpty()) {
                        _bulkLoanScanState.value = BulkLoanScanUiState.Review(directRows, sourceLabel = "Excel: $fileName")
                    } else {
                        val aiRes = aiScanner.scanLoanRegister(rawText = rawTextFallback, existingParties = existingParties)
                        aiRes.fold(
                            onSuccess = { rows ->
                                _bulkLoanScanState.value = BulkLoanScanUiState.Review(rows, sourceLabel = "AI Excel: $fileName")
                            },
                            onFailure = {
                                _bulkLoanScanState.value = BulkLoanScanUiState.Error(
                                    "Legacy binary .xls could not be decoded. Please save as .xlsx / .csv or upload a PDF/Photo."
                                )
                            }
                        )
                    }
                }
                else -> {
                    _bulkLoanScanState.value = BulkLoanScanUiState.Parsing("Scanning Loan Register with Gemini AI…")
                    val (bitmap, pdfBytes, rawText) = withContext(Dispatchers.IO) { readUriForAi(uri) }
                    if (bitmap == null && pdfBytes == null && rawText.isNullOrBlank()) {
                        _bulkLoanScanState.value = BulkLoanScanUiState.Error("Unable to read selected file ($fileName). Use Photo, PDF, CSV, or XLSX.")
                        return@launch
                    }
                    val res = aiScanner.scanLoanRegister(
                        bitmap = bitmap,
                        pdfBytes = pdfBytes,
                        rawText = rawText,
                        existingParties = existingParties
                    )
                    res.fold(
                        onSuccess = { rows ->
                            _bulkLoanScanState.value = BulkLoanScanUiState.Review(rows, sourceLabel = "AI Scan: $fileName")
                        },
                        onFailure = { err ->
                            _bulkLoanScanState.value = BulkLoanScanUiState.Error(err.message ?: "AI scan failed.")
                        }
                    )
                }
            }
        }
    }

    fun importLoanPartiesFromText(rawText: String, useAi: Boolean) {
        val actor = _currentUser.value ?: return
        if (!RolePermissions.canBulkImportLoanParties(actor.role, actor.assignedDivision)) return
        if (rawText.isBlank()) {
            showToast("Paste WhatsApp or ledger text rows first.")
            return
        }
        val existingParties = syncRepo.snapshot.value.parties
        if (!useAi) {
            val rows = BulkLoanImporter.parseRawTextLoanParties(rawText, existingParties)
            _bulkLoanScanState.value = if (rows.isEmpty()) {
                BulkLoanScanUiState.Error("No loan rows matched. Try 'AI Smart Parse (Gemini)' for free-form WhatsApp notes.")
            } else {
                BulkLoanScanUiState.Review(rows, sourceLabel = "WhatsApp / Text List")
            }
            return
        }
        viewModelScope.launch {
            _bulkLoanScanState.value = BulkLoanScanUiState.Parsing("Extracting loan rows from text with Gemini AI…")
            val res = aiScanner.scanLoanRegister(rawText = rawText, existingParties = existingParties)
            res.fold(
                onSuccess = { rows ->
                    _bulkLoanScanState.value = BulkLoanScanUiState.Review(rows, sourceLabel = "AI WhatsApp Parse")
                },
                onFailure = { err ->
                    _bulkLoanScanState.value = BulkLoanScanUiState.Error(err.message ?: "AI text parse failed.")
                }
            )
        }
    }

    fun updateBulkLoanRow(rowId: String, transform: (BulkLoanPartyRow) -> BulkLoanPartyRow) {
        val current = _bulkLoanScanState.value as? BulkLoanScanUiState.Review ?: return
        val updated = current.rows.map { r -> if (r.id == rowId) transform(r) else r }
        _bulkLoanScanState.value = current.copy(rows = updated)
    }

    fun addBlankBulkLoanRow() {
        val current = _bulkLoanScanState.value as? BulkLoanScanUiState.Review
        val newRow = BulkLoanPartyRow(partyName = "New Loan Party", siteCity = "Udaipur", capacityKw = 3.0)
        _bulkLoanScanState.value = BulkLoanScanUiState.Review(
            rows = (current?.rows ?: emptyList()) + newRow,
            sourceLabel = current?.sourceLabel?.ifBlank { "Manual Grid" } ?: "Manual Grid"
        )
    }

    fun removeBulkLoanRow(rowId: String) {
        val current = _bulkLoanScanState.value as? BulkLoanScanUiState.Review ?: return
        _bulkLoanScanState.value = current.copy(rows = current.rows.filterNot { it.id == rowId })
    }

    fun commitBulkLoanParties(txnId: String = "") {
        val actor = _currentUser.value ?: return
        if (!RolePermissions.canBulkImportLoanParties(actor.role, actor.assignedDivision)) return
        val review = _bulkLoanScanState.value as? BulkLoanScanUiState.Review ?: return
        val selectedRows = review.rows.filter { it.selected && it.partyName.isNotBlank() }
        if (selectedRows.isEmpty()) {
            showToast("Select at least one party row with a valid name to import.")
            return
        }
        val fp = "bulkImport|${selectedRows.size}|${selectedRows.firstOrNull()?.partyName.orEmpty()}"
        if (!txnGuard.tryClaim(txnId, fp)) return

        val (newParties, newAccounts) = BulkLoanImporter.toPartyProjects(selectedRows, actor.displayName)
        syncRepo.updateAndSyncKey(SupabaseSyncRepository.KEY_PARTIES) { snap ->
            snap.copy(parties = newParties + snap.parties)
        }
        syncRepo.updateAndSyncKey(SupabaseSyncRepository.KEY_LOANS_MCO) { it }
        syncRepo.updateAndSyncKey(SupabaseSyncRepository.KEY_BUSY_VOUCHERS) { snap ->
            val existingNames = snap.busyAccounts.map { it.name.trim().lowercase() }.toSet()
            val toAdd = newAccounts.filter { it.name.trim().lowercase() !in existingNames }
            if (toAdd.isEmpty()) snap else snap.copy(busyAccounts = snap.busyAccounts + toAdd)
        }

        val newReminders = LoanReminderEngine.computeReminders(syncRepo.snapshot.value.parties)
        _showBulkLoanImporter.value = false
        _bulkLoanScanState.value = BulkLoanScanUiState.Idle
        if (newReminders.isNotEmpty()) {
            _showRemindersDrawer.value = true
        }
        showToast("Imported & synced ${newParties.size} loan parties to Supabase (${newReminders.size} active reminders).")
    }

    fun addPartySiteNote(partyId: String, note: String) {
        val actor = _currentUser.value ?: return
        if (note.isBlank()) return
        syncRepo.updateAndSyncKey(SupabaseSyncRepository.KEY_PARTIES) { snap ->
            snap.copy(
                parties = snap.parties.map { p ->
                    if (p.id != partyId) p else p.copy(
                        siteLogs = listOf(
                            SiteLogEntry(
                                timestamp = nowTimestamp(),
                                author = actor.displayName,
                                message = note.trim()
                            )
                        ) + p.siteLogs
                    )
                }
            )
        }
        showToast("Site update posted & synced.")
    }

    // --- WHATSAPP GROUP MESSAGE PARSER ---

    fun previewWhatsAppMessage(rawText: String): ParsedWhatsAppUpdate {
        return WhatsAppParser.parse(rawText, syncRepo.snapshot.value.parties)
    }

    fun applyWhatsAppUpdate(parsed: ParsedWhatsAppUpdate, targetPartyId: String?, txnId: String = "") {
        val actor = _currentUser.value ?: return
        val resolvedId = targetPartyId ?: parsed.matchedPartyId
        val fp = "waUpdate|${resolvedId.orEmpty()}|${parsed.matchedPartyName}|${parsed.summaryNote.hashCode()}"
        if (!txnGuard.tryClaim(txnId, fp)) return

        if (resolvedId == null) {
            // Create party if authorized, else warn
            if (!RolePermissions.canCreateParty(actor.role)) {
                showToast("No matching party found. Select an existing party to attach update.")
                return
            }
            val newId = UUID.randomUUID().toString()
            val stages = defaultWorkflowStages().map { s ->
                if (s.id in parsed.stagesToComplete) {
                    s.copy(completed = true, completedAt = nowTimestamp(), completedBy = "${actor.displayName} (WhatsApp)")
                } else s
            }
            val newParty = PartyProject(
                id = newId,
                partyName = parsed.matchedPartyName,
                capacityKw = parsed.detectedKw ?: 3.0,
                leadSource = LeadSource.NB_DIRECT,
                createdDate = todayIsoDate(),
                stages = stages,
                siteLogs = listOf(
                    SiteLogEntry(
                        timestamp = nowTimestamp(),
                        author = actor.displayName,
                        message = parsed.summaryNote,
                        viaWhatsAppParser = true
                    )
                )
            )
            syncRepo.updateAndSyncKey(SupabaseSyncRepository.KEY_PARTIES) { snap ->
                snap.copy(parties = listOf(newParty) + snap.parties)
            }
            showToast("Created '${newParty.partyName}' & applied ${parsed.stagesToComplete.size} stage updates.")
            return
        }

        syncRepo.updateAndSyncKey(SupabaseSyncRepository.KEY_PARTIES) { snap ->
            snap.copy(
                parties = snap.parties.map { p ->
                    if (p.id != resolvedId) p else {
                        val updatedStages = p.stages.map { s ->
                            val allowed = RolePermissions.canEditWorkflowSection(actor.role, s.section)
                            if (allowed && s.id in parsed.stagesToComplete && !s.completed) {
                                s.copy(
                                    completed = true,
                                    completedAt = nowTimestamp(),
                                    completedBy = "${actor.displayName} (WhatsApp)"
                                )
                            } else s
                        }
                        p.copy(
                            capacityKw = parsed.detectedKw ?: p.capacityKw,
                            stages = updatedStages,
                            siteLogs = listOf(
                                SiteLogEntry(
                                    timestamp = nowTimestamp(),
                                    author = actor.displayName,
                                    message = parsed.summaryNote,
                                    viaWhatsAppParser = true
                                )
                            ) + p.siteLogs
                        )
                    }
                }
            )
        }
        showToast("WhatsApp update synced to ${parsed.matchedPartyName}.")
    }

    // --- SEARCHABLE DOCUMENT VAULT ---

    fun uploadPartyDocument(
        partyId: String,
        uri: Uri,
        title: String,
        category: DocumentCategory,
        notes: String
    ) {
        val actor = _currentUser.value ?: return
        viewModelScope.launch {
            val doc = withContext(Dispatchers.IO) {
                try {
                    val resolver = getApplication<Application>().contentResolver
                    val mime = resolver.getType(uri) ?: "application/octet-stream"
                    val origName = queryFileName(uri) ?: "doc_${System.currentTimeMillis()}"
                    val vaultDir = File(getApplication<Application>().filesDir, "vault").apply { mkdirs() }

                    val isImage = mime.startsWith("image/") ||
                        origName.endsWith(".jpg", ignoreCase = true) ||
                        origName.endsWith(".jpeg", ignoreCase = true) ||
                        origName.endsWith(".png", ignoreCase = true) ||
                        origName.endsWith(".webp", ignoreCase = true)

                    val (finalMime, destFile, thumbBase64) = if (isImage) {
                        val compressed = ImageCompressor.compressUriToJpegAndBitmap(
                            getApplication(),
                            uri,
                            maxWidth = ImageCompressor.DEFAULT_MAX_WIDTH,
                            maxBytes = ImageCompressor.DEFAULT_MAX_BYTES
                        )
                        val jpgFile = File(vaultDir, "${System.currentTimeMillis()}_${origName.substringBeforeLast('.').replace(" ", "_")}.jpg")
                        if (compressed != null) {
                            jpgFile.writeBytes(compressed.jpegBytes)
                            Triple("image/jpeg", jpgFile, compressed.base64Thumbnail)
                        } else {
                            resolver.openInputStream(uri)?.use { input ->
                                jpgFile.outputStream().use { output -> input.copyTo(output) }
                            }
                            Triple(mime, jpgFile, "")
                        }
                    } else {
                        val rawFile = File(vaultDir, "${System.currentTimeMillis()}_${origName.replace(" ", "_")}")
                        resolver.openInputStream(uri)?.use { input ->
                            rawFile.outputStream().use { output -> input.copyTo(output) }
                        }
                        Triple(mime, rawFile, "")
                    }

                    PartyDocument(
                        title = title.trim().ifBlank { origName },
                        category = category,
                        fileName = origName,
                        mimeType = finalMime,
                        localFilePath = destFile.absolutePath,
                        base64Thumbnail = thumbBase64,
                        uploadedAt = nowTimestamp(),
                        uploadedBy = actor.displayName,
                        notes = notes.trim()
                    )
                } catch (_: Exception) {
                    null
                }
            }

            if (doc == null) {
                showToast("Could not read selected file. Please try again.")
                return@launch
            }

            syncRepo.updateAndSyncKey(SupabaseSyncRepository.KEY_PARTIES) { snap ->
                snap.copy(
                    parties = snap.parties.map { p ->
                        if (p.id != partyId) p else p.copy(
                            documents = listOf(doc) + p.documents,
                            siteLogs = listOf(
                                SiteLogEntry(
                                    timestamp = nowTimestamp(),
                                    author = actor.displayName,
                                    message = "Uploaded ${doc.category.label}: ${doc.title}"
                                )
                            ) + p.siteLogs
                        )
                    }
                )
            }
            showToast("Document '${doc.title}' added to Vault & synced.")
        }
    }

    // --- INSTALLATION ON-SITE MATERIAL PURCHASE (PARTY-LINKED LOCAL PROCUREMENT) ---

    fun addSiteMaterialPurchase(
        partyId: String,
        itemDescription: String,
        quantity: String,
        vendorName: String,
        amount: Double,
        paymentSource: SitePurchasePaymentSource,
        billPhotoUri: Uri?,
        txnId: String = ""
    ) {
        val actor = _currentUser.value ?: return
        val party = syncRepo.snapshot.value.parties.firstOrNull { it.id == partyId } ?: return
        if (itemDescription.isBlank() || amount <= 0.0) {
            showToast("Enter item description and valid ₹ amount.")
            return
        }
        val fp = "sitePurchase|$partyId|${itemDescription.trim().lowercase()}|$amount|${paymentSource.name}"
        if (!txnGuard.tryClaim(txnId, fp)) return

        viewModelScope.launch {
            var savedPath = ""
            var thumbBase64 = ""
            if (billPhotoUri != null) {
                val (p, b64) = withContext(Dispatchers.IO) {
                    try {
                        val billsDir = File(getApplication<Application>().filesDir, "site_bills").apply { mkdirs() }
                        val dest = File(billsDir, "bill_${System.currentTimeMillis()}.jpg")
                        val compressed = ImageCompressor.compressUriToJpegAndBitmap(
                            getApplication(),
                            billPhotoUri,
                            maxWidth = ImageCompressor.DEFAULT_MAX_WIDTH,
                            maxBytes = ImageCompressor.DEFAULT_MAX_BYTES
                        )
                        if (compressed != null) {
                            dest.writeBytes(compressed.jpegBytes)
                            dest.absolutePath to compressed.base64Thumbnail
                        } else {
                            getApplication<Application>().contentResolver.openInputStream(billPhotoUri)?.use { input ->
                                dest.outputStream().use { out -> input.copyTo(out) }
                            }
                            dest.absolutePath to ""
                        }
                    } catch (_: Exception) {
                        "" to ""
                    }
                }
                savedPath = p
                thumbBase64 = b64
            }

            val cleanVendor = vendorName.trim().ifBlank { "Local Hardware Shop" }
            val voucherType = if (paymentSource == SitePurchasePaymentSource.VENDOR_CREDIT) {
                BusyVoucherType.PURCHASE
            } else {
                BusyVoucherType.PAYMENT
            }
            val nextNum = syncRepo.snapshot.value.busyVouchers.count { it.type == voucherType } + 1
            val vchNo = "${voucherType.prefix}-SITE-${nextNum.toString().padStart(4, '0')}"

            val purchase = SiteMaterialPurchase(
                itemDescription = itemDescription.trim(),
                quantity = quantity.trim().ifBlank { "1" },
                vendorName = cleanVendor,
                amount = amount,
                paymentSource = paymentSource,
                billPhotoPath = savedPath,
                billPhotoBase64 = thumbBase64,
                purchasedByLoginId = actor.loginId,
                purchasedByName = actor.displayName,
                purchaseDate = todayIsoDate(),
                linkedVoucherNo = vchNo
            )

            // 1. Add purchase to Party card
            syncRepo.updateAndSyncKey(SupabaseSyncRepository.KEY_PARTIES) { snap ->
                snap.copy(
                    parties = snap.parties.map { p ->
                        if (p.id != partyId) p else p.copy(
                            sitePurchases = listOf(purchase) + p.sitePurchases,
                            siteLogs = listOf(
                                SiteLogEntry(
                                    timestamp = nowTimestamp(),
                                    author = actor.displayName,
                                    message = "On-Site Purchase: ${purchase.itemDescription} (${purchase.quantity}) from $cleanVendor via ${paymentSource.label}"
                                )
                            ) + p.siteLogs
                        )
                    }
                )
            }

            // 2. Automatically deduct from Supervisor Imprest Wallet if paid via Supervisor Imprest Cash
            if (paymentSource == SitePurchasePaymentSource.SUPERVISOR_IMPREST) {
                syncRepo.updateAndSyncKey(SupabaseSyncRepository.KEY_SECURITY_CONFIG) { snap ->
                    val actorIsSupervisor = actor.role == StaffRole.SUPERVISOR
                    val targetLogin = if (actorIsSupervisor) {
                        actor.loginId
                    } else {
                        snap.staffUsers.firstOrNull { it.role == StaffRole.SUPERVISOR }?.loginId
                    }
                    snap.copy(
                        staffUsers = snap.staffUsers.map { u ->
                            if (targetLogin != null && u.loginId.equals(targetLogin, ignoreCase = true)) {
                                u.copy(imprestBalance = u.imprestBalance - amount)
                            } else u
                        }
                    )
                }
            }

            // 3. Automatically post Purchase (F9) / Payment (F5) voucher in BUSY 21 tagged to Party Cost Center
            syncRepo.updateAndSyncKey(SupabaseSyncRepository.KEY_BUSY_VOUCHERS) { snap ->
                val vendorExists = snap.busyAccounts.any { it.name.equals(cleanVendor, ignoreCase = true) }
                val updatedAccounts = if (vendorExists) snap.busyAccounts else snap.busyAccounts + BusyAccount(
                    name = cleanVendor,
                    group = BusyAccountGroup.SUNDRY_CREDITORS,
                    openingBalance = 0.0,
                    defaultCostCenter = party.leadSource
                )
                val vendorAcc = updatedAccounts.firstOrNull { it.name.equals(cleanVendor, ignoreCase = true) }
                    ?: BusyAccount(name = cleanVendor, group = BusyAccountGroup.SUNDRY_CREDITORS, defaultCostCenter = party.leadSource)
                val contraAcc = when (paymentSource) {
                    SitePurchasePaymentSource.SUPERVISOR_IMPREST ->
                        updatedAccounts.firstOrNull { it.id == "acc_imprest" }
                            ?: BusyAccount(id = "acc_imprest", name = "Supervisor Imprest Cash Wallet", group = BusyAccountGroup.CASH_IN_HAND)
                    SitePurchasePaymentSource.COMPANY_UPI ->
                        updatedAccounts.firstOrNull { it.id == "acc_sbi" }
                            ?: BusyAccount(id = "acc_sbi", name = "SBI Current A/c - NB Engineering (UPI)", group = BusyAccountGroup.BANK_ACCOUNTS)
                    SitePurchasePaymentSource.VENDOR_CREDIT ->
                        updatedAccounts.firstOrNull { it.id == "acc_site_mat" }
                            ?: BusyAccount(id = "acc_site_mat", name = "On-Site Local Hardware Purchases", group = BusyAccountGroup.PURCHASE_ACCOUNTS)
                }
                val vch = BusyVoucher(
                    type = voucherType,
                    voucherNo = vchNo,
                    date = todayIsoDate(),
                    partyAccountId = vendorAcc.id,
                    partyAccountName = vendorAcc.name,
                    contraAccountId = contraAcc.id,
                    contraAccountName = contraAcc.name,
                    costCenter = party.leadSource,
                    taxableAmount = amount,
                    totalAmount = amount,
                    billRefType = if (voucherType == BusyVoucherType.PURCHASE) BillRefType.NEW_REF else BillRefType.ON_ACCOUNT,
                    billRefNo = vchNo,
                    narration = "Party: ${party.partyName} | Site Mat: ${purchase.itemDescription} (${purchase.quantity}) | ${paymentSource.label}",
                    createdBy = actor.displayName
                )
                snap.copy(
                    busyAccounts = updatedAccounts,
                    busyVouchers = listOf(vch) + snap.busyVouchers
                )
            }

            showToast("Site material purchase saved & synced (${voucherType.shortcut} #$vchNo).")
        }
    }

    // --- LEKHA JOKHA WHATSAPP BULK PARSER (SITE KHARCHA & WORKER ADVANCES) ---

    fun previewBulkLekhaJokha(rawText: String): List<ParsedLekhaJokhaLine> {
        return WhatsAppParser.parseBulkLekhaJokha(rawText, syncRepo.snapshot.value.parties)
    }

    fun applyBulkLekhaJokha(lines: List<ParsedLekhaJokhaLine>, txnId: String = "") {
        val actor = _currentUser.value ?: return
        if (lines.isEmpty()) {
            showToast("No valid Lekha Jokha expense lines detected.")
            return
        }
        val fp = "lekhaJokha|${lines.size}|${lines.sumOf { it.amount }}"
        if (!txnGuard.tryClaim(txnId, fp)) return
        val date = todayIsoDate()
        val entries = lines.map { line ->
            SiteKharchaEntry(
                partyId = line.matchedPartyId.orEmpty(),
                partyName = line.matchedPartyName,
                category = line.category,
                workerOrTitle = line.workerOrTitle,
                amount = line.amount,
                date = date,
                note = line.rawLine,
                loggedBy = actor.displayName
            )
        }
        syncRepo.updateAndSyncKey(SupabaseSyncRepository.KEY_BUSY_VOUCHERS) { snap ->
            val expAcc = snap.busyAccounts.firstOrNull { it.id == "acc_site_exp" }
                ?: BusyAccount(id = "acc_site_exp", name = "Site Labour & Daily Kharcha Exp", group = BusyAccountGroup.DIRECT_EXPENSES)
            val cashAcc = snap.busyAccounts.firstOrNull { it.id == "acc_cash" }
                ?: BusyAccount(id = "acc_cash", name = "Cash-in-Hand (Madri Office)", group = BusyAccountGroup.CASH_IN_HAND)
            val baseNum = snap.busyVouchers.count { it.type == BusyVoucherType.PAYMENT } + 1
            val newVouchers = entries.mapIndexed { idx, kh ->
                val partyCostCenter = snap.parties.firstOrNull { it.id == kh.partyId }?.leadSource ?: LeadSource.NB_DIRECT
                BusyVoucher(
                    type = BusyVoucherType.PAYMENT,
                    voucherNo = "PMT-KH-${(baseNum + idx).toString().padStart(4, '0')}",
                    date = date,
                    partyAccountId = expAcc.id,
                    partyAccountName = expAcc.name,
                    contraAccountId = cashAcc.id,
                    contraAccountName = cashAcc.name,
                    costCenter = partyCostCenter,
                    taxableAmount = kh.amount,
                    totalAmount = kh.amount,
                    narration = "Lekha Jokha [${kh.partyName}]: ${kh.workerOrTitle}",
                    createdBy = actor.displayName
                )
            }
            snap.copy(
                siteKharcha = entries + snap.siteKharcha,
                busyVouchers = newVouchers + snap.busyVouchers
            )
        }

        showToast("Synced ${entries.size} Lekha Jokha entries to Site Kharcha & BUSY 21.")
    }

    // --- REAL-TIME TEAM CHAT & VIDEO MESSAGES ---

    fun sendTeamChatMessage(text: String, videoUri: Uri? = null) {
        val actor = _currentUser.value ?: return
        if (text.isBlank() && videoUri == null) return

        viewModelScope.launch {
            var localVideoPath = ""
            var videoLabel = ""
            if (videoUri != null) {
                val (path, label) = withContext(Dispatchers.IO) {
                    try {
                        val resolver = getApplication<Application>().contentResolver
                        val dir = File(getApplication<Application>().filesDir, "chat_videos").apply { mkdirs() }
                        val dest = File(dir, "vid_${System.currentTimeMillis()}.mp4")
                        resolver.openInputStream(videoUri)?.use { input ->
                            dest.outputStream().use { out -> input.copyTo(out) }
                        }
                        val sizeKb = (dest.length() / 1024L).coerceAtLeast(1L)
                        dest.absolutePath to "Site Video (${sizeKb} KB)"
                    } catch (_: Exception) {
                        "" to ""
                    }
                }
                localVideoPath = path
                videoLabel = label
            }

            val msg = TeamChatMessage(
                senderLoginId = actor.loginId,
                senderName = actor.displayName,
                senderRole = actor.role,
                division = actor.assignedDivision ?: _selectedDivisionFilter.value,
                text = text.trim().ifBlank { if (videoLabel.isNotBlank()) "Sent a site video update" else "" },
                videoPath = localVideoPath,
                videoLabel = videoLabel,
                timestamp = nowTimestamp()
            )
            syncRepo.updateAndSyncKey(SupabaseSyncRepository.KEY_CHAT) { snap ->
                snap.copy(teamChat = snap.teamChat + msg)
            }
        }
    }

    // --- INVENTORY: DCR PANELS, METERS, RAW MATERIALS ---

    fun addDcrPanelsBatch(
        serialsRaw: String,
        brand: String,
        wattage: Int,
        supplierName: String,
        invoiceNo: String,
        txnId: String = ""
    ) {
        val actor = _currentUser.value ?: return
        if (!RolePermissions.canAddStockOrPurchase(actor.role)) {
            showToast("Only Office, Accounts, or Owner can add new DCR stock.")
            return
        }
        val serials = serialsRaw.split(Regex("""[\n,;]+"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        if (serials.isEmpty()) {
            showToast("Enter at least one DCR panel serial number.")
            return
        }
        val fp = "dcrBatch|${serials.size}|${serials.firstOrNull().orEmpty()}"
        if (!txnGuard.tryClaim(txnId, fp)) return
        syncRepo.updateAndSyncKey(SupabaseSyncRepository.KEY_INVENTORY) { snap ->
            val existing = snap.dcrPanels.map { it.serialNumber.uppercase() }.toSet()
            val toAdd = serials.filter { it.uppercase() !in existing }.map { sn ->
                DcrPanel(
                    serialNumber = sn,
                    brand = brand.trim().ifBlank { "Waaree" },
                    wattage = wattage.coerceIn(100, 1000),
                    supplierName = supplierName.trim(),
                    purchaseInvoiceNo = invoiceNo.trim(),
                    status = PanelStatus.IN_STOCK,
                    addedDate = todayIsoDate()
                )
            }
            snap.copy(dcrPanels = toAdd + snap.dcrPanels)
        }
        showToast("Added ${serials.size} DCR panel serial(s) to stock.")
    }

    fun allocatePanelToParty(panelId: String, party: PartyProject?, txnId: String = "") {
        val actor = _currentUser.value ?: return
        if (!RolePermissions.canAllocateInventory(actor.role)) {
            showToast("Your role cannot allocate DCR panels.")
            return
        }
        val fp = "allocPanel|$panelId|${party?.id.orEmpty()}"
        if (!txnGuard.tryClaim(txnId, fp)) return
        syncRepo.updateAndSyncKey(SupabaseSyncRepository.KEY_INVENTORY) { snap ->
            snap.copy(
                dcrPanels = snap.dcrPanels.map { p ->
                    if (p.id != panelId) p else {
                        if (party == null) {
                            p.copy(status = PanelStatus.IN_STOCK, linkedPartyId = "", linkedPartyName = "")
                        } else {
                            p.copy(status = PanelStatus.ALLOCATED, linkedPartyId = party.id, linkedPartyName = party.partyName)
                        }
                    }
                }
            )
        }
        showToast(if (party != null) "Panel allocated to ${party.partyName}." else "Panel returned to stock.")
    }

    fun addSolarMeter(
        serialNumber: String,
        meterType: MeterType,
        makeAndPhase: String,
        testingStatus: DiscomTestingStatus,
        txnId: String = ""
    ) {
        val actor = _currentUser.value ?: return
        if (!RolePermissions.canAllocateInventory(actor.role)) return
        if (serialNumber.isBlank()) {
            showToast("Meter serial number is required.")
            return
        }
        val fp = "addMeter|${serialNumber.trim().lowercase()}"
        if (!txnGuard.tryClaim(txnId, fp)) return
        syncRepo.updateAndSyncKey(SupabaseSyncRepository.KEY_INVENTORY) { snap ->
            snap.copy(
                solarMeters = listOf(
                    SolarMeter(
                        serialNumber = serialNumber.trim(),
                        meterType = meterType,
                        makeAndPhase = makeAndPhase.trim().ifBlank { "L&T / 1-Phase" },
                        testingStatus = testingStatus
                    )
                ) + snap.solarMeters
            )
        }
        showToast("Meter ${serialNumber.trim()} added.")
    }

    fun updateMeterAllocation(
        meterId: String,
        testingStatus: DiscomTestingStatus,
        party: PartyProject?,
        txnId: String = ""
    ) {
        val actor = _currentUser.value ?: return
        if (!RolePermissions.canAllocateInventory(actor.role)) return
        val fp = "updMeter|$meterId|${testingStatus.name}|${party?.id.orEmpty()}"
        if (!txnGuard.tryClaim(txnId, fp)) return
        syncRepo.updateAndSyncKey(SupabaseSyncRepository.KEY_INVENTORY) { snap ->
            snap.copy(
                solarMeters = snap.solarMeters.map { m ->
                    if (m.id != meterId) m else m.copy(
                        testingStatus = testingStatus,
                        installedPartyId = party?.id.orEmpty(),
                        installedPartyName = party?.partyName.orEmpty()
                    )
                }
            )
        }
        showToast("Meter status updated & synced.")
    }

    fun addRawMaterialStock(
        materialId: String?,
        name: String,
        category: String,
        unit: String,
        supplierName: String,
        addedQty: Double,
        minAlert: Double,
        txnId: String = ""
    ) {
        val actor = _currentUser.value ?: return
        if (!RolePermissions.canAddStockOrPurchase(actor.role)) {
            showToast("Only Office, Accounts, or Owner can add raw material stock.")
            return
        }
        if (addedQty <= 0.0) {
            showToast("Quantity must be greater than 0.")
            return
        }
        val fp = "addRaw|${materialId.orEmpty()}|${name.trim().lowercase()}|$addedQty"
        if (!txnGuard.tryClaim(txnId, fp)) return
        syncRepo.updateAndSyncKey(SupabaseSyncRepository.KEY_INVENTORY) { snap ->
            if (materialId != null) {
                snap.copy(
                    rawMaterials = snap.rawMaterials.map { rm ->
                        if (rm.id != materialId) rm else rm.copy(
                            purchasedQty = rm.purchasedQty + addedQty,
                            supplierName = supplierName.trim().ifBlank { rm.supplierName },
                            minStockAlert = if (minAlert > 0) minAlert else rm.minStockAlert
                        )
                    }
                )
            } else {
                snap.copy(
                    rawMaterials = snap.rawMaterials + RawMaterial(
                        name = name.trim().ifBlank { "Solar BOS Material" },
                        category = category.trim().ifBlank { "BOS" },
                        unit = unit.trim().ifBlank { "Nos" },
                        supplierName = supplierName.trim(),
                        purchasedQty = addedQty,
                        usedQty = 0.0,
                        minStockAlert = minAlert.coerceAtLeast(1.0)
                    )
                )
            }
        }
        showToast("Raw material stock updated.")
    }

    fun issueRawMaterialToSite(materialId: String, party: PartyProject, qty: Double, txnId: String = "") {
        val actor = _currentUser.value ?: return
        if (!RolePermissions.canAllocateInventory(actor.role)) {
            showToast("Your role cannot issue materials.")
            return
        }
        if (qty <= 0.0) {
            showToast("Enter valid quantity to issue.")
            return
        }
        val currentRm = syncRepo.snapshot.value.rawMaterials.firstOrNull { it.id == materialId } ?: return
        if (qty > currentRm.remainingQty) {
            showToast("Insufficient balance! Remaining: ${currentRm.remainingQty} ${currentRm.unit}")
            return
        }
        val fp = "issueRaw|$materialId|${party.id}|$qty"
        if (!txnGuard.tryClaim(txnId, fp)) return

        syncRepo.updateAndSyncKey(SupabaseSyncRepository.KEY_INVENTORY) { snap ->
            snap.copy(
                rawMaterials = snap.rawMaterials.map { rm ->
                    if (rm.id != materialId) rm else rm.copy(
                        usedQty = rm.usedQty + qty,
                        issueLogs = listOf(
                            MaterialIssueLog(
                                partyId = party.id,
                                partyName = party.partyName,
                                quantity = qty,
                                date = todayIsoDate(),
                                issuedBy = actor.displayName
                            )
                        ) + rm.issueLogs
                    )
                }
            )
        }
        showToast("Issued $qty ${currentRm.unit} of ${currentRm.name} to ${party.partyName}.")
    }

    // --- BUSY 21 ACCOUNTING MASTERS & VOUCHERS (OWNER & ACCOUNTS ONLY) ---

    fun addBusyAccount(
        name: String,
        group: BusyAccountGroup,
        openingBalance: Double,
        gstin: String,
        costCenter: LeadSource,
        txnId: String = ""
    ) {
        val actor = _currentUser.value ?: return
        if (!RolePermissions.canAccessAccounting(actor.role)) return
        if (name.isBlank()) return
        val fp = "busyAcc|${name.trim().lowercase()}|${group.name}"
        if (!txnGuard.tryClaim(txnId, fp)) return
        syncRepo.updateAndSyncKey(SupabaseSyncRepository.KEY_BUSY_VOUCHERS) { snap ->
            snap.copy(
                busyAccounts = snap.busyAccounts + BusyAccount(
                    name = name.trim(),
                    group = group,
                    openingBalance = openingBalance,
                    gstin = gstin.trim().uppercase(),
                    defaultCostCenter = costCenter
                )
            )
        }
        showToast("Account Master '${name.trim()}' saved.")
    }

    fun addBusyItem(
        name: String,
        hsn: String,
        unit: String,
        gstRate: Double,
        saleRate: Double,
        purchaseRate: Double,
        openingStock: Double,
        txnId: String = ""
    ) {
        val actor = _currentUser.value ?: return
        if (!RolePermissions.canAccessAccounting(actor.role)) return
        if (name.isBlank()) return
        val fp = "busyItem|${name.trim().lowercase()}|$hsn"
        if (!txnGuard.tryClaim(txnId, fp)) return
        syncRepo.updateAndSyncKey(SupabaseSyncRepository.KEY_BUSY_VOUCHERS) { snap ->
            snap.copy(
                busyItems = snap.busyItems + BusyItem(
                    name = name.trim(),
                    hsnCode = hsn.trim().ifBlank { "854140" },
                    unit = unit.trim().ifBlank { "Nos" },
                    gstRatePercent = gstRate.coerceAtLeast(0.0),
                    defaultSaleRate = saleRate.coerceAtLeast(0.0),
                    defaultPurchaseRate = purchaseRate.coerceAtLeast(0.0),
                    stockQty = openingStock.coerceAtLeast(0.0)
                )
            )
        }
        showToast("Item Master '${name.trim()}' saved.")
    }

    fun postBusyVoucher(
        type: BusyVoucherType,
        date: String,
        partyAccount: BusyAccount,
        contraAccount: BusyAccount,
        costCenter: LeadSource,
        isInterStateIgst: Boolean,
        taxableAmount: Double,
        gstPercent: Double,
        billRefType: BillRefType,
        billRefNo: String,
        utrOrChequeNo: String,
        paidBy: String = "",
        narration: String,
        lineItems: List<BusyLineItem>,
        explicitCgst: Double? = null,
        explicitSgst: Double? = null,
        explicitIgst: Double? = null,
        customVoucherNo: String? = null,
        txnId: String = ""
    ): Boolean {
        val actor = _currentUser.value ?: return false
        if (!RolePermissions.canAccessAccounting(actor.role)) {
            showToast("Accounting restricted to Owner & Accounts Manager.")
            return false
        }
        val fp = "busyVch|${type.name}|${partyAccount.id}|${contraAccount.id}|$date|$taxableAmount|${utrOrChequeNo.trim()}"
        if (!txnGuard.tryClaim(txnId, fp)) {
            return true // Already submitted on first tap; treat duplicate tap as handled so UI dismisses cleanly
        }

        // Duplicate UTR guard for Payment / Receipt
        val cleanUtr = utrOrChequeNo.trim()
        if (cleanUtr.isNotBlank() && (type == BusyVoucherType.PAYMENT || type == BusyVoucherType.RECEIPT)) {
            val dup = syncRepo.snapshot.value.busyVouchers.any {
                it.utrOrChequeNo.equals(cleanUtr, ignoreCase = true)
            }
            if (dup) {
                txnGuard.releaseOnValidationFailure(txnId, fp)
                showToast("Blocked duplicate UTR/Cheque No: $cleanUtr already exists!")
                return false
            }
        }

        val computedTaxable = if (lineItems.isNotEmpty()) {
            lineItems.sumOf { it.taxableAmount }
        } else {
            taxableAmount.coerceAtLeast(0.0)
        }

        val (cgst, sgst, igst) = when {
            explicitCgst != null || explicitSgst != null || explicitIgst != null -> {
                Triple(explicitCgst ?: 0.0, explicitSgst ?: 0.0, explicitIgst ?: 0.0)
            }
            type == BusyVoucherType.SALES || type == BusyVoucherType.PURCHASE -> {
                val totalTax = if (lineItems.isNotEmpty()) {
                    lineItems.sumOf { it.gstAmount }
                } else {
                    computedTaxable * (gstPercent / 100.0)
                }
                if (isInterStateIgst) Triple(0.0, 0.0, totalTax)
                else Triple(totalTax / 2.0, totalTax / 2.0, 0.0)
            }
            else -> Triple(0.0, 0.0, 0.0)
        }

        val total = computedTaxable + cgst + sgst + igst
        if (total <= 0.0) {
            txnGuard.releaseOnValidationFailure(txnId, fp)
            showToast("Voucher amount must be greater than ₹0.")
            return false
        }

        val nextNum = syncRepo.snapshot.value.busyVouchers.count { it.type == type } + 1
        val vchNo = customVoucherNo?.takeIf { it.isNotBlank() }
            ?: if (type == BusyVoucherType.SALES) {
                "NB ${nextNum.toString().padStart(2, '0')}/2026-27"
            } else {
                "${type.prefix}-2627-${nextNum.toString().padStart(4, '0')}"
            }

        val voucher = BusyVoucher(
            type = type,
            voucherNo = vchNo,
            date = date.ifBlank { todayIsoDate() },
            partyAccountId = partyAccount.id,
            partyAccountName = partyAccount.name,
            contraAccountId = contraAccount.id,
            contraAccountName = contraAccount.name,
            costCenter = costCenter,
            isInterStateIgst = isInterStateIgst,
            taxableAmount = computedTaxable,
            cgstAmount = cgst,
            sgstAmount = sgst,
            igstAmount = igst,
            totalAmount = total,
            billRefType = billRefType,
            billRefNo = billRefNo.trim().ifBlank { vchNo },
            utrOrChequeNo = cleanUtr,
            paidBy = paidBy.trim(),
            narration = narration.trim(),
            lineItems = lineItems,
            partyGstin = partyAccount.gstin,
            createdBy = actor.displayName
        )

        syncRepo.updateAndSyncKey(SupabaseSyncRepository.KEY_BUSY_VOUCHERS) { snap ->
            val updatedItems = if (lineItems.isNotEmpty() && (type == BusyVoucherType.SALES || type == BusyVoucherType.PURCHASE)) {
                snap.busyItems.map { item ->
                    val matchedQty = lineItems
                        .filter { it.itemId == item.id || it.itemName.equals(item.name, ignoreCase = true) }
                        .sumOf { it.quantity }
                    if (matchedQty == 0.0) item else {
                        val delta = if (type == BusyVoucherType.PURCHASE) matchedQty else -matchedQty
                        item.copy(stockQty = (item.stockQty + delta).coerceAtLeast(0.0))
                    }
                }
            } else {
                snap.busyItems
            }
            snap.copy(
                busyVouchers = listOf(voucher) + snap.busyVouchers,
                busyItems = updatedItems
            )
        }

        // Auto-update Party receivedAmount when posting a Receipt (F6) linked to a party!
        if (type == BusyVoucherType.RECEIPT) {
            val linkedParty = syncRepo.snapshot.value.parties.firstOrNull {
                it.id == partyAccount.linkedPartyId || it.partyName.equals(partyAccount.name, ignoreCase = true)
            }
            if (linkedParty != null) {
                syncRepo.updateAndSyncKey(SupabaseSyncRepository.KEY_PARTIES) { snap ->
                    snap.copy(
                        parties = snap.parties.map { p ->
                            if (p.id != linkedParty.id) p else p.copy(
                                receivedAmount = p.receivedAmount + total
                            )
                        }
                    )
                }
            }
        }

        showToast("Posted ${type.label} (${type.shortcut}) #$vchNo")
        return true
    }

    fun deleteVoucher(voucherId: String) {
        val actor = _currentUser.value ?: return
        if (!RolePermissions.canDeleteRecords(actor.role)) {
            showToast("Only Owner / CEO can delete vouchers.")
            return
        }
        syncRepo.updateAndSyncKey(SupabaseSyncRepository.KEY_BUSY_VOUCHERS) { snap ->
            snap.copy(busyVouchers = snap.busyVouchers.filterNot { it.id == voucherId })
        }
        showToast("Voucher deleted.")
    }

    // --- SMART AI AUTO-IMPORT INSIDE BUSY 21 (FIREBASE AI LOGIC) ---

    fun resetAiScanner() {
        _aiScanState.value = AiScanUiState.Idle
    }

    fun scanBankStatementFromUri(uri: Uri, bankAccountId: String) {
        val actor = _currentUser.value ?: return
        if (!RolePermissions.canAccessAccounting(actor.role)) return

        viewModelScope.launch {
            _aiScanState.value = AiScanUiState.Scanning("Analyzing Bank Statement with Gemini AI…")
            val (bitmap, pdfBytes, rawText) = withContext(Dispatchers.IO) {
                readUriForAi(uri)
            }
            if (bitmap == null && pdfBytes == null && rawText.isNullOrBlank()) {
                _aiScanState.value = AiScanUiState.Error("Could not read the selected file. Try a clear image, PDF, or CSV.")
                return@launch
            }
            runBankScan(bitmap, pdfBytes, rawText, bankAccountId)
        }
    }

    fun scanBankStatementFromText(rawText: String, bankAccountId: String) {
        val actor = _currentUser.value ?: return
        if (!RolePermissions.canAccessAccounting(actor.role)) return
        if (rawText.isBlank()) {
            showToast("Paste bank statement rows or CSV text first.")
            return
        }
        viewModelScope.launch {
            _aiScanState.value = AiScanUiState.Scanning("Parsing Bank Statement entries with Gemini AI…")
            runBankScan(null, null, rawText, bankAccountId)
        }
    }

    private suspend fun runBankScan(
        bitmap: Bitmap?,
        pdfBytes: ByteArray?,
        rawText: String?,
        bankAccountId: String
    ) {
        val snap = syncRepo.snapshot.value
        val existingUtrs = snap.busyVouchers.map { it.utrOrChequeNo }.filter { it.isNotBlank() }.toSet()
        val result = aiScanner.scanBankStatement(
            bitmap = bitmap,
            pdfBytes = pdfBytes,
            rawText = rawText,
            existingAccounts = snap.busyAccounts,
            existingUtrs = existingUtrs
        )
        result.fold(
            onSuccess = { rows ->
                if (rows.isEmpty()) {
                    _aiScanState.value = AiScanUiState.Error("No debit/credit transactions were detected in this statement.")
                } else {
                    _aiScanState.value = AiScanUiState.BankReview(rows, bankAccountId)
                }
            },
            onFailure = { err ->
                _aiScanState.value = AiScanUiState.Error(err.message ?: "Bank Statement AI scan failed.")
            }
        )
    }

    fun toggleBankRowSelection(rowId: String) {
        val state = _aiScanState.value as? AiScanUiState.BankReview ?: return
        _aiScanState.value = state.copy(
            rows = state.rows.map { r ->
                if (r.id == rowId && !r.isDuplicateUtr) r.copy(selected = !r.selected) else r
            }
        )
    }

    fun postReviewedBankVouchers(txnId: String = "") {
        val state = _aiScanState.value as? AiScanUiState.BankReview ?: return
        val toPost = state.rows.filter { it.selected && !it.isDuplicateUtr }
        if (toPost.isEmpty()) {
            showToast("No non-duplicate rows selected to post.")
            return
        }
        val fp = "postAiBank|${toPost.size}|${toPost.firstOrNull()?.id.orEmpty()}"
        if (!txnGuard.tryClaim(txnId, fp)) return
        val snap = syncRepo.snapshot.value
        val bankAcc = snap.busyAccounts.firstOrNull { it.id == state.bankAccountId }
            ?: snap.busyAccounts.firstOrNull { it.group == BusyAccountGroup.BANK_ACCOUNTS }
            ?: snap.busyAccounts.firstOrNull()
            ?: run {
                txnGuard.releaseOnValidationFailure(txnId, fp)
                return
            }

        var postedCount = 0
        toPost.forEach { row ->
            var partyAcc = syncRepo.snapshot.value.busyAccounts.firstOrNull {
                it.id == row.matchedAccountId || it.name.equals(row.partyName, ignoreCase = true)
            }
            if (partyAcc == null) {
                val grp = if (row.voucherType == BusyVoucherType.RECEIPT) {
                    BusyAccountGroup.SUNDRY_DEBTORS
                } else {
                    BusyAccountGroup.SUNDRY_CREDITORS
                }
                partyAcc = BusyAccount(name = row.partyName, group = grp, defaultCostCenter = row.costCenter)
                val newAcc = partyAcc
                syncRepo.updateAndSyncKey(SupabaseSyncRepository.KEY_BUSY_VOUCHERS) { s ->
                    s.copy(busyAccounts = s.busyAccounts + newAcc)
                }
            }
            val ok = postBusyVoucher(
                type = row.voucherType,
                date = row.date,
                partyAccount = partyAcc,
                contraAccount = bankAcc,
                costCenter = row.costCenter,
                isInterStateIgst = false,
                taxableAmount = row.amount,
                gstPercent = 0.0,
                billRefType = BillRefType.ON_ACCOUNT,
                billRefNo = row.utrNo,
                utrOrChequeNo = row.utrNo,
                narration = row.narration,
                lineItems = emptyList()
            )
            if (ok) postedCount++
        }
        _aiScanState.value = AiScanUiState.Idle
        showToast("Auto-posted $postedCount BUSY vouchers (duplicate UTRs skipped).")
    }

    fun scanPurchaseBillFromUri(uri: Uri) {
        val actor = _currentUser.value ?: return
        if (!RolePermissions.canAccessAccounting(actor.role)) return
        viewModelScope.launch {
            _aiScanState.value = AiScanUiState.Scanning("Extracting GST Invoice & Items with Gemini AI…")
            val (bitmap, pdfBytes, rawText) = withContext(Dispatchers.IO) {
                readUriForAi(uri)
            }
            if (bitmap == null && pdfBytes == null && rawText.isNullOrBlank()) {
                _aiScanState.value = AiScanUiState.Error("Could not read selected bill file.")
                return@launch
            }
            val result = aiScanner.scanPurchaseBill(bitmap = bitmap, pdfBytes = pdfBytes, rawText = rawText)
            result.fold(
                onSuccess = { bill -> _aiScanState.value = AiScanUiState.BillReview(bill) },
                onFailure = { err -> _aiScanState.value = AiScanUiState.Error(err.message ?: "Bill scan failed.") }
            )
        }
    }

    fun postReviewedPurchaseBill(bill: ParsedPurchaseBill, costCenter: LeadSource, txnId: String = "") {
        val fp = "postAiBill|${bill.supplierName}|${bill.invoiceNo}|${bill.totalAmount}"
        if (!txnGuard.tryClaim(txnId, fp)) return
        val snap = syncRepo.snapshot.value
        var supplierAcc = snap.busyAccounts.firstOrNull {
            it.name.equals(bill.supplierName, ignoreCase = true)
        }
        if (supplierAcc == null) {
            supplierAcc = BusyAccount(
                name = bill.supplierName,
                group = BusyAccountGroup.SUNDRY_CREDITORS,
                gstin = bill.supplierGstin,
                defaultCostCenter = costCenter
            )
            val createdSupplier = supplierAcc
            syncRepo.updateAndSyncKey(SupabaseSyncRepository.KEY_BUSY_VOUCHERS) { s ->
                s.copy(busyAccounts = s.busyAccounts + createdSupplier)
            }
        }

        val purchaseAcc = snap.busyAccounts.firstOrNull { it.group == BusyAccountGroup.PURCHASE_ACCOUNTS }
            ?: BusyAccount(name = "Solar Materials Purchase (GST)", group = BusyAccountGroup.PURCHASE_ACCOUNTS)

        val busyLineItems = bill.lineItems.map { li ->
            BusyLineItem(
                itemName = li.itemName,
                hsnCode = li.hsnCode,
                quantity = li.quantity,
                unit = li.unit,
                rate = li.rate,
                gstRatePercent = li.gstRatePercent
            )
        }

        val ok = postBusyVoucher(
            type = BusyVoucherType.PURCHASE,
            date = bill.invoiceDate,
            partyAccount = supplierAcc,
            contraAccount = purchaseAcc,
            costCenter = costCenter,
            isInterStateIgst = bill.igstAmount > 0.0,
            taxableAmount = bill.taxableAmount,
            gstPercent = 0.0,
            billRefType = BillRefType.NEW_REF,
            billRefNo = bill.invoiceNo,
            utrOrChequeNo = "",
            narration = "AI Scanned Purchase Bill #${bill.invoiceNo} (GSTIN: ${bill.supplierGstin})",
            lineItems = busyLineItems,
            explicitCgst = bill.cgstAmount,
            explicitSgst = bill.sgstAmount,
            explicitIgst = bill.igstAmount,
            customVoucherNo = bill.invoiceNo
        )

        if (ok) {
            // Also add any new items to BUSY Item Master + Raw Materials + DCR Panel Serials!
            if (bill.detectedDcrSerials.isNotEmpty()) {
                addDcrPanelsBatch(
                    serialsRaw = bill.detectedDcrSerials.joinToString("\n"),
                    brand = bill.detectedPanelBrand.ifBlank { "Waaree" },
                    wattage = bill.detectedPanelWattage,
                    supplierName = bill.supplierName,
                    invoiceNo = bill.invoiceNo
                )
            }
            bill.lineItems.forEach { li ->
                val matchingRm = syncRepo.snapshot.value.rawMaterials.firstOrNull {
                    it.name.contains(li.itemName, ignoreCase = true) ||
                        li.itemName.contains(it.name.split(" ").first(), ignoreCase = true)
                }
                if (matchingRm != null) {
                    addRawMaterialStock(
                        materialId = matchingRm.id,
                        name = matchingRm.name,
                        category = matchingRm.category,
                        unit = matchingRm.unit,
                        supplierName = bill.supplierName,
                        addedQty = li.quantity,
                        minAlert = matchingRm.minStockAlert
                    )
                }
            }
            _aiScanState.value = AiScanUiState.Idle
            showToast("Posted Purchase (F9) #${bill.invoiceNo} & synced Inventory!")
        }
    }

    // --- AI SITE PHOTO PLACEMENT (GEMINI NANO BANANA 2.1) ---

    fun setRooftopBeforePhoto(uri: Uri) {
        viewModelScope.launch {
            val (bmp, _, _) = withContext(Dispatchers.IO) { readUriForAi(uri) }
            if (bmp == null) {
                _aiPhotoState.value = _aiPhotoState.value.copy(rooftopError = "Unable to decode selected rooftop photo.")
            } else {
                _aiPhotoState.value = _aiPhotoState.value.copy(rooftopBefore = bmp, rooftopAfter = null, rooftopError = null)
            }
        }
    }

    fun setInverterBeforePhoto(uri: Uri) {
        viewModelScope.launch {
            val (bmp, _, _) = withContext(Dispatchers.IO) { readUriForAi(uri) }
            if (bmp == null) {
                _aiPhotoState.value = _aiPhotoState.value.copy(inverterError = "Unable to decode selected inverter wall photo.")
            } else {
                _aiPhotoState.value = _aiPhotoState.value.copy(inverterBefore = bmp, inverterAfter = null, inverterError = null)
            }
        }
    }

    fun generateAiRooftopPlacement(
        capacityKw: Double,
        panelCount: Int,
        structureType: CostingStructureType,
        systemType: SolarSystemType
    ) {
        val before = _aiPhotoState.value.rooftopBefore ?: return
        viewModelScope.launch {
            _aiPhotoState.value = _aiPhotoState.value.copy(rooftopLoading = true, rooftopError = null)
            val result = aiPhotoRepo.placeSolarPanelsOnRooftop(before, capacityKw, panelCount, structureType, systemType)
            result.fold(
                onSuccess = { edited ->
                    _aiPhotoState.value = _aiPhotoState.value.copy(rooftopAfter = edited, rooftopLoading = false)
                },
                onFailure = { err ->
                    _aiPhotoState.value = _aiPhotoState.value.copy(
                        rooftopLoading = false,
                        rooftopError = err.message ?: "Rooftop AI placement failed."
                    )
                }
            )
        }
    }

    fun generateAiInverterPlacement(capacityKw: Double, systemType: SolarSystemType) {
        val before = _aiPhotoState.value.inverterBefore ?: return
        viewModelScope.launch {
            _aiPhotoState.value = _aiPhotoState.value.copy(inverterLoading = true, inverterError = null)
            val result = aiPhotoRepo.placeInverterOnWall(before, capacityKw, systemType)
            result.fold(
                onSuccess = { edited ->
                    _aiPhotoState.value = _aiPhotoState.value.copy(inverterAfter = edited, inverterLoading = false)
                },
                onFailure = { err ->
                    _aiPhotoState.value = _aiPhotoState.value.copy(
                        inverterLoading = false,
                        inverterError = err.message ?: "Inverter wall AI placement failed."
                    )
                }
            )
        }
    }

    fun clearAiPhotoErrors() {
        _aiPhotoState.value = _aiPhotoState.value.copy(rooftopError = null, inverterError = null)
    }

    // --- OWNER AUTOMATED SELF-TEST & HEALTH CHECK ---

    fun runAppSelfTest() {
        if (_isRunningSelfTest.value) return
        viewModelScope.launch {
            _isRunningSelfTest.value = true
            val checks = mutableListOf<HealthCheckItem>()
            val snap = syncRepo.snapshot.value

            // 1. Live Supabase nb_solar_store Read/Write Ping
            val pingResult = syncRepo.pingHealthCheck()
            checks += HealthCheckItem(
                name = "Supabase Cloud R/W Ping (nb_solar_store)",
                passed = pingResult.isSuccess,
                detail = pingResult.getOrElse { err ->
                    if (com.example.data.SupabaseStoreService.isPermissionDeniedError(err)) {
                        "FAIL: 42501 RLS grant needed — copy SQL fix above."
                    } else {
                        "FAIL: ${err.message ?: "Unreachable (offline queue active)"}"
                    }
                }
            )

            // 2. Owner, Solar & Wind Role Isolation Check
            val ownerUser = snap.staffUsers.firstOrNull { it.role == StaffRole.OWNER }
            val solarStaff = snap.staffUsers.firstOrNull { it.loginId == "solar.sup@nb" }
            val windStaff = snap.staffUsers.firstOrNull { it.loginId == "wind.sup@nb" }
            val divisionOk = ownerUser?.assignedDivision == null &&
                solarStaff?.assignedDivision == ProjectDivision.SOLAR &&
                windStaff?.assignedDivision == ProjectDivision.WIND &&
                !RolePermissions.canViewFinancials(StaffRole.SUPERVISOR) &&
                !RolePermissions.canViewPartyProfitability(StaffRole.OFFICE) &&
                RolePermissions.canViewPartyProfitability(StaffRole.OWNER)
            checks += HealthCheckItem(
                name = "Division Isolation & RBAC Privacy Matrix",
                passed = divisionOk,
                detail = if (divisionOk) {
                    "PASS: Owner=All, Solar/Wind strictly isolated, Field/Office privacy locks enforced."
                } else {
                    "FAIL: Built-in division or role permission rules mismatched."
                }
            )

            // 3. Real Costing & PM Surya Ghar Subsidy Check
            val rates = RealCostingCalculator.extractLatestPurchaseRates(
                snap.busyVouchers,
                snap.busyItems,
                CostingStructureType.STANDARD_RCC
            )
            val sub1 = RealCostingCalculator.computePmSuryaGharSubsidy(1.0, SolarSystemType.ON_GRID_DCR)
            val sub2 = RealCostingCalculator.computePmSuryaGharSubsidy(2.0, SolarSystemType.ON_GRID_DCR)
            val sub3 = RealCostingCalculator.computePmSuryaGharSubsidy(3.0, SolarSystemType.ON_GRID_DCR)
            val subOff = RealCostingCalculator.computePmSuryaGharSubsidy(5.0, SolarSystemType.OFF_GRID)
            val b3 = RealCostingCalculator.computeRealCosting(
                capacityKw = 3.0,
                systemType = SolarSystemType.ON_GRID_DCR,
                structureType = CostingStructureType.STANDARD_RCC,
                dimensions = SiteSurveyDimensions(),
                rates = rates
            )
            val mathOk = sub1 == 30_000.0 && sub2 == 60_000.0 && sub3 == 78_000.0 && subOff == 0.0 &&
                kotlin.math.abs((b3.taxableQuotedPriceInr + b3.gstAmountInr) - b3.grossQuotedPriceInr) < 1.0 &&
                kotlin.math.abs((b3.grossQuotedPriceInr - b3.pmSuryaGharSubsidyInr) - b3.finalNetCostAfterSubsidyInr) < 1.0 &&
                b3.grossQuotedPriceInr > 0.0
            checks += HealthCheckItem(
                name = "Live Real Costing & PM Surya Ghar Subsidy",
                passed = mathOk,
                detail = if (mathOk) {
                    "PASS: 1kW=₹30k, 2kW=₹60k, 3kW+=₹78k; 3kW Net=₹${b3.finalNetCostAfterSubsidyInr.toLong()}"
                } else {
                    "FAIL: Costing or subsidy calculation out of bounds."
                }
            )

            // 4. BUSY 21 Double-Entry & GST Accounting Audit
            val busyOk = runCatching {
                val tb = BusyAccountingEngine.computeTrialBalance(snap.busyAccounts, snap.busyVouchers)
                val costing = BusyAccountingEngine.computePartyCostingReport(
                    snap.parties, snap.dcrPanels, snap.solarMeters, snap.rawMaterials, snap.siteKharcha
                )
                tb.groups.isNotEmpty() && costing.size == snap.parties.size
            }.getOrDefault(false)
            checks += HealthCheckItem(
                name = "BUSY 21 Double-Entry & Costing Engine",
                passed = busyOk,
                detail = if (busyOk) {
                    "PASS: ${snap.busyAccounts.size} ledgers, ${snap.busyVouchers.size} vouchers, ${snap.parties.size} parties verified."
                } else {
                    "FAIL: BUSY 21 engine encountered an exception."
                }
            )

            // 5. Proposal PDF 4-Format Template & Print CSS Check
            val appCtx = getApplication<Application>().applicationContext
            val allFormatsHtml = ProposalFormat.entries.map { fmt ->
                ExportPrintHelper.buildProposalHtml(
                    context = appCtx,
                    format = fmt,
                    customerName = "QA Diagnostics Check",
                    customerMobile = "9929214883",
                    siteLocation = "Udaipur, Rajasthan",
                    consumerNo = "K-1001",
                    breakdown = b3,
                    comparisonBreakdowns = listOf(b3),
                    dimensions = SiteSurveyDimensions(),
                    includeOwnerInternalCosting = false
                )
            }
            val pdfOk = allFormatsHtml.size == 4 && allFormatsHtml.all { html ->
                html.contains("08CSZPK9277N2Z8") &&
                    html.contains("50200079191430") &&
                    html.contains("break-inside: avoid") &&
                    html.contains("NB ENGINEERING WORKS AND ELECTRICALS")
            }
            checks += HealthCheckItem(
                name = "Proposal PDF 4-Format HTML & A4 Print CSS",
                passed = pdfOk,
                detail = if (pdfOk) {
                    "PASS: All 4 formats rendered with GSTIN, HDFC Bank & page-break protection."
                } else {
                    "FAIL: One or more Proposal PDF templates missing company/CSS markers."
                }
            )

            _healthReport.value = AppHealthReport(
                ranAt = nowTimestamp(),
                checks = checks
            )
            _isRunningSelfTest.value = false
        }
    }

    // --- OWNER 1-CLICK BACKUP, EXCEL WORKBOOK, RESTORE & STORAGE HEALTH ---

    fun unlockBackupWithMasterKey(enteredKey: String): Boolean {
        val actor = _currentUser.value ?: return false
        if (actor.role != StaffRole.OWNER) {
            showToast("Company Backup & Restore is restricted to Owner / CEO.")
            return false
        }
        return if (BackupRestoreEngine.verifyOwnerMasterKey(enteredKey)) {
            _backupMasterUnlocked.value = true
            showToast("Owner Master Security Key verified. Backup & Restore unlocked.")
            true
        } else {
            showToast("Invalid Owner Master Security Key.")
            false
        }
    }

    fun lockBackupPanel() {
        _backupMasterUnlocked.value = false
    }

    fun downloadFullCompanyBackupJson() {
        val actor = _currentUser.value ?: return
        if (actor.role != StaffRole.OWNER || !_backupMasterUnlocked.value) {
            showToast("Unlock with Owner Master Security Key first.")
            return
        }
        if (_isBackupBusy.value) return
        viewModelScope.launch {
            _isBackupBusy.value = true
            try {
                val snap = syncRepo.snapshot.value
                val today = todayIsoDate()
                val json = withContext(Dispatchers.IO) {
                    BackupRestoreEngine.buildFullBackupJson(
                        snapshot = snap,
                        exportedBy = actor.displayName,
                        exportedAt = nowTimestamp(),
                        exportedDate = today
                    )
                }
                val res = ExportPrintHelper.exportAndShareFullBackupJson(getApplication(), json, today)
                res.fold(
                    onSuccess = { file ->
                        _lastBackupDateIso.value = today
                        getApplication<Application>().sessionDataStore.edit { prefs ->
                            prefs[KEY_LAST_BACKUP_ISO] = today
                        }
                        _healthRefreshTick.value = System.currentTimeMillis()
                        showToast("Full Company Backup saved & ready: ${file.name}")
                    },
                    onFailure = { err ->
                        showToast("Backup export failed: ${err.message ?: "error"}")
                    }
                )
            } finally {
                _isBackupBusy.value = false
            }
        }
    }

    fun exportMasterExcelWorkbook() {
        val actor = _currentUser.value ?: return
        if (actor.role != StaffRole.OWNER || !_backupMasterUnlocked.value) {
            showToast("Unlock with Owner Master Security Key first.")
            return
        }
        if (_isBackupBusy.value) return
        viewModelScope.launch {
            _isBackupBusy.value = true
            try {
                val snap = syncRepo.snapshot.value
                val today = todayIsoDate()
                val bytes = withContext(Dispatchers.IO) {
                    BackupRestoreEngine.buildMasterExcelWorkbookXlsx(snap)
                }
                val res = ExportPrintHelper.exportAndShareMasterExcelXlsx(getApplication(), bytes, today)
                res.fold(
                    onSuccess = { file ->
                        _healthRefreshTick.value = System.currentTimeMillis()
                        showToast("Master Excel Workbook exported: ${file.name}")
                    },
                    onFailure = { err ->
                        showToast("Excel export failed: ${err.message ?: "error"}")
                    }
                )
            } finally {
                _isBackupBusy.value = false
            }
        }
    }

    fun exportMasterSummaryCsv() {
        val actor = _currentUser.value ?: return
        if (actor.role != StaffRole.OWNER || !_backupMasterUnlocked.value) {
            showToast("Unlock with Owner Master Security Key first.")
            return
        }
        if (_isBackupBusy.value) return
        viewModelScope.launch {
            _isBackupBusy.value = true
            try {
                val snap = syncRepo.snapshot.value
                val today = todayIsoDate()
                val csv = withContext(Dispatchers.IO) {
                    BackupRestoreEngine.buildMasterSummaryCsv(snap)
                }
                val res = ExportPrintHelper.exportAndShareMasterSummaryCsv(getApplication(), csv, today)
                res.fold(
                    onSuccess = { file ->
                        _healthRefreshTick.value = System.currentTimeMillis()
                        showToast("Master CSV exported: ${file.name}")
                    },
                    onFailure = { err ->
                        showToast("CSV export failed: ${err.message ?: "error"}")
                    }
                )
            } finally {
                _isBackupBusy.value = false
            }
        }
    }

    fun previewRestoreFromBackupUri(uri: Uri) {
        val actor = _currentUser.value ?: return
        if (actor.role != StaffRole.OWNER || !_backupMasterUnlocked.value) {
            showToast("Unlock with Owner Master Security Key first.")
            return
        }
        if (_isBackupBusy.value) return
        viewModelScope.launch {
            _isBackupBusy.value = true
            try {
                val rawJson = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        .orEmpty()
                }
                val previewRes = withContext(Dispatchers.IO) {
                    BackupRestoreEngine.parseAndPreviewBackupJson(rawJson)
                }
                previewRes.fold(
                    onSuccess = { preview ->
                        _pendingRestorePreview.value = preview
                    },
                    onFailure = { err ->
                        showToast(err.message ?: "Invalid backup JSON file.")
                    }
                )
            } finally {
                _isBackupBusy.value = false
            }
        }
    }

    fun dismissRestorePreview() {
        _pendingRestorePreview.value = null
    }

    fun confirmRestoreFromBackup() {
        val actor = _currentUser.value ?: return
        if (actor.role != StaffRole.OWNER || !_backupMasterUnlocked.value) {
            showToast("Owner Master Security Key required to restore backup.")
            return
        }
        val preview = _pendingRestorePreview.value ?: return
        if (_isBackupBusy.value) return
        viewModelScope.launch {
            _isBackupBusy.value = true
            try {
                val res = syncRepo.restoreFullSnapshotToCloud(preview.snapshot)
                _pendingRestorePreview.value = null
                _healthRefreshTick.value = System.currentTimeMillis()
                res.fold(
                    onSuccess = { msg -> showToast(msg) },
                    onFailure = { err -> showToast("Restore failed: ${err.message ?: "error"}") }
                )
            } finally {
                _isBackupBusy.value = false
            }
        }
    }

    fun flushOfflineQueueNow() {
        if (_isFlushingQueue.value) return
        viewModelScope.launch {
            _isFlushingQueue.value = true
            try {
                val res = syncRepo.flushOfflineQueueNow()
                _healthRefreshTick.value = System.currentTimeMillis()
                res.fold(
                    onSuccess = { msg -> showToast(msg) },
                    onFailure = { err -> showToast(err.message ?: "Flush queue failed.") }
                )
            } finally {
                _isFlushingQueue.value = false
            }
        }
    }

    fun clearLocalCacheCloudSafe() {
        viewModelScope.launch {
            val freed = withContext(Dispatchers.IO) {
                syncRepo.clearLocalCacheCloudSafe()
            }
            _healthRefreshTick.value = System.currentTimeMillis()
            showToast("Cleared ${BackupRestoreEngine.formatByteSize(freed)} temporary cache safely. Cloud & Document Vault untouched.")
        }
    }

    // --- MEDIA HELPERS ---

    private fun readUriForAi(uri: Uri): Triple<Bitmap?, ByteArray?, String?> {
        return try {
            val resolver = getApplication<Application>().contentResolver
            val mime = resolver.getType(uri).orEmpty().lowercase()
            val fileName = queryFileName(uri).orEmpty().lowercase()

            when {
                mime.startsWith("image/") ||
                    fileName.endsWith(".jpg") ||
                    fileName.endsWith(".jpeg") ||
                    fileName.endsWith(".png") ||
                    fileName.endsWith(".webp") ||
                    fileName.endsWith(".heic") -> {
                    val compressed = ImageCompressor.compressUriToJpegAndBitmap(
                        context = getApplication(),
                        uri = uri,
                        maxWidth = ImageCompressor.DEFAULT_MAX_WIDTH,
                        maxBytes = ImageCompressor.DEFAULT_MAX_BYTES
                    )
                    Triple(compressed?.bitmap, null, null)
                }
                mime == "application/pdf" || fileName.endsWith(".pdf") -> {
                    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes != null && bytes.size < 18 * 1024 * 1024) {
                        Triple(null, bytes, null)
                    } else {
                        Triple(null, null, null)
                    }
                }
                else -> {
                    val text = resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    Triple(null, null, text?.take(60_000))
                }
            }
        } catch (_: Exception) {
            Triple(null, null, null)
        }
    }

    private fun queryFileName(uri: Uri): String? {
        return try {
            getApplication<Application>().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        syncRepo.close()
    }
}

private data class NavFilterTuple(
    val user: StaffUser?,
    val err: String?,
    val tab: MainNavTab,
    val partyId: String?,
    val source: LeadSource?
)

private data class SearchToastTuple(
    val pSearch: String,
    val iSearch: String,
    val toast: String?,
    val aiState: AiScanUiState,
    val darkOverride: Boolean?,
    val division: ProjectDivision?
)

private data class DiagnosticsBundle(
    val photoState: AiPhotoPlacementUiState,
    val healthReport: AppHealthReport?,
    val isRunningSelfTest: Boolean,
    val showBulkLoanImporter: Boolean,
    val bulkLoanScanState: BulkLoanScanUiState,
    val showRemindersDrawer: Boolean
)

private data class BackupSafetyBundle(
    val lastBackupDateIso: String,
    val pendingRestorePreview: BackupRestoreEngine.BackupPreview?,
    val isBackupBusy: Boolean,
    val isFlushingQueue: Boolean,
    val backupMasterUnlocked: Boolean
)

private data class CoreSnapshotTuple(
    val snapshot: AppDatabaseSnapshot,
    val syncStatus: SyncStatus,
    val safety: BackupSafetyBundle,
    val healthRefreshTick: Long
)
