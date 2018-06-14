package com.mkring.james.chatbackend.rocketchat

import com.google.gson.Gson
import com.mkring.james.JamesPool
import com.mkring.james.chatbackend.ChatBackend
import com.mkring.james.chatbackend.IncomingPayload
import com.mkring.james.fireAndForgetLoop
import com.neovisionaries.ws.client.WebSocket
import com.neovisionaries.ws.client.WebSocketAdapter
import com.neovisionaries.ws.client.WebSocketFactory
import com.neovisionaries.ws.client.WebSocketFrame
import kotlinx.coroutines.experimental.*
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class RocketBackend(
    val websocketTarget: String,
    val sslVerifyHostname: Boolean = true,
    val ignoreInvalidCa: Boolean = false,
    var defaultAvatar: String,
    val rocketUsername: String,
    val rocketPassword: String
) : ChatBackend() {
    val gson = Gson()
    val NOID = -1 // if method doesn't support a unique id, use this
    val random = Random()
    //
    private var loginResultMap: MutableMap<Int, CompletableFuture<String>> = mutableMapOf()

    lateinit var ws: WebSocket
    lateinit var outgoingJob: Job
    lateinit var subJob: Job
    private val subbedRoomIds = mutableSetOf<String>() // subbed room id cache

    override suspend fun start() {
        log.info("start()")
        ws = createWebsocket()
    }

    fun createWebsocket(): WebSocket = runBlocking {
        WebSocketFactory().also {
            if (ignoreInvalidCa) {
                it.sslContext = NaiveSSLContext.getInstance("TLS")
            }
            it.verifyHostname = sslVerifyHostname
        }.createSocket(websocketTarget).also {
            it.addListener(object : WebSocketAdapter() {
                // handles all incoming messages
                override fun onTextMessage(ws: WebSocket?, message: String?) {
                    log.info("onTextMessage: $message")
                    if (message == null) {
                        return
                    }
                    // handle empty updated
                    if (message == """{"msg":"updated","methods":["42"]}""") {
                        log.info("ignoring $message")
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
                            log.info("not relevant :/")
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
                                log.info("ignore messages by myself")
                                return
                            }

                            val username = self

                            // ignoring empty text
                            if (text == null) {
                                log.info("ignore empty text")
                                return
                            }

                            // ignore withouth rid
                            if (rid == null) {
                                log.info("ignore empty rid")
                                return
                            }

                            log.info("'$text' from rid=$rid")
                            log.info("found Streamupdate : $it")

                            launch(JamesPool) {
                                backendToJamesChannel.send(IncomingPayload(rid, username, text))
                            }
                        }
                    } catch (e: Exception) {
                        log.error(e.message)
                    }
                }

                override fun onDisconnected(
                    websocket: WebSocket?,
                    serverCloseFrame: WebSocketFrame?,
                    clientCloseFrame: WebSocketFrame?,
                    closedByServer: Boolean
                ) {
                    log.info("onDisconnected (byserver=$closedByServer): $serverCloseFrame $clientCloseFrame")
                    var successfullyReconnected = false
                    while (successfullyReconnected.not()) {
                        try {
                            runBlocking {
                                loginResultMap.clear()
                                subbedRoomIds.clear()
                                outgoingJob.cancelAndJoin()
                                subJob.cancelAndJoin()
                            }
                            ws.clearListeners()
                            ws.flush()
                            ws = createWebsocket()
                            successfullyReconnected = true
                        } catch (e: Exception) {
                            log.error("onDisconnected recreate connect error", e)
                            Thread.sleep(5000)
                        }
                    }
                    log.info("maybe reconnected successfully")
                }
            })
            if (it.doRocketchatAuth()) throw IllegalStateException("connect failed")

            launchSubscriptionRoutine(it)

            async {
                outgoingJob = fireAndForgetLoop("ChatBackend-outgoing-receiver") {
                    val (target, text, options) = fromJamesToBackendChannel.receive()
                    it.sendToChat(
                        target,
                        random.nextInt(10000),
                        text,
                        options.getOrDefault("avatar", defaultAvatar)
                    )
                }
            }
            it.isMissingCloseFrameAllowed = true
            it.pingInterval = 25 * 1000
        }
    }

    internal fun WebSocket.doRocketchatAuth(): Boolean {
        connect()
        CompletableFuture<String>().let {
            loginResultMap[NOID] = it
            sendJson(mapOf("msg" to "connect", "version" to "1", "support" to listOf("1")))
            it.get().also { log.info("connect answer: $it") }
        }

        Thread.sleep(100L) // needed cause future with NOID

        log.info("going to auth with plain user/pass:")
        if (!loginMethod(this)) {
            return true
        }
        return false
    }

    /**
     * launches corouting which checks for all known subs of the botaccount in use and subscribes to all if not already
     * subscribed
     */
    private fun launchSubscriptionRoutine(webSocket: WebSocket) {
        subJob = launch(JamesPool) {
            while (true) {
                log.trace("checking for new subs")
                try {
                    getBotSubscriptionsAndSubscribe(webSocket)
                } catch (e: Exception) {
                    log.error("checking subs failed", e)
                }
                log.trace("checking done")
                delay(2, TimeUnit.SECONDS)
            }
        }
    }

    /**
     * the actual subscribtion logic
     */
    internal fun getBotSubscriptionsAndSubscribe(webSocket: WebSocket) {
        var allAvailableStreams =
            gson.fromJson(callAndWait("subscriptions/get", webSocket = webSocket), AvailableStreamsAnswer::class.java)
                .also { log.debug(it.toString()) }

        log.trace("allAvailableStreams: $allAvailableStreams")
        allAvailableStreams?.let {
            it.result?.forEach { result ->
                result.rid?.let {
                    if (subbedRoomIds.contains(it)) {
                        log.trace("already subbed to rid $it")
                        return@forEach
                    }
                    ws.sendJson(
                        mapOf(
                            "msg" to "sub", "name" to "stream-room-messages", "id" to random.nextInt(10000).toString(),
                            "params" to arrayOf(it, false)
                        )
                    )
                    log.info("subbedto new room: $it")
                    subbedRoomIds.add(it)
                }
            }
        }
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
                log.error("no response after login")
                webSocket.disconnect()
                return false
            }
            if (it.contains("User not found")) {
                log.error("couldn't login, user not found - disconnected!")
                webSocket.disconnect()
                return false
            } else if (it.contains("Incorrect password")) {
                log.error("couldn't login, incorrect password - disconnected!")
                webSocket.disconnect()
                return false
            } else if (it.contains("Meteor.Error")) {
                log.error("meteor error, username password correct?")
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
            loginResultMap.put(uuid, it)
            webSocket.call(method, uuid, objects)
            it.get().also {
                log.info("got $method answer: $it")
                return it
            }
        }
        return null
    }

    fun WebSocket.call(method: String, uuid: Int, objects: Array<Any> = emptyArray()) {
        log.info("call: method=$method objects=$objects")
        val payload = mutableMapOf<String, Any>(
            "msg" to "method",
            "method" to method,
            "id" to uuid.toString()
        )
        if (objects.isNotEmpty()) {
            payload.put("params", objects)
        }
        sendJson(payload)
    }

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
        Gson().toJson(obj).let { this.send(it) }
    }

    companion object {
        private val log = LoggerFactory.getLogger(RocketBackend::class.java)
    }
}