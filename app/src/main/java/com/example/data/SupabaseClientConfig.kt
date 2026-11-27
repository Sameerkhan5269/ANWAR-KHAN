package com.example.data

import android.util.Log
import com.example.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.ktor.client.engine.okhttp.OkHttp
import kotlin.time.Duration.Companion.seconds

private const val TAG = "SupabaseClientConfig"

/**
 * Centralized Supabase client configuration for NB Solar & Wind ERP.
 * Reads `SUPABASE_URL` and `SUPABASE_ANON_KEY` from [BuildConfig] (populated via
 * `.env` / AI Studio Secrets panel), sanitizing accidental markdown URLs or placeholders
 * before initializing the PostgREST and Realtime WebSocket connection on `nb_solar_store`.
 */
object SupabaseClientConfig {
    const val DEFAULT_TABLE_NAME = "nb_solar_store"
    const val DEFAULT_SCHEMA_NAME = "public"

    private const val FALLBACK_PROJECT_URL = "https://qojwffdkknjtupifeejb.supabase.co"
    private const val FALLBACK_PUBLISHABLE_KEY = "sb_publishable_wuw1Glw5dDxb4pi50FCbtA_LdZKl5hV"

    val supabaseUrl: String by lazy {
        sanitizeSupabaseUrl(BuildConfig.SUPABASE_URL)
    }

    val supabaseAnonKey: String by lazy {
        sanitizeSupabaseKey(BuildConfig.SUPABASE_ANON_KEY)
    }

    val isConfigured: Boolean
        get() = supabaseUrl.startsWith("https://") && isValidSupabaseKey(supabaseAnonKey)

    internal fun sanitizeSupabaseUrl(raw: String): String {
        val trimmed = raw.trim()
        val match = Regex("""https://[a-zA-Z0-9-]+\.supabase\.co""").find(trimmed)
        return match?.value ?: FALLBACK_PROJECT_URL
    }

    internal fun sanitizeSupabaseKey(raw: String): String {
        val trimmed = raw.trim()
        return if (isValidSupabaseKey(trimmed)) {
            trimmed
        } else {
            if (trimmed.isNotBlank() && !trimmed.startsWith("YOUR_", ignoreCase = true)) {
                runCatching {
                    Log.w(TAG, "Ignoring malformed SUPABASE_ANON_KEY from BuildConfig; using configured publishable key.")
                }
            }
            FALLBACK_PUBLISHABLE_KEY
        }
    }

    internal fun isValidSupabaseKey(candidate: String): Boolean {
        val trimmed = candidate.trim()
        if (trimmed.length < 20) return false
        if (trimmed.startsWith("YOUR_", ignoreCase = true)) return false
        if (trimmed.contains("http://", ignoreCase = true) || trimmed.contains("https://", ignoreCase = true)) return false
        if (trimmed.contains("supabase.co", ignoreCase = true)) return false
        if (trimmed.contains("[") || trimmed.contains("]") || trimmed.contains("(") || trimmed.contains(")") || trimmed.contains(" ")) {
            return false
        }
        return trimmed.startsWith("sb_publishable_") ||
            (trimmed.startsWith("eyJ") && trimmed.count { it == '.' } == 2)
    }

    /**
     * Shared lazily initialized [SupabaseClient] configured with OkHttp,
     * PostgREST (`postgrest-kt`), and Realtime (`realtime-kt`) auto-reconnect.
     */
    val client: SupabaseClient by lazy {
        createClient(supabaseUrl, supabaseAnonKey)
    }

    fun createClient(
        url: String = supabaseUrl,
        anonKey: String = supabaseAnonKey
    ): SupabaseClient = createSupabaseClient(
        supabaseUrl = sanitizeSupabaseUrl(url),
        supabaseKey = sanitizeSupabaseKey(anonKey)
    ) {
        httpEngine = OkHttp.create()
        install(Postgrest)
        install(Realtime) {
            reconnectDelay = 10.seconds
        }
    }
}
