package moklev.dummy_lang.ast.impl

import moklev.asm.instructions.Load
import moklev.asm.utils.Variable
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
class Dereference(ctx: ParserRuleContext, val addr: Expression) : LValue(ctx) {
    override fun getType(state: CompilationState, scope: Scope): Type? {
        val addrType = addr.getType(state, scope)
        if (addrType is Type.PointerType) {
            return addrType.sourceType
        }
        state.addError(ctx, "Dereferencing of a non-pointer type $addrType")
        return null
    }

    override fun compileReference(builder: FunctionBuilder, state: CompilationState, scope: Scope): String {
        return addr.compileResult(builder, state, scope)
    }
}