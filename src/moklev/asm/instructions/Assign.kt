package moklev.asm.instructions

import moklev.asm.compiler.SSATransformer
import moklev.asm.interfaces.Instruction
import moklev.asm.interfaces.UnaryInstruction
import moklev.asm.utils.CompileTimeValue
import moklev.asm.utils.Variable
import moklev.utils.ASMBuilder

/**
 * @author Vyacheslav Moklev
 */
class Assign(lhs: Variable, rhs1: CompileTimeValue) : UnaryInstruction(lhs, rhs1) {
    override fun toString() = "$lhs = $rhs1"
    override fun substitute(variable: Variable, value: CompileTimeValue): Instruction {
        return Assign(lhs, if (rhs1 == variable) value else rhs1)
    }

    override fun simplify() = listOf(this)

    override fun compile(builder: ASMBuilder, variableAssignment: Map<String, String>) {
        val firstOperand = lhs.text(variableAssignment)
        val secondOperand = rhs1.text(variableAssignment)
        if (firstOperand != secondOperand) {
            builder.appendLine(
                    "mov",
                    firstOperand,
                    secondOperand
            )
        }
    }
}