package moklev.asm.utils

/**
 * @author Moklev Vyacheslav
 */

/**
 * Representation of static value. Could be valid for IR (Concept-ASM)
 * or for assembly ([StaticAssemblyValue]). Has a certain type.
 */
interface CompileTimeValue {
    /**
     * Type of the static value
     */
    val type: Type

    /**
     * Assume that value has another type
     */
    fun ofType(type: Type): CompileTimeValue
    
    /**
     * Transform IR representation to assembly representation (based on variable assignment
     * from register allocation)
     */
    fun value(variableAssignment: Map<String, StaticAssemblyValue>): StaticAssemblyValue? {
        return when (this) {
            is Variable -> variableAssignment[toString()]
            is StaticAssemblyValue -> this
            is InternalCompileTimeValue -> value
            else -> error("Not supported: $javaClass")
        }
    }
}

/**
 * Marker interface for immutable values which can be propagated
 */
interface ImmutableValue

/**
 * Variable in the IR (Concept-ASM)
 */
class Variable(val name: String, var version: Int = 0) : CompileTimeValue, ImmutableValue {
    override fun toString() = "$name${if (version > 0) ".$version" else ""}"
    
    override val type: Type
        get() = StaticUtils.state.getVarType(name)

    override fun ofType(type: Type): Variable {
        StaticUtils.state.setVarType(name, type)
        return this
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false
        other as Variable

        if (name != other.name) return false
        if (version != other.version) return false
        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + version
        return result
    }
}
/**
 * Undefined value, used in intermediate compilation steps.
 * Should not occur in final code
 */
object Undefined : CompileTimeValue {
    override val type: Type = Type.Undefined
    
    override fun toString() = "[undefined]"

    override fun equals(other: Any?): Boolean {
        return other is Undefined
    }

    override fun ofType(type: Type): CompileTimeValue = error("Cannot convert an Undefined")
}

/**
 * Representation of static value in assembly
 */
interface StaticAssemblyValue {
    /**
     * Size of an assembly value
     */
    val size: Int

    /**
     * String representation of this value in the assembly for code generation
     */
    val str: String

    /**
     * Conversion of value to the lower size
     * @param size size of new value
     * @return value with decreased size
     */
    fun ofSize(size: Int): StaticAssemblyValue

    /**
     * Converts internal assembly value to a compile time value of Concept-ASM.
     * Use with attention, unlike other compile time values like [Variable] resulting
     * compile time value can be mutable and can behave unexpectedly  
     */
    fun asCompileTimeValue(type: Type): CompileTimeValue {
        return InternalCompileTimeValue(this, type)
    }
}

class InternalCompileTimeValue(value: StaticAssemblyValue, override val type: Type) : CompileTimeValue {
    override fun toString(): String = value.str
    
    val value: StaticAssemblyValue = value.ofSize(type.size)

    override fun ofType(type: Type): CompileTimeValue {
        return InternalCompileTimeValue(value, type)
    }
}

/**
 * Location of variable in integer register.
 * @param registerNames list of names for sizes 8, 4, 2, 1 (if available)
 * @param size size of value in register
 */
class InRegister(val registerNames: List<String>, override val size: Int) : StaticAssemblyValue {
    override fun toString(): String = str

    override fun ofSize(size: Int): InRegister {
        return InRegister(registerNames,  size)
    }

    override val str: String
        get() = register

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false
        other as InRegister

        return register == other.register
    }

    override fun hashCode(): Int {
        return register.hashCode()
    }
    
    private val register: String
        get() = when (size) {
            8 -> registerNames.getOrNull(0)
            4 -> registerNames.getOrNull(1)
            2 -> registerNames.getOrNull(2)
            1 -> registerNames.getOrNull(3)
            else -> null
        } ?: error("Undefined register name for size $size")
}

