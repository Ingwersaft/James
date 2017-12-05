package com.mkring.james

import com.mkring.james.chatbackend.ChatBackend
import com.mkring.james.chatbackend.UniqueChatTarget
import com.mkring.james.mapping.Ask
import com.mkring.james.mapping.Mapping
import com.mkring.james.mapping.MappingPattern
import java.util.concurrent.TimeUnit


class TestBackend : ChatBackend {
    override fun addMapping(prefix: String, matcher: MappingPattern, mapping: Mapping.() -> Unit) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun login(options: Map<String, String>) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    val sendTargets: MutableList<UniqueChatTarget> = mutableListOf()
    val sendTexts: MutableList<String> = mutableListOf()
    val sendOptions: MutableList<Map<String, String>> = mutableListOf()
    override fun send(target: UniqueChatTarget, text: String, options: Map<String, String>) {
        sendTargets += target
        sendTexts += text
        sendOptions += options
    }

    val askTimeouts = mutableListOf<Int>()
    val askTimeunits = mutableListOf<TimeUnit>()
    val askTargets = mutableListOf<UniqueChatTarget>()
    val askTexts = mutableListOf<String>()
    val askOptions = mutableListOf<Map<String, String>>()

    var askNextAnswer: Ask<String> = Ask.Timeout
    override fun ask(timeout: Int, timeunit: TimeUnit, target: UniqueChatTarget, text: String, options: Map<String, String>): Ask<String> {
        askTimeouts += timeout
        askTimeunits += timeunit
        askTargets += target
        askTexts += text
        askOptions += options
        return askNextAnswer
    }

    override fun shutdown() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun toString(): String {
        return "TestBackend(sendTargets=$sendTargets, sendTexts=$sendTexts, sendOptions=$sendOptions)"
    }


}
