package com.mkring.james.chatbackend.telegram

import com.mkring.james.abortJob
import com.mkring.james.chatbackend.*
import com.mkring.james.mapping.Ask
import com.mkring.james.mapping.Mapping
import com.mkring.james.mapping.MappingPattern
import org.telegram.telegrambots.ApiContextInitializer
import org.telegram.telegrambots.TelegramBotsApi
import org.telegram.telegrambots.api.methods.send.SendMessage
import org.telegram.telegrambots.api.objects.Update
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.generics.BotSession
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit


class TelegramBackend(override val abortKeywords: MutableList<String>) : ChatBackend {

    private lateinit var session: BotSession

    override fun shutdown() {
        session.stop()
    }

    private lateinit var token: String

    private lateinit var username: String
    val chatLogicMappings = mutableMapOf<String, Mapping.() -> Unit>()

    val bot: TelegramLongPollingBot by lazy {
        ApiContextInitializer.init()
        object : TelegramLongPollingBot() {
            override fun getBotToken(): String = token
            override fun getBotUsername(): String = username
            override fun onUpdateReceived(update: Update?) {
                if (update == null) {
                    lg("update null")
                    return
                }
                if (update.message == null) {
                    lg("message null")
                    return
                }
                if (cleanText(update) == null) {
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

                // handle ask callbacks
                askResultMap[chatId]?.let {
                    if (text isIn abortKeywords) {
                        log.info("target $chatId received abortKeyword: $text")
                        send(chatId, "aborted")
                        askResultMap[chatId]?.cancel(true)
                        askResultMap.remove(chatId)
                        abortJob(chatId)
                        return
                    }
                    askResultMap[chatId]?.complete(text)
                    askResultMap.remove(chatId)
                    return
                }

                // launch first mapping
                launchFirstMatchingMapping(text = text, uniqueChatTarget = chatId, username = update.message.from.userName,
                        chat = this@TelegramBackend, chatLogicMappings = chatLogicMappings)
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
    }

    var askResultMap: MutableMap<UniqueChatTarget, CompletableFuture<String>> = mutableMapOf()

    override fun addMapping(prefix: String, matcher: MappingPattern, mapping: Mapping.() -> Unit) {
        lg("added mapping for '$matcher' with prefix '$prefix'")
        chatLogicMappings.put(prefix + matcher.pattern, mapping)
    }

    override fun login(options: Map<String, String>) {
        token = options.getValue("token")
        username = options.getValue("username")
        session = TelegramBotsApi().registerBot(bot)
    }

    override fun send(target: UniqueChatTarget, text: String, options: Map<String, String>) {
        bot.execute(SendMessage(target, text))
    }

    override fun ask(timeout: Int, timeunit: TimeUnit, target: UniqueChatTarget, text: String, options: Map<String, String>): Ask<String> {
        lg("ask: target=$target, text=$text")
        val future = CompletableFuture<String>()
        askResultMap.put(target, future)
        send(target, text)
        return Ask.of { future.get(timeout.toLong(), timeunit) }
    }

}

