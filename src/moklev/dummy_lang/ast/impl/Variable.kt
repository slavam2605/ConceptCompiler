package moklev.dummy_lang.ast.impl

import moklev.asm.instructions.Load
import moklev.asm.utils.StaticUtils
import moklev.asm.utils.Type.Pointer
import moklev.asm.utils.Variable
import moklev.dummy_lang.ast.interfaces.Expression
import moklev.dummy_lang.ast.interfaces.LValue
import moklev.dummy_lang.compiler.CompilationState
import moklev.dummy_lang.compiler.Scope
import moklev.dummy_lang.utils.FunctionBuilder
import moklev.dummy_lang.utils.Type
import org.antlr.v4.runtime.ParserRuleContext

/**
 * @author Vyacheslav Moklev
 */
class Variable(ctx: ParserRuleContext, val name: String) : LValue(ctx) {
    override fun getType(state: CompilationState, scope: Scope): Type? {
        val varType = scope.getType(name)
        if (varType != null)
            return varType 
        state.addError(ctx, "Undeclared variable $name")
        return null
    }

    override fun compileResult(builder: FunctionBuilder, state: CompilationState, scope: Scope): String {
        val type = getType(state, scope)?.toASMType() ?: return ""
        val pointerType = Pointer(type) // TODO why it is not used?
        val result = builder.tempVar
        builder.add(Load(type, Variable(result), Variable(name)))
        return result
    }

    override fun compileReference(builder: FunctionBuilder, state: CompilationState, scope: Scope): String {
        return name
    }
}