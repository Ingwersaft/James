package com.mkring.james.chatbackend.telegram

import com.mkring.james.chatbackend.ChatBackendV3
import com.mkring.james.chatbackend.IncomingPayload
import com.mkring.james.chatbackend.lg
import com.mkring.james.fireAndForgetLoop
import kotlinx.coroutines.experimental.launch
import org.slf4j.LoggerFactory
import org.telegram.telegrambots.ApiContextInitializer
import org.telegram.telegrambots.TelegramBotsApi
import org.telegram.telegrambots.api.methods.send.SendMessage
import org.telegram.telegrambots.api.objects.Update
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.generics.BotSession

private val log = LoggerFactory.getLogger(TelegramBackendV2::class.java)
class TelegramBackendV2(val botToken: String, val botUsername: String) : ChatBackendV3() {
    private lateinit var session: BotSession

    override suspend fun start() {
        log.info("start()")
        session = TelegramBotsApi().registerBot(bot)
        // handle outgoing
        fireAndForgetLoop("TelegramBackendV2-outgoing-receiver") {
            val (target, text, options) = fromJamesToBackendChannel.receive()
            bot.execute(SendMessage(target, text).apply {
                options["parse_mode"]?.let {
                    lg("SendMessage parse mode: $it")
                    setParseMode(it)
                }
            })
        }
        log.info("starting up done")
    }

    private val bot: TelegramLongPollingBot by lazy {
        log.info("TelegramLongPollingBot starting up")
        ApiContextInitializer.init()
        object : TelegramLongPollingBot() {
            override fun getBotToken(): String = this@TelegramBackendV2.botToken
            override fun getBotUsername(): String = this@TelegramBackendV2.botUsername
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