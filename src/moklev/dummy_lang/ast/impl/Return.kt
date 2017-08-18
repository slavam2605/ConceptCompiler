package moklev.dummy_lang.ast.impl

import moklev.asm.instructions.Return
import moklev.asm.utils.Type
import moklev.asm.utils.Variable
import moklev.dummy_lang.ast.interfaces.Expression
import moklev.dummy_lang.ast.interfaces.Statement
import moklev.dummy_lang.compiler.CompilationState
import moklev.dummy_lang.compiler.Scope
import moklev.dummy_lang.utils.FunctionBuilder
import org.antlr.v4.runtime.ParserRuleContext

/**
 * @author Vyacheslav Moklev
 */
class Return(ctx: ParserRuleContext, val result: Expression) : Statement(ctx) {
    override fun compile(builder: FunctionBuilder, state: CompilationState, scope: Scope) {
        val resultType = scope.resultType
        val exprType = result.getType(state, scope)
        if (resultType != exprType) {
            state.addError(ctx, "Mismatched types: function must return $resultType, but found $exprType")
            return
        }
        val resultName = result.compileResult(builder, state, scope)
        scope.freeAllAllocatedStacks(builder)
        builder.add(Return(Type.INT, Variable(resultName))) // TODO lol get type somehow
    }
}