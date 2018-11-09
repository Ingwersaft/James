package com.mkring.james.chatbackend.slack

import com.mkring.james.JamesPool
import com.mkring.james.chatbackend.ChatBackend
import com.mkring.james.chatbackend.IncomingPayload
import com.ullink.slack.simpleslackapi.impl.SlackSessionFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.coroutines.CoroutineContext

class SlackBackend(val botOauthToken: String) : ChatBackend(),
    Logger by LoggerFactory.getLogger(SlackBackend::class.java) {
    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = job + JamesPool

    override fun start() {
        info("staring slack session")
        val session = SlackSessionFactory.createWebSocketSlackSession(botOauthToken)
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
                session.findChannelById(payload.target).let {
                    session.sendMessage(it, payload.text)
                }
            }
        }
    }
}