package moklev.dummy_lang.ast.impl

import moklev.asm.instructions.Assign
import moklev.asm.instructions.Store
import moklev.asm.utils.Type
import moklev.asm.utils.Variable
import moklev.dummy_lang.ast.interfaces.Expression
import moklev.dummy_lang.ast.interfaces.LValue
import moklev.dummy_lang.ast.interfaces.Statement
import moklev.dummy_lang.compiler.CompilationState
import moklev.dummy_lang.compiler.Scope
import moklev.dummy_lang.utils.FunctionBuilder
import org.antlr.v4.runtime.ParserRuleContext

/**
 * @author Vyacheslav Moklev
 */
class Assign(ctx: ParserRuleContext, val lhs: Expression, val rhs: Expression) : Statement(ctx) {
    override fun compile(builder: FunctionBuilder, state: CompilationState, scope: Scope) {
        if (lhs !is LValue) {
            state.addError(ctx, "Can assign only to lvalue")
            return
        }
        val lhsInnerType = lhs.getType(state, scope) 
        val rhsType = rhs.getType(state, scope)
        if (lhsInnerType != rhsType) {
            state.addError(ctx, "Mismatched types: $lhsInnerType and $rhsType")
            return
        }
        val result = rhs.compileResult(builder, state, scope)
        val lhsAddress = lhs.compileReference(builder, state, scope)
        builder.add(Store(Variable(lhsAddress), Variable(result))) 
    }
}