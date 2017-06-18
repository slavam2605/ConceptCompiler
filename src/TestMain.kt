import moklev.asm.compiler.ConceptASMCompiler
import moklev.asm.compiler.RegisterAllocation
import moklev.asm.compiler.SSATransformer
import moklev.asm.instructions.Add
import moklev.asm.instructions.Assign
import moklev.asm.instructions.IfGreaterJump
import moklev.asm.instructions.Jump
import moklev.asm.interfaces.Call
import moklev.asm.interfaces.Label
import moklev.asm.utils.*
import moklev.utils.ASMBuilder

/**
 * @author Moklev Vyacheslav
 */
fun main(args: Array<String>) {
    val code = listOf(
            Label("L0"),
            Assign(Variable("x"), IntConst(1)),
            IfGreaterJump(Variable("x"), IntConst(1), "L1"),
            Jump("L2"),
            Label("L1"),
            Assign(Variable("x"), IntConst(42)),
            Jump("L3"),
            Label("L2"),
            Assign(Variable("x"), IntConst(69)),
            Jump("L3"),
            Label("L3"),    
            Call("f", listOf(Variable("x")))
    )

    println(ConceptASMCompiler.compile(ASMFunction("bar", emptyList(), code)))
    
//    val builder = ASMBuilder()
//    compileReassignment(builder, listOf(
//            InRegister("r1") to InRegister("r2"),
//            InRegister("r2") to InRegister("r3"),
//            InRegister("r3") to InRegister("r1"),
//            InRegister("r1") to InRegister("r4"),
//            InRegister("r4") to InRegister("r5"),
//            InRegister("r4") to InRegister("r6"),
//            InRegister("r7") to InRegister("r8"),
//            InRegister("r8") to InRegister("r9"),
//            InRegister("r9") to InRegister("r7")
//    ))
//    println(builder.build())
}

