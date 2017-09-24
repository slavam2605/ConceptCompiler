package moklev.asm.instructions

import moklev.asm.interfaces.BranchInstruction
import moklev.asm.interfaces.Instruction
import moklev.asm.interfaces.UnconditionalBranchInstruction
import moklev.asm.utils.*
import moklev.asm.utils.ASMBuilder

/**
 * @author Vyacheslav Moklev
 */
class Jump(override val label: String) : BranchInstruction, UnconditionalBranchInstruction {
    override fun toString() = "goto $label"
    override val usedValues = emptyList<CompileTimeValue>()
    override val allValues
        get() = listOf(label)

    override fun substitute(variable: Variable, value: CompileTimeValue): Instruction = this
    override fun simplify() = listOf(this)
    override fun coloringPreferences(): List<ColoringPreference> = emptyList()

    override fun compileBranch(builder: ASMBuilder, variableAssignment: Map<String, StaticAssemblyValue>, destLabel: String) {
        builder.appendLine("jmp", destLabel)
    }
}