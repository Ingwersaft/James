package com.mkring.james.chatbackend

import com.mkring.james.RunningJobs
import com.mkring.james.abortJob
import com.mkring.james.mapping.Ask
import com.mkring.james.mapping.Mapping
import com.mkring.james.mapping.MappingPattern
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.launch
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

typealias UniqueChatTarget = String

interface ChatBackend {
    fun addMapping(prefix: String, matcher: MappingPattern, mapping: Mapping.() -> Unit)
    fun login(options: Map<String, String>)
    fun send(target: UniqueChatTarget, text: String, options: Map<String, String> = emptyMap())
    fun ask(timeout: Int, timeunit: TimeUnit, target: UniqueChatTarget, text: String, options: Map<String, String> = emptyMap()): Ask<String>
    fun shutdown()
    val abortKeywords: MutableList<String>
}

fun Any.lg(toBeLogged: Any) {
    LoggerFactory.getLogger(this::class.java).apply {
        this.info(toBeLogged.toString())
    }
}

val log = LoggerFactory.getLogger(ChatBackend::class.java)
fun launchFirstMatchingMapping(text: String, uniqueChatTarget: String, username: String?, chat: ChatBackend,
                               chatLogicMappings: Map<String, Mapping.() -> Unit>): Job? {
    chatLogicMappings.filter { text.matches(Regex("^" + it.key + ".*", RegexOption.IGNORE_CASE)) }
            .entries.first().let { entry ->
        log.info("going to handle ${entry.key}")
        val job = launch {
            Mapping(text, uniqueChatTarget, username, chat).apply { entry.value.invoke(this) }
        }
        log.info("launch done")
        RunningJobs.running.put(uniqueChatTarget, job)
        return job
    }
}

internal infix fun String.isIn(abortKeywords: List<String>): Boolean {
    log.info("isIn(): $this $abortKeywords")
    return abortKeywords.contains(this)
}
