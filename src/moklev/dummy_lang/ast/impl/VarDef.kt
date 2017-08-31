package moklev.dummy_lang.ast.impl

import moklev.asm.instructions.StackAlloc
import moklev.asm.utils.Variable
import moklev.dummy_lang.ast.interfaces.Statement
import moklev.dummy_lang.compiler.CompilationState
import moklev.dummy_lang.compiler.Scope
import moklev.dummy_lang.utils.FunctionBuilder
import moklev.dummy_lang.utils.Type
import org.antlr.v4.runtime.ParserRuleContext

/**
 * @author Vyacheslav Moklev
 */
class VarDef(ctx: ParserRuleContext, val name: String, val type: Type) : Statement(ctx) {
    override fun compile(builder: FunctionBuilder, state: CompilationState, scope: Scope) {
        val defined = scope.add(name, type)
        if (defined) {
            state.addWarning(ctx, "Shadowing: variable $name was already defined")
        }
        builder.add(StackAlloc(Variable(name), type.sizeOf))
    }
}