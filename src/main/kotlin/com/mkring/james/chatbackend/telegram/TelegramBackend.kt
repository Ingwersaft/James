package com.mkring.james.chatbackend.telegram

import com.mkring.james.chatbackend.ChatBackend
import com.mkring.james.chatbackend.IncomingPayload
import com.mkring.james.lg
import com.mkring.james.lw
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.telegram.telegrambots.ApiContextInitializer
import org.telegram.telegrambots.TelegramBotsApi
import org.telegram.telegrambots.api.methods.send.SendMessage
import org.telegram.telegrambots.api.objects.Update
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.generics.BotSession

private val log = LoggerFactory.getLogger(TelegramBackend::class.java)

class TelegramBackend internal constructor(private val botToken: String, private val botUsername: String) :
    ChatBackend() {
    private lateinit var session: BotSession

    override fun start() {
        log.info("start()")
        session = TelegramBotsApi().registerBot(bot)
        // handle outgoing
        launch {
            fromJamesToBackendChannel.consumeEach { (target, text, options) ->
                try {
                    bot.execute(SendMessage(target, text).apply {
                        options["parse_mode"]?.let {
                            lg("SendMessage parse mode: $it")
                            setParseMode(it)
                        }
                    })
                } catch (e: Exception) {
                    lw("telegram: outgoing message error (target='$target',text='$text'): ${e::class.java.simpleName} - ${e.message}")
                }
            }
        }
    }

    private val bot: TelegramLongPollingBot by lazy {
        log.info("TelegramLongPollingBot starting up")
        ApiContextInitializer.init()
        object : TelegramLongPollingBot() {
            override fun getBotToken(): String = this@TelegramBackend.botToken
            override fun getBotUsername(): String = this@TelegramBackend.botUsername
            // handle incoming
            override fun onUpdateReceived(update: Update?) {
                log.debug("onUpdateReceived: $update")
                if (update == null) {
                    lg("update null")
                    return
                }
                if (update.message == null) {
                    lg("message null")
                    return
                }
                if (update.message.text == null) {
                    lg("text null")
                    return
                }
                if (update.message.from == null || update.message.from.id == null) {
                    lg("no user information, ignoring...")
                    return
                }
                if (update.message.chatId == null) {
                    lg("no chatid, ignoring...")
                    return
                }

                val chatId = update.message.chatId.toString()
                val text = cleanText(update)

                launch {
                    backendToJamesChannel.send(IncomingPayload(chatId, update.message.from.userName, text))
                }
            }
        }
    }

    override fun stop() {
        this.session.stop()
        super.stop()
    }

    private fun cleanText(update: Update): String {
        val s = update.message.text
        return if (s.contains("@")) {
            val splitted = s.split(Regex("@"))
            splitted.subList(0, splitted.size - 1).joinToString("@")
        } else {
            s
        }
    }
}