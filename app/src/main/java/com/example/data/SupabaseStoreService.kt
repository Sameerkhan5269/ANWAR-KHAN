package com.example.data

import android.util.Log
import com.example.core.nowIsoUtc
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

private const val TAG = "SupabaseStoreService"

/**
 * Serializable row matching the Supabase `nb_solar_store` table:
 * - `key` TEXT PRIMARY KEY
 * - `data` JSONB
 * - `updated_at` TIMESTAMPTZ
 */
@Serializable
data class NbSolarStoreRow(
    val key: String,
    val data: JsonElement = JsonNull,
    @SerialName("updated_at")
    val updatedAt: String = ""
)

/**
 * Runtime record model exposing `data` as `org.json` (`JSONArray`/`JSONObject`) or primitive
 * for seamless consumption across the app.
 */
data class SupabaseStoreRecord(
    val key: String,
    val data: Any,
    val updatedAt: String
)

enum class SupabaseRealtimeEventType {
    INSERT,
    UPDATE,
    DELETE;

    companion object {
        fun fromWire(raw: String?): SupabaseRealtimeEventType = when (raw?.uppercase()) {
            "INSERT" -> INSERT
            "DELETE" -> DELETE
            else -> UPDATE
        }
    }
}

sealed interface SupabaseStoreEvent {
    data class RecordChanged(
        val eventType: SupabaseRealtimeEventType,
        val key: String,
        val record: SupabaseStoreRecord?,
        val oldKey: String? = null
    ) : SupabaseStoreEvent

    data class ConnectionChanged(
        val connected: Boolean,
        val detail: String
    ) : SupabaseStoreEvent

    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : SupabaseStoreEvent
}

interface RealtimeStoreListener {
    fun onRecordChanged(
        eventType: SupabaseRealtimeEventType,
        key: String,
        record: SupabaseStoreRecord?
    )
    fun onConnectionChanged(connected: Boolean, detail: String) {}
    fun onError(message: String, cause: Throwable? = null) {}
}

/**
 * Official `supabase-kt` service utility connected permanently to `nb_solar_store`
 * using PostgREST (`postgrest-kt`) for CRUD and Realtime (`realtime-kt` + Ktor OkHttp WebSockets)
 * for multi-device synchronization.
 */
