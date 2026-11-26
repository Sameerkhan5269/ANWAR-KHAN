package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SupabaseStoreServiceTest {

    @Test
    fun `realtime parser dispatches INSERT UPDATE and DELETE events to listeners`() {
        val service = SupabaseStoreService()
        val received = mutableListOf<Triple<SupabaseRealtimeEventType, String, SupabaseStoreRecord?>>()

        val listener = object : RealtimeStoreListener {
            override fun onRecordChanged(
                eventType: SupabaseRealtimeEventType,
                key: String,
                record: SupabaseStoreRecord?
            ) {
                received.add(Triple(eventType, key, record))
            }
        }
        service.addListener(listener)

        val insertFrame = """
            {
              "topic": "realtime:public:nb_solar_store",
              "event": "postgres_changes",
              "payload": {
                "data": {
                  "type": "INSERT",
                  "record": {
                    "key": "parties",
                    "data": [{"partyName": "Ramesh Sharma"}],
                    "updated_at": "2026-11-25T10:00:00Z"
                  }
                }
              }
            }
        """.trimIndent()

        val deleteFrame = """
            {
              "topic": "realtime:public:nb_solar_store",
              "event": "postgres_changes",
              "payload": {
                "data": {
                  "type": "DELETE",
                  "old_record": {
                    "key": "parties"
                  }
                }
              }
            }
        """.trimIndent()

        service.handleRealtimePayload(insertFrame)
        service.handleRealtimePayload(deleteFrame)

        assertEquals(2, received.size)
        assertEquals(SupabaseRealtimeEventType.INSERT, received[0].first)
        assertEquals("parties", received[0].second)
        assertNotNull(received[0].third)

        assertEquals(SupabaseRealtimeEventType.DELETE, received[1].first)
        assertEquals("parties", received[1].second)
        assertNull(received[1].third)

        service.close()
    }
}
