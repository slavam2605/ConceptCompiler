package moklev.asm.instructions

import moklev.asm.interfaces.AssignInstruction
import moklev.asm.interfaces.Instruction
import moklev.asm.utils.*
import moklev.utils.ASMBuilder

/**
 * Internal instruction that marks [lhs] as externally initialized value
 *
 * @author Moklev Vyacheslav
 */
class ExternalAssign(lhs: Variable) : AssignInstruction(lhs) {
    override val usedValues: List<CompileTimeValue> = emptyList()

    override val allValues
        get() = listOf(lhs.toString())

    override fun substitute(variable: Variable, value: CompileTimeValue): Instruction = this

    override fun simplify(): List<Instruction> = listOf(this)

    override fun coloringPreferences(): List<ColoringPreference> = emptyList()

    override fun compile(builder: ASMBuilder, variableAssignment: Map<String, StaticAssemblyValue>) {}

    override fun toString(): String = "$lhs = [externally assigned]"
}