package moklev.asm.instructions

import moklev.asm.interfaces.Instruction
import moklev.asm.utils.*
import moklev.utils.ASMBuilder
import moklev.utils.Either

/**
 * @author Moklev Vyacheslav
 */
class Mul(lhs: Variable, rhs1: CompileTimeValue, rhs2: CompileTimeValue) : BinaryInstruction(lhs,  rhs1, rhs2) {
    override fun toString(): String = "$lhs = $rhs1 * $rhs2"
    
    override fun substitute(variable: Variable, value: CompileTimeValue): Instruction {
        return Mul(lhs, if (rhs1 == variable) value else rhs1, if (rhs2 == variable) value else rhs2)
    }

    override fun simplify(): List<Instruction> {
        if (rhs1 is IntConst && rhs1.value == 1) {
            return listOf(Assign(lhs, rhs2))
        }
        if (rhs2 is IntConst && rhs2.value == 1) {
            return listOf(Assign(lhs, rhs1))
        }
        if (rhs1 is IntConst && rhs1.value == 0) {
            return listOf(Assign(lhs, IntConst(0)))
        }
        if (rhs2 is IntConst && rhs2.value == 0) {
            return listOf(Assign(lhs, IntConst(0)))
        }
        // TODO implement more (like lea and x * 2 = x + x)
        return listOf(this)
    }

    override fun coalescingEdges(): List<Pair<String, Either<InRegister, String>>> {
        return listOf("$lhs" to Either.Right("$rhs1"))
    }

    override fun compile(builder: ASMBuilder, variableAssignment: Map<String, StaticAssemblyValue>) {
        val lhsValue = lhs.value(variableAssignment)!!
        val rhs1Value = rhs1.value(variableAssignment)!!
        val rhs2Value = rhs2.value(variableAssignment)!!
        
        compileBinaryOperation(builder, "imul", lhsValue, rhs1Value, rhs2Value, symmetric = true)
    }
}