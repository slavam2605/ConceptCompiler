package moklev.asm.instructions

import moklev.asm.interfaces.Instruction
import moklev.asm.utils.*

/**
 * @author Moklev Vyacheslav
 */
class Coerce(override val type: Type, lhs: Variable, rhs1: CompileTimeValue) : UnaryInstruction(lhs, rhs1) {
    override fun toString() = "$lhs = $rhs1 as $type"
    
    override fun substitute(variable: Variable, value: CompileTimeValue): Instruction {
        return Coerce(type, lhs, if (rhs1 == variable) value else rhs1)
    }

    override fun simplify(): List<Instruction> = listOf(this)

    override fun coloringPreferences(): List<ColoringPreference> = listOf()

    override fun compile(builder: ASMBuilder, variableAssignment: Map<String, StaticAssemblyValue>) {
        // TODO [NOT_CORRECT] should perform actual transform
        val firstOperand = lhs.value(variableAssignment)!!
        val secondOperand = rhs1.value(variableAssignment)!!

        compileAssign(builder, firstOperand, secondOperand)
    }
}