package moklev.dummy_lang.compiler

import moklev.dummy_lang.ast.impl.*
import moklev.dummy_lang.ast.impl.Function
import moklev.dummy_lang.ast.interfaces.Expression
import moklev.dummy_lang.ast.interfaces.Statement
import moklev.dummy_lang.parser.DummyLangParser
import moklev.dummy_lang.parser.DummyLangParserBaseVisitor
import moklev.dummy_lang.utils.INT_64
import moklev.dummy_lang.utils.Type

/**
 * @author Vyacheslav Moklev
 */
class ASTVisitor(val state: CompilationState, val scope: Scope) : DummyLangParserBaseVisitor<Any>() {
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
            is DummyLangParser.ExprStatementContext -> visitExprStatement(ctx)
            is DummyLangParser.CallStatementContext -> visitCallStatement(ctx)
            else -> error("Branch is not supported")
        }
    }

    override fun visitCallStatement(ctx: DummyLangParser.CallStatementContext): CallStatement {
        return CallStatement(ctx, ctx.IDENT().text, visitExprList(ctx.exprList()))
    }

    override fun visitExprStatement(ctx: DummyLangParser.ExprStatementContext): Expression {
        return visitExpression(ctx.expression())
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
        return VarDef(ctx, ctx.IDENT().text, visitType(ctx.type()), ctx.expression()?.let { visitExpression(it) })
    }

    override fun visitAssign(ctx: DummyLangParser.AssignContext): Assign {
        return Assign(ctx, visitExpression(ctx.expression(0)), visitExpression(ctx.expression(1)))
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
            is DummyLangParser.CallContext -> visitCall(ctx)
            is DummyLangParser.ParenExpressionContext -> visitParenExpression(ctx)
            is DummyLangParser.DereferenceContext-> visitDereference(ctx)
            is DummyLangParser.TypeCastContext -> visitTypeCast(ctx)
            is DummyLangParser.AddressOfContext -> visitAddressOf(ctx)
            is DummyLangParser.StructFieldContext -> visitStructField(ctx)
            is DummyLangParser.LogicalAndContext -> visitLogicalAnd(ctx)
            is DummyLangParser.LogicalOrContext -> visitLogicalOr(ctx)
            else -> error("Branch is not supported")
        }
    }

    override fun visitLogicalOr(ctx: DummyLangParser.LogicalOrContext): LogicalOr {
        return LogicalOr(ctx, visitExpression(ctx.left), visitExpression(ctx.right))
    }

    override fun visitLogicalAnd(ctx: DummyLangParser.LogicalAndContext): LogicalAnd {
        return LogicalAnd(ctx, visitExpression(ctx.left), visitExpression(ctx.right))
    }

    override fun visitStructField(ctx: DummyLangParser.StructFieldContext): StructField {
        return StructField(ctx, visitExpression(ctx.left), ctx.right.text)
    }

    override fun visitTypeCast(ctx: DummyLangParser.TypeCastContext): TypeCast {
        return TypeCast(ctx, visitType(ctx.type()), visitExpression(ctx.expression()))
    }

    override fun visitDereference(ctx: DummyLangParser.DereferenceContext): Dereference {
        return Dereference(ctx, visitExpression(ctx.expression()))
    }

    override fun visitAddressOf(ctx: DummyLangParser.AddressOfContext): AddressOf {
        return AddressOf(ctx, visitExpression(ctx.expression()))
    }

    override fun visitParenExpression(ctx: DummyLangParser.ParenExpressionContext): Expression {
        return visitExpression(ctx.expression())
    }

    override fun visitCall(ctx: DummyLangParser.CallContext): Call {
        return Call(ctx, ctx.IDENT().text, visitExprList(ctx.exprList()))
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
        return IntConst(ctx, ctx.INT_LITERAL().text.toLong())
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

    override fun visitExprList(ctx: DummyLangParser.ExprListContext): List<Expression> {
        return ctx.exprs.map { visitExpression(it) }
    }
    
    fun visitType(ctx: DummyLangParser.TypeContext): Type {
        return when (ctx) {
            is DummyLangParser.PointerTypeContext -> visitPointerType(ctx)
            is DummyLangParser.PrimitiveTypeContext -> visitPrimitiveType(ctx)
            is DummyLangParser.DefinedTypeContext -> visitDefinedType(ctx)
            else -> error("Branch is not supported")
        }
    }

    override fun visitPointerType(ctx: DummyLangParser.PointerTypeContext): Type.PointerType {
        return Type.PointerType(visitType(ctx.type()))
    }

    override fun visitPrimitiveType(ctx: DummyLangParser.PrimitiveTypeContext): Type.PrimitiveType {
        return Type.PrimitiveType(ctx.text, 8) // TODO not 8
    }

    override fun visitDefinedType(ctx: DummyLangParser.DefinedTypeContext): Type {
        return scope.getDeclaredType(ctx.IDENT().text) ?: run {
            state.addError(ctx, "Undefined type: ${ctx.IDENT().text}")
            INT_64 // TODO some more reasonable default type
        } 
    }

    fun visitTypeDefinition(ctx: DummyLangParser.TypeDefinitionContext): Pair<String, Type> {
        return when (ctx) {
            is DummyLangParser.StructDefinitionContext -> visitStructDefinition(ctx)
            else -> error("Branch is not supported")
        }
    }

    override fun visitStructDefinition(ctx: DummyLangParser.StructDefinitionContext): Pair<String, Type.StructType> {
        val fields = arrayListOf<Pair<String, Type>>()
        for (index in 0 until ctx.fieldNames.size) {
            fields.add(ctx.fieldNames[index].text to visitType(ctx.fieldTypes[index]))
        }
        return ctx.name.text to Type.StructType(ctx.name.text, fields)
    }
}