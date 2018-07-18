package com.mkring.james.chatbackend.discord

import com.mkring.james.chatbackend.ChatBackend
import com.mkring.james.chatbackend.IncomingPayload
import kotlinx.coroutines.experimental.channels.consumeEach
import kotlinx.coroutines.experimental.launch
import net.dv8tion.jda.core.AccountType
import net.dv8tion.jda.core.JDA
import net.dv8tion.jda.core.JDABuilder
import net.dv8tion.jda.core.entities.MessageChannel
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import net.dv8tion.jda.core.hooks.ListenerAdapter
import org.slf4j.LoggerFactory

class DiscordBackend(private val token: String) : ChatBackend() {
    private val log = LoggerFactory.getLogger(DiscordBackend::class.java)

    override suspend fun start() {
        jda.addEventListener(object : ListenerAdapter() {
            override fun onMessageReceived(event: MessageReceivedEvent) {
                if (event.author.name == self) {
                    log.debug("ignoring message from myself")
                    return
                }
                launch {
                    backendToJamesChannel.send(
                        IncomingPayload(
                            event.channel.id,
                            event.author.name,
                            event.message.contentDisplay
                        )
                    )
                }
            }
        })
        launch {
            fromJamesToBackendChannel.consumeEach { (target, text, _) ->
                try {
                    jda.getAnyChannel(target).sendMessage(text).complete()
                } catch (e: Exception) {
                    log.error("jda.getAnyChannel(target).sendMessage(text).complete() failed: ${e.message}", e)
                }
            }
        }
    }

    private val jda by lazy { JDABuilder(AccountType.BOT).setToken(token).buildBlocking() }
    private val self by lazy { jda.selfUser.name }
}

private fun JDA.getAnyChannel(id: String): MessageChannel = getPrivateChannelById(id) ?: getTextChannelById(id)

