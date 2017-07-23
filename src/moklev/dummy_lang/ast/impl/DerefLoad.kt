package moklev.dummy_lang.ast.impl

import moklev.asm.instructions.Load
import moklev.asm.utils.Variable
import moklev.dummy_lang.ast.interfaces.Expression
import moklev.dummy_lang.compiler.CompilationState
import moklev.dummy_lang.compiler.Scope
import moklev.dummy_lang.utils.FunctionBuilder
import moklev.dummy_lang.utils.INT_64
import moklev.dummy_lang.utils.Type
import org.antlr.v4.runtime.ParserRuleContext

/**
 * @author Moklev Vyacheslav
 */
class DerefLoad(ctx: ParserRuleContext, val addr: Expression) : Expression(ctx) {
    override fun compileResult(builder: FunctionBuilder, state: CompilationState, scope: Scope): String {
        val result = builder.tempVar
        val address = addr.compileResult(builder, state, scope)
        builder.add(Load(Variable(result), Variable(address)))
        return result
    }

    override fun getType(state: CompilationState, scope: Scope): Type? = INT_64 // TODO infer proper type
}