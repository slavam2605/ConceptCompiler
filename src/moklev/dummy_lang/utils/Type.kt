package moklev.dummy_lang.utils

/**
 * @author Vyacheslav Moklev
 */
const val POINTER_SIZE: Int = 8

sealed class Type {
    abstract val sizeOf: Int
    
    data class PrimitiveType(val typeName: String, override val sizeOf: Int) : Type() {
        override fun toString(): String = typeName
    }
    
    data class PointerType(val sourceType: Type) : Type() {
        override fun toString(): String = "$sourceType*"

        override val sizeOf: Int = POINTER_SIZE
    }
    
    fun toASMType(): moklev.asm.utils.Type {
        return when (this) {
            is PrimitiveType -> when (typeName) {
                "i64" -> moklev.asm.utils.Type.INT
                else -> error("Unknown primitive type")
            }
            is PointerType -> moklev.asm.utils.Type.INT
        }
    }
}

val INT_64 = Type.PrimitiveType("i64", 8)