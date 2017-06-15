package moklev.asm.utils

/**
 * @author Moklev Vyacheslav
 */
sealed class CompileTimeValue {
    fun value(variableAssignment: Map<String, MemoryLocation>): CompileTimeValue {
        return when (this) {
            is Variable -> variableAssignment[toString()]!!
            else -> this
        }
    }
}

/**
 * Static location of variable in memory
 */
sealed class MemoryLocation : CompileTimeValue()

/**
 * Location of variable in integer register with name [register]
 */
class InRegister(val register: String) : MemoryLocation() {
    override fun toString(): String = register
}

/**
 * Location of variable on stack in [rbp - [offset]]
 */
class InStack(val offset: Int) : MemoryLocation() {
    override fun toString(): String = "[rbp - $offset]"
}

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

class IntConst(val value: Int) : MemoryLocation() {
    override fun toString(): String = "$value"
}

class FloatConst(val value: Float) : MemoryLocation() {
    override fun toString(): String = "$value"
}

class DoubleConst(val value: Double) : MemoryLocation() {
    override fun toString(): String = "$value"
}

object Undefined : CompileTimeValue() {
    override fun toString() = "[undefined]"
}