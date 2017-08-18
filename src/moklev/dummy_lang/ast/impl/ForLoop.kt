package moklev.dummy_lang.ast.impl

import moklev.asm.instructions.Jump
import moklev.asm.instructions.StackFree
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
class ForLoop(
        ctx: ParserRuleContext,
        val init: Statement,
        val cond: Expression,
        val step: Statement,
        val body: List<Statement>
) : Statement(ctx) {
    override fun compile(builder: FunctionBuilder, state: CompilationState, scope: Scope) {
        if (cond !is BooleanExpression) {
            state.addError(ctx, "`for` condition must have type bool, but has ${cond.getType(state, scope)}")
            return
        }
        val loopCond = builder.tempLabel
        val loopBody = builder.tempLabel
        val afterLoop = builder.tempLabel
        scope.enterLocalScope()
        init.compile(builder, state, scope)
        builder.add(Label(loopCond))
        cond.compileBranch(builder, state, scope, loopBody, afterLoop)
        builder.add(Label(loopBody))
        scope.enterLocalScope()
        body.forEach { it.compile(builder, state, scope) }
        scope.leaveLocalScope(builder)
        step.compile(builder, state, scope)
        scope.leaveLocalScope(builder)
        builder
                .add(Jump(loopCond))
                .add(Label(afterLoop))
    }
}