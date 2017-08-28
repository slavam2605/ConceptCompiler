import moklev.asm.compiler.compile
import moklev.dummy_lang.compiler.ASTVisitor
import moklev.dummy_lang.compiler.CompilationState
import moklev.dummy_lang.compiler.Scope
import moklev.dummy_lang.parser.DummyLangLexer
import moklev.dummy_lang.parser.DummyLangParser
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import java.io.PrintWriter

/**
 * @author Moklev Vyacheslav
 */
fun main(args: Array<String>) {
    val stream = CharStreams.fromFileName("test_sources/pointer_test.cp")
    val parser = DummyLangParser(
            CommonTokenStream(
                    DummyLangLexer(stream)
            )
    )

    val functions = parser.file().function()
    val fileBuilder = StringBuilder()
    val state = CompilationState()
    val scope = Scope()
    val asmFunctions = functions.map { parseFunction ->
        val function = ASTVisitor.visitFunction(parseFunction)
        function.compile(state, scope)
    }
    state.errorList.forEach {
        println(it)
    }
    if (state.errorList.size > 0)
        return
    asmFunctions.forEach { conceptAsmCode ->
        val compiledCode = conceptAsmCode.compile()
        fileBuilder.append(compiledCode).append('\n')
    }
    
    val allCode = fileBuilder.toString()
    println("\n========== Compiled code ==========\n")
    println(allCode)
    with(PrintWriter("compiled\\file.asm")) {
        print("BITS 64\n")
        print("extern printInt\n")
        print("extern malloc\n")
        print("extern free\n")
        print("\n")
        print(allCode)
        close()
    }
}