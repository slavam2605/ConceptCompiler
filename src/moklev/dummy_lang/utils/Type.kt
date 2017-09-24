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

    data class StructType(val name: String, val fields: List<Pair<String, Type>>) : Type() {
        private val fieldIndex: Map<String, Int>
        private val fieldOffset: IntArray

        init {
            fieldIndex = fields
                    .asSequence()
                    .mapIndexed { index, (field, _) -> field to index }
                    .toMap()
            fieldOffset = IntArray(fields.size) { index ->
                if (index == 0) 0 else fields[index - 1].second.sizeOf
            }
            for (index in 1 until fieldOffset.size) {
                fieldOffset[index] += fieldOffset[index - 1]
            }
        }
        
        fun getField(index: Int): Field? {
            if (index < 0 || index >= fields.size)
                return null
            return Field(fields[index].second, fieldOffset[index])
        }
        
        fun getField(name: String): Field? {
            val fieldIndex = fieldIndex[name] ?: return null
            return getField(fieldIndex)
        }

        override fun toString(): String = name

        override val sizeOf: Int = fields
                .asSequence()
                .map { it.second.sizeOf }
                .sum()
        
        data class Field(val type: Type, val offset: Int)
    }

    fun toASMType(): moklev.asm.utils.Type {
        return when (this) {
            is PrimitiveType -> when (typeName) {
                "i64" -> moklev.asm.utils.Type.Int64
                "i32" -> moklev.asm.utils.Type.Int32
               else -> error("Unknown primitive type")
            }
            is PointerType -> moklev.asm.utils.Type.Pointer(this.sourceType.toASMType())
            is StructType -> moklev.asm.utils.Type.Blob(sizeOf)
        }
    }
}

val INT_64 = Type.PrimitiveType("i64", 8)
val INT_32 = Type.PrimitiveType("i32", 4)