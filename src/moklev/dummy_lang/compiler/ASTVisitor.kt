package moklev.dummy_lang.compiler

import moklev.dummy_lang.ast.impl.*
import moklev.dummy_lang.ast.impl.Function
import moklev.dummy_lang.ast.interfaces.Expression
import moklev.dummy_lang.ast.interfaces.Statement
import moklev.dummy_lang.parser.DummyLangParser
import moklev.dummy_lang.parser.DummyLangParserBaseVisitor
import moklev.dummy_lang.utils.Type

/**
 * @author Vyacheslav Moklev
 */
object ASTVisitor : DummyLangParserBaseVisitor<Any>() {
    override fun visitFunction(ctx: DummyLangParser.FunctionContext): Function {
        return Function(
                ctx,
                ctx.IDENT().text,
                visitTypedIdentList(ctx.typedIdentList()),
                visitType(ctx.type()),
                ctx.statement().map { visitStatement(it) }
        )
    }

    fun visitStatement(ctx: DummyLangParser.StatementContext): Statement {
        return when (ctx) {
            is DummyLangParser.VarDefContext -> visitVarDef(ctx)
            is DummyLangParser.AssignContext -> visitAssign(ctx)
            is DummyLangParser.ReturnContext -> visitReturn(ctx)
            is DummyLangParser.IfElseContext -> visitIfElse(ctx)
            is DummyLangParser.ForLoopContext -> visitForLoop(ctx) 
            else -> error("Branch is not supported")
        }
    }

    override fun visitForLoop(ctx: DummyLangParser.ForLoopContext): ForLoop {
        return ForLoop(
                ctx,
                visitStatement(ctx.init),
                visitExpression(ctx.cond),
                visitStatement(ctx.step),
                ctx.body.map { visitStatement(it) }
        )
    }

    override fun visitIfElse(ctx: DummyLangParser.IfElseContext): IfElse {
        return IfElse(
                ctx,
                visitExpression(ctx.expression()),
                ctx.ifTrue.map { visitStatement(it) },
                ctx.ifFalse.map { visitStatement(it) }
        )
    }

    override fun visitVarDef(ctx: DummyLangParser.VarDefContext): VarDef {
        return VarDef(ctx, ctx.IDENT().text, visitType(ctx.type()))
    }

    override fun visitAssign(ctx: DummyLangParser.AssignContext): Assign {
        return Assign(ctx, ctx.IDENT().text, visitExpression(ctx.expression()))
    }

    override fun visitReturn(ctx: DummyLangParser.ReturnContext): Return {
        return Return(ctx, visitExpression(ctx.expression()))
    }

    fun visitExpression(ctx: DummyLangParser.ExpressionContext): Expression {
        return when (ctx) {
            is DummyLangParser.TimesDivContext -> visitTimesDiv(ctx)
            is DummyLangParser.PlusMinusContext -> visitPlusMinus(ctx)
            is DummyLangParser.IntConstContext -> visitIntConst(ctx)
            is DummyLangParser.VariableContext -> visitVariable(ctx)
            is DummyLangParser.CompareOpContext -> visitCompareOp(ctx)
            else -> error("Branch is not supported")
        }
    }

    override fun visitCompareOp(ctx: DummyLangParser.CompareOpContext): BooleanBinaryOp {
        return BooleanBinaryOp(ctx, ctx.op.text, visitExpression(ctx.left), visitExpression(ctx.right))
    }

    override fun visitTimesDiv(ctx: DummyLangParser.TimesDivContext): BinaryOp {
        return BinaryOp(ctx, ctx.op.text, visitExpression(ctx.left), visitExpression(ctx.right))
    }

    override fun visitPlusMinus(ctx: DummyLangParser.PlusMinusContext): BinaryOp {
        return BinaryOp(ctx, ctx.op.text, visitExpression(ctx.left), visitExpression(ctx.right))
    }

    override fun visitIntConst(ctx: DummyLangParser.IntConstContext): IntConst {
        return IntConst(ctx, ctx.INT_LITERAL().text.toInt())
    }

    override fun visitVariable(ctx: DummyLangParser.VariableContext): Variable {
        return Variable(ctx, ctx.IDENT().text)
    }

    override fun visitTypedIdentList(ctx: DummyLangParser.TypedIdentListContext): List<Pair<Type, String>> {
        val idents = ctx.IDENT().map { it.text }
        val types = ctx.type().map { visitType(it) }
        val nbArguments = idents.size
        return (0..nbArguments - 1)
                .map { types[it] to idents[it] }
    }

    override fun visitType(ctx: DummyLangParser.TypeContext): Type {
        return Type.PrimitiveType(ctx.text)
    }
}