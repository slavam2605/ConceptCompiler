import moklev.asm.compiler.compile
import moklev.dummy_lang.compiler.ASTVisitor
import moklev.dummy_lang.compiler.CompilationState
import moklev.dummy_lang.parser.DummyLangLexer
import moklev.dummy_lang.parser.DummyLangParser
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
//    val stream = CharStreams.fromString("""
//        fun keks(a: i64, b: i64): i64 {
//            var x: i64;
//            x = 1;
//            var i: i64;
//            for (i = 1;; a + 1 > i; i = i + 1;) {
//                x = x * i;
//            }
//            return x;
//        }
//    """)
//    val stream = CharStreams.fromString("""
//        fun keks(x: i64, y: i64): i64 {
//            if x > 0 {
//                var x1: i64;
//                var x2: i64;
//                var x3: i64;
//                var x4: i64;
//                var x5: i64;
//                x1 = keks(x - 1);
//                x2 = keks(x - 1);
//                x3 = keks(x - 1);
//                x4 = keks(x - 1);
//                x5 = keks(x - 1);
//                return x * (x1 + x2 + x3 - x4 - x5);
//            }
//            return 1;
//        }
//    """)
//    val stream = CharStreams.fromString("""
//        fun keks(m: i64, n: i64): i64 {
//            if m == 0 {
//                return n + 1;
//            }
//            if n == 0 {
//                return keks(m - 1, 1);
//            }   
//            return keks(m - 1, keks(m, n - 1));
//        }
//    """)
//    val stream = CharStreams.fromString("""
//        fun keks(x: i64, y: i64): i64 {
//            var a: i64;
//            var b: i64;
//            a = x + y;
//            b = x - y;
//            return (a - y) / (x - b);
//        }
//    """)
    val stream = CharStreams.fromFileName("source.cp")
    val parser = DummyLangParser(
            CommonTokenStream(
                    DummyLangLexer(stream)
            )
    )
    val functions = parser.file().function()
    val fileBuilder = StringBuilder()
    for (parseFunction in functions) {
        val function = ASTVisitor.visitFunction(parseFunction)
        val state = CompilationState()
        val conceptAsmCode = function
                .compile(state)
        state.errorList.forEach {
            println(it)
        }
        if (state.errorList.size > 0)
            return
        val compiledCode = conceptAsmCode.compile()
        fileBuilder.append(compiledCode).append('\n')
    }

//    val compiledCode = ASMFunction("bar", listOf(Type.INT to "n"), code).compile()
    
    val allCode = fileBuilder.toString()
    println("\n========== Compiled code ==========\n")
    println(allCode)
    with(PrintWriter("compiled\\file.asm")) {
        print("BITS 64\n")
        print("extern printInt\n\n")
        print(allCode)
        close()
    }
}

