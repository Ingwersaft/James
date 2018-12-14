package com.mkring.james.integration.rocketchat

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.mkring.james.james
import junit.framework.TestCase.assertTrue
import org.apache.http.HttpEntity
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
import org.junit.Test
import java.net.URL
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

val base = "51.15.56.207:3000"
//val base = "localhost:3000"

val adminUser = "admin"
val adminPw = "12345"

class BasicRocketchatIntegration {
    @Test
    fun testConnectivity() {
        println("### testConnectivity ###")
        val readText = URL("http://$base").readText()
        assertTrue(readText.isNotEmpty())

        var done = CompletableFuture<Unit>()
        // setup james
        james {
            name = "testjames"
            rocketchat {
                ignoreInvalidCa = true
                sslVerifyHostname = false
                username = "bot"
                password = "1234"
                websocketTarget = "wss://$base/websocket"
            }
            map("complete", "") {
                done.complete(Unit)
            }
        }
        println("james started! awaiting completion of future")
        // send complete command via api
        sendCompleteToGeneral()
        done.get(1, TimeUnit.MINUTES)
        println("completed successfully!")
    }
}

private fun sendCompleteToGeneral() {
    val client = HttpClients.createDefault()
    val gson = Gson()
    // login
    val loginResponse =
        send(client, gson, "/api/v1/login", "{ \"user\": \"bot\", \"password\": \"1234\" }")
    val userId = loginResponse.get("data").asJsonObject.get("userId").asString
    val authToken = loginResponse.get("data").asJsonObject.get("authToken").asString
    println("userId=$userId authToken=$authToken")

    // get channel id
    val generalId = send(
        client,
        gson,
        "/api/v1/channels.info?roomName=general",
        null,
        authToken = authToken,
        userId = userId
    ).get("channel").asJsonObject.get("_id").asString
    println("generalId=$generalId")

    val postSuccess = send(
        client,
        gson,
        "/api/v1/chat.postMessage",
        "{ \"roomId\": \"$generalId\", \"text\": \"testjames complete\" }",
        authToken = authToken,
        userId = userId
    ).also { println("$it") }.let { it["success"].asBoolean }
    println("postSuccess=$postSuccess")
    assertTrue(postSuccess)
}

private fun createTestBotUser() {

}

private fun send(
    client: CloseableHttpClient,
    gson: Gson,
    resource: String,
    payload: String?,
    authToken: String? = null,
    userId: String? = null
): JsonObject {
    val request = if (payload == null) {
        HttpGet("http://$base$resource")
    } else {
        HttpPost("http://$base$resource").apply {
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

fun main(args: Array<String>) {
    println("debug:")
    sendCompleteToGeneral()
}
//docker run --env ADMIN_USERNAME=bot --env ADMIN_PASS=1234 --env ADMIN_EMAIL=admin@example.com --name rocketchat --link db -d -p 3000:3000 rocket.chat:0.71.1