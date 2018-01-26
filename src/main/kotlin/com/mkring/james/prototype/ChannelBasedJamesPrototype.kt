package com.mkring.james.prototype

import com.mkring.james.abortJob
import com.mkring.james.chatbackend.UniqueChatTarget
import com.mkring.james.chatbackend.isIn
import com.mkring.james.mapping.Ask
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.Channel
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

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
    val backendToJamesChannel: Channel<IncomingPayload> = Channel(10)
    val fromJamesToBackendChannel: Channel<OutgoingPayload> = Channel(10)
    abstract suspend fun start()
}

class ChatV2(
    val type: String,
    val mappings: Map<String, MappingV2.() -> Unit>,
    val chatBackendV3: ChatBackendV3,
    val abortKeywords: MutableList<String>
) {
    private val runningJobs = mutableMapOf<UniqueChatTarget, Job>()
    private val askResultMap: MutableMap<UniqueChatTarget, CompletableFuture<String>> = mutableMapOf()
    suspend fun start() {
        chatBackendV3.start()
        log.info("ChatV2 chat started, now iterating chatBackend")
        fireAndForgetLoop("receive-from-backends") {
            chatBackendV3.backendToJamesChannel.receive().let { (uniqueChatTarget, username, text) ->
                log.info("chatBackendV3.backendToJamesChannel received $text from $username - $uniqueChatTarget")
                if (callbackFutureHandled(text, uniqueChatTarget).not()) {
                    log.debug("mappings=$mappings")
                    mappings.map { it.key }.joinToString(";").let { println("chatLogicMappings keys =$it") }

                    mappings.entries.firstOrNull()?.let { entry ->
                        log.info("going to handle ${entry.key}")
                        val job = launch {
                            MappingV2(text, uniqueChatTarget, username, this@ChatV2).apply {
                                entry.value.invoke(this)
                            }
                        }
                        log.info("launch done")
                        runningJobs.put(uniqueChatTarget, job)
                    } ?: Unit.let {
                        log.debug("nothing found for $text from $uniqueChatTarget")
                    }
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
                abortJob(target)
                return true
            }
            askResultMap[target]?.complete(text)
            askResultMap.remove(target)
            return true
        }
        return false
    }

    fun send(outgoingPayload: OutgoingPayload) {
        async {
            chatBackendV3.fromJamesToBackendChannel.send(outgoingPayload)
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
        private val log = LoggerFactory.getLogger("ChatV2")
    }
}

class TestingChatBackend : ChatBackendV3() {
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

class MappingV2(
    private val commandText: String,
    private val uniqueChatTarget: UniqueChatTarget,
    val username: String?,
    private val parentChat: ChatV2
) {
    /**
     * prefix and pattern will be present too!
     */
    val arguments by lazy {
        commandText.removePrefix(username ?: "").trim().split(Regex("\\s+")).filterNot { it.isEmpty() }.toList()
    }

    /**
     * default timeout when asking
     */
    var askTimeout = 120

    /**
     * default timeout timeunit when asking
     */
    var timeUnit = TimeUnit.SECONDS

    fun send(text: String, options: Map<String, String> = emptyMap()) {
        async {
            parentChat.send(OutgoingPayload(uniqueChatTarget, text, options))
        }.awaitBlocking()
    }

    fun ask(text: String, options: Map<String, String> = emptyMap()): Ask<String> =
        parentChat.ask(text, options, askTimeout, timeUnit, uniqueChatTarget)
}

fun main(args: Array<String>) = runBlocking {
    val chat = TestingChatBackend()
    val mapping: MappingV2.() -> Unit = {
        send("world")
        ask("time").get().let { println("response: $it") }
    }
    ChatV2("TestingChatBackend",mutableMapOf("/hallo" to mapping), chat, mutableListOf("/abort")).apply {
        start()
    }

    println("wait then onMessage^")
    delay(100)
    chat.onMessage("/hallo")
    delay(100)
    chat.onMessage("penis")
    delay(100)
}

fun <T> Deferred<T>.awaitBlocking(): T = runBlocking {
    await()
}

private val fireAndForgetLoopLog = LoggerFactory.getLogger("fireAndForgetLoop")
suspend fun fireAndForgetLoop(name: String, block: suspend CoroutineScope.() -> Unit) = launch {
    while (true) {
        try {
            block()
        } catch (e: Exception) {
            fireAndForgetLoopLog.error("catched exception in $name routing: ${e::class.java.simpleName}:${e.message}")
        }
        delay(10)
    }
}.also {
    fireAndForgetLoopLog.info("launched $name")
}
