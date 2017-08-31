package moklev.asm.utils

/**
 * @author Moklev Vyacheslav
 */
sealed class Type {
    object INT : Type()
    data class RAW(val size: Int) : Type()
}