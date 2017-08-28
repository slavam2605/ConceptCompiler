package moklev.asm.utils

/**
 * @author Moklev Vyacheslav
 */

/**
 * Representation of static value. Could be valid for IR (Concept-ASM)
 * or for assembly ([StaticAssemblyValue])
 */
sealed class CompileTimeValue {
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
data class Variable(val name: String, var version: Int = 0) : CompileTimeValue(), ConstValue {
    override fun toString() = "$name${if (version > 0) ".$version" else ""}"
}

/**
 * Undefined value, used in intermediate compilation steps.
 * Should not occur in final code
 */
object Undefined : CompileTimeValue() {
    override fun toString() = "[undefined]"

    override fun equals(other: Any?): Boolean {
        return other is Undefined
    }
}

/**
 * Representation of static value in assembly
 */
sealed class StaticAssemblyValue : CompileTimeValue()

/**
 * Location of variable in integer register with name [register]
 */
data class InRegister(val register: String) : StaticAssemblyValue() {
    override fun toString(): String = register
}

/**
 * Location of variable on stack in [rbp - [offset]]
 */
data class InStack(val offset: Int) : StaticAssemblyValue() {
    override fun toString(): String = "qword [rbp ${if (offset < 0) "+" else "-"} ${Math.abs(offset)}]"
}

/**
 * Static integer constant value
 */
data class IntConst(val value: Long) : StaticAssemblyValue(), ConstValue {
    override fun toString(): String = "$value"
}

/**
 * Stack address: [rbp - [offset]]
 */
data class StackAddrVariable(val offset: Int) : StaticAssemblyValue(), ConstValue {
    override fun toString(): String = "[rbp ${sign(-offset)} ${Math.abs(offset)}]"

    private fun sign(value: Int): Char = when {
        value >= 0 -> '+'
        else -> '-'
    }
}

/**
 * x86 address representation: [[baseRegister] + [offsetRegister] * [offsetFactor] + [offset]]
 */
data class X86AddrConst(val baseRegister: InRegister?,
                   val offsetRegister: InRegister?,
                   val offsetFactor: Int,
                   val offset: Int) : StaticAssemblyValue() {
    override fun toString(): String = when {
        baseRegister != null && offsetRegister != null ->
            "[$baseRegister ${sign(offsetFactor)} $offsetRegister * ${Math.abs(offsetFactor)} ${sign(offset)} ${Math.abs(offset)}]"
        baseRegister != null ->
            "[$baseRegister ${sign(offset)} ${Math.abs(offset)}]"
        offsetRegister != null ->
            "[$offsetFactor * $offsetRegister ${sign(offset)} ${Math.abs(offset)}]"
        else ->
            "[$offset]"
    }

    private fun sign(value: Int): Char = when {
        value >= 0 -> '+'
        else -> '-'
    }
}

/**
 * Static float constant value
 */
data class FloatConst(val value: Float) : StaticAssemblyValue(), ConstValue {
    override fun toString(): String = "$value"
}

/**
 * Static double constant value
 */
data class DoubleConst(val value: Double) : StaticAssemblyValue(), ConstValue {
    override fun toString(): String = "$value"
}

// Useful constants
val RAX = InRegister("rax")
val RBX = InRegister("rbx")
val RCX = InRegister("rcx")
val RDX = InRegister("rdx")
val RDI = InRegister("rdi")
val RSI = InRegister("rsi")
val RBP = InRegister("rbp")
val RSP = InRegister("rsp")
val R8 = InRegister("r8")
val R9 = InRegister("r9")
val R10 = InRegister("r10")
val R11 = InRegister("r11")
val R12 = InRegister("r12")
val R13 = InRegister("r13")
val R14 = InRegister("r14")
val R15 = InRegister("r15")
