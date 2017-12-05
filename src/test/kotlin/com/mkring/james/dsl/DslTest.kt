package com.mkring.james.dsl

import com.mkring.james.chatbackend.rocketchat.RocketBackend
import com.mkring.james.james
import com.mkring.james.mapping.Mapping
import com.mkring.james.mapping.MappingPattern
import junit.framework.Assert.assertEquals
import junit.framework.TestCase.assertNull
import org.junit.Test

class DslTest {
    @Test
    fun createJamesViaDsl() {
        val created = james {
            autoStart = false
            rocketchat {
                websocketTarget = "wss://example.org/websocket"
                username = "fake"
                password = "no"
            }

            val block: Mapping.() -> Unit = {
                send("world")
            }
            map("/hallo", "some text", block)

            assertEquals(RocketBackend::class, chat::class)
            assertEquals(mappings[MappingPattern("/hallo", "some text")], block)
        }
        assertEquals(false, created.autoStart)
        assertNull(created.name)
    }
}