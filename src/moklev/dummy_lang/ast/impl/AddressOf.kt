package moklev.dummy_lang.ast.impl

import moklev.dummy_lang.ast.interfaces.Expression
import moklev.dummy_lang.ast.interfaces.LValue
import moklev.dummy_lang.compiler.CompilationState
import moklev.dummy_lang.compiler.Scope
import moklev.dummy_lang.utils.FunctionBuilder
import moklev.dummy_lang.utils.Type
import org.antlr.v4.runtime.ParserRuleContext

/**
 * @author Moklev Vyacheslav
 */
class AddressOf(ctx: ParserRuleContext, val expression: Expression) : Expression(ctx) {
    override fun compileResult(builder: FunctionBuilder, state: CompilationState, scope: Scope): String {
        if (expression !is LValue) {
            state.addError(ctx, "Can take a reference only from lvalue")
            return ""
        }
        return expression.compileReference(builder, state, scope)
    }

    override fun getType(state: CompilationState, scope: Scope): Type? {
        if (expression !is LValue)
            return null
        val sourceType = expression.getType(state, scope)
        if (sourceType != null) {
            return Type.PointerType(sourceType)
        }
        return null
    }
}