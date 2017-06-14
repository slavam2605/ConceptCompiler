package moklev.asm.instructions

import moklev.asm.interfaces.BranchInstruction
import moklev.asm.interfaces.Instruction
import moklev.asm.utils.CompileTimeValue
import moklev.asm.utils.IntConst
import moklev.asm.utils.Variable

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
}