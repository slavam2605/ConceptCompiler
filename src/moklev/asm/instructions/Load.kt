package moklev.asm.instructions

import moklev.asm.interfaces.AssignInstruction
import moklev.asm.interfaces.Instruction
import moklev.asm.interfaces.MemoryInstruction
import moklev.asm.utils.*
import moklev.utils.ASMBuilder

/**
 * @author Moklev Vyacheslav
 */
class Load(lhs: Variable, val rhsAddr: CompileTimeValue) : AssignInstruction(lhs), MemoryInstruction {
    override fun toString(): String = "$lhs = load $rhsAddr"
    
    override val usedValues: List<CompileTimeValue> = listOf(rhsAddr)

    override val allValues: List<CompileTimeValue> = listOf(lhs, rhsAddr)

    override val notMemoryUsed: List<CompileTimeValue> = listOf()

    override fun substitute(variable: Variable, value: CompileTimeValue): Instruction {
        if (rhsAddr == variable)
            return Load(lhs, value)
        return this
    }

    override fun simplify(): List<Instruction> = listOf(this)

    override fun coloringPreferences(): List<ColoringPreference> {
        if (rhsAddr is StackAddrVariable) {
            return listOf(Predefined("$lhs", InStack(rhsAddr.offset, rhsAddr.size)))
        }
        return emptyList()
    }

    override fun compile(builder: ASMBuilder, variableAssignment: Map<String, StaticAssemblyValue>) {
        val lhs = lhs.value(variableAssignment)!!
        val rhsAddr = rhsAddr.value(variableAssignment)!!
 
        // TODO I think it is correct due to predefined coloring
        if (rhsAddr is StackAddrVariable)
            return
        
        // TODO sizeof here
        
        val tempRegister1 = R15
        val tempRegister2 = R14
        val actualLhs = if (lhs is InStack) tempRegister1 else lhs
        val actualRhsAddr = if (rhsAddr is InStack) tempRegister2 else rhsAddr
        
        compileAssign(builder, actualRhsAddr, rhsAddr)
        
        if (actualRhsAddr is StackAddrVariable) {
            builder.appendLine("mov", actualLhs, "qword $actualRhsAddr")
        } else {
            builder.appendLine("mov", actualLhs, "qword [$actualRhsAddr]")   
        }
        
        compileAssign(builder, lhs, actualLhs)
    }
}