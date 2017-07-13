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
        if (rhs2 is IntConst && rhs2.value == 0) {
            return listOf(Assign(lhs, rhs1))
        }
        return listOf(this)
    }

    override fun coalescingEdges(): List<Pair<String, Either<InRegister, String>>> {
        return listOf("$lhs" to Either.Right("$rhs1"))
    }

    override fun compile(builder: ASMBuilder, variableAssignment: Map<String, StaticAssemblyValue>) {
        val lhsValue = lhs.value(variableAssignment)!!
        val rhs1Value = rhs1.value(variableAssignment)!!
        val rhs2Value = rhs2.value(variableAssignment)!!

        if (lhsValue != rhs1Value) {
            compileAssign(builder, lhsValue, rhs1Value)
            if (lhsValue is InStack && rhs2Value is InStack) {
                // TODO get temp register
                val tempRegister = "r15"
                builder.appendLine("mov", tempRegister, "$lhsValue")
                builder.appendLine("sub", tempRegister, "$rhs2Value")
                builder.appendLine("mov", "$lhsValue", tempRegister)
            } else if (lhsValue is InStack && rhs2Value !is InRegister) {
                // TODO must be sized
                builder.appendLine("sub", "qword $lhsValue", "$rhs2Value")
            } else {
                builder.appendLine("sub", "$lhsValue", "$rhs2Value")
            }
        } else if (lhsValue == rhs1Value) {
            if (lhsValue is InStack && rhs2Value is InStack) {
                // TODO get temp register
                val tempRegister = "r15"
                builder.appendLine("mov", tempRegister, "$lhsValue")
                builder.appendLine("sub", tempRegister, "$rhs2Value")
                builder.appendLine("mov", "$lhsValue", tempRegister)
            } else if (lhsValue is InStack && rhs2Value !is InRegister) {
                // TODO must be sized
                builder.appendLine("sub", "qword $lhsValue", "$rhs2Value")
            } else {
                builder.appendLine("sub", "$lhsValue", "$rhs2Value")
            }
        } 
    }
}