class SupabaseStoreService(
    private val projectUrl: String = SupabaseClientConfig.supabaseUrl,
    private val anonKey: String = SupabaseClientConfig.supabaseAnonKey,
    private val tableName: String = SupabaseClientConfig.DEFAULT_TABLE_NAME,
    private val schemaName: String = SupabaseClientConfig.DEFAULT_SCHEMA_NAME
) : Closeable {

    companion object {
        val DEFAULT_PROJECT_URL: String get() = SupabaseClientConfig.supabaseUrl
        val DEFAULT_ANON_KEY: String get() = SupabaseClientConfig.supabaseAnonKey
        const val DEFAULT_TABLE_NAME = SupabaseClientConfig.DEFAULT_TABLE_NAME

        fun isPermissionDeniedError(error: Throwable?): Boolean {
            var cur: Throwable? = error
            while (cur != null) {
                val msg = cur.message.orEmpty()
                if (msg.contains("42501") || msg.contains("permission denied", ignoreCase = true)) {
                    return true
                }
                cur = cur.cause
            }
            return false
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    val supabase: SupabaseClient by lazy {
        if (projectUrl == SupabaseClientConfig.supabaseUrl && anonKey == SupabaseClientConfig.supabaseAnonKey) {
            SupabaseClientConfig.client
        } else {
            SupabaseClientConfig.createClient(projectUrl, anonKey)
        }
    }

    private val _events = MutableSharedFlow<SupabaseStoreEvent>(
        replay = 0,
        extraBufferCapacity = 64
    )
    val events: SharedFlow<SupabaseStoreEvent> = _events.asSharedFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val listeners = CopyOnWriteArraySet<RealtimeStoreListener>()
    private val isClosed = AtomicBoolean(false)

    private var realtimeJob: Job? = null
    private var activeChannel: RealtimeChannel? = null

    fun addListener(listener: RealtimeStoreListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: RealtimeStoreListener) {
        listeners.remove(listener)
    }

    // -------------------------------------------------------------------------
    // POSTGREST CRUD OPERATIONS (`nb_solar_store`)
    // -------------------------------------------------------------------------

    suspend fun create(key: String, data: Any): Result<SupabaseStoreRecord> = withContext(Dispatchers.IO) {
        val cleanKey = key.trim()
        if (cleanKey.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Store key cannot be blank."))
        }
        runCatching {
            val row = NbSolarStoreRow(
                key = cleanKey,
                data = toJsonElement(data),
                updatedAt = nowIsoUtc()
            )
            val created = supabase.from(tableName)
                .insert(row) {
                    select()
                }
                .decodeSingle<NbSolarStoreRow>()
            created.toStoreRecord()
        }.onFailure { e ->
            Log.w(TAG, "create($cleanKey) failed: ${e.message}")
        }
    }

    suspend fun readAll(): Result<List<SupabaseStoreRecord>> = withContext(Dispatchers.IO) {
        runCatching {
            val rows = supabase.from(tableName)
                .select()
                .decodeList<NbSolarStoreRow>()
            rows.map { it.toStoreRecord() }
        }.onFailure { e ->
            Log.w(TAG, "readAll failed: ${e.message}")
        }
    }

    suspend fun readByKey(key: String): Result<SupabaseStoreRecord?> = withContext(Dispatchers.IO) {
        val cleanKey = key.trim()
        if (cleanKey.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Store key cannot be blank."))
        }
        runCatching {
            val rows = supabase.from(tableName)
                .select {
                    filter {
                        eq("key", cleanKey)
                    }
                    limit(1)
                }
                .decodeList<NbSolarStoreRow>()
            rows.firstOrNull()?.toStoreRecord()
        }.onFailure { e ->
            Log.w(TAG, "readByKey($cleanKey) failed: ${e.message}")
        }
    }

    suspend fun update(key: String, data: Any): Result<SupabaseStoreRecord> = withContext(Dispatchers.IO) {
        val cleanKey = key.trim()
        if (cleanKey.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Store key cannot be blank."))
        }
        runCatching {
            val element = toJsonElement(data)
            val timestamp = nowIsoUtc()
            val updated = supabase.from(tableName)
                .update({
                    set("data", element)
                    set("updated_at", timestamp)
                }) {
                    filter {
                        eq("key", cleanKey)
                    }
                    select()
                }
                .decodeSingle<NbSolarStoreRow>()
            updated.toStoreRecord()
        }.onFailure { e ->
            Log.w(TAG, "update($cleanKey) failed: ${e.message}")
        }
    }

    suspend fun upsert(key: String, data: Any): Result<SupabaseStoreRecord> = withContext(Dispatchers.IO) {
        val cleanKey = key.trim()
        if (cleanKey.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Store key cannot be blank."))
        }
        runCatching {
            val row = NbSolarStoreRow(
                key = cleanKey,
                data = toJsonElement(data),
                updatedAt = nowIsoUtc()
            )
            val saved = supabase.from(tableName)
                .upsert(row) {
                    onConflict = "key"
                    select()
                }
                .decodeSingle<NbSolarStoreRow>()
            saved.toStoreRecord()
        }.onFailure { e ->
            Log.w(TAG, "upsert($cleanKey) failed: ${e.message}")
        }
    }

    suspend fun delete(key: String): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanKey = key.trim()
        if (cleanKey.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Store key cannot be blank."))
        }
        runCatching {
            supabase.from(tableName).delete {
                filter {
                    eq("key", cleanKey)
                }
            }
            Unit
        }.onFailure { e ->
            Log.w(TAG, "delete($cleanKey) failed: ${e.message}")
        }
    }

    // -------------------------------------------------------------------------
    // SUPABASE REALTIME SUBSCRIPTION (`realtime-kt` on `nb_solar_store`)
    // -------------------------------------------------------------------------

    fun connectRealtime() {
        if (isClosed.get()) return
        realtimeJob?.cancel()

        realtimeJob = serviceScope.launch {
            while (isActive && !isClosed.get()) {
                try {
                    activeChannel?.let { old ->
                        runCatching { supabase.realtime.removeChannel(old) }
                    }

                    val channel = supabase.realtime.channel("realtime:$schemaName:$tableName")
                    activeChannel = channel

                    val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = schemaName) {
                        table = tableName
                    }

                    val changeJob = changeFlow.onEach { action ->
                        handlePostgresAction(action)
                    }.launchIn(this)

                    val statusJob = channel.status.onEach { status ->
                        when (status) {
                            RealtimeChannel.Status.SUBSCRIBED -> {
                                emitConnectionState(true, "Supabase Realtime Live ($tableName)")
                            }
                            RealtimeChannel.Status.UNSUBSCRIBED -> {
                                emitConnectionState(false, "Reconnecting Realtime…")
                            }
                            else -> Unit
                        }
                    }.launchIn(this)

                    try {
                        supabase.realtime.connect()
                        channel.subscribe(blockUntilSubscribed = true)
                        emitConnectionState(true, "Supabase Realtime Live ($tableName)")

                        // Keep coroutine alive while channel remains subscribed
                        channel.status.collect { st ->
                            if (st == RealtimeChannel.Status.UNSUBSCRIBED && !isClosed.get()) {
                                throw IllegalStateException("Channel unsubscribed; triggering reconnect")
                            }
                        }
                    } finally {
                        changeJob.cancel()
                        statusJob.cancel()
                    }
                } catch (e: Exception) {
                    if (isClosed.get()) break
                    Log.w(TAG, "Realtime subscription loop interrupted: ${e.message}")
                    emitConnectionState(false, "Reconnecting to Supabase…")
                    emitError("Realtime connection interrupted: ${e.message}", e)
                    delay(3_000L)
                }
            }
        }
    }

    fun disconnectRealtime() {
        realtimeJob?.cancel()
        realtimeJob = null
        serviceScope.launch {
            activeChannel?.let { ch ->
                runCatching { ch.unsubscribe() }
                runCatching { supabase.realtime.removeChannel(ch) }
            }
            activeChannel = null
            runCatching { supabase.realtime.disconnect() }
        }
        emitConnectionState(false, "Disconnected")
    }

    override fun close() {
        if (isClosed.compareAndSet(false, true)) {
            realtimeJob?.cancel()
            realtimeJob = null
            listeners.clear()
            emitConnectionState(false, "Disconnected")
            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    activeChannel?.let { ch ->
                        runCatching { ch.unsubscribe() }
                        runCatching { supabase.realtime.removeChannel(ch) }
                    }
                    activeChannel = null
                    runCatching { supabase.realtime.disconnect() }
                    runCatching { supabase.close() }
                }
                serviceScope.cancel()
            }
        }
    }

    private fun handlePostgresAction(action: PostgresAction) {
        when (action) {
            is PostgresAction.Insert -> {
                val rec = action.record.toStoreRecord() ?: return
                dispatchRecordEvent(SupabaseRealtimeEventType.INSERT, rec.key, rec, null)
            }
            is PostgresAction.Update -> {
                val rec = action.record.toStoreRecord() ?: return
                dispatchRecordEvent(SupabaseRealtimeEventType.UPDATE, rec.key, rec, null)
            }
            is PostgresAction.Delete -> {
                val oldKey = (action.oldRecord["key"] as? JsonPrimitive)?.content?.trim()
                    ?: return
                dispatchRecordEvent(SupabaseRealtimeEventType.DELETE, oldKey, null, oldKey)
            }
            is PostgresAction.Select -> Unit
        }
    }

    /**
     * Exposed internally for unit-testing Phoenix/Postgres CDC frames without network calls.
     */
    internal fun handleRealtimePayload(rawFrame: String) {
        try {
            val frame = JSONObject(rawFrame)
            val eventName = frame.optString("event")
            if (eventName == "postgres_changes" || eventName == "INSERT" || eventName == "UPDATE" || eventName == "DELETE") {
                val payload = frame.optJSONObject("payload") ?: return
                val envelope = payload.optJSONObject("data") ?: payload
                val rawType = envelope.optString("type")
                    .ifBlank { envelope.optString("eventType") }
                    .ifBlank { eventName }
                val eventType = SupabaseRealtimeEventType.fromWire(rawType)

                val recordObj = envelope.optJSONObject("record")
                val oldRecordObj = envelope.optJSONObject("old_record")
                val parsedRecord = recordObj?.let { obj ->
                    val k = obj.optString("key").trim()
                    val d = obj.opt("data")
                    if (k.isNotEmpty() && d != null && d != JSONObject.NULL) {
                        SupabaseStoreRecord(k, d, obj.optString("updated_at"))
                    } else null
                }
                val oldKey = oldRecordObj?.optString("key")?.takeIf { it.isNotBlank() }
                val resolvedKey = parsedRecord?.key ?: oldKey ?: return

                dispatchRecordEvent(
                    eventType = eventType,
                    key = resolvedKey,
                    record = if (eventType == SupabaseRealtimeEventType.DELETE) null else parsedRecord,
                    oldKey = oldKey
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse realtime payload", e)
            emitError("Failed to parse realtime payload", e)
        }
    }

    private fun dispatchRecordEvent(
        eventType: SupabaseRealtimeEventType,
        key: String,
        record: SupabaseStoreRecord?,
        oldKey: String?
    ) {
        val ev = SupabaseStoreEvent.RecordChanged(eventType, key, record, oldKey)
        _events.tryEmit(ev)
        listeners.forEach { listener ->
            runCatching { listener.onRecordChanged(eventType, key, record) }
        }
    }

    private fun emitConnectionState(connected: Boolean, detail: String) {
        _isConnected.value = connected
        _events.tryEmit(SupabaseStoreEvent.ConnectionChanged(connected, detail))
        listeners.forEach { listener ->
            runCatching { listener.onConnectionChanged(connected, detail) }
        }
    }

    private fun emitError(message: String, cause: Throwable? = null) {
        _events.tryEmit(SupabaseStoreEvent.Error(message, cause))
        listeners.forEach { listener ->
            runCatching { listener.onError(message, cause) }
        }
    }

    // -------------------------------------------------------------------------
    // JSON CONVERSION HELPERS (kotlinx.serialization <-> org.json)
    // -------------------------------------------------------------------------

    private fun toJsonElement(value: Any): JsonElement = when (value) {
        is JsonElement -> value
        is JSONArray -> json.parseToJsonElement(value.toString())
        is JSONObject -> json.parseToJsonElement(value.toString())
        is String -> {
            val trimmed = value.trim()
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                runCatching { json.parseToJsonElement(trimmed) }.getOrElse { JsonPrimitive(value) }
            } else {
                JsonPrimitive(value)
            }
        }
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        else -> JsonPrimitive(value.toString())
    }

    private fun NbSolarStoreRow.toStoreRecord(): SupabaseStoreRecord {
        val converted: Any = when (val el = this.data) {
            is JsonArray -> JSONArray(el.toString())
            is JsonObject -> JSONObject(el.toString())
            is JsonPrimitive -> el.content
            JsonNull -> JSONArray()
        }
        return SupabaseStoreRecord(
            key = this.key,
            data = converted,
            updatedAt = this.updatedAt
        )
    }

    private fun JsonObject.toStoreRecord(): SupabaseStoreRecord? {
        val key = (this["key"] as? JsonPrimitive)?.content?.trim().orEmpty()
        if (key.isEmpty()) return null
        val dataEl = this["data"] ?: JsonNull
        val updatedAt = (this["updated_at"] as? JsonPrimitive)?.content.orEmpty()
        return NbSolarStoreRow(key = key, data = dataEl, updatedAt = updatedAt).toStoreRecord()
    }
}
