package com.mkring.james.chatbackend.slack

import com.mkring.james.chatbackend.ChatBackend
import com.mkring.james.chatbackend.IncomingPayload
import com.mkring.james.fireAndForgetLoop
import com.ullink.slack.simpleslackapi.impl.SlackSessionFactory
import kotlinx.coroutines.experimental.channels.consumeEach
import kotlinx.coroutines.experimental.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class SlackBackend(val botOauthToken: String) : ChatBackend(),
    Logger by LoggerFactory.getLogger(SlackBackend::class.java) {
    override suspend fun start() {
        info("staring slack session")
        val session = SlackSessionFactory.createWebSocketSlackSession(botOauthToken)
        session.connect()
        session.addMessagePostedListener { event, _ ->
            info("received message: $event")
            val payload = IncomingPayload(event.channel.id, event.user.realName, event.messageContent)
            launch {
                backendToJamesChannel.send(payload)
            }
        }

        fireAndForgetLoop("Slack-outgoing-coroutine") {
            fromJamesToBackendChannel.consumeEach { payload ->
                session.findChannelById(payload.target).let {
                    session.sendMessage(it, payload.text)
                }
            }
        }
    }
}