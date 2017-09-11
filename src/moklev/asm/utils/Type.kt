package moklev.asm.utils

/**
 * @author Moklev Vyacheslav
 */
sealed class Type {
    abstract val size: Int

    object INT : Type() {
        override fun toString(): String = "INT"

        override val size: Int = 8
    }

    data class RAW(override val size: Int) : Type()
}