package com.mkring.james.mapping

/**
 * add fallback default value
 */
infix fun <T> Ask<T>.or(fallback: T) = when (this) {
    is Ask.Answer -> this
    else -> Ask.Answer(fallback)
}

/**
 * get answer or given default
 */
infix fun <T> Ask<T>.getOrElse(fallback: T) = when (this) {
    is Ask.Answer -> value
    else -> fallback
}

/**
 * map answer
 */
inline infix fun <T, R> Ask<T>.map(function: (T) -> (R)): Ask<R> = when (this) {
    is Ask.Timeout -> this
    is Ask.Answer -> Ask.Answer(function(value))
}

/**
 * validate answer
 */
inline infix fun <T> Ask<T>.any(predicate: (T) -> Boolean): Boolean = when (this) {
    is Ask.Answer -> predicate(value)
    is Ask.Timeout -> false
}

/**
 * class encapsulating chat-conversation results
 */
sealed class Ask<out T> {
    abstract fun get(): T

    /**
     * no answer given in time
     */
    object Timeout : Ask<Nothing>() {
        override fun get(): Nothing {
            throw IllegalStateException("timeout or aborted")
        }

        override fun toString() = "[Timeout]"
    }

    /**
     * answer object
     */
    data class Answer<out T>(val value: T) : Ask<T>() {
        override fun get(): T = value
        override fun toString() = "[Answer: $value]"
    }

    companion object {
        /**
         * wrap a given function
         */
        fun <T> of(f: () -> T): Ask<T> = try {
            Answer(f())
        } catch (ex: Exception) {
            Timeout
        }
    }
}
