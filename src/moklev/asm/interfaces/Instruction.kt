package moklev.asm.interfaces

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
            currentBlockLabel: String
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
            currentBlockLabel: String
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
        if (lhs.value(variableAssignment).toString() != rhs1.value(variableAssignment).toString()) {
            Assign(lhs, rhs1).compile(builder, variableAssignment)
        }
        val target = lhs.value(variableAssignment)
        val secondOperand = rhs2.value(variableAssignment)
        if (target is InStack && secondOperand is InStack) {
            // TODO get temp register
            val tempRegister = "r15"
            builder.appendLine("mov", tempRegister, "$target")
            builder.appendLine(op, tempRegister, "$secondOperand")
            builder.appendLine("mov", "$target", tempRegister)
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
            currentBlockLabel: String
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

class Call(val funcName: String, val args: List<CompileTimeValue>) : Instruction() {
    override fun toString() = "call $funcName(${args.joinToString()})"
    override val usedValues = args
    override fun substitute(variable: Variable, value: CompileTimeValue): Instruction {
        val newArgs = args.map { if (it == variable) value else it }
        return Call(funcName, newArgs)
    }

    override fun simplify() = listOf(this)

    override fun compile(
            builder: ASMBuilder,
            blocks: Map<String, SSATransformer.Block>,
            variableAssignment: VariableAssignment,
            currentBlockLabel: String
    ) {
        // TODO implement
        builder.appendLine("not_implemented::call${args.map { 
            if (it is Variable) 
                variableAssignment[currentBlockLabel]!![it.toString()].toString()
            else
                it.toString()
        }}")
    }
}

class Label(val name: String) : Instruction() {
    override fun toString() = "$name:"
    override val usedValues = emptyList<CompileTimeValue>()
    override fun substitute(variable: Variable, value: CompileTimeValue): Instruction = this
    override fun simplify() = listOf(this)
    override fun compile(
            builder: ASMBuilder,
            blocks: Map<String, SSATransformer.Block>,
            variableAssignment: VariableAssignment,
            currentBlockLabel: String
    ) {}
}

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
        return listOf(this)
    }

    override fun compile(builder: ASMBuilder, variableAssignment: Map<String, StaticAssemblyValue>) {}
}
