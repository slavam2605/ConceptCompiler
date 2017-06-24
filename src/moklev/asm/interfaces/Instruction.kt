package moklev.asm.interfaces

import moklev.asm.compiler.IntArgumentsAssignment
import moklev.asm.compiler.LiveRange
import moklev.asm.compiler.SSATransformer
import moklev.asm.instructions.Assign
import moklev.asm.utils.*
import moklev.utils.ASMBuilder

/**
 * @author Vyacheslav Moklev
 */

/**
 * Super class of every instruction of Concept-ASM three-address code
 */
sealed class Instruction {
    /**
     * All values used in the right (read-only) part of instruction
     */
    abstract val usedValues: List<CompileTimeValue>

    /**
     * Substitute {variable := value} in the right (read-only) part of instruction
     *
     * @param variable [Variable] to replace
     * @param value [CompileTimeValue] that should be instead of [variable]
     * @return Usually copy of instruction (but not always) with substitution done
     */
    abstract fun substitute(variable: Variable, value: CompileTimeValue): Instruction

    /**
     * Get simplified version of instruction. It can be transformed (return value is a singleton list),
     * transformed into several simpler instructions (return value is a multi-value list) and
     * optimized out (yielding empty list as a result)
     *
     * @return List of optimized instructions
     */
    abstract fun simplify(): List<Instruction>

    /**
     * Compile an instruction into assembly, using [builder] and information about basic blocks
     */
    abstract fun compile(
            builder: ASMBuilder,
            blocks: Map<String, SSATransformer.Block>,
            variableAssignment: VariableAssignment,
            currentBlockLabel: String,
            liveRange: Map<String, LiveRange>,
            indexInBlock: Int
    )
}

/**
 * An instruction that can modify variable. The only variable that can be
 * modified is [lhs]
 */
abstract class AssignInstruction(val lhs: Variable) : Instruction() {
    abstract fun compile(builder: ASMBuilder, variableAssignment: Map<String, StaticAssemblyValue>)

    override fun compile(
            builder: ASMBuilder,
            blocks: Map<String, SSATransformer.Block>,
            variableAssignment: VariableAssignment,
            currentBlockLabel: String, liveRange: Map<String, LiveRange>, indexInBlock: Int
    ) {
        val localAssignment = variableAssignment[currentBlockLabel]!!
        if (localAssignment[lhs.toString()] != null) {
            compile(builder, localAssignment)
        }
    }
}

/**
 * Binary instruction of form `v1 = v2 <op> v3`
 */
abstract class BinaryInstruction(lhs: Variable, val rhs1: CompileTimeValue, val rhs2: CompileTimeValue) : AssignInstruction(lhs) {
    override val usedValues = listOf(rhs1, rhs2)

    fun defaultCompile(builder: ASMBuilder, variableAssignment: Map<String, StaticAssemblyValue>, op: String) {
        val lhsValue = lhs.value(variableAssignment)!!
        val rhsValue = rhs1.value(variableAssignment)!!
        if (lhsValue != rhsValue) {
            compileAssign(builder, lhsValue, rhsValue)
        }
        val target = lhsValue
        val secondOperand = rhs2.value(variableAssignment)
        if (target is InStack && secondOperand is InStack) {
            // TODO get temp register
            val tempRegister = "r15"
            builder.appendLine("mov", tempRegister, "$target")
            builder.appendLine(op, tempRegister, "$secondOperand")
            builder.appendLine("mov", "$target", tempRegister)
        } else if (target is InStack && secondOperand !is InRegister) {
            // TODO must be sized
            builder.appendLine(op, "qword $target", "$secondOperand")
        } else {
            builder.appendLine(op, "$target", "$secondOperand")
        }
    }
}

/**
 * Unary instruction of form `v1 = <op> v2`
 */
abstract class UnaryInstruction(lhs: Variable, val rhs1: CompileTimeValue) : AssignInstruction(lhs) {
    override val usedValues = listOf(rhs1)
}

/**
 * Branch instruction that can change a control flow. [label] is a label of the basic block
 * that can be jumped onto
 */
abstract class BranchInstruction(val label: String) : Instruction() {
    abstract fun compileBranch(builder: ASMBuilder, variableAssignment: Map<String, StaticAssemblyValue>, destLabel: String)

