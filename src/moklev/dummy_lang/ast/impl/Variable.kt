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
 * @author Vyacheslav Moklev
 */
class Variable(ctx: ParserRuleContext, val name: String) : Expression(ctx), LValue {
    override fun getType(state: CompilationState, scope: Scope): Type? {
        val varType = scope.getType(name)
        if (varType != null)
            return varType 
        state.addError(ctx, "Undeclared variable $name")
        return null
    }

    override fun compileResult(builder: FunctionBuilder, state: CompilationState, scope: Scope): String {
        val result = builder.tempVar
        builder.add(Load(Variable(result), Variable(name)))
        return result
    }

    override fun compileReference(builder: FunctionBuilder, state: CompilationState, scope: Scope): String {
        return name
    }
}