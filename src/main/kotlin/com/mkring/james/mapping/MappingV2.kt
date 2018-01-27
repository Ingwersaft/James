package com.mkring.james.mapping

import com.mkring.james.ChatV2
import com.mkring.james.awaitBlocking
import com.mkring.james.chatbackend.OutgoingPayload
import com.mkring.james.chatbackend.UniqueChatTarget
import com.mkring.james.lg
import kotlinx.coroutines.experimental.async
import java.util.concurrent.TimeUnit

data class MappingPattern(val pattern: String, val info: String)

class MappingV2(
    private val commandText: String,
    private val uniqueChatTarget: UniqueChatTarget,
    val username: String?,
    private val mappingprefix: String,
    private val parentChat: ChatV2
) {
    /**
     * prefix and pattern will be present too!
     */
    val arguments by lazy {
        lg("commandText=$commandText username=$username")
        commandText.removePrefix(mappingprefix).trim().split(Regex("\\s+")).filterNot { it.isEmpty() }.toList()
    }

    /**
     * default timeout when asking
     */
    var askTimeout = 120

    /**
     * default timeout timeunit when asking
     */
    var timeUnit = TimeUnit.SECONDS

    /**
     * If you ask with retries, this will be printed when the predicate is false
     */
    val wrongAnswerText = "incompatible answer!"

    /**
     *  If you ask with retries, this will be printed when a timeout happens
     */
    val timeoutText = "timeout!"

    /**
     * If you ask with retries and all failed, this will be printed
     */
    val askWithRetryFailedText = "well, nevermind then"

    /**
     * Send some text to the chat counterpart
     */
    fun send(text: String, options: Map<String, String> = emptyMap()) {
        async {
            parentChat.send(OutgoingPayload(uniqueChatTarget, text, options))
        }.awaitBlocking()
    }

    /**
     *  Ask something using the mapping timeout
     *  @param text the quesion to be asked
     *  @param options chat specific options -> see James README
     */
    fun ask(text: String, options: Map<String, String> = emptyMap()): Ask<String> =
        parentChat.ask(text, options, askTimeout, timeUnit, uniqueChatTarget)

    /**
     * Ask something and retry X times if timeout OR [predicate] false
     * @param retries number of [retries] after the first failure. retries = 2 will ask a total of 3
     * @param predicate this will determine if the answer is valid
     * @param text the question to be asked
     * @param options chat specific options -> see James README
     */
    fun askWithRetry(
        retries: Int, text: String, options: Map<String, String> = emptyMap(),
        predicate: (String) -> Boolean
    ): Ask<String> {
        //sequence is lazy eval!
        val firstOrNull = IntRange(0, retries).asSequence().map {
            ask(text, options)
        }.filter {
                when (it) {
                    is Ask.Timeout -> {
                        send(timeoutText)
                        false
                    }
                    is Ask.Answer -> {
                        when (predicate(it.value)) {
                            true -> true
                            else -> {
                                send(wrongAnswerText)
                                false
                            }
                        }
                    }
                }
            }.firstOrNull()
        return when (firstOrNull) {
            null -> {
                send(askWithRetryFailedText)
                Ask.Timeout
            }
            else -> firstOrNull
        }
    }
}
