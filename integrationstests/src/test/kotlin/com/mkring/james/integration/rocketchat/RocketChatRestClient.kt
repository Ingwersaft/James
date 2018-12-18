package com.mkring.james.integration.rocketchat

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.apache.http.HttpEntity
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils

class RocketChatRestClient(val baseUrl: String, val adminUser: String, val adminPw: String) {
    private val client = HttpClients.createDefault()
    private val gson = Gson()

    lateinit var userId: String
    lateinit var authToken: String
    lateinit var generalChannelId: String

    fun login() {
        val loginResponse =
            send("/api/v1/login", "{ \"user\": \"$adminUser\", \"password\": \"$adminPw\" }")
        userId = loginResponse.get("data").asJsonObject.get("userId").asString
        authToken = loginResponse.get("data").asJsonObject.get("authToken").asString
        println("userId=$userId authToken=$authToken")
    }

    fun getGeneralChannelId() {
        val generalId = send(
            "/api/v1/channels.info?roomName=general",
            null,
            authToken = authToken,
            userId = userId
        ).get("channel").asJsonObject.get("_id").asString
        println("generalId=$generalId")
        generalChannelId = generalId
    }

    fun postMessageToChannel(text: String): Boolean {
        val postSuccess = send(
            "/api/v1/chat.postMessage",
            "{ \"roomId\": \"$generalChannelId\", \"text\": \"$text\" }",
            authToken = authToken,
            userId = userId
        ).also { println("$it") }.let { it["success"].asBoolean }
        println("postSuccess=$postSuccess")
        return postSuccess
    }

    fun createBotUser(botUser: String, botPassword: String) {
        val user = botUser
        val pw = botPassword
        val createSuccess = send(
            "/api/v1/users.create",
            """{"name": "$user", "email": "$user@example.org", "password": "$pw", "username": "$user",
                |"active": true, "joinDefaultChannels": true, "requirePasswordChange": false, "sendWelcomeEmail": false }""".trimMargin(),
            authToken = authToken,
            userId = userId
        ).also { println("users.create response: $it") }.let { it["success"].asBoolean }
        if (createSuccess.not()) {
            throw IllegalStateException("couldn't create bot user for test!")
        }
    }

    private fun send(
        resource: String,
        payload: String?,
        authToken: String? = null,
        userId: String? = null
    ): JsonObject {
        val request = if (payload == null) {
            HttpGet("http://$baseUrl$resource")
        } else {
            HttpPost("http://$baseUrl$resource").apply {
                entity = StringEntity(payload)
            }
        }
        return request.apply {
            addHeader("Content-type", "application/json")
            authToken?.also { addHeader("X-Auth-Token", it) }
            userId?.also { addHeader("X-User-Id", it) }
        }.let { client.execute(it).entity.asString() }.parse(gson)
    }

    private fun HttpEntity.asString(): String = EntityUtils.toString(this)
    private fun String.parse(gson: Gson): JsonObject = gson.fromJson(this, JsonObject::class.java)
}
