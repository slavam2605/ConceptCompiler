package moklev.asm.compiler

import moklev.asm.utils.ASMFunction
import moklev.utils.ASMBuilder

/**
 * @author Moklev Vyacheslav
 */
object ConceptASMCompiler {
    fun <A: Appendable> compileTo(dest: A, function: ASMFunction): A {
        dest.appendLine("global ${function.name}")
        dest.appendLine("${function.name}:")
        // TODO allocate arguments
        val blocks = /*SSATransformer.performOptimizations*/(
                SSATransformer.transform(function.instructions)
        )
        val liveRanges = RegisterAllocation.detectLiveRange(blocks)
        val conflictGraph = RegisterAllocation.buildConflictGraph(liveRanges)
        val variableAssignment = RegisterAllocation.colorGraph(
                setOf("rax", "rcx"), // TODO normal registers
                emptyMap(),          // TODO initial coloring
                conflictGraph
        )
        val builder = ASMBuilder()
        for (i in 0..blocks.size - 1) {
            val block = blocks[i]
            val nextBlockLabel = blocks.getOrNull(i + 1)?.label
            block.compile(
                    builder,
                    blocks
                            .asSequence()
                            .map { it.label to it }
                            .toMap(),
                    variableAssignment,
                    nextBlockLabel
            )
        }
        dest.append(builder.build())
        return dest
    }
    
    fun compile(function: ASMFunction): String {
        return compileTo(StringBuilder(), function).toString()
    }
    
    fun <A: Appendable> A.appendLine(x: Any?): Appendable {
        return append(x.toString()).append('\n')
    }
}