package com.mkring.james.mapping

import com.mkring.james.James
import com.mkring.james.JamesPool
import com.mkring.james.chatbackend.ChatBackend
import com.mkring.james.chatbackend.IncomingPayload
import com.mkring.james.chatbackend.OutgoingPayload
import com.mkring.james.chatbackend.UniqueChatTarget
import com.mkring.james.james
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.consumeEach
import org.junit.Test
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

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
        assertEquals("answer", gotAnswer.await())
        testBackend.outgoing.joinToString(";") { it.text }.let {
            assertEquals("something?;alright", it)
        }

    }

    @Test
    fun testAskWithRetry() = runBlocking {
        val gotAnswer = CompletableDeferred<String>()
        createHelloWorldTestJames {
            map("ask", "nvmd") {
                askWithRetry(2, "something?") { it == "second-try" }.get().let {
                    gotAnswer.complete(it)
                }
                send("alright")
            }
        }
        testBackend.send("ask")
        waitForAnswers(testBackend, 1)
        testBackend.send("answer")
        waitForAnswers(testBackend, 2)
        testBackend.send("second-try")
        waitForAnswers(testBackend, 3)
        assertEquals("second-try", gotAnswer.await())
        waitForAnswers(testBackend, 4)
        testBackend.outgoing.joinToString(";") { it.text }.let {
            assertEquals("something?;incompatible answer!;something?;alright", it)
        }

    }

    @Test
    fun testAskWithTimeout() = runBlocking {
        createHelloWorldTestJames {
            map("ask", "") {
                askTimeout = 1
                timeUnit = TimeUnit.MILLISECONDS
                val ask = ask("i don't expect anything:?")
                send((ask == Ask.Timeout).toString())
                send("done")
            }
        }
        testBackend.send("ask")
        waitForAnswers(testBackend, 3)
        assertEquals("i don't expect anything:?;true;done", testBackend.outgoing.joinToString(";") { it.text })
    }

    @Test
    fun testAskWithRetryAndTimeout() = runBlocking {
        createHelloWorldTestJames {
            map("ask", "") {
                askTimeout = 1
                timeUnit = TimeUnit.MILLISECONDS
                timeoutText = "i'm waiting"
                val ask = askWithRetry(1, "i don't expect anything:?") { true }
                send((ask == Ask.Timeout).toString())
                send("done")
            }
        }
        testBackend.send("ask")
        waitForAnswers(testBackend, 7)
        assertEquals("i don't expect anything:?;i'm waiting;i don't expect anything:?;i'm waiting;well, nevermind then;true;done",
            testBackend.outgoing.joinToString(";") { it.text })

    }

    @Test
    fun testArgumentsParsingWithoutName() = runBlocking {
        createHelloWorldTestJames {
            map("witharguments", "") {
                send(arguments.joinToString(";"))
            }
        }
        testBackend.send("witharguments arg1 arg2 arg3")
        waitForAnswers(testBackend, 1)
        assertEquals("witharguments;arg1;arg2;arg3", testBackend.outgoing.first().text)
    }

    @Test
    fun testArgumentsParsingWithName() = runBlocking {
        createHelloWorldTestJames {
            name = "someone"
            map("witharguments", "") {
                send(arguments.joinToString(";"))
            }
        }
        testBackend.send("someone witharguments arg1 arg2 arg3")
        waitForAnswers(testBackend, 1)
        assertEquals("witharguments;arg1;arg2;arg3", testBackend.outgoing.first().text)
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
    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = job + JamesPool

    lateinit var receivedJob: Job

    var outgoing: MutableList<OutgoingPayload> = mutableListOf()

    suspend fun send(text: String, id: UniqueChatTarget = "don't care", user: String = "someone") {
        backendToJamesChannel.send(IncomingPayload(id, user, text))
    }

    override fun start() {
        println("TestBackend: launching")
        receivedJob = launch {
            fromJamesToBackendChannel.consumeEach {
                outgoing.add(it)
                println("received on james-to-backend: $it")
                delay(10)
            }
        }
        println("TestBackend: started")
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
