package moklev.asm.utils

import moklev.utils.ASMBuilder
import java.util.*

/**
 * @author Vyacheslav Moklev
 */

fun compileAssign(builder: ASMBuilder, lhs: StaticAssemblyValue, rhs: StaticAssemblyValue) {
    if (lhs != rhs) {
        if (lhs is InStack && rhs is InStack) {
            // TODO get temp register
            val tempRegister = "r15"
            builder.appendLine("mov", tempRegister, "$rhs")
            builder.appendLine("mov", "$lhs", tempRegister)
        } else {
            builder.appendLine("mov", "$lhs", "$rhs")
        }
    }
}

fun compileReassignment(builder: ASMBuilder, assignList: List<Pair<StaticAssemblyValue, StaticAssemblyValue>>) {
    // Each memory location should be assigned only once, so in `backAssignGraph` 
    // for every node deg_out = 1, so map `StaticAssemblyValue → StaticAssemblyValue` is enough
    val backAssignGraph = HashMap<StaticAssemblyValue, StaticAssemblyValue>()
    val inputDegree = HashMap<StaticAssemblyValue, Int>()
    val noInputDegreeList = ArrayDeque<StaticAssemblyValue>()
    for ((src, dest) in assignList) {
        if (src == dest)
            continue
        backAssignGraph[dest] = src
        inputDegree.compute(src) { _, v -> (v ?: 0) + 1 }
        inputDegree.compute(dest) { _, v -> v ?: 0 }
    }
    inputDegree
            .filter { (_, deg) -> deg == 0 }
            .mapTo(noInputDegreeList) { it.key }
    
    while (noInputDegreeList.isNotEmpty()) {
        val dest = noInputDegreeList.pollFirst()
        val src = backAssignGraph[dest] ?: continue
        compileAssign(builder, dest, src)
        backAssignGraph.remove(dest)
        val newDegree = inputDegree.compute(src) { _, v -> v!! - 1 }!!
        if (newDegree == 0) {
            noInputDegreeList.addLast(src)
        }
    }
    
    val assigned = HashSet<StaticAssemblyValue>()
    for (dest in backAssignGraph.keys) {
        if (dest !in assigned) {
            // TODO properly choose temp register
            val tempRegister = InRegister("r14")
            compileAssign(builder, tempRegister, dest)
            var currentDest = dest
            while (true) {
                assigned.add(currentDest)
                val src = backAssignGraph[currentDest]!!
                if (src == dest) {
                    compileAssign(builder, currentDest, tempRegister)
                    break
                }
                compileAssign(builder, currentDest, src)
                currentDest = src
            }
        }
    }
    
}