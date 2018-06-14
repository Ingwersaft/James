package com.mkring.james

import kotlinx.coroutines.experimental.*
import org.slf4j.LoggerFactory
import java.util.concurrent.ForkJoinPool

val JamesPool = newFixedThreadPoolContext(
    when {
        ForkJoinPool.getCommonPoolParallelism() < 4 -> 4
        else -> ForkJoinPool.getCommonPoolParallelism()
    },
    "JamesPool"
)

fun <T> Deferred<T>.awaitBlocking(): T = runBlocking {
    await()
}

private val fireAndForgetLoopLog = LoggerFactory.getLogger("fireAndForgetLoop")
suspend fun fireAndForgetLoop(name: String, block: suspend CoroutineScope.() -> Unit) = launch(JamesPool) {
    while (true) {
        try {
            block()
        } catch (e: JobCancellationException) {
            fireAndForgetLoopLog.warn("$name: received JobCancellationException, will cancel myself")
            coroutineContext.cancel(e)
        } catch (e: Exception) {
            fireAndForgetLoopLog.error("catched exception in $name routing: ${e::class.java.simpleName}:${e.message}")
        }
        delay(10)
    }
}.also {
    fireAndForgetLoopLog.info("launched $name")
}

fun Any.lg(toBeLogged: Any) {
    LoggerFactory.getLogger(this::class.java).apply {
        this.info(toBeLogged.toString())
    }
}

internal infix fun String.isIn(abortKeywords: List<String>): Boolean {
    return abortKeywords.contains(this)
}