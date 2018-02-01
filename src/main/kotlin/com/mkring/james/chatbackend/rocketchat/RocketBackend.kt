package com.mkring.james.chatbackend.rocketchat

import com.google.gson.Gson
import com.mkring.james.chatbackend.ChatBackend
import com.mkring.james.chatbackend.IncomingPayload
import com.mkring.james.fireAndForgetLoop
import com.neovisionaries.ws.client.WebSocket
import com.neovisionaries.ws.client.WebSocketAdapter
import com.neovisionaries.ws.client.WebSocketFactory
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
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

    override suspend fun start() {
        log.info("start()")

        setupWs()

        ws.apply {
            connect()
            CompletableFuture<String>().let {
                loginResultMap[NOID] = it
                sendJson(mapOf("msg" to "connect", "version" to "1", "support" to listOf("1")))
                it.get().also { log.info("connect answer: $it") }
            }

            Thread.sleep(100L) // needed cause future with NOID

            log.info("going to auth with plain user/pass:")
            if (!loginMethod()) {
                return
            }

            launchSubscriptionRoutine()
        }

        fireAndForgetLoop("ChatBackend-outgoing-receiver") {
            val (target, text, options) = fromJamesToBackendChannel.receive()
            ws.sendToChat(target, random.nextInt(10000), text, options.getOrDefault("avatar", defaultAvatar))
        }
    }

    var loginResultMap: MutableMap<Int, CompletableFuture<String>> = mutableMapOf()

    lateinit var ws: WebSocket

    fun setupWs() {
        ws = WebSocketFactory().also {
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

                            launch {
                                backendToJamesChannel.send(IncomingPayload(rid, username, text))
                            }
                        }
                    } catch (e: Exception) {
                        log.error(e.message)
                    }
                }
            })
            it.isMissingCloseFrameAllowed = true
            it.pingInterval = 25 * 1000
        }
    }

    /**
     * launches corouting which checks for all known subs of the botaccount in use and subscribes to all if not already
     * subscribed
     */
    private fun launchSubscriptionRoutine() {
        launch {
            while (true) {
                log.debug("checking for new subs")
                try {
                    getBotSubscriptionsAndSubscribe()
                } catch (e: Exception) {
                    log.error("checking subs failed", e)
                }
                log.debug("checking done")
                delay(2, TimeUnit.SECONDS)
            }
        }
    }

    private val subbedRoomIds = mutableSetOf<String>() // subbed room id cache
    /**
     * the actual subscribtion logic
     */
    private fun getBotSubscriptionsAndSubscribe() {
        var allAvailableStreams = gson.fromJson(callAndWait("subscriptions/get"), AvailableStreamsAnswer::class.java)
            .also { log.debug(it.toString()) }

        log.debug("allAvailableStreams: $allAvailableStreams")
        allAvailableStreams?.let {
            it.result?.forEach { result ->
                result.rid?.let {
                    if (subbedRoomIds.contains(it)) {
                        log.debug("already subbed to rid $it")
                        return@forEach
                    }
                    ws.sendJson(
                        mapOf(
                            "msg" to "sub", "name" to "stream-room-messages", "id" to random.nextInt(10000).toString(),
                            "params" to arrayOf(it, false)
                        )
                    )
                    subbedRoomIds.add(it)
                }
            }
        }
    }

    private fun loginMethod(): Boolean {
        callAndWait(
            method = "login", objects = arrayOf(
                mapOf(
                    "user" to mapOf("username" to rocketUsername),
                    "password" to rocketPassword
                )
            )
        ).also {
            if (it == null) {
                log.error("no response after login")
                ws.disconnect()
                return false
            }
            if (it.contains("User not found")) {
                log.error("couldn't login, user not found - disconnected!")
                ws.disconnect()
                return false
            } else if (it.contains("Incorrect password")) {
                log.error("couldn't login, incorrect password - disconnected!")
                ws.disconnect()
                return false
            } else if (it.contains("Meteor.Error")) {
                log.error("meteor error, username password correct?")
                ws.disconnect()
                return false
            }
        }
        return true
    }

    private fun callAndWait(method: String, objects: Array<Any> = emptyArray()): String? {
        CompletableFuture<String>().let {
            val uuid = random.nextInt(10000)
            loginResultMap.put(uuid, it)
            ws.call(method, uuid, objects)
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