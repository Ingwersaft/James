package com.mkring.james

import com.mkring.james.mapping.getOrElse
import org.junit.Ignore
import org.junit.Test

@Ignore("usage examples, sleep needed for junit tests")
class JamesTest {
    @Test
    fun basicUsageTelegram() {
        james {
            telegram {
                token = "***REMOVED***"
                username = "***REMOVED***"
            }
            map("/frage", "eine frage") {
                val answer = ask("sende irgendwas bitte!").get()
                send("received: $answer from $username $senderId $arguments")
            }
        }
        Thread.sleep(1000000L)
    }

    @Test
    fun basicUsageRocketChat() {
        james {
            rocketchat {
                websocketTarget = "wss://***REMOVED***/websocket"
                username = ""
                password = ""
                sslVerifyHostname = false
                ignoreInvalidCa = true
            }
            name = "chatbot"
            map("frage", "eine frage") {
                askTimeout = 1
                val answer = ask("sende irgendwas bitte!").getOrElse("schade, zu langsam")
                send("received: $answer from $username $senderId $arguments")

                askTimeout = 20
                askWithRetry(3, "sende `testmich`") {
                    it == "testmich"
                }.let { send("got answer: ${it.getOrElse("dann halt nicht!")}") }
            }
        }
        Thread.sleep(1000000L)
    }
}