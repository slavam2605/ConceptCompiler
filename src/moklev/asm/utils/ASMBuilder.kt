package moklev.asm.utils

/**
 * @author Moklev Vyacheslav
 */
class ASMBuilder(val isDebug: Boolean = false) {
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
        operands.joinTo(builder).append('\n')
    }
    
    fun newLineComment(comment: Any) {
        if (isDebug)
            builder.append("\n    ; ").append(comment).append('\n')
    }
    
    fun build(): String = builder.toString()
}