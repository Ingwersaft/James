package com.mkring.james.integration.rocketchat

import com.mkring.james.james
import junit.framework.TestCase.assertTrue
import org.junit.Test
import java.net.URL
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

// private val base = "51.15.56.207:3000"
private val base = "localhost:3000"

// see .circleci/config.yml
private val adminUser = "admin"
private val adminPw = "12345"

class BasicRocketchatIntegration {
    @Test
    fun testConnectivity() {
        println("### testConnectivity ###")
        val readText = URL("http://$base").readText()
        assertTrue(readText.isNotEmpty())

        var done = CompletableFuture<Unit>()
        val adminClient = RocketChatRestClient(base, adminUser, adminPw)
        val (botUser, botPw) = Pair("botuser", "botpw123")
        adminClient.run {
            login()
            getGeneralChannelId()
            createBotUser(botUser, botPw)
        }
        james {
            name = "testjames"
            rocketchat {
                ignoreInvalidCa = true
                sslVerifyHostname = false
                username = botUser
                password = botPw
                websocketTarget = "wss://$base/websocket"
            }
            map("complete", "") {
                done.complete(Unit)
            }
        }
        println("james started! awaiting completion of future")
        // send complete command via api
        adminClient.postMessageToChannel("testjames complete").also { "sent message: $it" }
        // await completion
        done.get(1, TimeUnit.MINUTES)
        println("completed successfully!")
    }
}

//docker run --env ADMIN_USERNAME=admin --env ADMIN_PASS=12345 --env ADMIN_EMAIL=admin@example.com --name rocketchat --link db -d -p 3000:3000 rocket.chat:0.71.1