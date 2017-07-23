package moklev.dummy_lang.ast.impl

import moklev.asm.instructions.Store
import moklev.asm.utils.Variable
import moklev.dummy_lang.ast.interfaces.Expression
import moklev.dummy_lang.ast.interfaces.Statement
import moklev.dummy_lang.compiler.CompilationState
import moklev.dummy_lang.compiler.Scope
import moklev.dummy_lang.utils.FunctionBuilder
import org.antlr.v4.runtime.ParserRuleContext

/**
 * @author Moklev Vyacheslav
 */
class DerefStore(ctx: ParserRuleContext, val addr: Expression, val value: Expression) : Statement(ctx) {
    override fun compile(builder: FunctionBuilder, state: CompilationState, scope: Scope) {
        val result = value.compileResult(builder, state, scope)
        val address = addr.compileResult(builder, state, scope)
        builder.add(Store(Variable(address), Variable(result)))
    }
}