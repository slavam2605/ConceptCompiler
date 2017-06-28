package moklev.dummy_lang.ast.impl

import moklev.asm.instructions.Assign
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
class Assign(ctx: ParserRuleContext, val varName: String, val expression: Expression) : Statement(ctx) {
    override fun compile(builder: FunctionBuilder, state: CompilationState, scope: Scope) {
        val varType = scope.getType(varName)
        val exprType = expression.getType(state, scope)
        if (varType != exprType) {
            state.addError(ctx, "Mismatched types: $varType and $exprType")
            return
        }
        val result = expression.compileResult(builder, state, scope)
        builder.add(Assign(Variable(varName), Variable(result))) 
    }
}