package com.example.data

import android.content.Context
import android.util.Log
import com.example.core.nowTimestamp
import com.example.data.model.AppDatabaseSnapshot
import com.example.data.model.BusyAccount
import com.example.data.model.BusyAccountGroup
import com.example.data.model.BusyItem
import com.example.data.model.JsonCodec
import com.example.data.model.LeadSource
import com.example.data.model.RawMaterial
import com.example.data.model.StaffRole
import com.example.data.model.StaffUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "SupabaseSyncRepo"

enum class SyncConnectionState {
    LIVE,
    SYNCING,
    OFFLINE
}

data class SyncStatus(
    val state: SyncConnectionState = SyncConnectionState.SYNCING,
    val lastSyncedAt: String = "Connecting…",
    val detail: String = "Connecting to Supabase (nb_solar_store)…",
    val needsRlsGrant: Boolean = false,
    val queuedCount: Int = 0
)

/**
 * Permanent cloud-first repository connected to Supabase `nb_solar_store`
 * (no local disk cache fallback — all reads/writes and real-time updates flow directly
 * through [SupabaseStoreService]).
 */
class SupabaseSyncRepository(
    @Suppress("UNUSED_PARAMETER") private val context: Context,
    private val storeService: SupabaseStoreService = SupabaseStoreService()
) {

    companion object {
        val SUPABASE_URL: String get() = SupabaseClientConfig.supabaseUrl
        val SUPABASE_ANON_KEY: String get() = SupabaseClientConfig.supabaseAnonKey
        const val TABLE_NAME = SupabaseClientConfig.DEFAULT_TABLE_NAME

        const val KEY_STAFF = "staff_users"
        const val KEY_PARTIES = "parties"
        const val KEY_PANELS = "inventory_panels"
        const val KEY_METERS = "inventory_meters"
        const val KEY_RAW_MATERIALS = "inventory_raw_materials"
        const val KEY_ACCOUNTS = "busy_accounts"
        const val KEY_ITEMS = "busy_items"
        const val KEY_VOUCHERS = "busy_vouchers"
        const val KEY_KHARCHA = "site_kharcha"
        const val KEY_CHAT = "team_chat"
    }

    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _snapshot = MutableStateFlow(defaultInitialSnapshot())
    val snapshot: StateFlow<AppDatabaseSnapshot> = _snapshot.asStateFlow()

    private val _syncStatus = MutableStateFlow(SyncStatus())
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    @Volatile
    private var cloudReachable = false

    private val pendingOfflineKeys = ConcurrentHashMap.newKeySet<String>()

    init {
        observeRealtimeEvents()
        repoScope.launch {
            connectAndSyncForever()
        }
    }

    private suspend fun connectAndSyncForever() {
        storeService.connectRealtime()
        // Retry transient network errors; stop auto-retrying if 42501 SQL permission grant is missing
        while (repoScope.isActive) {
            val ok = pullAllFromCloud()
            if (ok || _syncStatus.value.needsRlsGrant) break
            delay(5_000L)
        }
    }

    private fun observeRealtimeEvents() {
        repoScope.launch {
            storeService.events.collect { event ->
                when (event) {
                    is SupabaseStoreEvent.RecordChanged -> {
                        cloudReachable = true
                        val updated = when (event.eventType) {
                            SupabaseRealtimeEventType.DELETE -> clearKeyPayload(_snapshot.value, event.key)
                            SupabaseRealtimeEventType.INSERT,
                            SupabaseRealtimeEventType.UPDATE -> {
                                val dataVal = event.record?.data ?: return@collect
                                applyKeyPayload(_snapshot.value, event.key, dataVal)
                            }
                        }
                        _snapshot.value = ensureRequiredDefaults(updated)
                        _syncStatus.value = SyncStatus(
                            state = SyncConnectionState.LIVE,
                            lastSyncedAt = nowTimestamp(),
                            detail = "Live update (${event.key})",
                            needsRlsGrant = false
                        )
                    }

                    is SupabaseStoreEvent.ConnectionChanged -> {
                        if (event.connected) {
                            if (!_syncStatus.value.needsRlsGrant) {
                                cloudReachable = true
                                _syncStatus.value = SyncStatus(
                                    state = SyncConnectionState.LIVE,
                                    lastSyncedAt = nowTimestamp(),
                                    detail = event.detail,
                                    needsRlsGrant = false
                                )
                                launch { pullAllFromCloud() }
                            }
                        } else if (!cloudReachable) {
                            _syncStatus.value = _syncStatus.value.copy(
                                state = SyncConnectionState.OFFLINE,
                                detail = if (_syncStatus.value.needsRlsGrant) {
                                    _syncStatus.value.detail
                                } else {
                                    event.detail
                                }
                            )
                        }
                    }

                    is SupabaseStoreEvent.Error -> {
                        Log.w(TAG, "Realtime service notice: ${event.message}")
                    }
                }
            }
        }
    }

    suspend fun pullAllFromCloud(): Boolean = withContext(Dispatchers.IO) {
        if (!cloudReachable) {
            _syncStatus.value = _syncStatus.value.copy(state = SyncConnectionState.SYNCING)
        }
        // Flush any queued offline mutations before pulling remote state
        val queuedSnapshot = pendingOfflineKeys.toList()
        if (queuedSnapshot.isNotEmpty()) {
            val currentSnap = _snapshot.value
            for (pendingKey in queuedSnapshot) {
                val queuedPayload = payloadForKey(currentSnap, pendingKey) ?: continue
                val pushRes = storeService.upsert(pendingKey, queuedPayload)
                if (pushRes.isSuccess) {
                    pendingOfflineKeys.remove(pendingKey)
                }
            }
        }

        val result = storeService.readAll()
        result.fold(
            onSuccess = { records ->
                cloudReachable = true
                var updated = _snapshot.value
                val presentKeys = mutableSetOf<String>()

                records.forEach { rec ->
                    if (rec.key !in pendingOfflineKeys) {
                        presentKeys.add(rec.key)
                        updated = applyKeyPayload(updated, rec.key, rec.data)
                    }
                }

                updated = ensureRequiredDefaults(updated)
                _snapshot.value = updated

                // Ensure core collections exist in Supabase nb_solar_store
                if (KEY_STAFF !in presentKeys) {
                    storeService.upsert(KEY_STAFF, JsonCodec.encodeStaffUsers(updated.staffUsers))
                }
                if (KEY_ACCOUNTS !in presentKeys) {
                    storeService.upsert(KEY_ACCOUNTS, JsonCodec.encodeBusyAccounts(updated.busyAccounts))
                }
                if (KEY_ITEMS !in presentKeys) {
                    storeService.upsert(KEY_ITEMS, JsonCodec.encodeBusyItems(updated.busyItems))
                }
                if (KEY_RAW_MATERIALS !in presentKeys) {
                    storeService.upsert(KEY_RAW_MATERIALS, JsonCodec.encodeRawMaterials(updated.rawMaterials))
                }

                _syncStatus.value = SyncStatus(
                    state = SyncConnectionState.LIVE,
                    lastSyncedAt = nowTimestamp(),
                    detail = "Live Supabase (${records.size} stores)" +
                        if (pendingOfflineKeys.isEmpty()) "" else " • ${pendingOfflineKeys.size} queued",
                    needsRlsGrant = false,
                    queuedCount = pendingOfflineKeys.size
                )
                true
            },
            onFailure = { err ->
                val isPermissionDenied = SupabaseStoreService.isPermissionDeniedError(err)
                cloudReachable = false
                _syncStatus.value = SyncStatus(
                    state = SyncConnectionState.OFFLINE,
                    lastSyncedAt = _syncStatus.value.lastSyncedAt,
                    detail = when {
                        isPermissionDenied ->
                            "Supabase 42501: Permission denied on nb_solar_store. Open Team & Sync to copy the GRANT/RLS SQL."
                        pendingOfflineKeys.isNotEmpty() ->
                            "Offline Queue (${pendingOfflineKeys.size} pending) — auto-syncs on reconnect"
                        else ->
                            "Supabase unreachable — retrying…"
                    },
                    needsRlsGrant = isPermissionDenied,
                    queuedCount = pendingOfflineKeys.size
                )
                false
            }
        )
    }

    /**
     * Live read/write ping against Supabase `nb_solar_store` for QA Diagnostics.
     */
    suspend fun pingHealthCheck(): Result<String> = withContext(Dispatchers.IO) {
        val readRes = storeService.readAll()
        val records = readRes.getOrElse { err ->
            return@withContext Result.failure(err)
        }
        val pingKey = "__qa_health_ping"
        val pingPayload = JSONArray().put("nb_solar_qa_ok_${System.currentTimeMillis()}")
        val writeRes = storeService.upsert(pingKey, pingPayload)
        if (writeRes.isFailure) {
            return@withContext Result.failure(writeRes.exceptionOrNull() ?: IllegalStateException("Write ping failed"))
        }
        storeService.delete(pingKey)
        Result.success("Read ${records.size} stores & R/W ping OK on nb_solar_store")
    }

    private fun payloadForKey(snap: AppDatabaseSnapshot, key: String): Any? = when (key) {
        KEY_STAFF -> JsonCodec.encodeStaffUsers(snap.staffUsers)
        KEY_PARTIES -> JsonCodec.encodeParties(snap.parties)
        KEY_PANELS -> JsonCodec.encodeDcrPanels(snap.dcrPanels)
        KEY_METERS -> JsonCodec.encodeSolarMeters(snap.solarMeters)
        KEY_RAW_MATERIALS -> JsonCodec.encodeRawMaterials(snap.rawMaterials)
        KEY_ACCOUNTS -> JsonCodec.encodeBusyAccounts(snap.busyAccounts)
        KEY_ITEMS -> JsonCodec.encodeBusyItems(snap.busyItems)
        KEY_VOUCHERS -> JsonCodec.encodeBusyVouchers(snap.busyVouchers)
        KEY_KHARCHA -> JsonCodec.encodeSiteKharcha(snap.siteKharcha)
        KEY_CHAT -> JsonCodec.encodeTeamChat(snap.teamChat)
        else -> null
    }

    /**
     * Updates reactive UI state and immediately persists the changed key to Supabase `nb_solar_store`
     * with automatic offline queue fallback if disconnected.
     */
    fun updateAndSyncKey(
        key: String,
        transform: (AppDatabaseSnapshot) -> AppDatabaseSnapshot
    ) {
        val next = ensureRequiredDefaults(transform(_snapshot.value))
        _snapshot.value = next

        val payload: Any = payloadForKey(next, key) ?: return

        repoScope.launch {
            _syncStatus.value = _syncStatus.value.copy(
                state = SyncConnectionState.SYNCING,
                queuedCount = pendingOfflineKeys.size
            )
            val result = storeService.upsert(key, payload)
            if (result.isSuccess) {
                pendingOfflineKeys.remove(key)
                // Also flush any previously queued offline keys on successful write
                for (queuedKey in pendingOfflineKeys.toList()) {
                    val qPayload = payloadForKey(_snapshot.value, queuedKey) ?: continue
                    if (storeService.upsert(queuedKey, qPayload).isSuccess) {
                        pendingOfflineKeys.remove(queuedKey)
                    }
                }
                cloudReachable = true
                _syncStatus.value = SyncStatus(
                    state = SyncConnectionState.LIVE,
                    lastSyncedAt = nowTimestamp(),
                    detail = "Live synced ($key)",
                    needsRlsGrant = false,
                    queuedCount = pendingOfflineKeys.size
                )
            } else {
                pendingOfflineKeys.add(key)
                val isPermissionDenied = SupabaseStoreService.isPermissionDeniedError(result.exceptionOrNull())
                cloudReachable = false
                _syncStatus.value = SyncStatus(
                    state = SyncConnectionState.OFFLINE,
                    lastSyncedAt = _syncStatus.value.lastSyncedAt,
                    detail = if (isPermissionDenied) {
                        "Supabase 42501: Permission denied on nb_solar_store. Open Team & Sync to copy the GRANT/RLS SQL."
                    } else {
                        "Saved to Offline Queue (${pendingOfflineKeys.size} pending) — will auto-sync"
                    },
                    needsRlsGrant = isPermissionDenied,
                    queuedCount = pendingOfflineKeys.size
                )
            }
        }
    }

    suspend fun deleteStoreKey(key: String): Result<Unit> = withContext(Dispatchers.IO) {
        val res = storeService.delete(key)
        if (res.isSuccess) {
            _snapshot.value = ensureRequiredDefaults(clearKeyPayload(_snapshot.value, key))
        }
        res
    }

    private fun applyKeyPayload(
        base: AppDatabaseSnapshot,
        key: String,
        dataVal: Any
    ): AppDatabaseSnapshot {
        val arr = when (dataVal) {
            is JSONArray -> dataVal
            is String -> runCatching { JSONArray(dataVal) }.getOrNull()
            else -> null
        } ?: return base

        return when (key) {
            KEY_STAFF -> base.copy(staffUsers = JsonCodec.decodeStaffUsers(arr))
            KEY_PARTIES -> base.copy(parties = JsonCodec.decodeParties(arr))
            KEY_PANELS -> base.copy(dcrPanels = JsonCodec.decodeDcrPanels(arr))
            KEY_METERS -> base.copy(solarMeters = JsonCodec.decodeSolarMeters(arr))
            KEY_RAW_MATERIALS -> base.copy(rawMaterials = JsonCodec.decodeRawMaterials(arr))
            KEY_ACCOUNTS -> base.copy(busyAccounts = JsonCodec.decodeBusyAccounts(arr))
            KEY_ITEMS -> base.copy(busyItems = JsonCodec.decodeBusyItems(arr))
            KEY_VOUCHERS -> base.copy(busyVouchers = JsonCodec.decodeBusyVouchers(arr))
            KEY_KHARCHA -> base.copy(siteKharcha = JsonCodec.decodeSiteKharcha(arr))
            KEY_CHAT -> base.copy(teamChat = JsonCodec.decodeTeamChat(arr))
            else -> base
        }
    }

    private fun clearKeyPayload(base: AppDatabaseSnapshot, key: String): AppDatabaseSnapshot = when (key) {
        KEY_STAFF -> base.copy(staffUsers = emptyList())
        KEY_PARTIES -> base.copy(parties = emptyList())
        KEY_PANELS -> base.copy(dcrPanels = emptyList())
        KEY_METERS -> base.copy(solarMeters = emptyList())
        KEY_RAW_MATERIALS -> base.copy(rawMaterials = emptyList())
        KEY_ACCOUNTS -> base.copy(busyAccounts = emptyList())
        KEY_ITEMS -> base.copy(busyItems = emptyList())
        KEY_VOUCHERS -> base.copy(busyVouchers = emptyList())
        KEY_KHARCHA -> base.copy(siteKharcha = emptyList())
        KEY_CHAT -> base.copy(teamChat = emptyList())
        else -> base
    }

    private fun ensureRequiredDefaults(snap: AppDatabaseSnapshot): AppDatabaseSnapshot {
        val builtIns = builtInStaffUsers()
        val builtInMap = builtIns.associateBy { it.loginId.lowercase() }
        val normalizedExisting = snap.staffUsers.map { u ->
            val bi = builtInMap[u.loginId.lowercase()]
            if (bi != null && u.assignedDivision == null && bi.assignedDivision != null) {
                u.copy(assignedDivision = bi.assignedDivision, isBuiltIn = true)
            } else u
        }
        val existingIds = normalizedExisting.map { it.loginId.lowercase() }.toSet()
        val mergedStaff = normalizedExisting + builtIns.filter { it.loginId.lowercase() !in existingIds }

        val existingAccIds = snap.busyAccounts.map { it.id }.toSet()
        val mergedAccounts = if (snap.busyAccounts.isEmpty()) {
            defaultBusyAccounts()
        } else {
            snap.busyAccounts + defaultBusyAccounts().filter { it.id !in existingAccIds }
        }
        val mergedItems = if (snap.busyItems.isEmpty()) defaultBusyItems() else snap.busyItems
        val mergedRaw = if (snap.rawMaterials.isEmpty()) defaultRawMaterials() else snap.rawMaterials

        return snap.copy(
            staffUsers = mergedStaff,
            busyAccounts = mergedAccounts,
            busyItems = mergedItems,
            rawMaterials = mergedRaw
        )
    }

    fun close() {
        storeService.close()
        repoScope.cancel()
    }

    private fun defaultInitialSnapshot(): AppDatabaseSnapshot = AppDatabaseSnapshot(
        staffUsers = builtInStaffUsers(),
        parties = emptyList(),
        dcrPanels = emptyList(),
        solarMeters = emptyList(),
        rawMaterials = defaultRawMaterials(),
        busyAccounts = defaultBusyAccounts(),
        busyItems = defaultBusyItems(),
        busyVouchers = emptyList()
    )

    private fun builtInStaffUsers(): List<StaffUser> = listOf(
        StaffUser("staff_owner", "owner@nb", "1111", "Owner / CEO", StaffRole.OWNER, true, imprestBalance = 0.0, assignedDivision = null),
        StaffUser("staff_accounts", "accounts@nb", "2222", "Accounts Manager", StaffRole.ACCOUNTS, true, imprestBalance = 0.0, assignedDivision = null),
        StaffUser("staff_solar_office", "solar.office@nb", "3331", "Solar Office Coordinator", StaffRole.OFFICE, true, imprestBalance = 0.0, assignedDivision = com.example.data.model.ProjectDivision.SOLAR),
        StaffUser("staff_solar_sup", "solar.sup@nb", "4441", "Solar Site Supervisor", StaffRole.SUPERVISOR, true, imprestBalance = 15000.0, assignedDivision = com.example.data.model.ProjectDivision.SOLAR),
        StaffUser("staff_solar_helper", "solar.helper@nb", "5551", "Solar Field Helper", StaffRole.HELPER, true, imprestBalance = 0.0, assignedDivision = com.example.data.model.ProjectDivision.SOLAR),
        StaffUser("staff_wind_office", "wind.office@nb", "3332", "Wind Office Coordinator", StaffRole.OFFICE, true, imprestBalance = 0.0, assignedDivision = com.example.data.model.ProjectDivision.WIND),
        StaffUser("staff_wind_sup", "wind.sup@nb", "4442", "Wind Site Supervisor", StaffRole.SUPERVISOR, true, imprestBalance = 15000.0, assignedDivision = com.example.data.model.ProjectDivision.WIND),
        StaffUser("staff_wind_helper", "wind.helper@nb", "5552", "Wind Field Helper", StaffRole.HELPER, true, imprestBalance = 0.0, assignedDivision = com.example.data.model.ProjectDivision.WIND)
    )

    private fun defaultBusyAccounts(): List<BusyAccount> = listOf(
        BusyAccount("acc_cash", "Cash-in-Hand (Shed No. 1 Office)", BusyAccountGroup.CASH_IN_HAND, 0.0),
        BusyAccount("acc_imprest", "Supervisor Imprest Cash Wallet", BusyAccountGroup.CASH_IN_HAND, 15000.0),
        BusyAccount("acc_hdfc", "HDFC Bank - Kanpur Udaipur (50200079191430)", BusyAccountGroup.BANK_ACCOUNTS, 0.0),
        BusyAccount("acc_sbi", "SBI Current A/c - NB Engineering (UPI)", BusyAccountGroup.BANK_ACCOUNTS, 0.0),
        BusyAccount("acc_sales_gst", "Solar & Wind EPC Sales (GST)", BusyAccountGroup.SALES_ACCOUNTS, 0.0),
        BusyAccount("acc_pur_gst", "Solar & Wind Materials Purchase (GST)", BusyAccountGroup.PURCHASE_ACCOUNTS, 0.0),
        BusyAccount("acc_site_mat", "On-Site Local Hardware Purchases", BusyAccountGroup.PURCHASE_ACCOUNTS, 0.0),
        BusyAccount("acc_thakur_comm", "Thakur Ji & Referral Commission A/c", BusyAccountGroup.INDIRECT_EXPENSES, 0.0, defaultCostCenter = LeadSource.THAKUR_JI),
        BusyAccount("acc_site_exp", "Site Labour & Daily Kharcha Exp", BusyAccountGroup.DIRECT_EXPENSES, 0.0),
        BusyAccount("acc_avvnl_fee", "AVVNL DISCOM & Meter Testing Fees", BusyAccountGroup.DIRECT_EXPENSES, 0.0),
        BusyAccount("acc_cgst_out", "Output CGST Payable", BusyAccountGroup.DUTIES_TAXES, 0.0),
        BusyAccount("acc_sgst_out", "Output SGST Payable", BusyAccountGroup.DUTIES_TAXES, 0.0),
        BusyAccount("acc_igst_out", "Output IGST Payable", BusyAccountGroup.DUTIES_TAXES, 0.0)
    )

    private fun defaultBusyItems(): List<BusyItem> = listOf(
        BusyItem("itm_panel_540", "DCR Solar PV Module 540W Mono PERC", "85414300", "Nos", 12.0, 14500.0, 12200.0, 0.0),
        BusyItem("itm_inv_3kw", "On-Grid Solar Inverter 3kW (1Ph)", "85044090", "Nos", 12.0, 26500.0, 22000.0, 0.0),
        BusyItem("itm_inv_5kw", "On-Grid Solar Inverter 5kW (1Ph/3Ph)", "85044090", "Nos", 12.0, 38500.0, 32500.0, 0.0),
        BusyItem("itm_gi_struct", "Hot-Dip GI Solar Mounting Structure", "73089090", "kg", 18.0, 135.0, 108.0, 0.0),
        BusyItem("itm_dc_cable", "Solar DC Cable 4 sq.mm (UV)", "85444999", "m", 18.0, 58.0, 44.0, 0.0),
        BusyItem("itm_ac_cable", "Armoured AC Cable 4C x 6 sq.mm", "85444999", "m", 18.0, 120.0, 92.0, 0.0),
        BusyItem("itm_earthing", "Copper Chemical Earthing Kit + LA", "85354090", "Sets", 18.0, 4800.0, 3600.0, 0.0),
        BusyItem("itm_epc_pkg", "Rooftop Solar / Wind EPC Turnkey System", "85414300", "kW", 18.0, 56000.0, 42000.0, 0.0)
    )

    private fun defaultRawMaterials(): List<RawMaterial> = listOf(
        RawMaterial("rm_gi", "Hot-Dip GI Structure", "Structure", "kg", "", 0.0, 0.0, 100.0, unitCost = 108.0),
        RawMaterial("rm_dc", "DC Solar Cable 4 sq.mm", "Cables", "m", "", 0.0, 0.0, 150.0, unitCost = 44.0),
        RawMaterial("rm_ac", "AC Cable 6 sq.mm", "Cables", "m", "", 0.0, 0.0, 100.0, unitCost = 92.0),
        RawMaterial("rm_inv", "On-Grid Solar Inverters", "Inverters", "units", "", 0.0, 0.0, 3.0, unitCost = 28000.0),
        RawMaterial("rm_earth", "Earthing Kits & Lightning Arrester", "Safety BOS", "sets", "", 0.0, 0.0, 5.0, unitCost = 3600.0),
        RawMaterial("rm_acdb", "ACDB / DCDB Protection Boxes", "Protection", "units", "", 0.0, 0.0, 4.0, unitCost = 2800.0)
    )
}
