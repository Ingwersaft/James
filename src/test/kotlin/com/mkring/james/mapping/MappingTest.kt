package com.mkring.james.mapping

import com.mkring.james.TestBackend
import junit.framework.TestCase.*
import org.junit.Test
import org.slf4j.LoggerFactory

class MappingTest {
    val logg = LoggerFactory.getLogger(this::class.java)

    val testChat: TestBackend = TestBackend()
    val senderId = "unique!dude"
    val username = "test.user"
    var defaultMatchedChatLine = "some text"

    @Test
    fun testSend() = prepareMapping("test hallo du da") {
        val toBeSend = "hello"
        val options = mapOf("A" to "1")
        send(toBeSend)
        send("")
        send("nvmd", options = options)

        logg.info("chat=$testChat")

        assertNotNull(testChat.sendOptions.first())
        assertNotNull(testChat.sendTargets.first())
        assertNotNull(testChat.sendTexts.first())

        assertEquals(toBeSend, testChat.sendTexts.first())
        assertEquals(senderId, testChat.sendTargets.first())
        assertTrue(testChat.sendOptions.first().isEmpty())

        assertEquals(options, testChat.sendOptions[1])
    }

    @Test
    fun testAsk() = prepareMapping {
        val askText = "please send me something"
        var answer: Ask<String> = askWithRetry(1, askText) {
            it.isNotBlank()
        }
        assertEquals(Ask.Timeout, answer)
        assertEquals(testChat.askTexts, listOf(askText, askText))

        val expectedAnswer = "an actual answer"
        testChat.askNextAnswer = Ask.of { expectedAnswer }
        answer = ask(askText)
        assertEquals(expectedAnswer, answer.get())
    }

    @Test
    fun testArgumentsSplit() {
        prepareMapping("/test hallo du da") {
            assertEquals(listOf("/test", "hallo", "du", "da"), arguments)
        }
        prepareMapping("") {
            assertEquals(emptyList<String>(), arguments)
        }
    }

    private fun prepareMapping(text: String = defaultMatchedChatLine, function: Mapping.() -> Unit) {
        Mapping(text = text, senderId = senderId, username = username, chat = testChat).apply {
            function()
        }
    }
}
