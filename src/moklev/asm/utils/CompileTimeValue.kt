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
 * Variable in the IR (Concept-ASM)
 */
class Variable(val name: String) : CompileTimeValue() {
    var version: Int = 0

    constructor(name: String, version: Int) : this(name) {
        this.version = version
    }

    override fun toString() = "$name${if (version > 0) ".$version" else ""}"

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
class InRegister(val register: String) : StaticAssemblyValue() {
    override fun toString(): String = register
    
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
class InStack(val offset: Int) : StaticAssemblyValue() {
    override fun toString(): String = "[rbp - $offset]"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as InStack

        return offset == other.offset

    }

    override fun hashCode(): Int {
        return offset
    }
}

/**
 * Static integer constant value
 */
class IntConst(val value: Int) : StaticAssemblyValue() {
    override fun toString(): String = "$value"
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as IntConst

        return value == other.value
    }

    override fun hashCode(): Int {
        return value
    }
}

/**
 * Static float constant value
 */
class FloatConst(val value: Float) : StaticAssemblyValue() {
    override fun toString(): String = "$value"
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as FloatConst

        return value == other.value
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }
}

/**
 * Static double constant value
 */
class DoubleConst(val value: Double) : StaticAssemblyValue() {
    override fun toString(): String = "$value"
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as DoubleConst

        return value == other.value
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }
}

// Useful constants
val RAX = InRegister("rax")
val RDI = InRegister("rdi")
val RSI = InRegister("rsi")
val RDX = InRegister("rdx")
val RCX = InRegister("rcx")
val R8 = InRegister("r8")
val R9 = InRegister("r9")
