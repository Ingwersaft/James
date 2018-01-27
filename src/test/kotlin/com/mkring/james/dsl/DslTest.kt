package com.mkring.james.dsl

import com.mkring.james.chatbackend.RocketChat
import com.mkring.james.jamesV3
import com.mkring.james.mapping.MappingPattern
import com.mkring.james.mapping.MappingV2
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import org.junit.Test

class DslTest {
    @Test
    fun createJamesViaDsl() {
        val created = jamesV3 {
            autoStart = false
            rocketchat {
                websocketTarget = "wss://example.org/websocket"
                username = "fake"
                password = "no"
            }

            val block: MappingV2.() -> Unit = {
                send("world")
            }
            map("/hallo", "some text", block)
            assertEquals(mappings[MappingPattern("/hallo", "some text")], block)
        }
        assertEquals(RocketChat::class, created.chatConfigs[0]::class)


        assertEquals(false, created.autoStart)
        assertNull(created.name)
    }

    @Test
    fun testUseOtherJames() {
        val other = jamesV3 {
            autoStart = false
            map("1", "1") {
                send("hello")
            }
            map("2", "2") {
                send("hello")
            }
        }
        val actualJames = jamesV3 {
            autoStart = false
            use(other)
            map("3", "3") {
                send("hello")
            }
            map("4", "4") {
                send("hello")
            }
        }
        assertEquals("1;2;3;4", actualJames.mappings.map { it.key.pattern }.joinToString(";"))
        assertEquals("1;2;3;4", actualJames.mappings.map { it.key.info }.joinToString(";"))
    }
}