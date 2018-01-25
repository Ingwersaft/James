package com.mkring.james

import com.mkring.james.chatbackend.*
import com.mkring.james.chatbackend.rocketchat.RocketBackend
import com.mkring.james.chatbackend.telegram.TelegramBackend
import com.mkring.james.mapping.Mapping
import com.mkring.james.mapping.MappingPattern

@DslMarker
annotation class LimitClosureScope

/**
 * builds and starts a James instance
 */
fun james(init: James.() -> Unit): James = James().also(init).autoStart()

@LimitClosureScope
class James(
    var name: String? = null,
    var autoStart: Boolean = true, val abortKeywords: MutableList<String> = mutableListOf()
) {
    internal lateinit var chat: ChatBackend
    internal val mappings: MutableMap<MappingPattern, Mapping.() -> Unit> = mutableMapOf()
    private var additionalChatOptions: Map<String, String> = emptyMap()

    internal lateinit var chatConfig: ChatConfig
    fun autoStart() = if (autoStart) {
        start()
    } else {
        this
    }

    fun start(): James {
        lg("start()")

        createChatBackend()
        val mappingprefix = when (name) {
            null -> ""
            else -> name + " "
        }

        chat.abortKeywords.addAll(abortKeywords)

        lg("mapping prefix:$mappingprefix")
        val plainHelp = mappings.map { "$mappingprefix${it.key.pattern} - ${it.key.info}" }.joinToString("\n")

        addHelpMapping(mappingprefix, plainHelp, "help")
        if (chat is TelegramBackend) addHelpMapping(mappingprefix, plainHelp, "/help")

        mappings.forEach { chat.addMapping(mappingprefix, it.key, it.value) }
        chat.login(additionalChatOptions)

        return this
    }

    private fun createChatBackend() {
        val config = chatConfig
        when (config) {
            is RocketChat -> {
                this.chat = RocketBackend(
                    websocketTarget = config.websocketTarget,
                    ignoreInvalidCa = config.ignoreInvalidCa,
                    sslVerifyHostname = config.sslVerifyHostname,
                    abortKeywords = mutableListOf(),
                    jamesName = name ?: "",
                    defaultAvatar = config.defaultAvatar
                )
                this.additionalChatOptions = mapOf("username" to config.username, "password" to config.password)
            }
            is Telegram -> {
                this.chat = TelegramBackend(mutableListOf(), name ?: "")
                this.additionalChatOptions = mapOf(
                    "token" to config.token,
                    "username" to config.username
                )
            }
        }

    }

    private fun addHelpMapping(mappingprefix: String, plainHelp: String, helpCommand: String) {
        chat.addMapping(mappingprefix, MappingPattern(helpCommand, "")) {
            val lines = mutableListOf<String>()
            lines += "${name ?: "James"} at yor service:"
            lines += ""
            if (abortKeywords.isNotEmpty()) {
                lines += "abort interactions with: ${abortKeywords.joinToString(", ")}"
            }
            lines += "---"
            lines += plainHelp
            send(lines.joinToString("\n"))
        }
    }

    fun stop() {
        lg("stop()")
        chat.shutdown()
    }

    /**
     *  @param block
     *  @param pattern The pattern to match this mapping to.
     *  Will be used like `text.matches(Regex("^" + pattern + ".*", RegexOption.IGNORE_CASE))`
     *  @param helptext Text used when help gets build and sent
     */
    fun map(pattern: String, helptext: String, block: Mapping.() -> Unit) {
        lg("map() for pattern=$pattern helptext=$helptext")
        mappings.put(MappingPattern(pattern, helptext), block)
    }

    /**
     * create RochetChat config/chat
     */
    fun rocketchat(init: RocketChat.() -> Unit) {
        chatConfig = RocketChat().also(init)
    }

    /**
     * create Telegram config/chat
     */
    fun telegram(init: Telegram.() -> Unit) {
        chatConfig = Telegram().also(init)
    }

    /**
     * Use mappings of other James instance (uses only mappings, no other config )
     * Hint: Create other James instances with autoStart disabled
     */
    fun use(other: James) {
        other.mappings.forEach { p, block ->
            map(p.pattern, p.info, block)
        }
    }
}

