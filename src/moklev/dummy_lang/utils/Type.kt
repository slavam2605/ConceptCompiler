package moklev.dummy_lang.utils

/**
 * @author Vyacheslav Moklev
 */
sealed class Type {
    class PrimitiveType(val typeName: String) : Type()
    
    fun toASMType(): moklev.asm.utils.Type {
        return when (this) {
            is PrimitiveType -> when (typeName) {
                "i64" -> moklev.asm.utils.Type.INT
                else -> error("Unknown primitive type")
            }
            else -> error("Unknown type")
        }
    }
}