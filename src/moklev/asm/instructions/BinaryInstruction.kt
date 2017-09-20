package moklev.asm.instructions

import moklev.asm.interfaces.AssignInstruction
import moklev.asm.utils.CompileTimeValue
import moklev.asm.utils.Variable

/**
 * Binary instruction of form `v1 = v2 <op> v3`
 *
 * @author Moklev Vyacheslav
 */
abstract class BinaryInstruction(lhs: Variable, val rhs1: CompileTimeValue, val rhs2: CompileTimeValue) : AssignInstruction(lhs) {
    override val usedValues = listOf(rhs1, rhs2)

    override val allValues
        get() = listOf(lhs.toString(), rhs1.toString(), rhs2.toString())
}