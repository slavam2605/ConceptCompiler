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
class Div(lhs: Variable, rhs1: CompileTimeValue, rhs2: CompileTimeValue) : BinaryInstruction(lhs, rhs1, rhs2) {
    override fun toString() = "$lhs = $rhs1 / $rhs2"
    
    override fun substitute(variable: Variable, value: CompileTimeValue): Instruction {
        return Div(lhs, if (rhs1 == variable) value else rhs1, if (rhs2 == variable) value else rhs2)
    }

    override fun simplify(): List<Instruction> {
        if (rhs2 is IntConst && rhs2.value == 1) {
            return listOf(Assign(lhs, rhs1))
        }
        return listOf(this)
    }

    override fun coalescingEdges(): List<Pair<String, Either<InRegister, String>>> {
        return listOf(
                "$lhs" to Either.Left(RAX),
                "$rhs1" to Either.Left(RAX),
                "$lhs" to Either.Right("$rhs1")
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

        val rdxUsed = liveRange
                .asSequence()
                .filter { indexInBlock > it.value.firstIndex && indexInBlock < it.value.lastIndex }
                .mapNotNull { localAssignment[it.key] }
                .filterIsInstance<InRegister>()
                .any { it == RDX }

        val raxUsed = liveRange
                .asSequence()
                .filter { indexInBlock > it.value.firstIndex && indexInBlock < it.value.lastIndex }
                .mapNotNull { localAssignment[it.key] }
                .filterIsInstance<InRegister>()
                .any { it == RAX }
        
        
        val tempRegister = R15 // TODO normal temp register
        val tempRegister2 = R14

        if (raxUsed)
            compileAssign(builder, tempRegister2, RAX)
        if (rdxUsed) 
            compileAssign(builder, tempRegister, RDX)
        compileAssign(builder, RAX, rhs1)

        val actualRhs2 = if (rhs2 == RDX) tempRegister else rhs2 
        
        builder.appendLine("cqo")
        builder.appendLine("idiv", actualRhs2)
        
        compileAssign(builder, lhs, RAX)
        if (raxUsed)
            compileAssign(builder, RAX, tempRegister2)
        if (rdxUsed)
            compileAssign(builder, RDX, tempRegister)
    }

    override fun compile(builder: ASMBuilder, variableAssignment: Map<String, StaticAssemblyValue>) = error("Not applicable")
}