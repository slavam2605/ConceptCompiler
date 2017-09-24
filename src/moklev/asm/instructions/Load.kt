package moklev.asm.instructions

import moklev.asm.interfaces.AssignInstruction
import moklev.asm.interfaces.Instruction
import moklev.asm.interfaces.MemoryInstruction
import moklev.asm.utils.*
import moklev.asm.utils.ASMBuilder

/**
 * @author Moklev Vyacheslav
 */
class Load(override val type: Type, override val lhs: Variable, val rhsAddr: CompileTimeValue) : AssignInstruction, MemoryInstruction {
    override fun toString(): String = "$lhs = load $rhsAddr"

    override val usedValues: List<CompileTimeValue> = listOf(rhsAddr)

    override val allValues: List<String>
        get() = listOf(lhs.toString(), rhsAddr.toString())

    override val notMemoryUsed: List<CompileTimeValue> = listOf()

    override fun substitute(variable: Variable, value: CompileTimeValue): Instruction {
        if (rhsAddr == variable)
            return Load(type, lhs, value)
        return this
    }

    override fun simplify(): List<Instruction> = listOf(this)

    override fun coloringPreferences(): List<ColoringPreference> {
        if (rhsAddr is StackAddrVariable) {
            return listOf(Predefined("$lhs", InStack(rhsAddr.offset, rhsAddr.type.dereference())))
        }
        return emptyList()
    }

    override fun compile(builder: ASMBuilder, variableAssignment: Map<String, StaticAssemblyValue>) {
        // TODO [NOT_CORRECT] not correct for short ints like int32, int16, ...
        val lhs = lhs.value(variableAssignment)!!
        val rhsAddr = rhsAddr.value(variableAssignment)!!

        // TODO I think it is correct due to predefined coloring
        if (rhsAddr is StackAddrVariable)
            return

        // TODO sizeof here

        val tempRegister1 = R15(Type.Int64)
        val tempRegister2 = R14(Type.Int64)
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