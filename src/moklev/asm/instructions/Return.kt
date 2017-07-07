package moklev.asm.instructions

import moklev.asm.compiler.endBlockLabel
import moklev.asm.interfaces.BranchInstruction
import moklev.asm.interfaces.Instruction
import moklev.asm.utils.*
import moklev.utils.ASMBuilder
import moklev.utils.Either

/**
 * @author Moklev Vyacheslav
 */
class Return(val type: Type, val rhs: CompileTimeValue) : BranchInstruction(endBlockLabel) {
    override val usedValues: List<CompileTimeValue> = listOf(rhs) 

    override fun substitute(variable: Variable, value: CompileTimeValue): Instruction {
        if (rhs == variable)
            return Return(type, value)
        return this
    }

    override fun simplify(): List<Instruction> = listOf(this)

    override fun coalescingEdges(): List<Pair<String, Either<InRegister, String>>> {
        if (rhs is Variable) {
            return listOf("$rhs" to Either.Left(InRegister("rax")))
        }
        return listOf()
    }

    override fun compileBranch(
            builder: ASMBuilder, 
            variableAssignment: Map<String, StaticAssemblyValue>, 
            destLabel: String
    ) {
        when (type) {
            Type.INT -> {
                compileAssign(builder, InRegister("rax"), rhs.value(variableAssignment)!!)
                builder.appendLine("jmp", destLabel)
            }
            else -> NotImplementedError()
        }
    }

    override fun toString(): String = "return $rhs"
}