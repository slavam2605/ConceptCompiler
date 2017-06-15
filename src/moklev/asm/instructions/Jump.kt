package moklev.asm.instructions

import moklev.asm.interfaces.BranchInstruction
import moklev.asm.interfaces.Instruction
import moklev.asm.utils.CompileTimeValue
import moklev.asm.utils.Variable
import moklev.utils.ASMBuilder

/**
 * @author Vyacheslav Moklev
 */
class Jump(label: String) : BranchInstruction(label) {
    override fun toString() = "goto $label"
    override val usedValues = emptyList<CompileTimeValue>()
    override fun substitute(variable: Variable, value: CompileTimeValue): Instruction = this
    override fun simplify() = listOf(this)

    override fun compileBranch(builder: ASMBuilder, variableAssignment: Map<String, String>, destLabel: String) {
        builder.appendLine("jmp", destLabel)
    }
}