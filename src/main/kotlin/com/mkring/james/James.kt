package com.mkring.james

import com.mkring.james.chatbackend.ChatBackend
import com.mkring.james.chatbackend.RocketChat
import com.mkring.james.chatbackend.Telegram
import com.mkring.james.chatbackend.lg
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
class James(var name: String? = null,
            var autoStart: Boolean = true) {
    internal lateinit var chat: ChatBackend
    internal val mappings: MutableMap<MappingPattern, Mapping.() -> Unit> = mutableMapOf()
    private var additionalChatOptions: Map<String, String> = emptyMap()

    fun autoStart() = if (autoStart) {
        start()
    } else {
        this
    }

    fun start(): James {
        lg("start()")
        val mappingprefix = when (name) {
            null -> ""
            else -> name + " "
        }
        lg("mapping prefix:$mappingprefix")
        val plainHelp = mappings.map { "$mappingprefix${it.key.pattern} - ${it.key.info}" }.joinToString("\n")

        addHelpMapping(mappingprefix, plainHelp, "help")
        if (chat is TelegramBackend) addHelpMapping(mappingprefix, plainHelp, "/help")

        mappings.forEach { chat.addMapping(mappingprefix, it.key, it.value) }
        chat.login(additionalChatOptions)

        return this
    }

    private fun addHelpMapping(mappingprefix: String, plainHelp: String, helpCommand: String) {
        chat.addMapping(mappingprefix, MappingPattern(helpCommand, "")) {
            """James at yor service:
                    |---
                """.trimMargin().trim().let { send(it + "\n" + plainHelp) }
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
        val config = RocketChat().also(init)
        config.let {
            this.chat = RocketBackend(websocketTarget = it.websocketTarget,
                    ignoreInvalidCa = it.ignoreInvalidCa,
                    sslVerifyHostname = it.sslVerifyHostname)
            this.additionalChatOptions = mapOf("username" to it.username, "password" to it.password)
        }
    }

    /**
     * create Telegram config/chat
     */
    fun telegram(init: Telegram.() -> Unit) {
        val config = Telegram().also(init)
        config.let {
            this.chat = TelegramBackend()
            this.additionalChatOptions = mapOf("token" to it.token,
                    "username" to it.username)
        }
    }
}

