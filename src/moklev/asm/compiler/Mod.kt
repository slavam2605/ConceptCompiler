package moklev.asm.compiler

import moklev.asm.instructions.Assign
import moklev.asm.instructions.BinaryInstruction
import moklev.asm.interfaces.Instruction
import moklev.asm.utils.*
import moklev.utils.ASMBuilder
import moklev.utils.Either

/**
 * @author Moklev Vyacheslav
 */
class Mod(lhs: Variable, rhs1: CompileTimeValue, rhs2: CompileTimeValue) : BinaryInstruction(lhs, rhs1, rhs2) {
    override fun toString() = "$lhs = $rhs1 % $rhs2"
    
    override fun substitute(variable: Variable, value: CompileTimeValue): Instruction {
        return Mod(lhs, if (rhs1 == variable) value else rhs1, if (rhs2 == variable) value else rhs2)
    }

    override fun simplify(): List<Instruction> {
        if (rhs2 is IntConst && rhs2.value == 1) {
            return listOf(Assign(lhs, IntConst(0)))
        }
        return listOf(this)
    }

    override fun coalescingEdges(): List<Pair<String, Either<InRegister, String>>> {
        return listOf(
                "$lhs" to Either.Left(RDX),
                "$rhs1" to Either.Left(RAX)
        )
    }

    override fun compile(
            builder: ASMBuilder,
            blocks: Map<String, SSATransformer.Block>,
            variableAssignment: VariableAssignment,
            currentBlockLabel: String,
            liveRange: Map<String, LiveRange>,
            indexInBlock: Int)
    {
        val localAssignment = variableAssignment[currentBlockLabel]!!
        val lhs = lhs.value(localAssignment)!!
        val rhs1 = rhs1.value(localAssignment)!!
        val rhs2 = rhs2.value(localAssignment)!!

        compileDiv(builder, null, lhs, rhs1, rhs2, localAssignment, liveRange, indexInBlock, "${this.lhs}")
    }

    override fun compile(builder: ASMBuilder, variableAssignment: Map<String, StaticAssemblyValue>) = error("Not applicable")
}