package com.mkring.james

import com.mkring.james.chatbackend.ChatConfig
import com.mkring.james.chatbackend.RocketChat
import com.mkring.james.chatbackend.Telegram
import com.mkring.james.chatbackend.lg
import com.mkring.james.chatbackend.telegram.TelegramBackendV2
import com.mkring.james.mapping.MappingPattern
import com.mkring.james.prototype.*
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch

/**
 * builds and starts a James instance
 */
fun jamesV3(init: JamesV3.() -> Unit): JamesV3 = JamesV3().also(init).autoStart()

@LimitClosureScope
class JamesV3(
    var name: String? = null,
    var autoStart: Boolean = true, val abortKeywords: MutableList<String> = mutableListOf()
) {
    internal val chatBackends: MutableList<ChatBackendV3> = mutableListOf()
    internal val chatConfigs: MutableList<ChatConfig> = mutableListOf()

    internal val mappings: MutableMap<MappingPattern, MappingV2.() -> Unit> = mutableMapOf()

    private var additionalChatOptions: Map<String, String> = emptyMap()

    internal val actualChats: MutableList<ChatV2> = mutableListOf()
    fun autoStart() = if (autoStart) {
        start()
    } else {
        this
    }

    fun start(): JamesV3 = async {
        lg("start()")

        createChatBackends()
        val mappingprefix = when (name) {
            null -> ""
            else -> name + " "
        }

        lg("mapping prefix:$mappingprefix")
        val plainHelp = mappings.map { "$mappingprefix${it.key.pattern} - ${it.key.info}" }.joinToString("\n")
        val helpMapping = createHelpMapping(plainHelp, "help")
        val helpMappingSlash = createHelpMapping(plainHelp, "/help")
        mappings[MappingPattern(helpMapping.first, "nvmd")] = helpMapping.second
        mappings[MappingPattern(helpMappingSlash.first, "nvmd")] = helpMappingSlash.second
        log.info("going to startup ${chatBackends.size} chatBackends")
        chatBackends.forEach {
            actualChats += ChatV2(
                mappingprefix = mappingprefix,
                type = it::class.java.simpleName,
                mappings = mappings.map { "$mappingprefix${it.key.pattern}" to it.value }.toMap(),
                abortKeywords = abortKeywords, chatBackendV3 = it
            ).also {
                launch {
                    log.info("starting ${it.type}")
                    it.start()
                }
            }
        }

        return@async this@JamesV3
    }.awaitBlocking()

    private fun createChatBackends() {
        chatConfigs.map {
            when (it) {
                is RocketChat -> {
//                    RocketBackend(
//                        websocketTarget = config.websocketTarget,
//                        ignoreInvalidCa = config.ignoreInvalidCa,
//                        sslVerifyHostname = config.sslVerifyHostname,
//                        abortKeywords = mutableListOf(),
//                        jamesName = name ?: "",
//                        defaultAvatar = config.defaultAvatar
//                    )
                    TODO("RocketChat backend v2 not yet implemented")
                }
                is Telegram -> chatBackends += TelegramBackendV2(it.token, it.username)
            }
        }
    }

    private fun createHelpMapping(
        plainHelp: String,
        helpCommand: String
    ): Pair<String, MappingV2.() -> Unit> {
        val lines = mutableListOf<String>()
        lines += "${name ?: "James"} at yor service:"
        lines += ""
        if (abortKeywords.isNotEmpty()) {
            lines += "abort interactions with: ${abortKeywords.joinToString(", ")}"
        }
        lines += "---"
        lines += plainHelp
        val mappingBlock: MappingV2.() -> Unit = {
            send(lines.joinToString("\n"))
        }
        return Pair(helpCommand, mappingBlock)
    }

    fun stop() {
        lg("stop()")
        // TODO("support real stop")
    }

    /**
     *  @param block
     *  @param pattern The pattern to match this mapping to.
     *  Will be used like `text.matches(Regex("^" + pattern + ".*", RegexOption.IGNORE_CASE))`
     *  @param helptext Text used when help gets build and sent
     */
    fun map(pattern: String, helptext: String, block: MappingV2.() -> Unit) {
        lg("map() for pattern=$pattern helptext=$helptext")
        mappings[MappingPattern(pattern, helptext)] = block
    }

    /**
     * create RochetChat config/chat
     */
    fun rocketchat(init: RocketChat.() -> Unit) {
        chatConfigs += RocketChat().also(init)
    }

    /**
     * create Telegram config/chat
     */
    fun telegram(init: Telegram.() -> Unit) {
        chatConfigs += Telegram().also(init)
    }

    /**
     * Use mappings of other James instance (uses only mappings, no other config )
     * Hint: Create other James instances with autoStart disabled
     */
    fun use(other: JamesV3) {
        other.mappings.forEach { p, block ->
            map(p.pattern, p.info, block)
        }
    }
}

