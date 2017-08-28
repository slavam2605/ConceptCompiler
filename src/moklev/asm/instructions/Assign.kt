package moklev.asm.instructions

import moklev.asm.interfaces.Instruction
import moklev.asm.utils.*
import moklev.utils.ASMBuilder

/**
 * @author Vyacheslav Moklev
 */
class Assign(lhs: Variable, rhs1: CompileTimeValue) : UnaryInstruction(lhs, rhs1) {
    override fun toString() = "$lhs = $rhs1"
    
    override fun substitute(variable: Variable, value: CompileTimeValue): Instruction {
        return Assign(lhs, if (rhs1 == variable) value else rhs1)
    }

    override fun simplify() = listOf(this)

    override fun coloringPreferences(): List<ColoringPreference> {
        if (rhs1 is InRegister) 
            return listOf(Predefined("$lhs", rhs1))
        return listOf(Coalesce("$lhs", "$rhs1"))
    }

    override fun compile(builder: ASMBuilder, variableAssignment: Map<String, StaticAssemblyValue>) {
        val firstOperand = lhs.value(variableAssignment)!!
        val secondOperand = rhs1.value(variableAssignment)!!
        compileAssign(builder, firstOperand, secondOperand)
    }
}