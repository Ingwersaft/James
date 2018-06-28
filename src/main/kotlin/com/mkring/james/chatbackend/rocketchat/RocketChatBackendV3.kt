package com.mkring.james.chatbackend.rocketchat

import com.beust.klaxon.Json
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.mkring.james.chatbackend.ChatBackend
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
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.reactive.consumeEach
import kotlinx.coroutines.experimental.runBlocking
import okhttp3.OkHttpClient
import java.util.*

fun main(args: Array<String>) = runBlocking {
    RocketChatBackendV3("<>", "<>", "<>").start()
    Thread.sleep(2000000)
}

class RocketChatBackendV3(
    private val webSocketTargetUrl: String,
    private val username: String,
    private val password: String
) : ChatBackend() {
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

    override suspend fun start() {
        var subbedRooms = mutableListOf<String>()
        var connected = false
        launch {
            client.observeEvents().consumeEach {
                when (it) {
                    is WebSocket.Event.OnMessageReceived -> Unit //debug log
                    is WebSocket.Event.OnConnectionOpened<*> -> {
                        println("opened connection: $it")
                        connected = true
                    }
                    is WebSocket.Event.OnConnectionClosing -> {
                        subbedRooms.clear()
                        println("closing?!")
                    }
                    is WebSocket.Event.OnConnectionClosed -> {
                        subbedRooms.clear()
                        println("closed!")
                    }
                    is WebSocket.Event.OnConnectionFailed -> {
                        subbedRooms.clear()
                        println("failed!")
                    }
                }
            }
        }
        launch {
            var connectionId: String? = null
            var token: String? = null
            client.onMessage().consumeEach {
                when {
                    it.msg == "ping" -> client.sendPong(Pong())
                    it.server_id == "0" -> client.sendConnect(Connect())
                    it.msg == "connected" -> {
                        println("connected: $it")
                        client.sendLogin(LoginRequest(params = listOf(Param(User(username), password))))
                    }
                    it.msg == "added" -> {
                        println("added: $it")
                        connectionId = it.id
                    }
                    it.msg == "result" && it.resultElement() != null -> {
                        val tokenResult = it.resultElement()
                        println("loginResponse: $tokenResult")
                        token = tokenResult?.token
                        connected = true
                        client.sendGetSubscriptionRequest(GetSubscriptionRequest())
                    }
                    it.msg == "result" && it.resultArray() != null -> {
                        val subs = it.resultArray()
                        println("subResponse: $subs")
                        subs?.forEach {
                            client.sendSubscribeToRoom(SubscribeRequest(roomId = it.rid))
                            subbedRooms.add(it.rid)
                            println("subbed to room ${it.rid}")
                        }
                    }
                    else -> println("unhandled!: $it")
                }
            }
        }
        launch {
            fromJamesToBackendChannel.consumeEach {
                println("looks like i should send something: $it")
            }
        }
        clientLifecycleRegistry.onNext(Lifecycle.State.Started)
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
}

private val gson = Gson()

data class RocketResponse(
    val server_id: String? = null,

    val msg: String? = null,
    val session: String? = null,
    val collection: String? = null,
    val id: String? = null,
    val fields: Fields? = null,

    @SerializedName("result")
    val resultObject: JsonElement? = null,

    val methods: List<String>? = null,
    val subs: List<String>? = null
) {
    fun resultElement(): TokenResult? {
        return if (resultObject?.isJsonObject == true) {
            try {
                gson.fromJson(resultObject, TokenResult::class.java)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    fun resultArray(): List<ResultElement>? {
        return if (resultObject?.isJsonArray == true) {
            try {
                gson.fromJson<List<ResultElement>>(resultObject, object : TypeToken<List<ResultElement>>() {}.type)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }
}

data class Fields(
    val emails: List<Email>,
    val username: String
)

data class Email(
    val address: String,
    val verified: Boolean
)

data class ResultElement(
    val t: String,
    val ts: TokenExpires,
    val ls: TokenExpires,
    val name: String,
    val fname: String,
    val rid: String,
    val u: U,
    val open: Boolean,
    val alert: Boolean,
    val roles: List<String>,
    val unread: Long,
    val userMentions: Long,
    val groupMentions: Long,

    @Json(name = "_updatedAt")
    val updatedAt: TokenExpires,

    @Json(name = "_id")
    val id: String
)

data class TokenExpires(
    @Json(name = "\$date")
    val date: Long
)

data class U(
    @Json(name = "_id")
    val id: String,

    val username: String,
    val name: Any? = null
)

data class TokenResult(
    val id: String,
    val token: String,
    val tokenExpires: TokenExpires,
    val type: String
)


private val rand = Random()

data class Connect(
    private val msg: String = "connect",
    private val version: String = "1",
    private val support: List<String> = listOf("1")
)

data class Pong(
    private val msg: String = "pong"
)

data class LoginRequest(
    val params: List<Param>
) : GenericRequest("method", "login")

class GetSubscriptionRequest : GenericRequest("method", "subscriptions/get")

data class Param(
    val user: User,
    val password: String
)

data class User(
    val username: String
)

open class GenericRequest(
    private val msg: String,
    val method: String,
    val id: String = rand.nextInt(10_000).toString()
)

//{"msg":"sub","name":"stream-room-messages","id":"1348","params":["gck3eFTruG3d38kLD",false]}
data class SubscribeRequest(
    private val msg: String = "sub",
    private val name: String = "stream-room-messages",
    private val id: String = rand.nextInt(10_000).toString(),
    @Expose(serialize = false)
    private val roomId: String,
    private val params: List<Any> = listOf(roomId, false)
)
