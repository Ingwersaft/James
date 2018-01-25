package com.mkring.james.prototype

import com.mkring.james.chatbackend.UniqueChatTarget
import com.mkring.james.chatbackend.log
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.Channel

data class IncomingPayload(
    val target: UniqueChatTarget,
    val username: String?,
    val text: String
)

data class OutgoingPayload(
    val target: UniqueChatTarget,
    val text: String,
    val options: Map<String, String> = emptyMap()
)

abstract class ChatBackendV3 {
    val backendToJamesChannel: Channel<IncomingPayload> = Channel(2)
    val fromJamesToBackendChannel: Channel<OutgoingPayload> = Channel(2)

    abstract suspend fun start()
}

class ChatV2(
    val mappings: MutableMap<String, MappingV2.() -> Unit>,
    val chatBackendV3: ChatBackendV3
) {
    private val runningJobs = mutableMapOf<UniqueChatTarget, Job>()
    suspend fun start() {
        chatBackendV3.start()
        println("ChatV2 chat started, now iterating chatBackend")
        launch {
            while (true) {
                chatBackendV3.backendToJamesChannel.receive().let { (uniqueChatTarget, username, text) ->
                    println("chatBackendV3.backendToJamesChannel received $text from $username - $uniqueChatTarget")
                    println("mappings=$mappings")
                    mappings.map { it.key }.joinToString(";").let { println("chatLogicMappings keys =$it") }

                    mappings.entries.firstOrNull()?.let { entry ->
                        log.info("going to handle ${entry.key}")
                        val job = launch {
                            MappingV2(text, uniqueChatTarget, username, chatBackendV3.fromJamesToBackendChannel).apply {
                                entry.value.invoke(this)
                            }
                        }
                        log.info("launch done")
                        runningJobs.put(uniqueChatTarget, job)
                    }
                }
            }
        }
    }
}


class MainClassChatBackend : ChatBackendV3() {
    override suspend fun start() {
        println("start called")
        launch {
            while (true) {
                fromJamesToBackendChannel.receive().let {
                    println("going to send to backend: $it")
                }
                delay(10)
            }
        }
        println("start done")
    }

    suspend fun onMessage(text: String) {
        println("onMessage called")
        backendToJamesChannel.send(IncomingPayload("<id>", "<user>", text))
        println("onMessage done")
    }

}

class MappingV2(
    private val commandText: String,
    private val uniqueChatTarget: UniqueChatTarget,
    val username: String?,
    private val fromJamesToBackendChannel: Channel<OutgoingPayload>
) {

    fun send(text: String, options: Map<String, String> = emptyMap()) {
        val deferred = async {
            fromJamesToBackendChannel.send(OutgoingPayload(uniqueChatTarget, text))
        }.awaitBlocking()
    }
}

private fun <T> Deferred<T>.awaitBlocking(): T = runBlocking {
    await()
}

fun main(args: Array<String>) = runBlocking {
    val chat = MainClassChatBackend()
    val mapping: MappingV2.() -> Unit = {
        send("world")
    }
    ChatV2(mutableMapOf("/hallo" to mapping), chat).apply {
        start()
    }

    println("wait then onMessage^")
    delay(100)
    chat.onMessage("/hallo")
    delay(1000)
}