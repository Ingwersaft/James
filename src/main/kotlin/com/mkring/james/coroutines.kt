package com.mkring.james

import kotlinx.coroutines.experimental.*
import org.slf4j.LoggerFactory

fun <T> Deferred<T>.awaitBlocking(): T = runBlocking {
    await()
}

private val fireAndForgetLoopLog = LoggerFactory.getLogger("fireAndForgetLoop")
suspend fun fireAndForgetLoop(name: String, block: suspend CoroutineScope.() -> Unit) = launch {
    while (true) {
        try {
            block()
        } catch (e: Exception) {
            fireAndForgetLoopLog.error("catched exception in $name routing: ${e::class.java.simpleName}:${e.message}")
        }
        delay(10)
    }
}.also {
    fireAndForgetLoopLog.info("launched $name")
}