    override fun compile(
            builder: ASMBuilder,
            blocks: Map<String, SSATransformer.Block>,
            variableAssignment: VariableAssignment,
            currentBlockLabel: String, liveRange: Map<String, LiveRange>, indexInBlock: Int
    ) {
        // TODO properly handle reassignment between blocks
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
 * Base class for instructions that does not directly modify their arguments
 */
abstract class ReadonlyInstruction : Instruction()

/**
 * Call of function (subroutine)
 */
class Call(val funcName: String, val args: List<Pair<Type, CompileTimeValue>>) : Instruction() {
    private val callerToSave = listOf(
            "rax", "rcx", "rdx",
            "rdi", "rsi", "rsp",
            "r8", "r9", "r10", "r11"
    ).map { InRegister(it) }

    override fun toString() = "call $funcName(${args.joinToString()})"

    override val usedValues = args.map { it.second }

    override fun substitute(variable: Variable, value: CompileTimeValue): Instruction {
        val newArgs = args.map { it.first to if (it.second == variable) value else it.second }
        return Call(funcName, newArgs)
    }

    override fun simplify() = listOf(this)

    override fun compile(
            builder: ASMBuilder,
            blocks: Map<String, SSATransformer.Block>,
            variableAssignment: VariableAssignment,
            currentBlockLabel: String, 
            liveRange: Map<String, LiveRange>, 
            indexInBlock: Int
    ) {
        val localAssignment = variableAssignment[currentBlockLabel]!!
        val registersToSave = liveRange
                .asSequence()
                .filter { indexInBlock >= it.value.firstIndex && indexInBlock < it.value.lastIndex }
                .map { localAssignment[it.key]!! }
                .filterIsInstance<InRegister>()
                .filter { it in callerToSave }
                .toList()
        
        for (register in registersToSave) {
            compilePush(builder, register)
        }
        
        val intArguments = args
                .asSequence()
                .filter { it.first == Type.INT }
                .map { it.second }
        
        intArguments.forEachIndexed { i, arg ->
            compileAssign(builder, IntArgumentsAssignment[i], arg.value(localAssignment)!!)
        }
        
        // TODO align stack to 16 bytes
        
        builder.appendLine("call", funcName)
        
        for (register in registersToSave.asReversed()) {
            compilePop(builder, register)
        }
    }
}

/**
 * Actually not an instruction, just label to jump on
 */
class Label(val name: String) : Instruction() {
    override fun toString() = "$name:"
    override val usedValues = emptyList<CompileTimeValue>()
    override fun substitute(variable: Variable, value: CompileTimeValue): Instruction = this
    override fun simplify() = listOf(this)
    override fun compile(
            builder: ASMBuilder,
            blocks: Map<String, SSATransformer.Block>,
            variableAssignment: VariableAssignment,
            currentBlockLabel: String, liveRange: Map<String, LiveRange>, indexInBlock: Int
    ) {
    }
}

/**
 * Phi node of SSA graph. Controls rules of merging variable value from
 * multiple incoming blocks
 */
class Phi(lhs: Variable, val pairs: List<Pair<String, CompileTimeValue>>) : AssignInstruction(lhs) {
    override fun toString(): String {
        return "$lhs = phi ${pairs.joinToString { "[${it.first}, ${it.second}]" }}"
    }

    override val usedValues: List<CompileTimeValue>
        get() {
            val list = mutableListOf<CompileTimeValue>()
            for ((_, rhs) in pairs) {
                list.add(rhs)
            }
            return list
        }

    override fun substitute(variable: Variable, value: CompileTimeValue): Instruction {
        return Phi(lhs, pairs.map {
            if (it.second == variable) it.first to value else it
        })
    }

    override fun simplify(): List<Instruction> {
        if (pairs.isEmpty())
            return emptyList()
        if (pairs.size == 1)
            return listOf(Assign(lhs, pairs[0].second))
        if (pairs.all { it.second == pairs[0].second })
            return listOf(Assign(lhs, pairs[0].second))
        return listOf(this)
    }

    override fun compile(builder: ASMBuilder, variableAssignment: Map<String, StaticAssemblyValue>) {}
}

/**
 * Internal instruction that marks [lhs] as externally initialized value
 */
class ExternalAssign(lhs: Variable) : AssignInstruction(lhs) {
    override val usedValues: List<CompileTimeValue> = emptyList()

    override fun substitute(variable: Variable, value: CompileTimeValue): Instruction = this
    
    override fun simplify(): List<Instruction> = listOf(this)

    override fun compile(builder: ASMBuilder, variableAssignment: Map<String, StaticAssemblyValue>) {}

    override fun toString(): String = "$lhs = [externally assigned]"
}

/**
 * Instruction with no arguments and special semantics like `ret` or `leave`
 */
class NoArgumentsInstruction(val name: String) : Instruction() {
    override val usedValues: List<CompileTimeValue> = listOf()

    override fun substitute(variable: Variable, value: CompileTimeValue): Instruction = this

    override fun simplify(): List<Instruction> = listOf(this)

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