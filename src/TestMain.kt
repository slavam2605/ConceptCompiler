import moklev.asm.compiler.compile
import moklev.asm.instructions.*
import moklev.asm.interfaces.Call
import moklev.asm.interfaces.Label
import moklev.asm.utils.ASMFunction
import moklev.asm.utils.IntConst
import moklev.asm.utils.Type
import moklev.asm.utils.Variable
import java.io.PrintWriter

/**
 * @author Moklev Vyacheslav
 */
fun main(args: Array<String>) {
    val code = listOf(
            Label("L0"),
            Add(Variable("n"), Variable("n"), IntConst(1)),
            Assign(Variable("acc"), IntConst(0)),
            Assign(Variable("i"), IntConst(1)),
            Jump("L1"),
            Label("L1"),
            IfGreaterJump(Variable("n"), Variable("i"), "L2"),
            Jump("L3"),
            Label("L2"),
            Add(Variable("acc"), Variable("acc"), Variable("i")),
            Add(Variable("i"), Variable("i"), IntConst(1)),
            Jump("L1"),
            Label("L3"),
            Call("printInt", listOf(Type.INT to Variable("acc"))),
            Return(Type.INT, Variable("acc"))
    )
    
    val compiledCode = ASMFunction("bar", listOf(Type.INT to "n"), code).compile()
    println("\n========== Compiled code ==========\n")
    println(compiledCode)
    with(PrintWriter("C:\\Users\\slava\\yasm_sse\\file.asm")) {
        println("extern printInt")
        println(compiledCode)
        close()
    }
}

