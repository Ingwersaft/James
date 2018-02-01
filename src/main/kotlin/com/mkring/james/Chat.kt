package com.mkring.james

import com.mkring.james.chatbackend.ChatBackend
import com.mkring.james.chatbackend.OutgoingPayload
import com.mkring.james.chatbackend.UniqueChatTarget
import com.mkring.james.mapping.Ask
import com.mkring.james.mapping.Mapping
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class Chat(
    val type: String,
    val mappings: Map<String, Mapping.() -> Unit>,
    val chatBackend: ChatBackend,
    val abortKeywords: MutableList<String>,
    val mappingprefix: String
) {
    private val runningJobs = mutableMapOf<UniqueChatTarget, Job>()
    private val askResultMap: MutableMap<UniqueChatTarget, CompletableFuture<String>> = mutableMapOf()
    suspend fun start() {
        log.info("start() called")
        log.info("received the following mappings:\n" + mappings.map { it.key }.joinToString("\n"))
        chatBackend.start()
        log.info("Chat chat started, now iterating chatBackend")
        fireAndForgetLoop("receive-from-backends") {
            chatBackend.backendToJamesChannel.receive().let { (uniqueChatTarget, username, text) ->
                log.info("chatBackend.backendToJamesChannel received $text from $username - $uniqueChatTarget")
                if (callbackFutureHandled(text, uniqueChatTarget).not()) {
                    mappings.map { it.key }.joinToString(";").let { println("chatLogicMappings keys =$it") }

                    mappings.entries.firstOrNull { text.startsWith(it.key) }?.let { entry ->
                        log.info("going to handle ${entry.key}")
                        val job = launch {
                            Mapping(text, uniqueChatTarget, username, mappingprefix, this@Chat).apply {
                                entry.value.invoke(this)
                            }
                        }
                        log.info("launch done")
                        runningJobs[uniqueChatTarget] = job
                        return@fireAndForgetLoop
                    }
                    log.debug("nothing found for $text from $uniqueChatTarget")
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

    fun send(outgoingPayload: OutgoingPayload) {
        async(JamesPool) {
            chatBackend.fromJamesToBackendChannel.send(outgoingPayload)
        }.awaitBlocking()
    }

    fun ask(
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

    companion object {
        private val log = LoggerFactory.getLogger("Chat")
    }
}
