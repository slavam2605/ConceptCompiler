package moklev.asm.instructions

import moklev.asm.compiler.endBlockLabel
import moklev.asm.interfaces.BranchInstruction
import moklev.asm.interfaces.Instruction
import moklev.asm.interfaces.UnconditionalBranchInstruction
import moklev.asm.utils.*
import moklev.utils.ASMBuilder

/**
 * @author Moklev Vyacheslav
 */
class Return(val type: Type, val rhs: CompileTimeValue) : BranchInstruction(endBlockLabel), UnconditionalBranchInstruction {
    override val usedValues: List<CompileTimeValue> = listOf(rhs)

    override val allValues: List<String>
        get() = listOf(rhs.toString())

    override fun substitute(variable: Variable, value: CompileTimeValue): Instruction {
        if (rhs == variable)
            return Return(type, value)
        return this
    }

    override fun simplify(): List<Instruction> = listOf(this)

    override fun coloringPreferences(): List<ColoringPreference> {
        if (rhs is Variable) {
            return listOf(
                    Target("$rhs", RAX)
            )
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
                compileAssign(builder, RAX, rhs.value(variableAssignment)!!)
                builder.appendLine("jmp", destLabel)
            }
            else -> NotImplementedError()
        }
    }

    override fun toString(): String = "return $rhs"
}