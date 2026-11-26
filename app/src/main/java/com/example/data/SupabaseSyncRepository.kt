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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private const val TAG = "SupabaseSyncRepo"

enum class SyncConnectionState {
    LIVE,
    SYNCING,
    OFFLINE
}

data class SyncStatus(
    val state: SyncConnectionState = SyncConnectionState.SYNCING,
    val lastSyncedAt: String = "Connecting…",
    val detail: String = "Supabase Realtime (nb_solar_store)"
)

/**
 * Multi-device cloud database repository backed by [SupabaseStoreService] (`nb_solar_store`)
 * with resilient local JSON disk caching for field phones on slow networks.
 */
class SupabaseSyncRepository(
    private val context: Context,
    private val storeService: SupabaseStoreService = SupabaseStoreService()
) {

    companion object {
        const val SUPABASE_URL = SupabaseStoreService.DEFAULT_PROJECT_URL
        const val SUPABASE_ANON_KEY = SupabaseStoreService.DEFAULT_ANON_KEY
        const val TABLE_NAME = SupabaseStoreService.DEFAULT_TABLE_NAME

        const val KEY_STAFF = "staff_users"
        const val KEY_PARTIES = "parties"
        const val KEY_PANELS = "inventory_panels"
        const val KEY_METERS = "inventory_meters"
        const val KEY_RAW_MATERIALS = "inventory_raw_materials"
        const val KEY_ACCOUNTS = "busy_accounts"
        const val KEY_ITEMS = "busy_items"
        const val KEY_VOUCHERS = "busy_vouchers"
    }

    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cacheFile = File(context.filesDir, "nb_solar_cache.json")

    private val _snapshot = MutableStateFlow(defaultInitialSnapshot())
    val snapshot: StateFlow<AppDatabaseSnapshot> = _snapshot.asStateFlow()

    private val _syncStatus = MutableStateFlow(SyncStatus())
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    init {
        loadFromLocalCache()
        observeRealtimeEvents()
        repoScope.launch {
            pullAllFromCloud()
            storeService.connectRealtime()
        }
    }

    private fun observeRealtimeEvents() {
        repoScope.launch {
            storeService.events.collect { event ->
                when (event) {
                    is SupabaseStoreEvent.RecordChanged -> {
                        val updated = when (event.eventType) {
                            SupabaseRealtimeEventType.DELETE -> clearKeyPayload(_snapshot.value, event.key)
                            SupabaseRealtimeEventType.INSERT,
                            SupabaseRealtimeEventType.UPDATE -> {
                                val dataVal = event.record?.data ?: return@collect
                                applyKeyPayload(_snapshot.value, event.key, dataVal)
                            }
                        }
                        val safeNext = ensureRequiredDefaults(updated)
                        _snapshot.value = safeNext
                        saveToLocalCache(safeNext)
                        _syncStatus.value = SyncStatus(
                            state = SyncConnectionState.LIVE,
                            lastSyncedAt = nowTimestamp(),
                            detail = "Realtime ${event.eventType.name} (${event.key})"
                        )
                    }

                    is SupabaseStoreEvent.ConnectionChanged -> {
                        _syncStatus.value = _syncStatus.value.copy(
                            state = if (event.connected) SyncConnectionState.LIVE else SyncConnectionState.OFFLINE,
                            detail = event.detail
                        )
                        if (event.connected) {
                            launch { pullAllFromCloud() }
                        }
                    }

                    is SupabaseStoreEvent.Error -> {
                        Log.w(TAG, "Realtime service error: ${event.message}", event.cause)
                    }
                }
            }
        }
    }

    private fun loadFromLocalCache() {
        try {
            if (cacheFile.exists()) {
                val text = cacheFile.readText()
                if (text.isNotBlank()) {
                    val decoded = JsonCodec.decodeSnapshot(JSONObject(text))
                    _snapshot.value = ensureRequiredDefaults(decoded)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Local cache read error (falling back to defaults)", e)
        }
    }

    private fun saveToLocalCache(snap: AppDatabaseSnapshot) {
        try {
            val tmp = File(context.filesDir, "nb_solar_cache.json.tmp")
            tmp.writeText(JsonCodec.encodeSnapshot(snap).toString())
            tmp.renameTo(cacheFile)
        } catch (e: Exception) {
            Log.e(TAG, "Local cache write error", e)
        }
    }

    suspend fun pullAllFromCloud(): Boolean = withContext(Dispatchers.IO) {
        _syncStatus.value = _syncStatus.value.copy(state = SyncConnectionState.SYNCING)
        val result = storeService.readAll()
        result.fold(
            onSuccess = { records ->
                var updated = _snapshot.value
                val presentKeys = mutableSetOf<String>()

                records.forEach { rec ->
                    presentKeys.add(rec.key)
                    updated = applyKeyPayload(updated, rec.key, rec.data)
                }

                updated = ensureRequiredDefaults(updated)
                _snapshot.value = updated
                saveToLocalCache(updated)

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
                    detail = "Synced (${records.size} cloud stores)"
                )
                true
            },
            onFailure = { err ->
                Log.w(TAG, "pullAllFromCloud failed: ${err.message}")
                _syncStatus.value = SyncStatus(
                    state = SyncConnectionState.OFFLINE,
                    lastSyncedAt = _syncStatus.value.lastSyncedAt,
                    detail = "Offline mode — using local cache"
                )
                false
            }
        )
    }

    /**
     * Atomically updates local state + cache immediately (optimistic UI) and upserts the
     * modified key to Supabase `nb_solar_store` via [SupabaseStoreService].
     */
    fun updateAndSyncKey(
        key: String,
        transform: (AppDatabaseSnapshot) -> AppDatabaseSnapshot
    ) {
        val next = ensureRequiredDefaults(transform(_snapshot.value))
        _snapshot.value = next
        saveToLocalCache(next)

        val payload: Any = when (key) {
            KEY_STAFF -> JsonCodec.encodeStaffUsers(next.staffUsers)
            KEY_PARTIES -> JsonCodec.encodeParties(next.parties)
            KEY_PANELS -> JsonCodec.encodeDcrPanels(next.dcrPanels)
            KEY_METERS -> JsonCodec.encodeSolarMeters(next.solarMeters)
            KEY_RAW_MATERIALS -> JsonCodec.encodeRawMaterials(next.rawMaterials)
            KEY_ACCOUNTS -> JsonCodec.encodeBusyAccounts(next.busyAccounts)
            KEY_ITEMS -> JsonCodec.encodeBusyItems(next.busyItems)
            KEY_VOUCHERS -> JsonCodec.encodeBusyVouchers(next.busyVouchers)
            else -> return
        }

        repoScope.launch {
            _syncStatus.value = _syncStatus.value.copy(state = SyncConnectionState.SYNCING)
            val result = storeService.upsert(key, payload)
            _syncStatus.value = if (result.isSuccess) {
                SyncStatus(
                    state = SyncConnectionState.LIVE,
                    lastSyncedAt = nowTimestamp(),
                    detail = "Live synced ($key)"
                )
            } else {
                SyncStatus(
                    state = SyncConnectionState.OFFLINE,
                    lastSyncedAt = _syncStatus.value.lastSyncedAt,
                    detail = "Saved locally — tap Sync when online"
                )
            }
        }
    }

    /**
     * Deletes an entire key row from `nb_solar_store` and resets its local collection.
     */
    suspend fun deleteStoreKey(key: String): Result<Unit> = withContext(Dispatchers.IO) {
        val res = storeService.delete(key)
        if (res.isSuccess) {
            val next = ensureRequiredDefaults(clearKeyPayload(_snapshot.value, key))
            _snapshot.value = next
            saveToLocalCache(next)
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
        else -> base
    }

    private fun ensureRequiredDefaults(snap: AppDatabaseSnapshot): AppDatabaseSnapshot {
        val builtIns = builtInStaffUsers()
        val existingIds = snap.staffUsers.map { it.loginId.lowercase() }.toSet()
        val mergedStaff = snap.staffUsers + builtIns.filter { it.loginId.lowercase() !in existingIds }

        val mergedAccounts = if (snap.busyAccounts.isEmpty()) defaultBusyAccounts() else snap.busyAccounts
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
        StaffUser("staff_owner", "owner@nb", "1111", "Owner / CEO", StaffRole.OWNER, true),
        StaffUser("staff_accounts", "accounts@nb", "2222", "Accounts Manager", StaffRole.ACCOUNTS, true),
        StaffUser("staff_office", "office@nb", "3333", "Office & Docs Coordinator", StaffRole.OFFICE, true),
        StaffUser("staff_supervisor", "supervisor@nb", "4444", "Site Supervisor", StaffRole.SUPERVISOR, true),
        StaffUser("staff_helper", "helper@nb", "5555", "Field Helper", StaffRole.HELPER, true)
    )

    private fun defaultBusyAccounts(): List<BusyAccount> = listOf(
        BusyAccount("acc_cash", "Cash-in-Hand (Madri Office)", BusyAccountGroup.CASH_IN_HAND, 0.0),
        BusyAccount("acc_sbi", "SBI Current A/c - NB Engineering", BusyAccountGroup.BANK_ACCOUNTS, 0.0),
        BusyAccount("acc_hdfc", "HDFC Bank - Solar EPC A/c", BusyAccountGroup.BANK_ACCOUNTS, 0.0),
        BusyAccount("acc_sales_gst", "Solar EPC Sales (GST)", BusyAccountGroup.SALES_ACCOUNTS, 0.0),
        BusyAccount("acc_pur_gst", "Solar Materials Purchase (GST)", BusyAccountGroup.PURCHASE_ACCOUNTS, 0.0),
        BusyAccount("acc_thakur_comm", "Thakur Ji Commission & Split A/c", BusyAccountGroup.INDIRECT_EXPENSES, 0.0, defaultCostCenter = LeadSource.THAKUR_JI),
        BusyAccount("acc_site_exp", "Site Labour & Fabrication Exp", BusyAccountGroup.DIRECT_EXPENSES, 0.0),
        BusyAccount("acc_avvnl_fee", "AVVNL DISCOM & Meter Testing Fees", BusyAccountGroup.DIRECT_EXPENSES, 0.0),
        BusyAccount("acc_cgst_out", "Output CGST Payable", BusyAccountGroup.DUTIES_TAXES, 0.0),
        BusyAccount("acc_sgst_out", "Output SGST Payable", BusyAccountGroup.DUTIES_TAXES, 0.0),
        BusyAccount("acc_igst_out", "Output IGST Payable", BusyAccountGroup.DUTIES_TAXES, 0.0)
    )

    private fun defaultBusyItems(): List<BusyItem> = listOf(
        BusyItem("itm_panel_540", "DCR Solar Panel 540W Mono PERC", "854140", "Nos", 5.0, 14500.0, 12200.0, 0.0),
        BusyItem("itm_inv_3kw", "On-Grid Solar Inverter 3kW (1Ph)", "850440", "Nos", 12.0, 26500.0, 22000.0, 0.0),
        BusyItem("itm_inv_5kw", "On-Grid Solar Inverter 5kW (1Ph/3Ph)", "850440", "Nos", 12.0, 38500.0, 32500.0, 0.0),
        BusyItem("itm_gi_struct", "Hot-Dip GI Mounting Structure", "730890", "kg", 18.0, 135.0, 108.0, 0.0),
        BusyItem("itm_dc_cable", "Solar DC Cable 4 sq.mm (UV)", "854449", "m", 18.0, 58.0, 44.0, 0.0),
        BusyItem("itm_ac_cable", "Armoured AC Cable 4C x 6 sq.mm", "854449", "m", 18.0, 120.0, 92.0, 0.0),
        BusyItem("itm_earthing", "Copper Chemical Earthing Kit + LA", "853540", "Sets", 18.0, 4800.0, 3600.0, 0.0),
        BusyItem("itm_epc_pkg", "Rooftop Solar EPC Turnkey System", "854140", "kW", 13.8, 56000.0, 42000.0, 0.0)
    )

    private fun defaultRawMaterials(): List<RawMaterial> = listOf(
        RawMaterial("rm_gi", "Hot-Dip GI Structure", "Structure", "kg", "", 0.0, 0.0, 100.0),
        RawMaterial("rm_dc", "DC Solar Cable 4 sq.mm", "Cables", "m", "", 0.0, 0.0, 150.0),
        RawMaterial("rm_ac", "AC Cable 6 sq.mm", "Cables", "m", "", 0.0, 0.0, 100.0),
        RawMaterial("rm_inv", "On-Grid Solar Inverters", "Inverters", "units", "", 0.0, 0.0, 3.0),
        RawMaterial("rm_earth", "Earthing Kits & Lightning Arrester", "Safety BOS", "sets", "", 0.0, 0.0, 5.0),
        RawMaterial("rm_acdb", "ACDB / DCDB Protection Boxes", "Protection", "units", "", 0.0, 0.0, 4.0)
    )
}
