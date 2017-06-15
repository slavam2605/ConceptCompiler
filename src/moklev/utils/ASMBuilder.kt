package moklev.utils

/**
 * @author Moklev Vyacheslav
 */
class ASMBuilder {
    val builder = StringBuilder()
    
    fun appendLine(instruction: String, vararg operands: String) {
        builder.append(instruction).append(" ")
        operands.joinTo(builder).append("\n")
    }
    
    fun build(): String = builder.toString()
}