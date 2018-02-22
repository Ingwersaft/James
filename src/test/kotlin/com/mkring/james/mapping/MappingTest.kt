package com.mkring.james.mapping

import com.mkring.james.chatbackend.ChatBackend
import com.mkring.james.chatbackend.IncomingPayload
import com.mkring.james.chatbackend.OutgoingPayload
import com.mkring.james.chatbackend.UniqueChatTarget
import com.mkring.james.fireAndForgetLoop
import com.mkring.james.james
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.runBlocking
import org.junit.Test
import org.slf4j.LoggerFactory

class MappingTest {
    val log = LoggerFactory.getLogger(MappingTest::class.java)
    val testBackend = TestBackend()
    @Test
    fun testHelloWorld(): Unit = runBlocking {
        createHelloWorldTestJames()

        testBackend.send("hello", "<test>", "<testUser>")
        waitForAnswers(testBackend, 1)
        val answer = testBackend.outgoing.first()
        log.info("received $answer")
        assertEquals("<test>", answer.target)
        assertEquals("world, dear <testUser>", answer.text)
        assertTrue(answer.options.isEmpty())
    }

    @Test
    fun testGeneratedHelp(): Unit = runBlocking {
        createHelloWorldTestJames()
        testBackend.send("help")
        waitForAnswers(testBackend, 1)
        testBackend.outgoing.first().let {
            assertEquals(
                """
                    |James at yor service:
                    |
                    |---
                    |hello - returns world
                    |""".trimMargin().trim(), it.text
            )
        }
    }

    @Test
    fun testGeneratedHelpWithJamesUsername() {
        TODO("implement me")
    }

    @Test
    fun testMultipleSend() {
        TODO("implement me")
    }

    @Test
    fun testAsk() {
        TODO("implement me")
    }

    @Test
    fun testAskWithRetry() {
        TODO("implement me")
    }

    @Test
    fun testAskWithTimeout() {
        TODO("implement me")
    }

    @Test
    fun testArgumentsParsing() {
        TODO("implement me")
    }

    private fun createHelloWorldTestJames() {
        james {
            addCustomChatBackend(testBackend)
            map("hello", "returns world") {
                send("world, dear $username")
            }
        }
    }

}

class TestBackend : ChatBackend() {

    var outgoing: MutableList<OutgoingPayload> = mutableListOf()
    suspend fun send(text: String, id: UniqueChatTarget = "don't care", user: String = "someone") {
        backendToJamesChannel.send(IncomingPayload(id, user, text))
    }

    override suspend fun start() {
        fireAndForgetLoop("james-to-backend") {
            fromJamesToBackendChannel.receive().let {
                outgoing.add(it)
                println("received on james-to-backend: $it")
            }
            delay(10)
        }
    }

}

suspend fun waitForAnswers(testBackend: TestBackend, answerAmount: Int, times: Int = 10, duration: Long = 10) {
    var waits = 0
    while (testBackend.outgoing.size != answerAmount) {
        delay(duration)
        waits++
        if (waits == times) throw IllegalStateException("waited too damn long")
    }
}
