package moklev.utils

/**
 * @author Moklev Vyacheslav
 */
sealed class Either<out A, out B> {
    class Left<out A, out B>(val value: A) : Either<A, B>() {
        override fun toString(): String = "Left($value)"
        
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false
            return other is Left<*, *> && value == other.value
        }

        override fun hashCode(): Int {
            return value?.hashCode() ?: 0
        }
    }
    
    class Right<out A, out B>(val value: B) : Either<A, B>() {
        override fun toString(): String = "Right($value)"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false
            return other is Right<*, *> && value == other.value
        }

        override fun hashCode(): Int {
            return value?.hashCode() ?: 0
        }
    }
    
    inline fun <X, Y> map(f: (A) -> X, g: (B) -> Y): Either<X, Y> {
        return when (this) {
            is Left -> Left(f(value))
            is Right -> Right(g(value))
        }
    }
    
    fun left(): A {
        return when (this) {
            is Left -> value
            else -> throw IllegalStateException("Expected Left, but was $this")
        }
    }

    fun right(): B {
        return when (this) {
            is Right -> value
            else -> throw IllegalStateException("Expected Right, but was $this")
        }
    }
}

fun <A: Any, B> Either<A, B>.maybeLeft(): A? {
    return when (this) {
        is Either.Left -> value
        else -> null
    }
}

fun <A, B: Any> Either<A, B>.maybeRight(): B? {
    return when (this) {
        is Either.Right -> value
        else -> null
    }
}