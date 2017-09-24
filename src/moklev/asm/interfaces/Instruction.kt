package moklev.asm.interfaces

import moklev.asm.compiler.LiveRange
import moklev.asm.compiler.SSATransformer
import moklev.asm.instructions.Phi
import moklev.asm.utils.*
import moklev.asm.utils.ASMBuilder

/**
 * @author Vyacheslav Moklev
 */

/**
 * Interface of every instruction of Concept-ASM three-address code
 */
interface Instruction {
    /**
     * All values used in the right (read-only) part of instruction
     */
    val usedValues: List<CompileTimeValue>

    /**
     * String representation of all values used in the instruction (in fixed order)
     */
    val allValues: List<String>

    /**
     * Substitute {variable := value} in the right (read-only) part of instruction
     *
     * @param variable [Variable] to replace
     * @param value [CompileTimeValue] that should be instead of [variable]
     * @return Usually copy of instruction (but not always) with substitution done
     */
    fun substitute(variable: Variable, value: CompileTimeValue): Instruction

    /**
     * Get simplified version of instruction. It can be transformed (return value is a singleton list),
     * transformed into several simpler instructions (return value is a multi-value list) and
     * optimized out (yielding empty list as a result)
     *
     * @return List of optimized instructions
     */
    fun simplify(): List<Instruction>

    /**
     * Compile an instruction into assembly, using [builder] and information about basic blocks
     */
    fun compile(
            builder: ASMBuilder,
            blocks: Map<String, SSATransformer.Block>,
            variableAssignment: VariableAssignment,
            currentBlockLabel: String,
            liveRange: Map<String, LiveRange>,
            indexInBlock: Int
    )

    /**
     * Define all coloring preferences defined by this instruction
     *
     * @return list of coloring preferences
     */
    fun coloringPreferences(): List<ColoringPreference>
}

/**
 * An instruction that can modify variable. The only variable that can be
 * modified is [lhs]
 */
interface AssignInstruction : Instruction {
    val type: Type
    val lhs: Variable
    
    fun init(state: ASMState) {
        state.setVarType(lhs.name, type)
    }
    
    fun compile(builder: ASMBuilder, variableAssignment: Map<String, StaticAssemblyValue>)

    override fun compile(
            builder: ASMBuilder,
            blocks: Map<String, SSATransformer.Block>,
            variableAssignment: VariableAssignment,
            currentBlockLabel: String, 
            liveRange: Map<String, LiveRange>, 
            indexInBlock: Int
    ) {
        val localAssignment = variableAssignment[currentBlockLabel]!!
        if (localAssignment[lhs.toString()] != null) {
            compile(builder, localAssignment)
        }
    }
}

/**
 * Branch instruction that can change a control flow. [label] is a label of the basic block
 * that can be jumped onto
 */
interface BranchInstruction : Instruction {
    val label: String
    
    fun compileBranch(builder: ASMBuilder, variableAssignment: Map<String, StaticAssemblyValue>, destLabel: String)

    override fun compile(
            builder: ASMBuilder,
            blocks: Map<String, SSATransformer.Block>,
            variableAssignment: VariableAssignment,
            currentBlockLabel: String, liveRange: Map<String, LiveRange>, indexInBlock: Int
    ) {
        val localAssignment = variableAssignment[currentBlockLabel]!!
        val nextBlockAssignment = variableAssignment[label]!!

        val targetBlock = blocks[label]!!
        val jointList = targetBlock.instructions
                .asSequence()
                .filterIsInstance<Phi>()
                .mapNotNull {
                    val first = it.pairs.first { it.first == currentBlockLabel }.second
                            .value(localAssignment) ?: return@mapNotNull null
                    val second = it.lhs.value(nextBlockAssignment) ?: return@mapNotNull null
                    if (first == second) null else first to second
                }
                .toMutableList()
        for ((variable, assignment) in localAssignment) {
            val newAssignment = nextBlockAssignment[variable] ?: continue
            if (assignment != newAssignment) {
                jointList.add(assignment to newAssignment)
            }
        }

        if (jointList.isEmpty()) {
            compileBranch(builder, localAssignment, label)
        } else {
            val tempLabel = StaticUtils.nextLabel()
            val afterLabel = StaticUtils.nextLabel()
            compileBranch(builder, localAssignment, tempLabel)
            builder.appendLine("jmp", afterLabel)
            builder.appendLine("$tempLabel:")

            compileReassignment(builder, jointList)

            builder.appendLine("jmp", label)
            builder.appendLine("$afterLabel:")
        }
    }
}

/**
 * Actually not an instruction, just label to jump on
 */
class Label(val name: String) : Instruction {
    override fun toString() = "$name:"
    override val usedValues = emptyList<CompileTimeValue>()
    override val allValues
        get() = listOf(name)

    override fun substitute(variable: Variable, value: CompileTimeValue): Instruction = this
    override fun simplify() = listOf(this)
    override fun coloringPreferences(): List<ColoringPreference> = emptyList()
    override fun compile(
            builder: ASMBuilder,
            blocks: Map<String, SSATransformer.Block>,
            variableAssignment: VariableAssignment,
            currentBlockLabel: String, liveRange: Map<String, LiveRange>, indexInBlock: Int
    ) {
    }
}

/**
 * Instruction with no arguments and special semantics like `ret` or `leave`
 */
class RawTextInstruction(val name: String) : Instruction {
    override val usedValues: List<CompileTimeValue> = emptyList()

    override val allValues: List<String>
        get() = listOf(name)

    override fun substitute(variable: Variable, value: CompileTimeValue): Instruction = this

    override fun simplify(): List<Instruction> = listOf(this)

    override fun coloringPreferences(): List<ColoringPreference> = emptyList()

    override fun compile(
            builder: ASMBuilder,
            blocks: Map<String, SSATransformer.Block>,
            variableAssignment: VariableAssignment,
            currentBlockLabel: String,
            liveRange: Map<String, LiveRange>,
            indexInBlock: Int
    ) {
        builder.appendLine(name)
    }

    override fun toString(): String = name
}

/**
 * Marker interface for unconditional branch instructions
 */
interface UnconditionalBranchInstruction

/**
 * Instruction which works with memory (like store/load operations)
 */
interface MemoryInstruction {
    /**
     * Values that used not for memory operations
     */
    val notMemoryUsed: List<CompileTimeValue>
}