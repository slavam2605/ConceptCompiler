package moklev.dummy_lang.ast.impl

import moklev.asm.interfaces.Label
import moklev.dummy_lang.ast.interfaces.BooleanExpression
import moklev.dummy_lang.ast.interfaces.Expression
import moklev.dummy_lang.compiler.CompilationState
import moklev.dummy_lang.compiler.Scope
import moklev.dummy_lang.utils.FunctionBuilder
import org.antlr.v4.runtime.ParserRuleContext

/**
 * @author Moklev Vyacheslav
 */
class LogicalAnd(ctx: ParserRuleContext, val left: Expression, val right: Expression) : BooleanExpression(ctx) {
    override fun compileBranch(builder: FunctionBuilder,
                               state: CompilationState,
                               scope: Scope,
                               labelIfTrue: String,
                               labelIfFalse: String) {
        if (left !is BooleanExpression) 
            state.addError(ctx, "Left operand of && must be a boolean expression")
        if (right !is BooleanExpression)
            state.addError(ctx, "Right operand of && must be a boolean expression")
        if (left !is BooleanExpression || right !is BooleanExpression)
            return

        // TODO improve type check
        left.getType(state, scope)
        right.getType(state, scope)
        
        val intermediateLabel = builder.tempLabel
        left.compileBranch(builder, state, scope, intermediateLabel, labelIfFalse)
        builder.add(Label(intermediateLabel))
        right.compileBranch(builder, state, scope, labelIfTrue, labelIfFalse)
    }
}