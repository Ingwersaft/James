package com.mkring.james.chatbackend.rocketchat

import com.google.gson.Gson
import com.mkring.james.JamesPool
import com.mkring.james.chatbackend.ChatBackend
import com.mkring.james.chatbackend.IncomingPayload
import com.mkring.james.fireAndForgetLoop
import com.neovisionaries.ws.client.*
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.CompletableFuture

class RocketChatBackendV2(
    val websocketTarget: String,
    val sslVerifyHostname: Boolean = true,
    val ignoreInvalidCa: Boolean = false,
    var defaultAvatar: String,
    val rocketUsername: String,
    val rocketPassword: String
) : ChatBackend(), Logger by LoggerFactory.getLogger(RocketChatBackendV2::class.java) {
    override suspend fun start() {
        info("connecting to backend $websocketTarget")
        async {
            info("launching outgoing coroutine")
            fireAndForgetLoop("rocketchat-outgoing-coroutine") {
                val (target, text, options) = fromJamesToBackendChannel.receive()
                try {
                    webSocket.sendToChat(
                        target,
                        random.nextInt(10000),
                        text,
                        options.getOrDefault("avatar", defaultAvatar)
                    )
                } catch (e: Exception) {
                    error("webSocket.sendToChat failed", e)
                }
            }
        }
        webSocket = connectToRocketChat()
        info("connected")
    }

    val timer = kotlin.concurrent.timer("reconnect-backend-timer", true, 10000, 10000) {
        info("reconnect-backend-timer run")
        if (::webSocket.isInitialized.not()) {
            return@timer
        }
        if (webSocket.state == WebSocketState.CLOSED) {
            info("looks like the socket is closed, will refresh")
            try {
                subbedRoomIds.clear()
                loginResultMap.values.forEach { it.cancel(true) }
                loginResultMap.clear()
                webSocket = connectToRocketChat()
            } catch (e: Exception) {
                error("reconnect-backend-timer failed", e)
            }
        }
    }
    val timer2 = kotlin.concurrent.timer("check-subs", true, 10000, 2000) {
        info("checking for new subs")
        if (::webSocket.isInitialized.not()) {
            return@timer
        }
        if (webSocket.state == WebSocketState.OPEN) {
            try {
                while (::webSocket.isInitialized.not()) {
                    trace("webSocket not initialized")
                    Thread.sleep(100)
                }
                if (webSocket.state != WebSocketState.OPEN) {
                    throw IllegalStateException("webSocket.state != WebSocketState.OPEN")
                }
                getBotSubscriptionsAndSubscribe()
            } catch (e: Exception) {
                error("checking subs failed", e)
            }
        } else {
            info("won't update cause socket not open")
        }
        trace("checking done")
    }

    lateinit var webSocket: WebSocket
    private val subbedRoomIds = mutableSetOf<String>() // subbed room id cache
    private var loginResultMap: MutableMap<Int, CompletableFuture<String>> = mutableMapOf()

    private fun connectToRocketChat(): WebSocket = WebSocketFactory().also {
        if (ignoreInvalidCa) {
            it.sslContext = NaiveSSLContext.getInstance("TLS")
        }
        it.verifyHostname = sslVerifyHostname
    }.createSocket(websocketTarget).apply {
        addListener(object : WebSocketAdapter() {

            override fun onTextMessage(ws: WebSocket?, message: String?) {
                info("onTextMessage: $message")
                if (message == null) {
                    return
                }
                // handle empty updated
                if (message == """{"msg":"updated","methods":["42"]}""") {
                    info("ignoring $message")
                    return
                }
                // handle pong
                if (message == "{\"msg\":\"ping\"}") {
                    ws?.sendJson(mapOf("msg" to "pong"))
                    return
                }

                // handle toplevel future:
                val referenceId: Int? = Regex(""""id"\n?:\n?"[0-9]+"""").find(message)?.groupValues?.map {
                    it.split(Regex(":"))[1].replace(Regex("\""), "")
                        .replace(Regex("\n"), "").toInt()
                }?.first()
                referenceId?.let {
                    loginResultMap[it]?.complete(message)
                    loginResultMap.remove(it)
                    return
                }

                // handle NOID future:
                loginResultMap[NOID]?.let {
                    it.complete(message)
                    loginResultMap.remove(NOID)
                    return
                }

                // handle mappings
                try {
                    if (message.contains(Regex(""""msg":"changed"""")).not()) {
                        info("not relevant :/")
                        return
                    }
                    val streamMessageUpdate = gson.fromJson(message, StreamMessageUpdate::class.java)
                    streamMessageUpdate.let {
                        // parse
                        val self = it.fields?.args?.first()?.u?.username
                        val rid = it.fields?.args?.first()?.rid
                        val text = it.fields?.args?.first()?.msg

                        // ignore messages by myself
                        if (self == rocketUsername) {
                            info("ignore messages by myself")
                            return
                        }

                        val username = self

                        // ignoring empty text
                        if (text == null) {
                            info("ignore empty text")
                            return
                        }

                        // ignore withouth rid
                        if (rid == null) {
                            info("ignore empty rid")
                            return
                        }

                        info("'$text' from rid=$rid")
                        info("found Streamupdate : $it")

                        launch(JamesPool) {
                            backendToJamesChannel.send(IncomingPayload(rid, username, text))
                        }
                    }
                } catch (e: Exception) {
                    error(e.message)
                }
            }

            override fun onDisconnected(
                websocket: WebSocket?,
                serverCloseFrame: WebSocketFrame?,
                clientCloseFrame: WebSocketFrame?,
                closedByServer: Boolean
            ) {
                info("onDisconnected()")
                super.onDisconnected(websocket, serverCloseFrame, clientCloseFrame, closedByServer)
                Thread.sleep(5000)
                this@RocketChatBackendV2.webSocket = connectToRocketChat()
            }

            override fun onError(websocket: WebSocket?, cause: WebSocketException?) {
                info("onError() ${cause?.message}")
                super.onError(websocket, cause)
            }

            override fun onConnectError(websocket: WebSocket?, exception: WebSocketException?) {
                info("onConnectError() ${exception?.message}")
                super.onConnectError(websocket, exception)
            }

            override fun onUnexpectedError(websocket: WebSocket?, cause: WebSocketException?) {
                info("onUnexpectedError() ${cause?.message}")
                super.onUnexpectedError(websocket, cause)
            }

            override fun onStateChanged(websocket: WebSocket?, newState: WebSocketState?) {
                info("onStateChanged() $newState")
                super.onStateChanged(websocket, newState)
            }
        })
        this.connect()
        info("WebSocket.connect() done: $state")
        while (state == WebSocketState.CLOSED) {
            info("not connected yet")
            Thread.sleep(100)
        }
        if (doRocketchatAuth())
            throw IllegalStateException("connect failed")
    }

    internal fun WebSocket.doRocketchatAuth(): Boolean {
        CompletableFuture<String>().let {
            loginResultMap[NOID] = it
            sendJson(mapOf("msg" to "connect", "version" to "1", "support" to listOf("1")))
            it.get().also { info("connect answer: $it") }
        }

        Thread.sleep(100L) // needed cause future with NOID

        info("going to auth with plain user/pass:")
        if (!loginMethod(this)) {
            return true
        }
        return false
    }

    internal fun loginMethod(webSocket: WebSocket): Boolean {
        callAndWait(
            webSocket = webSocket,
            method = "login", objects = arrayOf(
                mapOf(
                    "user" to mapOf("username" to rocketUsername),
                    "password" to rocketPassword
                )
            )
        ).also {
            if (it == null) {
                error("no response after login")
                webSocket.disconnect()
                return false
            }
            if (it.contains("User not found")) {
                error("couldn't login, user not found - disconnected!")
                webSocket.disconnect()
                return false
            } else if (it.contains("Incorrect password")) {
                error("couldn't login, incorrect password - disconnected!")
                webSocket.disconnect()
                return false
            } else if (it.contains("Meteor.Error")) {
                error("meteor error, username password correct?")
                webSocket.disconnect()
                return false
            }
        }
        return true
    }

    internal fun callAndWait(
        method: String,
        objects: Array<Any> = emptyArray(),
        webSocket: WebSocket
    ): String? {
        CompletableFuture<String>().let {
            val uuid = random.nextInt(10000)
            loginResultMap[uuid] = it
            webSocket.call(method, uuid, objects)
            it.get().also {
                info("got $method answer: $it")
                return it
            }
        }
        return null
    }

    fun WebSocket.call(method: String, uuid: Int, objects: Array<Any> = emptyArray()) {
        info("call: method=$method objects=$objects")
        val payload = mutableMapOf<String, Any>(
            "msg" to "method",
            "method" to method,
            "id" to uuid.toString()
        )
        if (objects.isNotEmpty()) {
            payload["params"] = objects
        }
        sendJson(payload)
    }

    internal fun getBotSubscriptionsAndSubscribe() {
        var allAvailableStreams =
            gson.fromJson(callAndWait("subscriptions/get", webSocket = webSocket), AvailableStreamsAnswer::class.java)
                .also { debug(it.toString()) }

        info("allAvailableStreams: $allAvailableStreams")
        allAvailableStreams?.let {
            it.result?.forEach { result ->
                result.rid?.let {
                    if (subbedRoomIds.contains(it)) {
                        trace("already subbed to rid $it")
                        return@forEach
                    }
                    webSocket.sendJson(
                        mapOf(
                            "msg" to "sub", "name" to "stream-room-messages", "id" to random.nextInt(10000).toString(),
                            "params" to arrayOf(it, false)
                        )
                    )
                    info("subbedto new room: $it")
                    subbedRoomIds.add(it)
                }
            }
        }
    }


}

private val log = LoggerFactory.getLogger("WebSocket-Extensions")
private val gson = Gson()
private val NOID = -1 // if method doesn't support a unique id, use this
private val random = Random()

fun WebSocket.sendToChat(rid: String, uuid: Int, text: String, avatarEmoji: String) {
    sendJson(
        mapOf(
            "msg" to "method",
            "method" to "sendMessage",
            "id" to "$uuid",
            "params" to arrayOf(
                mapOf(
                    "rid" to rid,
                    "msg" to text,
                    "emoji" to avatarEmoji
                )
            )
        )
    )
}

fun WebSocket.send(text: String) {
    log.info("send: text=$text")
    this.sendText(text)
}

fun WebSocket.sendJson(obj: Any) {
    log.info("sendJson: obj=$obj")
    gson.toJson(obj).let { this.send(it) }
}