package com.mkring.james.mapping

import com.mkring.james.James
import com.mkring.james.chatbackend.ChatBackend
import com.mkring.james.chatbackend.IncomingPayload
import com.mkring.james.chatbackend.OutgoingPayload
import com.mkring.james.chatbackend.UniqueChatTarget
import com.mkring.james.fireAndForgetLoop
import com.mkring.james.james
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.experimental.CompletableDeferred
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
    fun testGeneratedHelpWithJamesUsername() = runBlocking {
        createHelloWorldTestJames {
            name = "test-me"
        }
        testBackend.send("help") // ignored cause name is test-me
        testBackend.send("test-me help")
        waitForAnswers(testBackend, 1)
        testBackend.outgoing.first().let {
            assertEquals(
                """
                    |test-me at yor service:
                    |
                    |---
                    |test-me hello - returns world
                    |""".trimMargin().trim(), it.text
            )
        }
    }

    @Test
    fun testMultipleSend() = runBlocking {
        createHelloWorldTestJames {
            map("multiple", "nvmd") {
                send("a")
                send("b")
            }
        }
        testBackend.send("multiple")
        waitForAnswers(testBackend, 2)
        testBackend.outgoing.joinToString(";") { it.text }.let {
            assertEquals("a;b", it)
        }
    }

    @Test
    fun testAsk() = runBlocking {
        val gotAnswer = CompletableDeferred<String>()
        createHelloWorldTestJames {
            map("ask", "nvmd") {
                ask("something?").get().let {
                    gotAnswer.complete(it)
                }
                send("alright")
            }
        }
        testBackend.send("ask")
        waitForAnswers(testBackend, 1)
        testBackend.send("answer")
        waitForAnswers(testBackend, 2)
        assertEquals("answer",gotAnswer.await())
        testBackend.outgoing.joinToString(";") { it.text }.let {
            assertEquals("something?;alright", it)
        }

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

    private fun createHelloWorldTestJames(addition: James.() -> Unit = {}) {
        james {
            addition()
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

suspend fun waitForAnswers(testBackend: TestBackend, answerAmount: Int, times: Int = 20, duration: Long = 10) {
    var waits = 0
    while (testBackend.outgoing.size != answerAmount) {
        delay(duration)
        waits++
        if (waits == times) throw IllegalStateException("waited too damn long")
    }
}
