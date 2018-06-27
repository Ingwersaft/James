package com.mkring.james.chatbackend.rocketchat

// To parse the JSON, install Klaxon and do:
//
//   val streamRoomResponse = StreamRoomResponse.fromJson(jsonString)
//   val streamRoom = StreamRoom.fromJson(jsonString)
//   val getSubsResponse = GetSubsResponse.fromJson(jsonString)
//   val getSubs = GetSubs.fromJson(jsonString)
//   val updated = Updated.fromJson(jsonString)
//   val loginResult = LoginResult.fromJson(jsonString)
//   val userAddedToCollection = UserAddedToCollection.fromJson(jsonString)
//   val loginResponse = LoginResponse.fromJson(jsonString)
//   val login = Login.fromJson(jsonString)
//   val connectResponse = ConnectResponse.fromJson(jsonString)
//   val connect = Connect.fromJson(jsonString)

import com.beust.klaxon.Converter
import com.beust.klaxon.Json
import com.beust.klaxon.JsonValue
import com.beust.klaxon.Klaxon

private fun <T> Klaxon.convert(
    k: kotlin.reflect.KClass<*>,
    fromJson: (JsonValue) -> T,
    toJson: (T) -> String,
    isUnion: Boolean = false
) =
    this.converter(object : Converter {
        @Suppress("UNCHECKED_CAST")
        override fun toJson(value: Any) = toJson(value as T)

        override fun fromJson(jv: JsonValue) = fromJson(jv) as Any
        override fun canConvert(cls: Class<*>) = cls == k.java || (isUnion && cls.superclass == k.java)
    })

private val klaxon = Klaxon()
    .convert(ParamUnion::class, { ParamUnion.fromJson(it) }, { it.toJson() }, true)

data class StreamRoomResponse(
    val msg: String,
    val subs: List<String>
) {
    public fun toJson() = klaxon.toJsonString(this)

    companion object {
        public fun fromJson(json: String) = klaxon.parse<StreamRoomResponse>(json)
    }
}

data class StreamRoom(
    val msg: String,
    val name: String,
    val id: String,
    val params: List<ParamUnion>
) {
    public fun toJson() = klaxon.toJsonString(this)

    companion object {
        public fun fromJson(json: String) = klaxon.parse<StreamRoom>(json)
    }
}

sealed class ParamUnion {
    class BoolValue(val value: Boolean) : ParamUnion()
    class StringValue(val value: String) : ParamUnion()

    public fun toJson(): String = klaxon.toJsonString(
        when (this) {
            is BoolValue -> this.value
            is StringValue -> this.value
        }
    )

    companion object {
        public fun fromJson(jv: JsonValue): ParamUnion = when (jv.inside) {
            is Boolean -> BoolValue(jv.boolean!!)
            is String -> StringValue(jv.string!!)
            else -> throw IllegalArgumentException()
        }
    }
}

data class GetSubsResponse(
    val msg: String,
    val id: String,
    val result: List<ResultElement>
) {
    public fun toJson() = klaxon.toJsonString(this)

    companion object {
        public fun fromJson(json: String) = klaxon.parse<GetSubsResponse>(json)
    }
}

data class ResultElement(
    val t: String,
    val ts: TokenExpires,
    val ls: TokenExpires,
    val name: String,
    val fname: String? = null,
    val rid: String,
    val u: U,
    val open: Boolean,
    val alert: Boolean,
    val unread: Long,
    val userMentions: Long,
    val groupMentions: Long,

    @Json(name = "_updatedAt")
    val updatedAt: TokenExpires,

    @Json(name = "_id")
    val id: String,

    val roles: List<String>? = null
)

data class TokenExpires(
    @Json(name = "\$date")
    val date: Long
)

data class Login(
    val msg: String,
    val method: String,
    val id: String,
    val params: List<ParamClass>? = null
) {
    public fun toJson() = klaxon.toJsonString(this)

    companion object {
        public fun fromJson(json: String) = klaxon.parse<Login>(json)
    }
}

data class ParamClass(
    val user: User,
    val password: String
)

data class User(
    val username: String
)

data class Updated(
    val msg: String,
    val methods: List<String>
) {
    public fun toJson() = klaxon.toJsonString(this)

    companion object {
        public fun fromJson(json: String) = klaxon.parse<Updated>(json)
    }
}

data class LoginResult(
    val msg: String,
    val id: String,
    val result: LoginResultResult
) {
    public fun toJson() = klaxon.toJsonString(this)

    companion object {
        public fun fromJson(json: String) = klaxon.parse<LoginResult>(json)
    }
}

data class LoginResultResult(
    val id: String,
    val token: String,
    val tokenExpires: TokenExpires,
    val type: String
)

data class UserAddedToCollection(
    val msg: String,
    val collection: String,
    val id: String,
    val fields: FieldsGson
) {
    public fun toJson() = klaxon.toJsonString(this)

    companion object {
        public fun fromJson(json: String) = klaxon.parse<UserAddedToCollection>(json)
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

data class LoginResponse(
    val msg: String,
    val session: String
) {
    public fun toJson() = klaxon.toJsonString(this)

    companion object {
        public fun fromJson(json: String) = klaxon.parse<LoginResponse>(json)
    }
}

data class ConnectResponse(
    @Json(name = "server_id")
    val serverID: String
) {
    public fun toJson() = klaxon.toJsonString(this)

    companion object {
        public fun fromJson(json: String) = klaxon.parse<ConnectResponse>(json)
    }
}

data class Connect(
    val msg: String,
    val version: String,
    val support: List<String>
) {
    public fun toJson() = klaxon.toJsonString(this)

    companion object {
        public fun fromJson(json: String) = klaxon.parse<Connect>(json)
    }
}
