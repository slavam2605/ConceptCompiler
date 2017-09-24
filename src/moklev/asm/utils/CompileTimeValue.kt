package moklev.asm.utils

/**
 * @author Moklev Vyacheslav
 */

/**
 * Representation of static value. Could be valid for IR (Concept-ASM)
 * or for assembly ([StaticAssemblyValue]). Has a certain type.
 */
sealed class CompileTimeValue {
    /**
     * Type of the static value
     */
    abstract val type: Type

    /**
     * Assume that value has another type
     */
    abstract fun ofType(type: Type): CompileTimeValue
    
    /**
     * Transform IR representation to assembly representation (based on variable assignment
     * from register allocation)
     */
    fun value(variableAssignment: Map<String, StaticAssemblyValue>): StaticAssemblyValue? {
        return when (this) {
            is Variable -> variableAssignment[toString()]
            is StaticAssemblyValue -> this
            else -> error("Not supported: $javaClass")
        }
    }
}

/**
 * Marker interface for constant values which are allowed to be propagated
 */
interface ConstValue

/**
 * Variable in the IR (Concept-ASM)
 */
class Variable(val name: String, var version: Int = 0) : CompileTimeValue(), ConstValue {
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
object Undefined : CompileTimeValue() {
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
sealed class StaticAssemblyValue : CompileTimeValue() {
    /**
     * Size of an assembly value
     */
    val size: Int
        get() = type.size

    /**
     * Specialization of [ofType] for [StaticAssemblyValue]
     */
    override abstract fun ofType(type: Type): StaticAssemblyValue 
}

/**
 * Location of variable in integer register with name [register]
 */
// TODO magic constant 8
class InRegister(val register: String, override val type: Type) : StaticAssemblyValue() {
    override fun toString(): String = register

    override fun ofType(type: Type): InRegister {
        return InRegister(register,  type)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false
        other as InRegister

        return register == other.register
    }

    override fun hashCode(): Int {
        return register.hashCode()
    }
}

/**
 * Location of variable on stack in [rbp - [offset]]
 */
// TODO eliminate default size
// TODO assign this with regard to size (qword -> ...)
// TODO toString() must not affect compilation
data class InStack(val offset: Int, override val type: Type, val fromRsp: Boolean = false) : StaticAssemblyValue() {
    override fun toString(): String = "qword [${
        if (fromRsp) "rsp" else "rbp"
    } ${if (offset < 0) "+" else "-"} ${Math.abs(offset)}]"

    override fun ofType(type: Type): InStack {
        return InStack(offset, type, fromRsp)
    }
}

/**
 * Static integer constant value
 */
data class Int64Const(val value: Long) : StaticAssemblyValue(), ConstValue {
    override fun toString(): String = "$value"

    override val type: Type = Type.Int64

    override fun ofType(type: Type): Nothing = error("Cannot convert a const value")
}

/**
 * Stack address: [rbp - [offset]]
 */
data class StackAddrVariable(val offset: Int, override val type: Type) : StaticAssemblyValue(), ConstValue {
    // TODO [NOT_CORRECT] StackAddrVariable has a certain addr type?
    override fun toString(): String = "[rbp ${sign(-offset)} ${Math.abs(offset)}]"

    private fun sign(value: Int): Char = when {
        value >= 0 -> '+'
        else -> '-'
    }

    override fun ofType(type: Type): Nothing = error("Cannot convert a const value")
}

data class ComplexValue(val list: List<StaticAssemblyValue>, override val type: Type) : StaticAssemblyValue() {
    override fun ofType(type: Type): ComplexValue {
        return ComplexValue(list, type)
    }
}

private fun inRegisterConstructor(name: String) = { type: Type ->
    InRegister(name, type)
}

// Useful constants
val RAX = inRegisterConstructor("rax")
val RBX = inRegisterConstructor("rbx")
val RCX = inRegisterConstructor("rcx")
val RDX = inRegisterConstructor("rdx")
val RDI = inRegisterConstructor("rdi")
val RSI = inRegisterConstructor("rsi")
val RBP = inRegisterConstructor("rbp")
val RSP = inRegisterConstructor("rsp")
val R8 = inRegisterConstructor("r8")
val R9 = inRegisterConstructor("r9")
val R10 = inRegisterConstructor("r10")
val R11 = inRegisterConstructor("r11")
val R12 = inRegisterConstructor("r12")
val R13 = inRegisterConstructor("r13")
val R14 = inRegisterConstructor("r14")
val R15 = inRegisterConstructor("r15")
