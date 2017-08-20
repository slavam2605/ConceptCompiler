package moklev.asm.instructions

import moklev.asm.interfaces.AssignInstruction
import moklev.asm.interfaces.Instruction
import moklev.asm.utils.*
import moklev.utils.ASMBuilder

/**
 * @author Moklev Vyacheslav
 */
class Load(lhs: Variable, val rhs: CompileTimeValue) : AssignInstruction(lhs) {
    override fun toString(): String = "$lhs = load $rhs"
    
    override val usedValues: List<CompileTimeValue> = listOf(rhs)

    override fun substitute(variable: Variable, value: CompileTimeValue): Instruction {
        if (rhs == variable)
            return Load(lhs, value)
        return this
    }

    override fun simplify(): List<Instruction> = listOf(this)

    override fun coloringPreferences(): List<ColoringPreference> = emptyList()

    override fun compile(builder: ASMBuilder, variableAssignment: Map<String, StaticAssemblyValue>) {
        val lhs = lhs.value(variableAssignment)!!
        val rhs = rhs.value(variableAssignment)!!
        
        // TODO sizeof here
        
        val tempRegister1 = R15
        val tempRegister2 = R14
        val actualLhs = if (lhs is InStack) tempRegister1 else lhs
        val actualRhs = if (rhs is InStack) tempRegister2 else rhs
        
        compileAssign(builder, actualRhs, rhs)
        
        if (actualRhs is X86AddrConst) {
            builder.appendLine("mov", actualLhs, "qword $actualRhs")
        } else {
            builder.appendLine("mov", actualLhs, "qword [$actualRhs]")   
        }
        
        compileAssign(builder, lhs, actualLhs)
    }
}