package moklev.utils

/**
 * @author Moklev Vyacheslav
 */
class ASMBuilder {
    val builder = StringBuilder()
    
    fun label(label: String) {
        builder.append("$label:\n")
    }
    
    fun appendLine(instruction: String, vararg operands: Any?) {
        builder
                .append("    ")
                .append(instruction)
        if (operands.isNotEmpty()) 
            builder.append(" ")
        operands.joinTo(builder).append("\n")
    }
    
    fun build(): String = builder.toString()
}