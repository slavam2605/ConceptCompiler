package moklev.asm.utils

import moklev.dummy_lang.parser.DummyLangParser

/**
 * @author Moklev Vyacheslav
 */
private const val MACHINE_POINTER_SIZE = 8

sealed class Type {
    abstract val size: Int

    object Int64 : Type() {
        override fun toString(): String = "Int64"
        override val size: Int = 8
    }

    object Int32 : Type() {
        override fun toString(): String = "Int32"
        override val size: Int = 4
    }

    data class Blob(override val size: Int) : Type()
    
    data class Pointer(val type: Type) : Type() {
        override val size: Int = MACHINE_POINTER_SIZE
    }
    
    object Undefined : Type() {
        override val size: Int
            get() = error("Undefined is not a valid type")
    }
}

fun Type.dereference() : Type {
    if (this !is Type.Pointer)
        error("Type is not a pointer")
    return type
}