package compiler.integrational

import compiler.TestBase
import moklev.asm.compiler.compile
import moklev.dummy_lang.compiler.*
import moklev.dummy_lang.parser.DummyLangLexer
import moklev.dummy_lang.parser.DummyLangParser
import moklev.utils.Either
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.junit.Assert.assertEquals
import org.junit.Before
import java.io.File
import java.io.InputStreamReader
import java.io.PrintWriter

/**
 * @author Moklev Vyacheslav
 */
internal open class RunnerTestBase: TestBase() {
    @Before
    fun cleanUp() {
        File("$ROOT_DIRECTORY/out.asm").delete()
        File("$ROOT_DIRECTORY/out.o").delete()
        File("$ROOT_DIRECTORY/main").delete()
    }
    
    protected fun compileString(source: String): Either<List<CompileError>, ProcessResult> {
        val stream = CharStreams.fromString(source)
        val parser = DummyLangParser(
                CommonTokenStream(
                        DummyLangLexer(stream)
                )
        )

        val file = parser.file()
        val typeDefinitions = file.typeDefinition()
        val functions = file.function()
        val fileBuilder = StringBuilder()
        val state = CompilationState()
        val scope = Scope()
        val visitor = ASTVisitor(state, scope)

        typeDefinitions.forEach { typeDefinitionContext ->
            val (name, type) = visitor.visitTypeDefinition(typeDefinitionContext)
            scope.declareType(name, type)
        }
        
        val asmFunctions = functions.map { parseFunction ->
            val function = visitor.visitFunction(parseFunction)
            function.compile(state, scope)
        }
        if (state.errorList.size > 0)
            return Either.Left(state.errorList)

        asmFunctions.forEach { conceptAsmCode ->
            val compiledCode = conceptAsmCode.compile()
            fileBuilder.append(compiledCode).append('\n')
        }

        val allCode = fileBuilder.toString()
        with(PrintWriter("$ROOT_DIRECTORY/out.asm")) {
            println("BITS 64")
            println("extern printInt")
            println("extern malloc")
            println("extern free")
            println()
            print(allCode)
            close()
        }

        val processBuilderCompile = ProcessBuilder("bash", "make.sh")
                .directory(File("./$ROOT_DIRECTORY"))
        val processCompile = processBuilderCompile.start()
        val exitCodeCompile = processCompile.waitFor()
        val readerErrorCompile = InputStreamReader(processCompile.errorStream)
        if (exitCodeCompile != 0) 
            return Either.Left(listOf(CompileError(0, 0, "", readerErrorCompile.readText(), ErrorLevel.ERROR)))
        
        val processBuilderRunner = ProcessBuilder("bash", "run.sh")
                .directory(File("./$ROOT_DIRECTORY"))
        val processRunner = processBuilderRunner.start()
        val exitCodeRunner = processRunner.waitFor()
        val readerInputRunner = InputStreamReader(processRunner.inputStream)
        val readerErrorRunner = InputStreamReader(processRunner.errorStream)

        println("Errors:\n${readerErrorRunner.readText()}")
        return Either.Right(ProcessResult(exitCodeRunner, readerInputRunner.readText()))
    }
    
    protected fun assertCompiles(source: String): ProcessResult {
        val result = compileString(source)
        return result.mapJoin({
            error("Not compiles: ${it.joinToString()}")
        }) { it }
    }
    
    protected fun assertZeroExitCode(result: ProcessResult) {
        assertEquals("Exit code must be zero", 0, result.exitCode)
    }
    
    protected fun parseIntResults(result: ProcessResult): List<Long> {
        val parts = result.output.trim().split(' ', '\t', '\r', '\n')
        return parts.map { part ->
            try {
                part.toLong()
            } catch (e: NumberFormatException) {
                fail("Part of output is not an integer: \"$part\"")
            }
        }
    }
    
    protected fun assertIntResults(source: String, vararg results: Long) {
        val processResult = assertCompiles(source)
        assertZeroExitCode(processResult)
        val intResults = parseIntResults(processResult)
        assertEquals(results.asList(), intResults)
    }
    
    companion object {
        private val ROOT_DIRECTORY = "../test_sandbox"
    }
}

internal class ProcessResult(val exitCode: Int, val output: String)