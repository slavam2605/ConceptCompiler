package moklev.utils

/**
 * @author Moklev Vyacheslav
 */
sealed class Either<out A, out B> {
    class Left<out A, out B>(val value: A) : Either<A, B>() {
        override fun toString(): String = "Left($value)"
    }
    
    class Right<out A, out B>(val value: B) : Either<A, B>() {
        override fun toString(): String = "Right($value)"
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