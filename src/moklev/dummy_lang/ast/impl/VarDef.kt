package moklev.dummy_lang.ast.impl

import moklev.dummy_lang.ast.interfaces.ASTNode
import moklev.dummy_lang.ast.interfaces.Statement
import moklev.dummy_lang.utils.FunctionBuilder
import moklev.dummy_lang.utils.Type
import org.antlr.v4.runtime.ParserRuleContext

/**
 * @author Vyacheslav Moklev
 */
class VarDef(ctx: ParserRuleContext, val name: String, val type: Type) : Statement(ctx) {
    override fun compile(builder: FunctionBuilder) {
//        TODO("not implemented")
    }
}