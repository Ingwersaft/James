package com.mkring.james.chatbackend.rocketchat

import com.beust.klaxon.Json
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import java.util.*

// rocketchat -> james

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
    val emails: List<Email>? = null,
    val username: String? = null,
    val eventName: String? = null,
    val args: List<Arg>? = null
)

data class Arg(
    val _id: String,

    val rid: String,
    val msg: String,
    val ts: TokenExpires,
    val u: U,
    val mentions: List<Any?>,
    val channels: List<Any?>,

    val _updatedAt: TokenExpires
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

// james -> rocketchat


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

data class SubscribeRequest(
    private val msg: String = "sub",
    private val name: String = "stream-room-messages",
    private val id: String = rand.nextInt(10_000).toString(),
    @Expose(serialize = false)
    val roomId: String,
    private val params: List<Any> = listOf(roomId, false)
)

data class SendMessageRequest(
    @Expose(serialize = false)
    private val rid: String,
    @Expose(serialize = false)
    private val emoji: String,
    @Expose(serialize = false)
    private val messageText: String,
    private val params: List<Map<String, String>> = listOf(
        mapOf(
            "rid" to rid,
            "msg" to messageText,
            "emoji" to emoji
        )
    )
) : GenericRequest("method", "sendMessage")