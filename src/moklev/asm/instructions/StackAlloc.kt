package moklev.asm.instructions

import moklev.asm.interfaces.AssignInstruction
import moklev.asm.interfaces.Instruction
import moklev.asm.utils.*
import moklev.asm.utils.ASMBuilder

/**
 * @author Moklev Vyacheslav
 */
class StackAlloc(override val type: Type, override val lhs: Variable, val allocType: Type) : AssignInstruction {
    val size: Int
        get() = allocType.size
    
    override fun toString(): String = "$lhs = stack_alloc($size)"

    override val usedValues: List<CompileTimeValue> = emptyList()

    override val allValues: List<String>
        get() = listOf(lhs.toString(), size.toString())

    override fun substitute(variable: Variable, value: CompileTimeValue): Instruction = this

    override fun simplify(): List<Instruction> = listOf(this)

    override fun coloringPreferences(): List<ColoringPreference> = emptyList()

    override fun compile(builder: ASMBuilder, variableAssignment: Map<String, StaticAssemblyValue>)
            = error("Must be statically allocated")
}