package moklev.asm.instructions

import moklev.asm.interfaces.Instruction
import moklev.asm.interfaces.UnaryInstruction
import moklev.asm.utils.CompileTimeValue
import moklev.asm.utils.InStack
import moklev.asm.utils.MemoryLocation
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

    override fun compile(builder: ASMBuilder, variableAssignment: Map<String, MemoryLocation>) {
        val firstOperand = lhs.value(variableAssignment)
        val secondOperand = rhs1.value(variableAssignment)
        if (firstOperand.toString() != secondOperand.toString()) {
            if (firstOperand is InStack && secondOperand is InStack) {
                // TODO get temp register
                val tempRegister = "r15"
                builder.appendLine("mov", tempRegister, "$secondOperand")
                builder.appendLine("mov", "$firstOperand", tempRegister)
            } else {
                builder.appendLine("mov", "$firstOperand", "$secondOperand")
            }
        }
    }
}