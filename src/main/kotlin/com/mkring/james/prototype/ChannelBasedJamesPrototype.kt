package com.mkring.james.prototype

import com.mkring.james.chatbackend.UniqueChatTarget
import com.mkring.james.mapping.MappingPattern
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.SendChannel
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking

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

abstract class ChatBackendV3(
    val backendToJamesChannel: SendChannel<IncomingPayload>,
    val fromJamesToBackendChannel: ReceiveChannel<OutgoingPayload>
) {
    //abstract fun getJamesToBackendChannel(): ReceiveChannel<OutgoingPayload>
    abstract fun start()
}

class MainClassChatBackend(
    backendToJamesChannel: SendChannel<IncomingPayload>,
    fromJamesToBackendChannel: ReceiveChannel<OutgoingPayload>
) : ChatBackendV3(backendToJamesChannel, fromJamesToBackendChannel) {

    override fun start() {
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
        backendToJamesChannel.send(IncomingPayload("<id>", "<user>", text))
    }
}

class ChatV2(
    val mappings: MutableMap<MappingPattern, MappingV2.() -> Unit>,
    val chatBackendV3: ChatBackendV3
    // TODO
) {
    fun todo() {
        chatBackendV3
    }
}

class MappingV2(private val incomingPayload: IncomingPayload, private val chat: ChatV2) {

    fun send(text: String, options: Map<String, String> = emptyMap()) {
        TODO()
    }
}

fun main(args: Array<String>) = runBlocking {

    val outgoing: Channel<OutgoingPayload> = Channel()
    val incoming: Channel<IncomingPayload> = Channel()

    launch {
        while (true) {
            incoming.receive().let {
                // fake found mapping

                println("received from chat: $it")
                println("going to answer")
                outgoing.send(OutgoingPayload(it.target, "sdsd"))
            }
            delay(10)
        }
    }

    val chat = MainClassChatBackend(incoming, outgoing).apply { start() }
    delay(100)
    chat.onMessage("/hallo welt")
    delay(1000)
    chat.onMessage("sdkjdk")
}