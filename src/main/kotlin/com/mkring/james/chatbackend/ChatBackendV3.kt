package com.mkring.james.chatbackend

import kotlinx.coroutines.experimental.channels.Channel

typealias UniqueChatTarget = String

/**
 * TODO add info!
 */
data class IncomingPayload(
    val target: UniqueChatTarget,
    val username: String?,
    val text: String
)

/**
 * TODO add info!
 */
data class OutgoingPayload(
    val target: UniqueChatTarget,
    val text: String,
    val options: Map<String, String> = emptyMap()
)

/**
 * TODO add info!
 */
abstract class ChatBackendV3 {
    val backendToJamesChannel: Channel<IncomingPayload> = Channel(10)
    val fromJamesToBackendChannel: Channel<OutgoingPayload> = Channel(10)
    abstract suspend fun start()
}