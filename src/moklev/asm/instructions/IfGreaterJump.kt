package moklev.asm.instructions

import moklev.asm.interfaces.BranchInstruction
import moklev.asm.interfaces.Instruction
import moklev.asm.utils.*
import moklev.utils.ASMBuilder

/**
 * @author Vyacheslav Moklev
 */
class IfGreaterJump(val rhs1: CompileTimeValue, val rhs2: CompileTimeValue, label: String) : BranchInstruction(label) {
    override fun toString() = "if ($rhs1 > $rhs2) goto $label"
    override val usedValues = listOf(rhs1, rhs2)
    
    override fun substitute(variable: Variable, value: CompileTimeValue): Instruction {
        return IfGreaterJump(if (rhs1 == variable) value else rhs1, if (rhs2 == variable) value else rhs2, label)    
    }

    override fun simplify(): List<Instruction> {
        if (rhs1 is IntConst && rhs2 is IntConst) {
            if (rhs1.value > rhs2.value)
                return listOf(Jump(label))
            else
                return emptyList()
        }
        return listOf(this)
    }

    override fun compileBranch(builder: ASMBuilder, variableAssignment: Map<String, StaticAssemblyValue>, destLabel: String) {
        val firstOperand = rhs1.value(variableAssignment)
        val secondOperand = rhs2.value(variableAssignment)
        if (firstOperand is InStack && secondOperand is InStack) {
            // TODO get temp register
            val tempRegister = "r15"
            // TODO replace to compileAssign
            builder.appendLine("mov", tempRegister,"$secondOperand")
            builder.appendLine("cmp", "$firstOperand" , tempRegister)
        } else if (firstOperand is InStack && secondOperand !is InRegister) {
            builder.appendLine("cmp", "qword $firstOperand" ,"$secondOperand")            
        } else {
            builder.appendLine("cmp", "$firstOperand" ,"$secondOperand")
        }
        builder.appendLine("jg", destLabel)
    }
}