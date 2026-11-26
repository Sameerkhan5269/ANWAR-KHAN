package com.example.data

import android.util.Log
import com.example.core.nowIsoUtc
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

private const val TAG = "SupabaseStoreService"

/**
 * Row model matching the `nb_solar_store` table:
 * - `key` TEXT PRIMARY KEY
 * - `data` JSONB (JSONObject, JSONArray, or primitive)
 * - `updated_at` TIMESTAMPTZ
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

/**
 * Optional callback listener for consumers that prefer listener registration over Flow collection.
 */
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
 * Production-grade Kotlin service utility for Supabase PostgREST CRUD and Realtime WebSocket
 * multi-device synchronization against the `nb_solar_store` table.
 */
class SupabaseStoreService(
    private val projectUrl: String = DEFAULT_PROJECT_URL,
    private val anonKey: String = DEFAULT_ANON_KEY,
    private val tableName: String = DEFAULT_TABLE_NAME,
    private val schemaName: String = "public",
    private val okHttpClient: OkHttpClient = defaultHttpClient()
) : Closeable {

    companion object {
        const val DEFAULT_PROJECT_URL = "https://qojwffdkknjtupifeejb.supabase.co"
        const val DEFAULT_ANON_KEY = "sb_publishable_wuw1Glw5dDxb4pi50FCbtA_LdZKl5hV"
        const val DEFAULT_TABLE_NAME = "nb_solar_store"

        private const val HEARTBEAT_INTERVAL_MS = 25_000L
        private const val INITIAL_BACKOFF_MS = 2_000L
        private const val MAX_BACKOFF_MS = 30_000L

        private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    }

    private val baseHttpUrl: HttpUrl = projectUrl.trimEnd('/').toHttpUrl()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _events = MutableSharedFlow<SupabaseStoreEvent>(
        replay = 0,
        extraBufferCapacity = 64
    )
    val events: SharedFlow<SupabaseStoreEvent> = _events.asSharedFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val listeners = CopyOnWriteArraySet<RealtimeStoreListener>()
    private val phoenixRef = AtomicInteger(1)
    private val isClosed = AtomicBoolean(false)
    private val reconnectAttempts = AtomicInteger(0)

    @Volatile
    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null

    // -------------------------------------------------------------------------
    // LISTENER MANAGEMENT
    // -------------------------------------------------------------------------

    fun addListener(listener: RealtimeStoreListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: RealtimeStoreListener) {
        listeners.remove(listener)
    }

    // -------------------------------------------------------------------------
    // POSTGREST CRUD OPERATIONS (`nb_solar_store`)
    // -------------------------------------------------------------------------

    /**
     * CREATE: Inserts a new row into `nb_solar_store`. Fails if `key` already exists.
     */
    suspend fun create(key: String, data: Any): Result<SupabaseStoreRecord> = withContext(Dispatchers.IO) {
        val cleanKey = key.trim()
        if (cleanKey.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Store key cannot be blank."))
        }
        val timestamp = nowIsoUtc()
        val payload = JSONObject().apply {
            put("key", cleanKey)
            put("data", data)
            put("updated_at", timestamp)
        }
        val url = tableUrlBuilder().build()
        val request = authorizedRequestBuilder(url)
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "return=representation")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        executeSingleRecordRequest(request, fallbackRecord = SupabaseStoreRecord(cleanKey, data, timestamp))
    }

    /**
     * READ ALL: Fetches all key/data rows from `nb_solar_store`.
     */
    suspend fun readAll(): Result<List<SupabaseStoreRecord>> = withContext(Dispatchers.IO) {
        try {
            val url = tableUrlBuilder()
                .addQueryParameter("select", "key,data,updated_at")
                .build()
            val request = authorizedRequestBuilder(url).get().build()

            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("Supabase readAll failed (HTTP ${response.code}): $body")
                    )
                }
                val rows = parseRecordArray(body)
                Result.success(rows)
            }
        } catch (e: Exception) {
            Log.e(TAG, "readAll failed", e)
            Result.failure(e)
        }
    }

    /**
     * READ BY KEY: Fetches a single row by its primary key, or `null` if not found.
     */
    suspend fun readByKey(key: String): Result<SupabaseStoreRecord?> = withContext(Dispatchers.IO) {
        val cleanKey = key.trim()
        if (cleanKey.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Store key cannot be blank."))
        }
        try {
            val url = tableUrlBuilder()
                .addQueryParameter("select", "key,data,updated_at")
                .addQueryParameter("key", "eq.$cleanKey")
                .addQueryParameter("limit", "1")
                .build()
            val request = authorizedRequestBuilder(url).get().build()

            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("Supabase readByKey($cleanKey) failed (HTTP ${response.code}): $body")
                    )
                }
                Result.success(parseRecordArray(body).firstOrNull())
            }
        } catch (e: Exception) {
            Log.e(TAG, "readByKey($cleanKey) failed", e)
            Result.failure(e)
        }
    }

    /**
     * UPDATE: Patches `data` and `updated_at` for an existing `key` in `nb_solar_store`.
     */
    suspend fun update(key: String, data: Any): Result<SupabaseStoreRecord> = withContext(Dispatchers.IO) {
        val cleanKey = key.trim()
        if (cleanKey.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Store key cannot be blank."))
        }
        val timestamp = nowIsoUtc()
        val payload = JSONObject().apply {
            put("data", data)
            put("updated_at", timestamp)
        }
        val url = tableUrlBuilder()
            .addQueryParameter("key", "eq.$cleanKey")
            .build()
        val request = authorizedRequestBuilder(url)
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "return=representation")
            .patch(payload.toString().toRequestBody(jsonMediaType))
            .build()

        executeSingleRecordRequest(request, fallbackRecord = SupabaseStoreRecord(cleanKey, data, timestamp))
    }

    /**
     * UPSERT: Atomically inserts or updates the row for `key` (conflict resolution on `key`).
     */
    suspend fun upsert(key: String, data: Any): Result<SupabaseStoreRecord> = withContext(Dispatchers.IO) {
        val cleanKey = key.trim()
        if (cleanKey.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Store key cannot be blank."))
        }
        val timestamp = nowIsoUtc()
        val payload = JSONObject().apply {
            put("key", cleanKey)
            put("data", data)
            put("updated_at", timestamp)
        }
        val url = tableUrlBuilder()
            .addQueryParameter("on_conflict", "key")
            .build()
        val request = authorizedRequestBuilder(url)
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "resolution=merge-duplicates,return=representation")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        executeSingleRecordRequest(request, fallbackRecord = SupabaseStoreRecord(cleanKey, data, timestamp))
    }

    /**
     * DELETE: Removes a row by primary key from `nb_solar_store`.
     */
    suspend fun delete(key: String): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanKey = key.trim()
        if (cleanKey.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Store key cannot be blank."))
        }
        try {
            val url = tableUrlBuilder()
                .addQueryParameter("key", "eq.$cleanKey")
                .build()
            val request = authorizedRequestBuilder(url)
                .addHeader("Prefer", "return=minimal")
                .delete()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("Supabase delete($cleanKey) failed (HTTP ${response.code}): $body")
                    )
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "delete($cleanKey) failed", e)
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------------------
    // REAL-TIME WEBSOCKET SUBSCRIPTION (`postgres_changes` on `nb_solar_store`)
    // -------------------------------------------------------------------------

    fun connectRealtime() {
        if (isClosed.get()) return
        reconnectJob?.cancel()
        heartbeatJob?.cancel()
        webSocket?.cancel()

        val wsScheme = if (baseHttpUrl.scheme == "https") "wss" else "ws"
        val wsUrl = buildString {
            append("$wsScheme://${baseHttpUrl.host}")
            if (baseHttpUrl.port != 443 && baseHttpUrl.port != 80) {
                append(":${baseHttpUrl.port}")
            }
            append("/realtime/v1/websocket?apikey=$anonKey&vsn=1.0.0")
        }

        val request = Request.Builder().url(wsUrl).build()
        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                reconnectAttempts.set(0)
                sendJoinChannel(ws)
                startHeartbeat(ws)
                emitConnectionState(true, "Realtime WebSocket connected ($schemaName.$tableName)")
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleRealtimePayload(text)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                val msg = "Realtime connection dropped: ${t.message ?: "network error"}"
                Log.w(TAG, msg)
                emitConnectionState(false, msg)
                emitError(msg, t)
                scheduleReconnect()
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                val detail = "Realtime socket closed ($code${if (reason.isNotBlank()) ": $reason" else ""})"
                emitConnectionState(false, detail)
                if (!isClosed.get() && code != 1000) {
                    scheduleReconnect()
                }
            }
        })
    }

    fun disconnectRealtime() {
        reconnectJob?.cancel()
        heartbeatJob?.cancel()
        webSocket?.close(1000, "Client disconnected")
        webSocket = null
        emitConnectionState(false, "Disconnected")
    }

    override fun close() {
        if (isClosed.compareAndSet(false, true)) {
            disconnectRealtime()
            listeners.clear()
            serviceScope.cancel()
        }
    }

    private fun sendJoinChannel(ws: WebSocket) {
        val joinFrame = JSONObject().apply {
            put("topic", "realtime:$schemaName:$tableName")
            put("event", "phx_join")
            put("ref", phoenixRef.getAndIncrement().toString())
            put("payload", JSONObject().apply {
                put("config", JSONObject().apply {
                    put("broadcast", JSONObject().put("self", false))
                    put("presence", JSONObject().put("key", ""))
                    put("postgres_changes", JSONArray().apply {
                        put(JSONObject().apply {
                            put("event", "*")
                            put("schema", schemaName)
                            put("table", tableName)
                        })
                    })
                })
            })
        }
        ws.send(joinFrame.toString())
    }

    private fun startHeartbeat(ws: WebSocket) {
        heartbeatJob?.cancel()
        heartbeatJob = serviceScope.launch {
            while (isActive && !isClosed.get()) {
                delay(HEARTBEAT_INTERVAL_MS)
                val hb = JSONObject().apply {
                    put("topic", "phoenix")
                    put("event", "heartbeat")
                    put("payload", JSONObject())
                    put("ref", phoenixRef.getAndIncrement().toString())
                }
                val sent = ws.send(hb.toString())
                if (!sent) break
            }
        }
    }

    private fun scheduleReconnect() {
        if (isClosed.get()) return
        heartbeatJob?.cancel()
        reconnectJob?.cancel()
        val attempt = reconnectAttempts.incrementAndGet()
        val backoffMs = min(MAX_BACKOFF_MS, INITIAL_BACKOFF_MS * (1L shl min(attempt - 1, 4)))

        reconnectJob = serviceScope.launch {
            delay(backoffMs)
            if (isActive && !isClosed.get()) {
                Log.d(TAG, "Reconnecting Supabase Realtime (attempt #$attempt)…")
                connectRealtime()
            }
        }
    }

    internal fun handleRealtimePayload(rawFrame: String) {
        try {
            val frame = JSONObject(rawFrame)
            val eventName = frame.optString("event")

            if (eventName == "phx_reply") {
                val payload = frame.optJSONObject("payload")
                val status = payload?.optString("status")
                if (status == "error") {
                    emitError("Realtime channel error: ${payload.opt("response")}")
                }
                return
            }

            if (eventName == "postgres_changes" || eventName == "INSERT" || eventName == "UPDATE" || eventName == "DELETE") {
                val payload = frame.optJSONObject("payload") ?: return
                val envelope = payload.optJSONObject("data") ?: payload

                val rawType = envelope.optString("type")
                    .ifBlank { envelope.optString("eventType") }
                    .ifBlank { eventName }
                val eventType = SupabaseRealtimeEventType.fromWire(rawType)

                val recordObj = envelope.optJSONObject("record")
                val oldRecordObj = envelope.optJSONObject("old_record")

                val parsedRecord = recordObj?.let { parseSingleRecordObject(it) }
                val oldKey = oldRecordObj?.optString("key")?.takeIf { it.isNotBlank() }
                val resolvedKey = parsedRecord?.key ?: oldKey ?: return

                val changeEvent = SupabaseStoreEvent.RecordChanged(
                    eventType = eventType,
                    key = resolvedKey,
                    record = if (eventType == SupabaseRealtimeEventType.DELETE) null else parsedRecord,
                    oldKey = oldKey
                )
                _events.tryEmit(changeEvent)
                listeners.forEach { listener ->
                    runCatching {
                        listener.onRecordChanged(eventType, resolvedKey, changeEvent.record)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Realtime frame", e)
            emitError("Failed to parse realtime payload", e)
        }
    }

    // -------------------------------------------------------------------------
    // INTERNAL HTTP & JSON HELPERS
    // -------------------------------------------------------------------------

    private fun tableUrlBuilder(): HttpUrl.Builder = baseHttpUrl.newBuilder()
        .addPathSegment("rest")
        .addPathSegment("v1")
        .addPathSegment(tableName)

    private fun authorizedRequestBuilder(url: HttpUrl): Request.Builder = Request.Builder()
        .url(url)
        .addHeader("apikey", anonKey)
        .addHeader("Authorization", "Bearer $anonKey")
        .addHeader("Accept", "application/json")

    private fun executeSingleRecordRequest(
        request: Request,
        fallbackRecord: SupabaseStoreRecord
    ): Result<SupabaseStoreRecord> {
        return try {
            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return Result.failure(
                        IllegalStateException("Supabase HTTP ${response.code}: $body")
                    )
                }
                val parsed = if (body.isNotBlank()) parseRecordArray(body).firstOrNull() else null
                Result.success(parsed ?: fallbackRecord)
            }
        } catch (e: Exception) {
            Log.e(TAG, "PostgREST mutation failed", e)
            Result.failure(e)
        }
    }

    private fun parseRecordArray(rawJson: String): List<SupabaseStoreRecord> {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty()) return emptyList()
        return if (trimmed.startsWith("[")) {
            val arr = JSONArray(trimmed)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    parseSingleRecordObject(obj)?.let { add(it) }
                }
            }
        } else {
            parseSingleRecordObject(JSONObject(trimmed))?.let { listOf(it) } ?: emptyList()
        }
    }

    private fun parseSingleRecordObject(obj: JSONObject): SupabaseStoreRecord? {
        val key = obj.optString("key").trim()
        if (key.isEmpty()) return null
        val dataVal = obj.opt("data")
        if (dataVal == null || dataVal == JSONObject.NULL) return null
        val updatedAt = obj.optString("updated_at")
        return SupabaseStoreRecord(key = key, data = dataVal, updatedAt = updatedAt)
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
}
