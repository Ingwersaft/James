package com.mkring.james

import com.mkring.james.chatbackend.ChatBackend
import com.mkring.james.chatbackend.OutgoingPayload
import com.mkring.james.chatbackend.UniqueChatTarget
import com.mkring.james.chatbackend.rocketchat.singleLineLog
import com.mkring.james.mapping.Ask
import com.mkring.james.mapping.Mapping
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

class Chat(
    val type: String,
    val mappings: Map<String, Mapping.() -> Unit>,
    val chatBackend: ChatBackend,
    val abortKeywords: MutableList<String>,
    val mappingprefix: String
) : CoroutineScope {
    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = job + JamesPool

    private val runningJobs = mutableMapOf<UniqueChatTarget, Job>()
    private val askResultMap: MutableMap<UniqueChatTarget, CompletableFuture<String>> = mutableMapOf()
    internal suspend fun start() {
        log.info("start() called")
        log.info("received the following mappings:\n" + mappings.map { it.key }.joinToString("\n"))
        chatBackend.start()
        log.info("Chat chat started, now iterating chatBackend channel")
        launch {
            chatBackend.backendToJamesChannel.consumeEach { (uniqueChatTarget, username, text) ->
                log.info("chatBackend.backendToJamesChannel received $text from $username - $uniqueChatTarget")
                try {
                    if (callbackFutureHandled(text, uniqueChatTarget).not()) {
                        mappings.map { it.key }.joinToString(";").let { log.debug("chatLogicMappings keys =$it") }

                        mappings.entries.firstOrNull { text.startsWith(it.key) }?.let { entry ->
                            log.info("going to handle ${entry.key}")
                            val job = launch {
                                Mapping(text, uniqueChatTarget, username, mappingprefix, this@Chat).apply {
                                    entry.value.invoke(this)
                                }
                            }
                            log.info("launch done")
                            runningJobs[uniqueChatTarget] = job
                        }
                        log.trace("nothing found for $text from $uniqueChatTarget")
                    }
                } catch (e: Exception) {
                    log.warn("chatBackend.backendToJamesChannel.consumeEach threw exception: ${e.singleLineLog()}")
                }
            }
        }
    }

    private fun callbackFutureHandled(text: String, target: UniqueChatTarget): Boolean {
        askResultMap[target]?.let {
            if (text isIn abortKeywords) {
                log.info("target $target received abortKeyword: $text")
                send(OutgoingPayload(target, "aborted"))
                askResultMap[target]?.cancel(true)
                askResultMap.remove(target)
                return true
            }
            askResultMap[target]?.complete(text)
            askResultMap.remove(target)
            return true
        }
        return false
    }

    internal fun send(outgoingPayload: OutgoingPayload) = runBlocking {
        chatBackend.fromJamesToBackendChannel.send(outgoingPayload)
    }

    internal fun ask(
        text: String,
        options: Map<String, String> = emptyMap(),
        timeout: Int,
        timeunit: TimeUnit,
        target: UniqueChatTarget
    ): Ask<String> {
        val future = CompletableFuture<String>()
        askResultMap[target] = future
        send(OutgoingPayload(target, text, options))
        return Ask.of { future.get(timeout.toLong(), timeunit) }
    }

    internal fun stop() {
        job.cancel()
    }

    companion object {
        private val log = LoggerFactory.getLogger("Chat")
    }
}
