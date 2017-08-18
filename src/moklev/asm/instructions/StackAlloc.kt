package moklev.asm.instructions

import moklev.asm.interfaces.AssignInstruction
import moklev.asm.interfaces.Instruction
import moklev.asm.utils.*
import moklev.utils.ASMBuilder

/**
 * @author Moklev Vyacheslav
 */
class StackAlloc(lhs: Variable, val size: Int) : AssignInstruction(lhs) {
    override fun toString(): String = "$lhs = stack_alloc($size)"
    
    override val usedValues: List<CompileTimeValue> = emptyList()

    override fun substitute(variable: Variable, value: CompileTimeValue): Instruction = this

    override fun simplify(): List<Instruction> = listOf(this)

    override fun coloringPreferences(): List<ColoringPreference> = emptyList()

    override fun compile(builder: ASMBuilder, variableAssignment: Map<String, StaticAssemblyValue>)
        = error("Must be statically allocated")
}