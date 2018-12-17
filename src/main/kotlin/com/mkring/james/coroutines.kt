package com.mkring.james

import kotlinx.coroutines.newFixedThreadPoolContext
import org.slf4j.LoggerFactory
import java.util.concurrent.ForkJoinPool

/**
 * threadpool dispatcher for james with at least 4 threads
 */
val JamesPool = newFixedThreadPoolContext(
    when {
        ForkJoinPool.getCommonPoolParallelism() < 4 -> 4
        else -> ForkJoinPool.getCommonPoolParallelism()
    },
    "JamesPool"
)

/**
 * logging extension function
 */
fun Any.lg(toBeLogged: Any) {
    LoggerFactory.getLogger(this::class.java).apply {
        this.info(toBeLogged.toString())
    }
}

/**
 * logging extension function: warn
 */
fun Any.lw(toBeLogged: Any) {
    LoggerFactory.getLogger(this::class.java).apply {
        this.warn(toBeLogged.toString())
    }
}

internal infix fun String.isIn(abortKeywords: List<String>): Boolean {
    return abortKeywords.contains(this)
}