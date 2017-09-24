package moklev.asm.instructions

import moklev.asm.interfaces.BranchInstruction
import moklev.asm.interfaces.Instruction
import moklev.asm.utils.*
import moklev.asm.utils.ASMBuilder

/**
 * @author Vyacheslav Moklev
 */
class BinaryCompareJump(val op: String, val rhs1: CompileTimeValue, val rhs2: CompileTimeValue, override val label: String) : BranchInstruction {
    companion object {
        val instruction = hashMapOf(// TODO maybe change instructions
                "==" to "je",
                "!=" to "jne",
                ">" to "jg",
                ">=" to "jge",
                "<" to "jl",
                "<=" to "jle"
        )

        val intComparator = hashMapOf<String, (Long, Long) -> Boolean>(
                "==" to { a, b -> a == b },
                "!=" to { a, b -> a != b },
                ">" to { a, b -> a > b },
                ">=" to { a, b -> a >= b },
                "<" to { a, b -> a < b },
                "<=" to { a, b -> a <= b }
        )
    }

    init {
        if (op !in instruction)
            error("$op is not valid operator")
    }

    override fun toString() = "if ($rhs1 $op $rhs2) goto $label"
    override val usedValues = listOf(rhs1, rhs2)
    override val allValues
        get() = listOf(op, rhs1.toString(), rhs2.toString(), label)

    override fun substitute(variable: Variable, value: CompileTimeValue): Instruction {
        return BinaryCompareJump(op, if (rhs1 == variable) value else rhs1, if (rhs2 == variable) value else rhs2, label)
    }

    override fun simplify(): List<Instruction> {
        if (rhs1 is Int64Const && rhs2 is Int64Const) {
            val comparator = intComparator[op]!!
            if (comparator.invoke(rhs1.value, rhs2.value))
                return listOf(Jump(label))
            else
                return emptyList()
        }
        return listOf(this)
    }

    override fun coloringPreferences(): List<ColoringPreference> = emptyList()

    override fun compileBranch(builder: ASMBuilder, variableAssignment: Map<String, StaticAssemblyValue>, destLabel: String) {
        val rhs1Value = rhs1.value(variableAssignment)!!
        val rhs2Value = rhs2.value(variableAssignment)!!

        compileCompare(builder, rhs1Value, rhs2Value)
        builder.appendLine(instruction[op]!!, destLabel)
    }
}