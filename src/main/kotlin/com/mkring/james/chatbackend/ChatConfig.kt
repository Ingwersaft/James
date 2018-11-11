package com.mkring.james.chatbackend

import com.mkring.james.LimitClosureScope

/**
 * Supported chat configs
 */
@LimitClosureScope
sealed class ChatConfig

/**
 * Rocketchat config
 */
class RocketChat : ChatConfig() {
    lateinit var websocketTarget: String
    var sslVerifyHostname: Boolean = true
    var ignoreInvalidCa: Boolean = false
    lateinit var username: String
    lateinit var password: String
    var defaultAvatar = ":tophat:"

}

/**
 * Telegram config
 */
class Telegram : ChatConfig() {
    lateinit var token: String
    lateinit var username: String
}

/**
 * Slack config
 */
class Slack: ChatConfig(){
    lateinit var botOauthToken: String
}