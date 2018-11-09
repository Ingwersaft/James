package com.mkring.james

import com.mkring.james.chatbackend.*
import com.mkring.james.chatbackend.rocketchat.RocketChatBackend
import com.mkring.james.chatbackend.slack.SlackBackend
import com.mkring.james.chatbackend.telegram.TelegramBackend
import com.mkring.james.mapping.Mapping
import com.mkring.james.mapping.MappingPattern
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import kotlin.coroutines.CoroutineContext

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
    var autoStart: Boolean = true,
    val abortKeywords: MutableList<String> = mutableListOf()
) : CoroutineScope {
    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = job + JamesPool

    internal val chatBackends: MutableList<ChatBackend> = mutableListOf()
    internal val chatConfigs: MutableList<ChatConfig> = mutableListOf()

    internal val mappings: MutableMap<MappingPattern, Mapping.() -> Unit> = mutableMapOf()

    internal val actualChats: MutableList<Chat> = mutableListOf()
    internal fun autoStart() = if (autoStart) {
        start()
    } else {
        this
    }

    /**
     * check if james started
     */
    fun isStarted() = started

    private var started: Boolean = false
    fun start(): James {
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
        started = true
        return this
    }

    private fun createChatBackends() {
        chatConfigs.map {
            chatBackends += when (it) {
                is RocketChat -> RocketChatBackend(
                    webSocketTargetUrl = it.websocketTarget,
                    sslVerifyHostname = it.sslVerifyHostname,
                    ignoreInvalidCa = it.ignoreInvalidCa,
                    defaultAvatar = it.defaultAvatar,
                    username = it.username,
                    password = it.password
                )
                is Telegram -> TelegramBackend(it.token, it.username)
                is Slack -> SlackBackend(it.botOauthToken)
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

    fun slack(init: Slack.() -> Unit) {
        chatConfigs += Slack().also(init)
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

    /**
     * If you need to initiate a conversation you can use this method (if james started already)
     *
     * @param uniqueChatTarget target chat for which conversation shall be started
     * @param mappingLogic your logic for this conversation
     */
    fun initiateConversation(uniqueChatTarget: UniqueChatTarget, mappingLogic: Mapping.() -> Unit) {
        log.info("initiateConversation to $uniqueChatTarget")
        if (started.not()) {
            throw IllegalAccessError("james isn't started yet!")
        }
        actualChats.forEach {
            Mapping("<initiateConversation>", uniqueChatTarget, null, "<initiateConversation>", it).apply {
                mappingLogic()
            }
        }
    }
}

