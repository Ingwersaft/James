package com.mkring.james.mapping

infix fun <T> Ask<T>.or(fallback: T) = when (this) {
    is Ask.Answer -> this
    else -> Ask.Answer(fallback)
}

infix fun <T> Ask<T>.getOrElse(fallback: T) = when (this) {
    is Ask.Answer -> value
    else -> fallback
}

inline infix fun <T, R> Ask<T>.map(function: (T) -> (R)): Ask<R> = when (this) {
    is Ask.Timeout -> this
    is Ask.Answer -> Ask.Answer(function(value))
}

inline infix fun <T> Ask<T>.any(predicate: (T) -> Boolean): Boolean = when (this) {
    is Ask.Answer -> predicate(value)
    is Ask.Timeout -> false
}

sealed class Ask<out T> {
    abstract fun get(): T

    object Timeout : Ask<Nothing>() {
        override fun get(): Nothing {
            throw IllegalStateException("timeout or aborted")
        }
        override fun toString() = "[Timeout]"
    }

    data class Answer<out T>(val value: T) : Ask<T>() {
        override fun get(): T = value
        override fun toString() = "[Answer: $value]"
    }

    companion object {
        fun <T> of(f: () -> T): Ask<T> = try {
            Answer(f())
        } catch (ex: Exception) {
            Timeout
        }
    }
}
