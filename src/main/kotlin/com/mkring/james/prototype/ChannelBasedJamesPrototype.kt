package com.mkring.james.prototype

import com.mkring.james.chatbackend.UniqueChatTarget
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.SendChannel

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
    val backendToJamesChannel: SendChannel<IncomingPayload>
) {
    abstract fun getJamesToBackendChannel(): ReceiveChannel<OutgoingPayload>
    abstract fun start()
}

