package com.mkring.james.chatbackend

import com.mkring.james.JamesPool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlin.coroutines.CoroutineContext

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
 * Abstract Chatbackend handling most coroutine-related logic. Can be customized if needed.
 */
abstract class ChatBackend : CoroutineScope {
    /**
     * Default job, can be overridden
     */
    open val job = Job()

    /**
     * Default CoroutineContext using JamesPool, can be overridden
     */
    override val coroutineContext: CoroutineContext
        get() = job + JamesPool

    /**
     * Default Channel for received Payloads. This is the incoming direction (backend -> chatbackend -> james -> mapping)
     */
    open val backendToJamesChannel: Channel<IncomingPayload> = Channel(10)
    /**
     * Default Channel for outgoing Payloads. This is the outgoing direction (mapping -> james -> chatbackend -> backend)
     */
    open val fromJamesToBackendChannel: Channel<OutgoingPayload> = Channel(10)

    /**
     *  Setup your custom backend connection here.
     */
    abstract fun start()

    /**
     * Default implementation for stopping the current context and all childs
     */
    open fun stop() = job.cancel()
}