package moklev.asm.utils

/**
 * @author Moklev Vyacheslav
 */
sealed class CompileTimeValue {
    fun text(variableAssignment: Map<String, String>): String {
        return when (this) {
            is Variable -> variableAssignment[toString()]!!
            is IntConst -> "$value"
            else -> error("Not supported: $javaClass")
        }
    }
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

class IntConst(val value: Int) : CompileTimeValue() {
    override fun toString() = "$value"
}

class FloatConst(val value: Float) : CompileTimeValue()

class DoubleConst(val value: Double) : CompileTimeValue()

object Undefined : CompileTimeValue() {
    override fun toString() = "[undefined]"
}