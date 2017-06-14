package moklev.asm.instructions

import moklev.asm.interfaces.BinaryInstruction
import moklev.asm.interfaces.Instruction
import moklev.asm.utils.CompileTimeValue
import moklev.asm.utils.IntConst
import moklev.asm.utils.Variable

/**
 * @author Moklev Vyacheslav
 */
class Add(lhs: Variable, rhs1: CompileTimeValue, rhs2: CompileTimeValue) : BinaryInstruction(lhs, rhs1, rhs2) {
    override fun toString() = "$lhs = $rhs1 + $rhs2"
    override fun substitute(variable: Variable, value: CompileTimeValue): Instruction {
        return Add(lhs, if (rhs1 == variable) value else rhs1, if (rhs2 == variable) value else rhs2)
    }

    override fun simplify(): List<Instruction> {
        if (rhs1 is IntConst && rhs2 is IntConst) {
            return listOf(Assign(lhs, IntConst(rhs1.value + rhs2.value)))
        }
        if (rhs1 is IntConst && rhs1.value == 0) {
            return listOf(Assign(lhs, rhs2))
        }
        if (rhs2 is IntConst && rhs2.value == 0) {
            return listOf(Assign(lhs, rhs2))
        }
        return listOf(this)
    }
}