import moklev.asm.compiler.SSATransformer
import moklev.asm.compiler.compile
import moklev.asm.interfaces.Phi
import moklev.asm.utils.Variable
import moklev.dummy_lang.compiler.ASTVisitor
import moklev.dummy_lang.compiler.CompilationState
import moklev.dummy_lang.parser.DummyLangLexer
import moklev.dummy_lang.parser.DummyLangParser
import org.antlr.v4.runtime.ANTLRInputStream
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import java.io.PrintWriter


/**
 * @author Moklev Vyacheslav
 */
fun main(args: Array<String>) {
//    val code = listOf(
//            Label("L0"),
//            Add(Variable("n"), Variable("n"), IntConst(1)),
//            Assign(Variable("acc"), IntConst(0)),
//            Assign(Variable("i"), IntConst(1)),
//            Jump("L1"),
//            Label("L1"),
//            IfGreaterJump(Variable("n"), Variable("i"), "L2"),
//            Jump("L3"),
//            Label("L2"),
//            Add(Variable("acc"), Variable("acc"), Variable("i")),
//            Add(Variable("i"), Variable("i"), IntConst(1)),
//            Jump("L1"),
//            Label("L3"),
//            Call("printInt", listOf(Type.INT to Variable("acc"))),
//            Return(Type.INT, Variable("acc"))
//    )

//    val code = listOf(
//            Label("L0"),
//            Add(Variable("acc"), Variable("arg1"), Variable("arg2")),
//            Add(Variable("acc"), Variable("acc"), Variable("arg3")),
//            Add(Variable("acc"), Variable("acc"), Variable("arg4")),
//            Assign(Variable("i"), IntConst(0)),
//            Jump("L1"),
//            Label("L1"),
//            Add(Variable("i"), Variable("i"), IntConst(1)),
//            IfGreaterJump(Variable("i"), IntConst(100), "L2"),
//            Jump("L1"),
//            Label("L2"),
//            Add(Variable("res"), Variable("i"), Variable("acc")),
//            Return(Type.INT, Variable("res"))
//    )

//    val compiledCode = ASMFunction("bar", listOf(
//            Type.INT to "arg1",
//            Type.INT to "arg2",
//            Type.INT to "arg3",
//            Type.INT to "arg4"
//    ), code).compile()

    // TODO detect if variable was not initialized:
    // var x: i64;
    // var y: i64;
    // var z: bool;
    // z = x > y;
    val stream = CharStreams.fromString("""
        fun keks(a: i64, b: i64): i64 {
            var x: i64;
            x = 1;
            var i: i64;
            for (i = 1;; a + 1 > i; i = i + 1;) {
                x = x * i;
            }
            return x;
        }
    """)
    val parser = DummyLangParser(
            CommonTokenStream(
                    DummyLangLexer(stream)
            )
    )
    val function = ASTVisitor.visitFunction(parser.function())
    val state = CompilationState()
    val conceptAsmCode = function
            .compile(state)
    state.errorList.forEach {
        println(it)
    }
    if (state.errorList.size > 0)
        return
    val compiledCode = conceptAsmCode.compile()

//    val compiledCode = ASMFunction("bar", listOf(Type.INT to "n"), code).compile()
    println("\n========== Compiled code ==========\n")
    println(compiledCode)
    with(PrintWriter("compiled\\file.asm")) {
        print("BITS 64\n")
        print("extern printInt\n\n")
        print(compiledCode)
        close()
    }
}

