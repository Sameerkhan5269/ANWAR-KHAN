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
import org.json.JSONObject
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

data class StorageHealthStats(
    val cloudJsonBytes: Long = 0L,
    val localCacheBytes: Long = 0L,
    val localVaultBytes: Long = 0L,
    val offlineQueuedCount: Int = 0,
    val totalRecordsCount: Int = 0,
    val moduleCount: Int = 6
)

/**
 * Permanent cloud-first repository connected to Supabase `nb_solar_store`.
 * Uses 6 separated module key-rows (`nb_parties`, `nb_inventory`, `nb_busy_vouchers`,
 * `nb_loans_mco`, `nb_chat`, `nb_security_config`) so simultaneous updates from Office
 * and Field Supervisors never overwrite each other.
 */
class SupabaseSyncRepository(
    @Suppress("UNUSED_PARAMETER") private val context: Context,
    private val storeService: SupabaseStoreService = SupabaseStoreService()
) {

    companion object {
        val SUPABASE_URL: String get() = SupabaseClientConfig.supabaseUrl
        val SUPABASE_ANON_KEY: String get() = SupabaseClientConfig.supabaseAnonKey
        const val TABLE_NAME = SupabaseClientConfig.DEFAULT_TABLE_NAME

        // 6 Separated Module Key-Rows in `nb_solar_store`
        const val KEY_PARTIES = "nb_parties"
        const val KEY_INVENTORY = "nb_inventory"
        const val KEY_BUSY_VOUCHERS = "nb_busy_vouchers"
        const val KEY_LOANS_MCO = "nb_loans_mco"
        const val KEY_CHAT = "nb_chat"
        const val KEY_SECURITY_CONFIG = "nb_security_config"

        val ALL_CANONICAL_KEYS = listOf(
            KEY_PARTIES,
            KEY_INVENTORY,
            KEY_BUSY_VOUCHERS,
            KEY_LOANS_MCO,
            KEY_CHAT,
            KEY_SECURITY_CONFIG
        )

        // Legacy key aliases for seamless one-time cloud migration / fallback reads
        private const val LEGACY_KEY_STAFF = "staff_users"
        private const val LEGACY_KEY_PARTIES = "parties"
        private const val LEGACY_KEY_PANELS = "inventory_panels"
        private const val LEGACY_KEY_METERS = "inventory_meters"
        private const val LEGACY_KEY_RAW_MATERIALS = "inventory_raw_materials"
        private const val LEGACY_KEY_ACCOUNTS = "busy_accounts"
        private const val LEGACY_KEY_ITEMS = "busy_items"
        private const val LEGACY_KEY_VOUCHERS = "busy_vouchers"
        private const val LEGACY_KEY_KHARCHA = "site_kharcha"
        private const val LEGACY_KEY_CHAT = "team_chat"

        fun encodePayloadForKey(snap: AppDatabaseSnapshot, key: String): Any? = when (key) {
            KEY_PARTIES -> JsonCodec.encodeParties(snap.parties)
            KEY_INVENTORY -> JsonCodec.encodeInventoryBundle(
                dcrPanels = snap.dcrPanels,
                solarMeters = snap.solarMeters,
                rawMaterials = snap.rawMaterials
            )
            KEY_BUSY_VOUCHERS -> JsonCodec.encodeBusyVouchersBundle(
                accounts = snap.busyAccounts,
                items = snap.busyItems,
                vouchers = snap.busyVouchers,
                siteKharcha = snap.siteKharcha
            )
            KEY_LOANS_MCO -> {
                val records = if (snap.loanMcoRecords.isNotEmpty()) {
                    snap.loanMcoRecords
                } else {
                    JsonCodec.extractLoanMcoRecords(snap.parties)
                }
                JsonCodec.encodeLoanMcoRecords(records)
            }
            KEY_CHAT -> JsonCodec.encodeTeamChat(snap.teamChat)
            KEY_SECURITY_CONFIG -> JsonCodec.encodeSecurityConfig(snap.staffUsers)
            else -> null
        }

        fun applyStoreKeyPayload(
            base: AppDatabaseSnapshot,
            key: String,
            dataVal: Any
        ): AppDatabaseSnapshot {
            return when (key) {
                KEY_PARTIES, LEGACY_KEY_PARTIES -> {
                    val arr = toJsonArray(dataVal) ?: return base
                    val decodedParties = JsonCodec.decodeParties(arr)
                    val mergedParties = JsonCodec.mergeLoanMcoOverlay(decodedParties, base.loanMcoRecords)
                    base.copy(parties = mergedParties)
                }

                KEY_LOANS_MCO -> {
                    val arr = toJsonArray(dataVal) ?: return base
                    val records = JsonCodec.decodeLoanMcoRecords(arr)
                    val mergedParties = JsonCodec.mergeLoanMcoOverlay(base.parties, records)
                    base.copy(
                        parties = mergedParties,
                        loanMcoRecords = records
                    )
                }

                KEY_INVENTORY -> {
                    val obj = toJsonObject(dataVal) ?: return base
                    val (panels, meters, raw) = JsonCodec.decodeInventoryBundle(obj)
                    base.copy(
                        dcrPanels = panels,
                        solarMeters = meters,
                        rawMaterials = raw
                    )
                }

                KEY_BUSY_VOUCHERS -> {
                    val obj = toJsonObject(dataVal) ?: return base
                    val bundle = JsonCodec.decodeBusyVouchersBundle(obj)
                    base.copy(
                        busyAccounts = bundle.accounts,
                        busyItems = bundle.items,
                        busyVouchers = bundle.vouchers,
                        siteKharcha = bundle.siteKharcha
                    )
                }

                KEY_CHAT, LEGACY_KEY_CHAT -> {
                    val arr = toJsonArray(dataVal) ?: return base
                    base.copy(teamChat = JsonCodec.decodeTeamChat(arr))
                }

                KEY_SECURITY_CONFIG -> {
                    val obj = toJsonObject(dataVal)
                    val users = if (obj != null) {
                        JsonCodec.decodeSecurityConfig(obj)
                    } else {
                        JsonCodec.decodeStaffUsers(toJsonArray(dataVal))
                    }
                    if (users.isEmpty()) base else base.copy(staffUsers = users)
                }

                // Legacy individual keys
                LEGACY_KEY_STAFF -> {
                    val arr = toJsonArray(dataVal) ?: return base
                    base.copy(staffUsers = JsonCodec.decodeStaffUsers(arr))
                }
                LEGACY_KEY_PANELS -> {
                    val arr = toJsonArray(dataVal) ?: return base
                    base.copy(dcrPanels = JsonCodec.decodeDcrPanels(arr))
                }
                LEGACY_KEY_METERS -> {
                    val arr = toJsonArray(dataVal) ?: return base
                    base.copy(solarMeters = JsonCodec.decodeSolarMeters(arr))
                }
                LEGACY_KEY_RAW_MATERIALS -> {
                    val arr = toJsonArray(dataVal) ?: return base
                    base.copy(rawMaterials = JsonCodec.decodeRawMaterials(arr))
                }
                LEGACY_KEY_ACCOUNTS -> {
                    val arr = toJsonArray(dataVal) ?: return base
                    base.copy(busyAccounts = JsonCodec.decodeBusyAccounts(arr))
                }
                LEGACY_KEY_ITEMS -> {
                    val arr = toJsonArray(dataVal) ?: return base
                    base.copy(busyItems = JsonCodec.decodeBusyItems(arr))
                }
                LEGACY_KEY_VOUCHERS -> {
                    val arr = toJsonArray(dataVal) ?: return base
                    base.copy(busyVouchers = JsonCodec.decodeBusyVouchers(arr))
                }
                LEGACY_KEY_KHARCHA -> {
                    val arr = toJsonArray(dataVal) ?: return base
                    base.copy(siteKharcha = JsonCodec.decodeSiteKharcha(arr))
                }
                else -> base
            }
        }

        fun clearStoreKeyPayload(base: AppDatabaseSnapshot, key: String): AppDatabaseSnapshot = when (key) {
            KEY_PARTIES, LEGACY_KEY_PARTIES -> base.copy(parties = emptyList())
            KEY_LOANS_MCO -> base.copy(loanMcoRecords = emptyList())
            KEY_INVENTORY -> base.copy(dcrPanels = emptyList(), solarMeters = emptyList(), rawMaterials = emptyList())
            KEY_BUSY_VOUCHERS -> base.copy(busyAccounts = emptyList(), busyItems = emptyList(), busyVouchers = emptyList(), siteKharcha = emptyList())
            KEY_CHAT, LEGACY_KEY_CHAT -> base.copy(teamChat = emptyList())
            KEY_SECURITY_CONFIG, LEGACY_KEY_STAFF -> base.copy(staffUsers = emptyList())
            LEGACY_KEY_PANELS -> base.copy(dcrPanels = emptyList())
            LEGACY_KEY_METERS -> base.copy(solarMeters = emptyList())
            LEGACY_KEY_RAW_MATERIALS -> base.copy(rawMaterials = emptyList())
            LEGACY_KEY_ACCOUNTS -> base.copy(busyAccounts = emptyList())
            LEGACY_KEY_ITEMS -> base.copy(busyItems = emptyList())
            LEGACY_KEY_VOUCHERS -> base.copy(busyVouchers = emptyList())
            LEGACY_KEY_KHARCHA -> base.copy(siteKharcha = emptyList())
            else -> base
        }

        private fun toJsonArray(dataVal: Any): JSONArray? = when (dataVal) {
            is JSONArray -> dataVal
            is String -> runCatching { JSONArray(dataVal) }.getOrNull()
            else -> null
        }

        private fun toJsonObject(dataVal: Any): JSONObject? = when (dataVal) {
            is JSONObject -> dataVal
            is String -> runCatching { JSONObject(dataVal) }.getOrNull()
            else -> null
        }
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
                        if (event.key !in pendingOfflineKeys) {
                            val updated = when (event.eventType) {
                                SupabaseRealtimeEventType.DELETE -> clearStoreKeyPayload(_snapshot.value, event.key)
                                SupabaseRealtimeEventType.INSERT,
                                SupabaseRealtimeEventType.UPDATE -> {
                                    val dataVal = event.record?.data ?: return@collect
                                    applyStoreKeyPayload(_snapshot.value, event.key, dataVal)
                                }
                            }
                            _snapshot.value = ensureRequiredDefaults(updated)
                        }
                        _syncStatus.value = SyncStatus(
                            state = SyncConnectionState.LIVE,
                            lastSyncedAt = nowTimestamp(),
                            detail = "Live update (${event.key})",
                            needsRlsGrant = false,
                            queuedCount = pendingOfflineKeys.size
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
                                    needsRlsGrant = false,
                                    queuedCount = pendingOfflineKeys.size
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
                val queuedPayload = encodePayloadForKey(currentSnap, pendingKey) ?: continue
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

                // Apply legacy keys first, then canonical `nb_*` module keys on top
                val (canonical, legacy) = records.partition { it.key in ALL_CANONICAL_KEYS }
                legacy.forEach { rec ->
                    presentKeys.add(rec.key)
                    updated = applyStoreKeyPayload(updated, rec.key, rec.data)
                }
                canonical.forEach { rec ->
                    if (rec.key !in pendingOfflineKeys) {
                        presentKeys.add(rec.key)
                        updated = applyStoreKeyPayload(updated, rec.key, rec.data)
                    }
                }

                // Ensure loan overlay stays populated even if migrated from legacy `parties`
                if (updated.loanMcoRecords.isEmpty() && updated.parties.isNotEmpty()) {
                    updated = updated.copy(loanMcoRecords = JsonCodec.extractLoanMcoRecords(updated.parties))
                }

                updated = ensureRequiredDefaults(updated)
                _snapshot.value = updated

                // Auto-seed / migrate separated 6 module key-rows if missing in Supabase
                for (key in ALL_CANONICAL_KEYS) {
                    if (key !in presentKeys) {
                        val payload = encodePayloadForKey(updated, key) ?: continue
                        storeService.upsert(key, payload)
                    }
                }

                _syncStatus.value = SyncStatus(
                    state = SyncConnectionState.LIVE,
                    lastSyncedAt = nowTimestamp(),
                    detail = "Live Supabase (${ALL_CANONICAL_KEYS.size} module rows)" +
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
        Result.success("Read ${records.size} rows & R/W ping OK on nb_solar_store")
    }

    /**
     * Updates reactive UI state and immediately persists ONLY the targeted module key-row
     * to Supabase `nb_solar_store` with automatic offline queue fallback if disconnected.
     */
    fun updateAndSyncKey(
        key: String,
        transform: (AppDatabaseSnapshot) -> AppDatabaseSnapshot
    ) {
        var next = ensureRequiredDefaults(transform(_snapshot.value))
        if (key == KEY_LOANS_MCO) {
            next = next.copy(loanMcoRecords = JsonCodec.extractLoanMcoRecords(next.parties))
        }
        _snapshot.value = next

        val payload: Any = encodePayloadForKey(next, key) ?: return

        repoScope.launch {
            _syncStatus.value = _syncStatus.value.copy(
                state = SyncConnectionState.SYNCING,
                queuedCount = pendingOfflineKeys.size
            )
            val result = storeService.upsert(key, payload)
            if (result.isSuccess) {
                pendingOfflineKeys.remove(key)
                // Flush any previously queued offline keys on successful write
                for (queuedKey in pendingOfflineKeys.toList()) {
                    val qPayload = encodePayloadForKey(_snapshot.value, queuedKey) ?: continue
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
            _snapshot.value = ensureRequiredDefaults(clearStoreKeyPayload(_snapshot.value, key))
        }
        res
    }

    /**
     * Explicit "Flush Queue Now" action for Team & Sync Live Storage Health card.
     */
    suspend fun flushOfflineQueueNow(): Result<String> = withContext(Dispatchers.IO) {
        val beforeCount = pendingOfflineKeys.size
        _syncStatus.value = _syncStatus.value.copy(
            state = SyncConnectionState.SYNCING,
            detail = if (beforeCount > 0) "Flushing $beforeCount queued offline update(s)…" else "Syncing with Supabase cloud…",
            queuedCount = beforeCount
        )
        val snap = _snapshot.value
        var flushed = 0
        var lastError: Throwable? = null
        for (pendingKey in pendingOfflineKeys.toList()) {
            val payload = encodePayloadForKey(snap, pendingKey) ?: continue
            val res = storeService.upsert(pendingKey, payload)
            if (res.isSuccess) {
                pendingOfflineKeys.remove(pendingKey)
                flushed++
            } else {
                lastError = res.exceptionOrNull()
            }
        }
        val pullOk = pullAllFromCloud()
        val remaining = pendingOfflineKeys.size
        when {
            remaining == 0 && pullOk ->
                Result.success(
                    if (beforeCount > 0) "Flushed $flushed/$beforeCount offline queued update(s) to Supabase Cloud."
                    else "Queue empty — Live synced with Supabase Cloud."
                )
            remaining > 0 ->
                Result.failure(lastError ?: IllegalStateException("$remaining offline update(s) still queued — check network."))
            else ->
                Result.failure(lastError ?: IllegalStateException("Supabase unreachable — offline queue preserved safely."))
        }
    }

    /**
     * Restores a validated Full Company Backup across all 6 canonical module key-rows
     * (`nb_parties`, `nb_inventory`, `nb_busy_vouchers`, `nb_loans_mco`, `nb_chat`, `nb_security_config`).
     */
    suspend fun restoreFullSnapshotToCloud(restored: AppDatabaseSnapshot): Result<String> = withContext(Dispatchers.IO) {
        var normalized = ensureRequiredDefaults(restored)
        if (normalized.loanMcoRecords.isEmpty() && normalized.parties.isNotEmpty()) {
            normalized = normalized.copy(loanMcoRecords = JsonCodec.extractLoanMcoRecords(normalized.parties))
        }
        _snapshot.value = normalized

        var pushed = 0
        var lastErr: Throwable? = null
        _syncStatus.value = _syncStatus.value.copy(
            state = SyncConnectionState.SYNCING,
            detail = "Restoring 6 company modules to Supabase…"
        )
        for (key in ALL_CANONICAL_KEYS) {
            val payload = encodePayloadForKey(normalized, key) ?: continue
            val res = storeService.upsert(key, payload)
            if (res.isSuccess) {
                pendingOfflineKeys.remove(key)
                pushed++
            } else {
                pendingOfflineKeys.add(key)
                lastErr = res.exceptionOrNull()
            }
        }

        if (pendingOfflineKeys.isEmpty()) {
            cloudReachable = true
            _syncStatus.value = SyncStatus(
                state = SyncConnectionState.LIVE,
                lastSyncedAt = nowTimestamp(),
                detail = "Restored all $pushed module rows to Supabase Cloud.",
                needsRlsGrant = false,
                queuedCount = 0
            )
            Result.success("Restored & synced ${normalized.parties.size} parties, ${normalized.busyVouchers.size} vouchers across $pushed cloud modules.")
        } else {
            val isPerm = SupabaseStoreService.isPermissionDeniedError(lastErr)
            _syncStatus.value = SyncStatus(
                state = SyncConnectionState.OFFLINE,
                lastSyncedAt = _syncStatus.value.lastSyncedAt,
                detail = "Restored locally; ${pendingOfflineKeys.size} modules queued for cloud sync.",
                needsRlsGrant = isPerm,
                queuedCount = pendingOfflineKeys.size
            )
            Result.success("Restored locally (${normalized.parties.size} parties). ${pendingOfflineKeys.size} modules queued to sync when online.")
        }
    }

    fun computeStorageHealth(): StorageHealthStats {
        val snap = _snapshot.value
        val totalJsonBytes = ALL_CANONICAL_KEYS.sumOf { key ->
            encodePayloadForKey(snap, key)?.toString()?.toByteArray(Charsets.UTF_8)?.size?.toLong() ?: 0L
        }
        val cacheBytes = dirSizeBytes(context.cacheDir)
        val vaultBytes = dirSizeBytes(java.io.File(context.filesDir, "vault")) +
            dirSizeBytes(java.io.File(context.filesDir, "site_bills")) +
            dirSizeBytes(java.io.File(context.filesDir, "chat_videos"))
        val totalRecords = snap.parties.size +
            snap.dcrPanels.size +
            snap.solarMeters.size +
            snap.rawMaterials.size +
            snap.busyVouchers.size +
            snap.busyAccounts.size +
            snap.busyItems.size +
            snap.siteKharcha.size +
            snap.teamChat.size +
            snap.staffUsers.size

        return StorageHealthStats(
            cloudJsonBytes = totalJsonBytes,
            localCacheBytes = cacheBytes,
            localVaultBytes = vaultBytes,
            offlineQueuedCount = pendingOfflineKeys.size,
            totalRecordsCount = totalRecords,
            moduleCount = ALL_CANONICAL_KEYS.size
        )
    }

    /**
     * Clears only disposable temporary files inside `cacheDir` (exported PDFs/CSVs, camera staging photos,
     * image caches). Strictly preserves Supabase cloud records, `filesDir` Vault documents, and DataStore session!
     */
    fun clearLocalCacheCloudSafe(): Long {
        val before = dirSizeBytes(context.cacheDir)
        context.cacheDir?.listFiles()?.forEach { child ->
            runCatching { child.deleteRecursively() }
        }
        val after = dirSizeBytes(context.cacheDir)
        return (before - after).coerceAtLeast(0L)
    }

    private fun dirSizeBytes(file: java.io.File?): Long {
        if (file == null || !file.exists()) return 0L
        if (file.isFile) return file.length()
        return file.listFiles()?.sumOf { dirSizeBytes(it) } ?: 0L
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
