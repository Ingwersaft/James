package com.mkring.james.chatbackend

import com.mkring.james.mapping.Ask
import com.mkring.james.mapping.Mapping
import com.mkring.james.mapping.MappingPattern
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.experimental.runBlocking
import org.junit.Test
import java.util.concurrent.TimeUnit

class ChatBackendTest {
    @Test
    fun testLaunchFIrstMatchingMapping() {

        val mappings: MutableMap<String, Mapping.() -> Unit> = mutableMapOf()
        mappings.put("das not") {
            send("nvmd")
        }
        mappings.put("das not aswell") {
            send("nvmd")
        }
        var triggered = false
        mappings.put("some") {
            triggered = true

            assertEquals(listOf("some", "text"), arguments)
            send("answer")
        }
        val fakeChat = FakeChat()
        launchFIrstMatchingMapping("some text", "target", "nvmd", fakeChat, mappings).also {
            runBlocking { it?.join() }
        }
        assertEquals("answer", fakeChat.lastSend)
        assertTrue(triggered)
    }
}

class FakeChat : ChatBackend {
    override fun addMapping(prefix: String, matcher: MappingPattern, mapping: Mapping.() -> Unit) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun login(options: Map<String, String>) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    lateinit var lastSend: String
    override fun send(target: UniqueChatTarget, text: String, options: Map<String, String>) {
        lastSend = text
    }

    override fun ask(timeout: Int, timeunit: TimeUnit, target: UniqueChatTarget, text: String, options: Map<String, String>): Ask<String> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun shutdown() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}
