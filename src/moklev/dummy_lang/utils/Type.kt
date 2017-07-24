package moklev.dummy_lang.utils

/**
 * @author Vyacheslav Moklev
 */
sealed class Type {
    data class PrimitiveType(val typeName: String) : Type() {
        override fun toString(): String = typeName
    }
    
    data class PointerType(val sourceType: Type) : Type() {
        override fun toString(): String = "$sourceType*"
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

val INT_64 = Type.PrimitiveType("i64")