package moklev.asm.utils

import moklev.asm.compiler.SSATransformer
import moklev.asm.interfaces.AssignInstruction

/**
 * @author Moklev Vyacheslav
 */
object StaticUtils {
    private var labelCount = 0
    
    fun nextLabel(): String {
        val label = ".LL$labelCount"
        labelCount += 1
        return label
    }
    
    val state = ASMState()
}