/**
 * Location of value on stack in [rbp - [offset]]
 * @param offset offset from rbp (or rsp is [fromRsp] is `true`)
 * @param size size of value
 * @param fromRsp if `true` then value is located in [rsp - [offset]] rather than in [rbp - [offset]]
 */
data class InStack(val offset: Int, override val size: Int, val fromRsp: Boolean = false) : StaticAssemblyValue {
    override fun toString(): String {
        return "[${if (fromRsp) "rsp" else "rbp"} ${if (offset < 0) "+" else "-"} ${Math.abs(offset)}]:$size"
    }
    
    override fun ofSize(size: Int): InStack {
        return InStack(offset, size, fromRsp)
    }

    override val str: String 
        get() {
            val str = " [${if (fromRsp) "rsp" else "rbp"} ${if (offset < 0) "+" else "-"} ${Math.abs(offset)}]"
            return when (size) {
                8 -> "qword"
                4 -> "dword"
                2 -> "word"
                1 -> "byte"
                else -> error("Unsupported size $size")
            } + str
        }
}

/**
 * Interface for constants (both compile time and assembly)
 */
interface ConstValue : CompileTimeValue, StaticAssemblyValue, ImmutableValue {
    override val size: Int
        get() = type.size

    override fun ofType(type: Type): CompileTimeValue {
        if (type == this.type)
            return this
        error("Can't change type of const value")
    }

    override fun ofSize(size: Int): StaticAssemblyValue {
        if (size == this.size)
            return this
        error("Can't change size of const value")
    }
}

/**
 * 64-bit signed integer constant value
 */
data class Int64Const(val value: Long) : ConstValue {
    override fun toString(): String = str

    override val type: Type = Type.Int64
    
    override val str: String
        get() = value.toString()
}

/**
 * Stack address: [rbp - [offset]]
 */
data class StackAddrVariable(val offset: Int, override val type: Type) : ConstValue {
    override fun toString(): String = str

    override val str: String
        get() = "[rbp ${sign(-offset)} ${Math.abs(offset)}]"

    private fun sign(value: Int): Char = when {
        value >= 0 -> '+'
        else -> '-'
    }
}

data class ComplexValue(val list: List<StaticAssemblyValue>) : StaticAssemblyValue {
    override fun toString(): String = error("toString() from ComplexValue")
    
    override val size: Int
        get() = list.sumBy { it.size }
    
    override val str: String
        get() = error("str from ComplexValue")

    override fun ofSize(size: Int): StaticAssemblyValue {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

// Useful constants
// TODO lower byte of rsi, rdi, rsp, rbp?
val RAX = InRegister(listOf("rax", "eax", "ax", "al"), 8)
val RBX = InRegister(listOf("rbx", "ebx", "bx", "bl"), 8)
val RCX = InRegister(listOf("rcx", "ecx", "cx", "cl"), 8)
val RDX = InRegister(listOf("rdx", "edx", "dx", "dl"), 8)
val RDI = InRegister(listOf("rdi", "edi", "di"), 8)
val RSI = InRegister(listOf("rsi", "esi", "si"), 8)
val RBP = InRegister(listOf("rbp", "ebp", "bp"), 8)
val RSP = InRegister(listOf("rsp", "esp", "sp"), 8)
val R8 = InRegister(listOf("r8", "r8d", "r8w", "r8b"), 8)
val R9 = InRegister(listOf("r9", "r9d", "r9w", "r9b"), 8)
val R10 = InRegister(listOf("r10", "r10d", "r10w", "r10b"), 8)
val R11 = InRegister(listOf("r11", "r11d", "r11w", "r11b"), 8)
val R12 = InRegister(listOf("r12", "r12d", "r12w", "r12b"), 8)
val R13 = InRegister(listOf("r13", "r13d", "r13w", "r13b"), 8)
val R14 = InRegister(listOf("r14", "r14d", "r14w", "r14b"), 8)
val R15 = InRegister(listOf("r15", "r15d", "r15w", "r15b"), 8)
