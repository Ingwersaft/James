package com.mkring.james

import com.mkring.james.chatbackend.ChatBackend
import com.mkring.james.chatbackend.ChatConfig
import com.mkring.james.chatbackend.RocketChat
import com.mkring.james.chatbackend.Telegram
import com.mkring.james.chatbackend.rocketchat.RocketBackend
import com.mkring.james.chatbackend.telegram.TelegramBackend
import com.mkring.james.mapping.Mapping
import com.mkring.james.mapping.MappingPattern
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch
import org.slf4j.LoggerFactory

@DslMarker
annotation class LimitClosureScope

/**
 * builds and starts a James instance
 */
fun james(init: James.() -> Unit): James = James().also(init).autoStart()

private val log = LoggerFactory.getLogger(James::class.java)

@LimitClosureScope
class James(
    var name: String? = null,
    var autoStart: Boolean = true, val abortKeywords: MutableList<String> = mutableListOf()
) {
    internal val chatBackends: MutableList<ChatBackend> = mutableListOf()
    internal val chatConfigs: MutableList<ChatConfig> = mutableListOf()

    internal val mappings: MutableMap<MappingPattern, Mapping.() -> Unit> = mutableMapOf()

    internal val actualChats: MutableList<Chat> = mutableListOf()
    fun autoStart() = if (autoStart) {
        start()
    } else {
        this
    }

    fun start(): James = async {
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
            actualChats += Chat(
                mappingprefix = mappingprefix,
                type = it::class.java.simpleName,
                mappings = mappings.map { "$mappingprefix${it.key.pattern}" to it.value }.toMap(),
                abortKeywords = abortKeywords, chatBackend = it
            ).also {
                launch {
                    log.info("starting ${it.type}")
                    it.start()
                }
            }
        }

        return@async this@James
    }.awaitBlocking()

    private fun createChatBackends() {
        chatConfigs.map {
            chatBackends += when (it) {
                is RocketChat -> RocketBackend(
                    websocketTarget = it.websocketTarget,
                    sslVerifyHostname = it.sslVerifyHostname,
                    ignoreInvalidCa = it.ignoreInvalidCa,
                    defaultAvatar = it.defaultAvatar,
                    rocketUsername = it.username,
                    rocketPassword = it.password
                )
                is Telegram -> TelegramBackend(it.token, it.username)
            }
        }
    }

    private fun createHelpMapping(
        plainHelp: String,
        helpCommand: String
    ): Pair<String, Mapping.() -> Unit> {
        val lines = mutableListOf<String>()
        lines += "${name ?: "James"} at yor service:"
        lines += ""
        if (abortKeywords.isNotEmpty()) {
            lines += "abort interactions with: ${abortKeywords.joinToString(", ")}"
        }
        lines += "---"
        lines += plainHelp
        val mappingBlock: Mapping.() -> Unit = {
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
    fun map(pattern: String, helptext: String, block: Mapping.() -> Unit) {
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
    fun use(other: James) {
        other.mappings.forEach { p, block ->
            map(p.pattern, p.info, block)
        }
    }

    /**
     * If you need something other than the default backends, provide your own one
     * !Important! You have to interact with both io-channels:
     *
     * [ChatBackend.backendToJamesChannel]: When your backend receives messages, you have to forward it
     * to this channel. James will process them.
     *
     * [ChatBackend.fromJamesToBackendChannel]: You must! receive messaged on this channel. This is the channel
     * for James to send something back to the backend caller. This is best handled in a backend thread or coroutine
     */
    fun addCustomChatBackend(custom: ChatBackend) {
        chatBackends += custom
    }
}

