package moklev.dummy_lang.ast.impl

import moklev.asm.instructions.Jump
import moklev.asm.interfaces.Label
import moklev.dummy_lang.ast.interfaces.BooleanExpression
import moklev.dummy_lang.ast.interfaces.Expression
import moklev.dummy_lang.ast.interfaces.Statement
import moklev.dummy_lang.compiler.CompilationState
import moklev.dummy_lang.compiler.Scope
import moklev.dummy_lang.utils.FunctionBuilder
import org.antlr.v4.runtime.ParserRuleContext

/**
 * @author Moklev Vyacheslav
 */
class IfElse(
        ctx: ParserRuleContext, 
        val expression: Expression, 
        val ifTrue: List<Statement>, 
        val ifFalse: List<Statement>
) : Statement(ctx) {
    override fun compile(builder: FunctionBuilder, state: CompilationState, scope: Scope) {
        if (expression !is BooleanExpression) {
            state.addError(ctx, "`if` condition must have type bool, but has ${expression.getType(state, scope)}")
            return
        }
        val ifTrueLabel = builder.tempLabel
        val ifFalseLabel = builder.tempLabel
        val afterLabel = builder.tempLabel
        expression.compileBranch(builder, state, scope, ifTrueLabel, ifFalseLabel)
        builder.add(Label(ifTrueLabel))
        scope.enterLocalScope()
        ifTrue.forEach { it.compile(builder, state, scope) }
        scope.leaveLocalScope(builder)
        builder.add(Jump(afterLabel))
        builder.add(Label(ifFalseLabel))
        scope.enterLocalScope()
        ifFalse.forEach { it.compile(builder, state, scope) }
        scope.leaveLocalScope(builder)
        builder.add(Jump(afterLabel))
        builder.add(Label(afterLabel))
    }
}