package moklev.asm.compiler

import moklev.asm.compiler.RuntimeObject.StackObject
import moklev.asm.compiler.RuntimeObject.VariableObject
import moklev.asm.instructions.Assign
import moklev.asm.instructions.Load
import moklev.asm.instructions.Store
import moklev.asm.interfaces.AssignInstruction
import moklev.asm.utils.Variable
import moklev.asm.utils.StackAddrVariable
import java.util.*

/**
 * @author Moklev Vyacheslav
 */
typealias ObjectGraph = MutableMap<RuntimeObject, MutableSet<RuntimeObject>>

fun performPointerAnalysis(blocks: List<SSATransformer.Block>): PointsToInfo {
    val pointsTo: ObjectGraph = hashMapOf()
    val subsetGraph: ObjectGraph = hashMapOf()
    val complexConstraintIn: ObjectGraph = hashMapOf() // *v < q
    val complexConstraintOut: ObjectGraph = hashMapOf() // *v > q
    
    fun initNode(obj: RuntimeObject) {
        pointsTo[obj] = hashSetOf()
        subsetGraph[obj] = hashSetOf()
        complexConstraintIn[obj] = hashSetOf()
        complexConstraintOut[obj] = hashSetOf()
    }
    
    blocks.asSequence()
            .flatMap { it.instructions.asSequence() }
            .filterIsInstance<AssignInstruction>()
            .map { it.lhs.toString() }
            .forEach {
                initNode(VariableObject(it))
            }
    blocks.forEach { block ->
        block.instructions.forEach { instruction ->
            when {
                instruction is Assign && instruction.rhs1 is StackAddrVariable -> {
                    val node = VariableObject(instruction.lhs.toString())
                    val obj = StackObject(instruction.rhs1)
                    initNode(obj)
                    pointsTo[node]!!.add(obj)
                }
                instruction is Assign && instruction.rhs1 is Variable -> {
                    val targetNode = VariableObject(instruction.lhs.toString())
                    val sourceNode = VariableObject(instruction.rhs1.toString())
                    subsetGraph[sourceNode]!!.add(targetNode)
                }
                instruction is Load -> {
                    val sourceNode = VariableObject(instruction.rhsAddr.toString())
                    val targetNode = VariableObject(instruction.lhs.toString())
                    complexConstraintIn[sourceNode]!!.add(targetNode)
                }
                instruction is Store && instruction.rhs is Variable -> {
                    val sourceNode = VariableObject(instruction.lhsAddr.toString())
                    val targetNode = VariableObject(instruction.rhs.toString())
                    complexConstraintOut[sourceNode]!!.add(targetNode)
                }
            }
        }
    }
    
    val queue = ArrayDeque<RuntimeObject>()
    val inQueue = hashSetOf<RuntimeObject>()
    pointsTo.forEach { node, set ->  
        if (set.isNotEmpty()) {
            queue.addLast(node)
            inQueue.add(node)
        }
    }
    while (queue.isNotEmpty()) {
        val v = queue.pollFirst()
        val nodePointsTo = pointsTo[v]!!
        nodePointsTo.forEach { a ->
            complexConstraintIn[v]!!.forEach { p ->
                if (subsetGraph[a]!!.add(p) && a !in inQueue) {
                    queue.addLast(a)
                    inQueue.add(a)
                }
            }
            complexConstraintOut[v]!!.forEach { q ->
                if (subsetGraph[q]!!.add(a) && q !in inQueue) {
                    queue.addLast(q)
                    inQueue.add(q)
                }
            }
        }
        subsetGraph[v]!!.forEach { q ->
            if (pointsTo[q]!!.addAll(pointsTo[v]!!) && q !in inQueue) {
                queue.addLast(q)
                inQueue.add(q)
            }
        }
    }
    return PointsToInfo(pointsTo)
}

data class PointsToInfo(val pointsTo: Map<RuntimeObject, Set<RuntimeObject>>)

sealed class RuntimeObject {
    data class VariableObject(val name: String) : RuntimeObject()
    data class StackObject(val addr: StackAddrVariable) : RuntimeObject()
    object HeapObject : RuntimeObject()
}