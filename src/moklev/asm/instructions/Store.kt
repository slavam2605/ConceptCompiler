package moklev.asm.instructions

import moklev.asm.compiler.LiveRange
import moklev.asm.compiler.SSATransformer
import moklev.asm.interfaces.Instruction
import moklev.asm.interfaces.ReadonlyInstruction
import moklev.asm.utils.*
import moklev.utils.ASMBuilder

/**
 * @author Moklev Vyacheslav
 */
class Store(val lhsAddr: CompileTimeValue, val rhs: CompileTimeValue) : ReadonlyInstruction() {
    override fun toString(): String = "store $lhsAddr, $rhs"
    
    override val usedValues: List<CompileTimeValue> = listOf(lhsAddr, rhs)

    override fun substitute(variable: Variable, value: CompileTimeValue): Instruction {
        return Store(if (lhsAddr == variable) value else lhsAddr, if (rhs == variable) value else rhs)
    }

    override fun simplify(): List<Instruction> = listOf(this)

    override fun coloringPreferences(): List<ColoringPreference> = emptyList()

    override fun compile(
            builder: ASMBuilder,
            blocks: Map<String, SSATransformer.Block>,
            variableAssignment: VariableAssignment,
            currentBlockLabel: String,
            liveRange: Map<String, LiveRange>,
            indexInBlock: Int) {
        val localAssignment = variableAssignment[currentBlockLabel]!!
        val lhsAddr = lhsAddr.value(localAssignment)!!
        val rhs = rhs.value(localAssignment)!!
        
        val tempRegister1 = R15
        val tempRegister2 = R14
        val actualLhsAddr = if (lhsAddr is InStack) tempRegister1 else lhsAddr
        val actualRhs = if (rhs is InStack) tempRegister2 else rhs
        
        compileAssign(builder, actualLhsAddr, lhsAddr)
        compileAssign(builder, actualRhs, rhs)
        
        compileStore(builder, actualLhsAddr, rhs)
    }
}