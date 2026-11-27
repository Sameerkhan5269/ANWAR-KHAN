package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseClientConfigTest {

    @Test
    fun `rejects markdown url pasted as anon key and falls back to valid publishable key`() {
        val malformed = "[https://qojwffdkknjtupifeejb.supabase.co](https://qojwffdkknjtupifeejb.supabase.co)"
        assertFalse(SupabaseClientConfig.isValidSupabaseKey(malformed))

        val resolved = SupabaseClientConfig.sanitizeSupabaseKey(malformed)
        assertTrue(resolved.startsWith("sb_publishable_"))
    }

    @Test
    fun `accepts valid jwt or sb_publishable anon key`() {
        val pubKey = "sb_publishable_wuw1Glw5dDxb4pi50FCbtA_LdZKl5hV"
        assertTrue(SupabaseClientConfig.isValidSupabaseKey(pubKey))
        assertEquals(pubKey, SupabaseClientConfig.sanitizeSupabaseKey(pubKey))

        val jwtKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJyb2xlIjoiYW5vbiJ9.signaturepart12345"
        assertTrue(SupabaseClientConfig.isValidSupabaseKey(jwtKey))
        assertEquals(jwtKey, SupabaseClientConfig.sanitizeSupabaseKey(jwtKey))
    }

    @Test
    fun `extracts clean https supabase url from markdown link`() {
        val markdownUrl = "[https://qojwffdkknjtupifeejb.supabase.co](https://qojwffdkknjtupifeejb.supabase.co)"
        assertEquals("https://qojwffdkknjtupifeejb.supabase.co", SupabaseClientConfig.sanitizeSupabaseUrl(markdownUrl))
    }
}
