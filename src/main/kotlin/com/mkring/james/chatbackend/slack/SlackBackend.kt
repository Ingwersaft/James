package com.mkring.james.chatbackend.slack

import com.mkring.james.chatbackend.ChatBackend
import com.mkring.james.chatbackend.IncomingPayload
import com.mkring.james.lw
import com.ullink.slack.simpleslackapi.SlackSession
import com.ullink.slack.simpleslackapi.impl.SlackSessionFactory
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * use the dsl function inside james
 */
class SlackBackend internal constructor(private val botOauthToken: String) : ChatBackend(),
    Logger by LoggerFactory.getLogger(SlackBackend::class.java) {

    private lateinit var session: SlackSession
    override fun start() {
        info("staring slack session")
        session = SlackSessionFactory.createWebSocketSlackSession(botOauthToken)
        session.connect()
        session.addMessagePostedListener { event, _ ->
            info("received message: $event")
            val payload = IncomingPayload(event.channel.id, event.user.realName, event.messageContent)
            runBlocking {
                launch {
                    backendToJamesChannel.send(payload)
                }
            }
        }

        launch {
            fromJamesToBackendChannel.consumeEach { payload ->
                try {
                    session.findChannelById(payload.target).let {
                        session.sendMessage(it, payload.text)
                    }
                } catch (e: Exception) {
                    lw("slack: outgoing message error (target='${payload.target}',text='${payload.text}'): ${e::class.java.simpleName} - ${e.message}")
                }
            }
        }
    }

    override fun stop() {
        session.disconnect()
        super.stop()
    }
}