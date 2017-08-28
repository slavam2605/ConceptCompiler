package moklev.asm.instructions

import moklev.asm.interfaces.Instruction
import moklev.asm.utils.*
import moklev.utils.ASMBuilder
import moklev.utils.Either

/**
 * @author Moklev Vyacheslav
 */
class Sub(lhs: Variable, rhs1: CompileTimeValue, rhs2: CompileTimeValue) : BinaryInstruction(lhs, rhs1, rhs2) {
    override fun toString() = "$lhs = $rhs1 - $rhs2"

    override fun substitute(variable: Variable, value: CompileTimeValue): Instruction {
        return Sub(lhs, if (rhs1 == variable) value else rhs1, if (rhs2 == variable) value else rhs2)
    }

    override fun simplify(): List<Instruction> {
        if (rhs1 is IntConst && rhs2 is IntConst) {
            return listOf(Assign(lhs, IntConst(rhs1.value - rhs2.value)))
        }
        if (rhs2 is IntConst && rhs2.value == 0L) {
            return listOf(Assign(lhs, rhs1))
        }
        return listOf(this)
    }

    override fun coloringPreferences(): List<ColoringPreference> {
        return listOf(
                Coalesce("$lhs", "$rhs1")
        )
    }

    override fun compile(builder: ASMBuilder, variableAssignment: Map<String, StaticAssemblyValue>) {
        val lhsValue = lhs.value(variableAssignment)!!
        val rhs1Value = rhs1.value(variableAssignment)!!
        val rhs2Value = rhs2.value(variableAssignment)!!

        compileBinaryOperation(builder, "sub", lhsValue, rhs1Value, rhs2Value)
    }
}