package moklev.dummy_lang.ast.impl

import moklev.asm.utils.ASMFunction
import moklev.dummy_lang.ast.interfaces.ASTNode
import moklev.dummy_lang.ast.interfaces.Statement
import moklev.dummy_lang.compiler.CompilationState
import moklev.dummy_lang.compiler.Scope
import moklev.dummy_lang.utils.FunctionBuilder
import moklev.dummy_lang.utils.Type
import org.antlr.v4.runtime.ParserRuleContext

/**
 * @author Vyacheslav Moklev
 */
class Function(
        ctx: ParserRuleContext, 
        val name: String, 
        val arguments: List<Pair<Type, String>>, 
        val resultType: Type,
        val statements: List<Statement>
) : ASTNode(ctx) {
    fun compile(state: CompilationState): ASMFunction {
        val builder = FunctionBuilder(name, arguments)
        val scope = Scope(resultType, arguments)
        statements.forEach {
            it.compile(builder, state, scope)
        }
        return builder.build()
    }
}