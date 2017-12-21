package com.mkring.james.chatbackend

import com.mkring.james.LimitClosureScope

@LimitClosureScope
sealed class ChatConfig

class RocketChat : ChatConfig() {
    lateinit var websocketTarget: String
    var sslVerifyHostname: Boolean = true
    var ignoreInvalidCa: Boolean = false
    lateinit var username: String
    lateinit var password: String
    var defaultAvatar = ":tophat:"

}

class Telegram : ChatConfig() {
    lateinit var token: String
    lateinit var username: String
}