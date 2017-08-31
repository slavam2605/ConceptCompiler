package moklev.dummy_lang.ast.interfaces

import moklev.asm.instructions.Load
import moklev.asm.utils.Variable
import moklev.dummy_lang.compiler.CompilationState
import moklev.dummy_lang.compiler.Scope
import moklev.dummy_lang.utils.FunctionBuilder
import org.antlr.v4.runtime.ParserRuleContext

/**
 * @author Moklev Vyacheslav
 */
abstract class LValue(ctx: ParserRuleContext) : Expression(ctx) {
    override fun compileResult(builder: FunctionBuilder, state: CompilationState, scope: Scope): String {
        val result = builder.tempVar
        val address = compileReference(builder, state, scope)
        builder.add(Load(Variable(result), Variable(address)))
        return result
    }
    
    abstract fun compileReference(builder: FunctionBuilder, state: CompilationState, scope: Scope): String
}