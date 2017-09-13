package moklev.asm.instructions

import moklev.asm.interfaces.AssignInstruction
import moklev.asm.utils.CompileTimeValue
import moklev.asm.utils.Variable

/**
 * Unary instruction of form `v1 = <op> v2`
 * 
 * @author Moklev Vyacheslav
 */
abstract class UnaryInstruction(lhs: Variable, val rhs1: CompileTimeValue) : AssignInstruction(lhs) {
    override val usedValues = listOf(rhs1)

    override val allValues = listOf(lhs, rhs1)
}