package moklev.asm.instructions

import moklev.asm.interfaces.BranchInstruction
import moklev.asm.interfaces.Instruction
import moklev.asm.utils.*
import moklev.utils.ASMBuilder

/**
 * @author Moklev Vyacheslav
 */
// TODO maybe replace label
class Return(val type: Type, val rhs: CompileTimeValue) : BranchInstruction("func_end") {
    override val usedValues: List<CompileTimeValue> = listOf(rhs) 

    override fun substitute(variable: Variable, value: CompileTimeValue): Instruction {
        if (rhs == variable)
            return Return(type, value)
        return this
    }

    override fun simplify(): List<Instruction> = listOf(this)

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