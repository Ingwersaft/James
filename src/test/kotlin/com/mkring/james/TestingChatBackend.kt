package com.mkring.james

import com.mkring.james.chatbackend.ChatBackend
import com.mkring.james.chatbackend.IncomingPayload
import kotlinx.coroutines.experimental.delay

class TestingChatBackend : ChatBackend() {
    override suspend fun start() {
        println("start called")
        fireAndForgetLoop("debugging") {
            fromJamesToBackendChannel.receive().let {
                println("going to send to backend: $it")
            }
            delay(10)
        }
        println("start done")
    }

    suspend fun onMessage(text: String) {
        println("onMessage called")
        backendToJamesChannel.send(IncomingPayload("<id>", "<user>", text))
        println("onMessage done")
    }
}