import moklev.asm.compiler.ConceptASMCompiler
import moklev.asm.compiler.RegisterAllocation
import moklev.asm.compiler.SSATransformer
import moklev.asm.instructions.Add
import moklev.asm.instructions.Assign
import moklev.asm.instructions.IfGreaterJump
import moklev.asm.instructions.Jump
import moklev.asm.interfaces.Call
import moklev.asm.interfaces.Label
import moklev.asm.utils.ASMFunction
import moklev.asm.utils.IntConst
import moklev.asm.utils.Variable
import moklev.utils.ASMBuilder

/**
 * @author Moklev Vyacheslav
 */
fun main(args: Array<String>) {
    // L0: x = 10
    //     x = x + 1
    //     y = 10 + x
    //     jump (x > y) L1
    //     jump L2
    // L1: x = 42
    //     jump L3
    // L2: x = 69
    //     jump L3
    // L3: call f(x)
    val code = listOf(
            Label("L0"),
            Assign(Variable("x"), IntConst(1)),
//            Assign(Variable("t1"), IntConst(1)),
//            Assign(Variable("t2"), IntConst(1)),
//            Assign(Variable("t3"), IntConst(1)),
//            Assign(Variable("t4"), IntConst(1)),
//            Assign(Variable("t5"), IntConst(1)),
//            Assign(Variable("t6"), IntConst(1)),
//            Add(Variable("a"), Variable("t1"), Variable("t2")),
//            Add(Variable("a"), Variable("a"), Variable("t3")),
//            Add(Variable("a"), Variable("a"), Variable("t4")),
//            Add(Variable("a"), Variable("a"), Variable("t5")),
//            Add(Variable("a"), Variable("a"), Variable("t6")),
            Add(Variable("x"), Variable("x"), IntConst(1)),
            Add(Variable("y"), IntConst(10), Variable("x")),
            IfGreaterJump(Variable("x"), Variable("y"), "L1"),
            Jump("L2"),
            Label("L1"),
            Assign(Variable("x"), IntConst(42)),
            Jump("L3"),
            Label("L2"),
            Assign(Variable("x"), IntConst(69)),
            Jump("L3"),
            Label("L3"),    
            Call("f", listOf(Variable("x")))
    )

//    println(ConceptASMCompiler.compile(ASMFunction("bar", emptyList(), code)))
//    
//    
    
    for (instruction in code) {
        println(instruction)
    }

    println("\n--------\n")

    val blocks = /*SSATransformer.performOptimizations*/(
            SSATransformer.transform(code)
    )

    for (block in blocks) {
        println("${block.label}:")
        println(block.instructions.joinToString(separator = "\n"))
        println()
    }

    val liveRanges = RegisterAllocation.detectLiveRange(blocks)
    println(liveRanges)
    val graph = RegisterAllocation.buildConflictGraph(liveRanges)
    println(graph)
    val variableAssignment = RegisterAllocation.colorGraph(
            setOf("rax", "rbx", "rcx", "rdx"),
            emptyMap(),
            graph
    )
    println(variableAssignment)
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
    println(builder.build())
}

