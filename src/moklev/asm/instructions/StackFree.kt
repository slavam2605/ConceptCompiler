package moklev.asm.instructions

import moklev.asm.compiler.LiveRange
import moklev.asm.compiler.SSATransformer
import moklev.asm.interfaces.Instruction
import moklev.asm.interfaces.ReadonlyInstruction
import moklev.asm.utils.ColoringPreference
import moklev.asm.utils.CompileTimeValue
import moklev.asm.utils.Variable
import moklev.asm.utils.VariableAssignment
import moklev.utils.ASMBuilder

/**
 * @author Moklev Vyacheslav
 */
class StackFree(val size: Int) : ReadonlyInstruction() {
    override fun toString(): String = "stack_free($size)"

    override val usedValues: List<CompileTimeValue> = emptyList()

    override val allValues: List<String>
        get() = listOf(size.toString())

    override fun substitute(variable: Variable, value: CompileTimeValue): Instruction = this

    override fun simplify(): List<Instruction> = listOf(this)

    override fun compile(builder: ASMBuilder,
                         blocks: Map<String, SSATransformer.Block>,
                         variableAssignment: VariableAssignment,
                         currentBlockLabel: String,
                         liveRange: Map<String, LiveRange>,
                         indexInBlock: Int) = Unit

    override fun coloringPreferences(): List<ColoringPreference> = emptyList()
}