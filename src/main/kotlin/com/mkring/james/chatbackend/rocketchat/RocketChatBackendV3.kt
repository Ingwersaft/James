package com.mkring.james.chatbackend.rocketchat

import com.mkring.james.chatbackend.ChatBackend
import com.mkring.james.chatbackend.IncomingPayload
import com.tinder.scarlet.Lifecycle
import com.tinder.scarlet.Scarlet
import com.tinder.scarlet.Stream
import com.tinder.scarlet.WebSocket
import com.tinder.scarlet.lifecycle.LifecycleRegistry
import com.tinder.scarlet.messageadapter.gson.GsonMessageAdapter
import com.tinder.scarlet.websocket.okhttp.newWebSocketFactory
import com.tinder.scarlet.ws.Receive
import com.tinder.scarlet.ws.Send
import com.tinder.streamadapter.coroutines.CoroutinesStreamAdapterFactory
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.consumeEach
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.reactive.consumeEach
import okhttp3.OkHttpClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory


class RocketChatBackendV3(
    private val webSocketTargetUrl: String,
    private val username: String,
    private val password: String,
    private val defaultAvatar: String
) : ChatBackend(), Logger by LoggerFactory.getLogger(RocketChatBackendV3::class.java) {
    private val clientLifecycleRegistry = LifecycleRegistry()

    private val client: RocketChat by lazy {
        val scarlet = Scarlet.Builder()
            .webSocketFactory(OkHttpClient().newWebSocketFactory(webSocketTargetUrl))
            .lifecycle(clientLifecycleRegistry)
            .addMessageAdapterFactory(GsonMessageAdapter.Factory())
            .addStreamAdapterFactory(CoroutinesStreamAdapterFactory())
            .build()
        scarlet.create<RocketChat>()
    }
    private val subbedRooms = mutableListOf<String>()
    private var connected = false

    override suspend fun start() {
        launch {
            info("launching `handleOutgoingMessages` coroutine")
            handleOutgoingMessages()
        }
        launch {
            info("launching `handleWebsocketClientEvents` coroutine")
            handleWebsocketClientEvents()
        }
        launch {
            info("launching `handleRocketchatApiEvents` coroutine")
            handleRocketchatApiEvents()
        }
        launch {
            info("launching `refreshSubscriptions` coroutine")
            refreshSubscriptions()
        }
        clientLifecycleRegistry.onNext(Lifecycle.State.Started)
    }

    private suspend fun refreshSubscriptions() {
        while (true) {
            delay(2000)
            if (connected) {
                client.sendGetSubscriptionRequest(GetSubscriptionRequest())
                debug("sendGetSubscriptionRequest executed")
            }
        }
    }

    private suspend fun handleRocketchatApiEvents() {
        client.onMessage().consumeEach {
            when {
                it.msg == "ping" -> client.sendPong(Pong())
                it.server_id == "0" -> client.sendConnect(Connect())
                it.msg == "connected" -> {
                    info("connected: $it")
                    client.sendLogin(LoginRequest(params = listOf(Param(User(username), password))))
                }
                it.msg == "added" -> {
                    info("added with connection id `${it.id}`: $it")
                }
                it.msg == "result" && it.resultElement() != null -> {
                    val tokenResult = it.resultElement()
                    info("loginResponse with token `${tokenResult?.token}`: $tokenResult")
                    connected = true
                    client.sendGetSubscriptionRequest(GetSubscriptionRequest())
                }
                it.msg == "result" && it.resultArray() != null -> {
                    val subs = it.resultArray()
                    debug("subResponse: $subs")
                    subs?.filter { subbedRooms.contains(it.rid).not() }?.forEach {
                        client.sendSubscribeToRoom(SubscribeRequest(roomId = it.rid))
                        subbedRooms.add(it.rid)
                        debug("subbed to room ${it.rid}")
                    }
                }
                it.msg == "changed" && it.collection == "stream-room-messages" -> {
                    debug("received message: ${it.fields}")
                    debug("args: ${it.fields?.args}")
                    it.fields?.args?.forEach {
                        info("arg: $it")
                        if (it.u.username != username) {
                            backendToJamesChannel.send(IncomingPayload(it.rid, it.u.username, it.msg))
                        } else {
                            debug("ignoring msg from myself")
                        }
                    }
                }
                it.msg == "changed" -> {
                    debug("changed: $it")
                }
                it.msg == "updated" -> {
                    debug("updated: $it")
                }
                it.msg == "ready" -> {
                    debug("ready: $it")
                }
                else -> warn("unhandled and unsupported event: $it")
            }
        }
    }

    private suspend fun handleWebsocketClientEvents() {
        client.observeEvents().consumeEach {
            when (it) {
                is WebSocket.Event.OnMessageReceived -> Unit //debug log
                is WebSocket.Event.OnConnectionOpened<*> -> {
                    info("opened connection: $it")
                    connected = true
                }
                is WebSocket.Event.OnConnectionClosing -> {
                    subbedRooms.clear()
                    connected = false
                    warn("websocket connection is closing!")
                }
                is WebSocket.Event.OnConnectionClosed -> {
                    subbedRooms.clear()
                    connected = false
                    error("websocket connection closed!")
                }
                is WebSocket.Event.OnConnectionFailed -> {
                    subbedRooms.clear()
                    connected = false
                    error("connection to `${webSocketTargetUrl}` failed!")
                }
            }
        }
    }

    private suspend fun handleOutgoingMessages() {
        fromJamesToBackendChannel.consumeEach {
            if (connected) {
                client.sendMessage(
                    SendMessageRequest(
                        rid = it.target,
                        messageText = it.text,
                        emoji = it.options.getOrDefault("avatar", defaultAvatar)
                    )
                )
            } else {
                error("not connected so won't send $it!")
            }
        }
    }
}

interface RocketChat {
    @Receive // scarlet stream
    fun observeEvents(): Stream<WebSocket.Event>

    @Receive
    fun onMessage(): ReceiveChannel<RocketResponse>

    @Send
    fun sendConnect(connect: Connect)

    @Send
    fun sendPong(pong: Pong)

    @Send
    fun sendLogin(login: LoginRequest)

    @Send
    fun sendGetSubscriptionRequest(request: GetSubscriptionRequest)

    @Send
    fun sendSubscribeToRoom(request: SubscribeRequest)

    @Send
    fun sendMessage(request: SendMessageRequest)
}
