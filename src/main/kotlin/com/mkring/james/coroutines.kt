package com.mkring.james

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.runBlocking
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

fun Any.lg(toBeLogged: Any) {
    LoggerFactory.getLogger(this::class.java).apply {
        this.info(toBeLogged.toString())
    }
}

internal infix fun String.isIn(abortKeywords: List<String>): Boolean {
    return abortKeywords.contains(this)
}