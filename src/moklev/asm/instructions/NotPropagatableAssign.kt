package moklev.asm.instructions

import moklev.asm.interfaces.Instruction
import moklev.asm.utils.*
import moklev.utils.ASMBuilder

/**
 * @author Vyacheslav Moklev
 */
class NotPropagatableAssign(lhs: Variable, rhs1: CompileTimeValue) : UnaryInstruction(lhs, rhs1) {
    override fun toString() = "$lhs := $rhs1"

    override fun substitute(variable: Variable, value: CompileTimeValue): Instruction {
        return NotPropagatableAssign(lhs, if (rhs1 == variable) value else rhs1)
    }

    override fun simplify() = listOf(this)

    override fun coloringPreferences(): List<ColoringPreference> {
        return listOf(
                Coalesce("$lhs", "$rhs1")
        )
    }

    override fun compile(builder: ASMBuilder, variableAssignment: Map<String, StaticAssemblyValue>) {
        val firstOperand = lhs.value(variableAssignment)!!
        val secondOperand = rhs1.value(variableAssignment)!!
        compileAssign(builder, firstOperand, secondOperand)
    }
}