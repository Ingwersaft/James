package com.mkring.james.integration.rocketchat

import com.mkring.james.james
import junit.framework.TestCase.assertTrue
import org.junit.Test
import java.net.URL

class BasicRocketchatIntegration {

    @Test
    fun testConnectivity() {
        println("### testConnectivity ###")
        val readText = URL("http://localhost:3000").readText()
        assertTrue(readText.isNotEmpty())

        // setup james
        james {
            rocketchat {
                ignoreInvalidCa = true
                sslVerifyHostname = false
                username = "bot"
                password = "1234"
                websocketTarget = "wss://localhost/websocket"
            }
            map("ping", "") {
                send("pong")
            }
        }
        println("james started!")
    }
}