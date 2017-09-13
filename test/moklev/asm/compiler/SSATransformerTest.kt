package moklev.asm.compiler

import moklev.asm.compiler.SSATransformer.Block
import moklev.asm.instructions.*
import moklev.asm.interfaces.Instruction
import moklev.asm.utils.Int64Const
import moklev.asm.utils.Type
import moklev.asm.utils.Undefined
import moklev.asm.utils.Variable
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * @author Moklev Vyacheslav
 */
class SSATransformerTest {
    private data class PatternBlock(val name: String, val instructions: List<Instruction>)
    
    private fun Block.toPatternBlock(): PatternBlock = PatternBlock(
            label,
            instructions.map { it.substitute(Variable(""), Undefined) }
    )
    
    private fun assertBlocksMatch(blocks: List<Block>, patterns: List<PatternBlock>): Map<String, String> {
        val blockMap = blocks.associateBy { it.label }
        val patternMap = patterns.associateBy { it.name }
        assertEquals(patternMap.keys, blockMap.keys, "Number of blocks")

        val patternVariableMap = mutableMapOf<String, String>()
        val blockNames = blockMap.keys
        for (name in blockNames) {
            val block = assertNotNull(blockMap[name])
            val pattern = assertNotNull(patternMap[name])
            assertEquals(pattern.instructions.size, block.instructions.size, "Number of instructions in block $name")

            block.instructions.forEachIndexed { index, blockInstruction ->
                val patternInstruction = pattern.instructions[index]
                assertEquals(patternInstruction.javaClass, blockInstruction.javaClass, "Class of instruction")

                val blockValues = blockInstruction.allValues
                val patternValues = patternInstruction.allValues
                assertEquals(patternValues.size, blockValues.size, "Number of instruction arguments")

                for (valueIndex in 0 until blockValues.size) {
                    val blockValue = blockValues[valueIndex]
                    val patternValue = patternValues[valueIndex]
                    assertEquals(patternValue.javaClass, blockValue.javaClass, "Class of instruction value")

                    if (blockValue is Variable) {
                        patternValue as Variable
                        val patternName = patternValue.toString()
                        val blockVarName = blockValue.toString()
                        if (patternName.startsWith("$")) {
                            val lastValue = patternVariableMap[patternName]
                            assertTrue("Different variables are mapped into the same pattern $patternName") {
                                lastValue == null || lastValue == blockVarName
                            }
                            patternVariableMap[patternName] = blockVarName
                        } else {
                            assertEquals(patternName, blockVarName, "Instruction variables")
                        }
                    } else {
                        assertEquals(patternValue, blockValue, "Instruction values")
                    }
                }
            }
        }
        return patternVariableMap
    }

    @Test
    fun partlyTransformToSSATest1() {
        val block = Block("block_name", ArrayDeque(listOf<Instruction>(
                Assign(Variable("x"), Int64Const(42)),
                Assign(Variable("x"), Int64Const(69)),
                Assign(Variable("x"), Int64Const(128)),
                Assign(Variable("x"), Int64Const(91))
        )))
        val result = SSATransformer.partlyTransformToSSA(listOf(block))
        assertBlocksMatch(result, listOf(
                PatternBlock("block_name", listOf(
                        Assign(Variable("$1"), Int64Const(42)),
                        Assign(Variable("$2"), Int64Const(69)),
                        Assign(Variable("$3"), Int64Const(128)),
                        Assign(Variable("$4"), Int64Const(91))
                ))
        ))
    }
    
    @Test
    fun partlyTransformToSSATest2() {
        val blockA = Block("A", ArrayDeque(listOf(
                Assign(Variable("x"), Int64Const(42)),
                Jump("C")
        )))
        val blockB = Block("B", ArrayDeque(listOf(
                Assign(Variable("x"), Int64Const(69)),
                Jump("C")
        )))
        val blockC = Block("C", ArrayDeque(listOf<Instruction>(
                Assign(Variable("y"), Variable("x"))
        )))
        blockA.addNextBlock(blockC)
        blockB.addNextBlock(blockC)
        val result = SSATransformer.partlyTransformToSSA(listOf(blockA, blockB, blockC))

        val patternA = PatternBlock("A", listOf(
                Assign(Variable("$1"), Int64Const(42)),
                Jump("C")
        ))
        val patternB = PatternBlock("B", listOf(
                Assign(Variable("$2"), Int64Const(69)),
                Jump("C")
        ))
        val patternC = PatternBlock("C", listOf(
                Phi(Variable("$3"), listOf(
                        "A" to Variable("$1"),
                        "B" to Variable("$2")
                )),
                Assign(Variable("y"), Variable("$3"))
        ))
        assertBlocksMatch(result, listOf(patternA, patternB, patternC))
    }
    
    @Test
    fun partlyTransformToSSATest3() {
        val blockStart = Block("start", ArrayDeque(listOf(
                Assign(Variable("x"), Int64Const(42)),
                Jump("loop_start")
        )))
        val blockLoopStart = Block("loop_start", ArrayDeque(listOf(
                Assign(Variable("i"), Int64Const(0)),
                Jump("loop")
        )))
        val blockLoop = Block("loop", ArrayDeque(listOf(
                Add(Variable("i"), Variable("i"), Int64Const(1)),
                BinaryCompareJump(">", Variable("i"), Int64Const(10), "after_loop"),
                Jump("loop")
        )))
        val blockAfterLoop = Block("after_loop", ArrayDeque(listOf<Instruction>(
                Return(Type.INT, Variable("x"))
        )))
        blockStart.addNextBlock(blockLoopStart)
        blockLoopStart.addNextBlock(blockLoop)
        blockLoop.addNextBlock(blockAfterLoop)
        blockLoop.addNextBlock(blockLoop)
        
        val result = SSATransformer.partlyTransformToSSA(listOf(blockStart, blockLoopStart, blockLoop, blockAfterLoop))

        val patternStart = PatternBlock("start", listOf(
                Assign(Variable("x"), Int64Const(42)),
                Jump("loop_start")
        ))
        val patternLoopStart = PatternBlock("loop_start", listOf(
                Assign(Variable("$1"), Int64Const(0)),
                Jump("loop")
        ))
        val patternLoop = PatternBlock("loop", listOf(
                Phi(Variable("$2"), listOf(
                        "loop_start" to Variable("$1"), 
                        "loop" to Variable("$3")
                )),
                Add(Variable("$3"), Variable("$2"), Int64Const(1)),
                BinaryCompareJump(">", Variable("$3"), Int64Const(10), "after_loop"),
                Jump("loop")
        ))
        val patternAfterLoop = PatternBlock("after_loop", listOf(
                Phi(Variable("$4"), listOf(
                        "loop" to Variable("$3")
                )),
                Return(Type.INT, Variable("x"))
        ))
        
        assertBlocksMatch(result, listOf(patternStart, patternLoopStart, patternLoop, patternAfterLoop))
    }
    
    @Test
    fun partlyTransformToSSATest4() {
        val block = Block("block_name", ArrayDeque(listOf<Instruction>(
                Assign(Variable("x"), Int64Const(42)),
                Assign(Variable("x"), Int64Const(69)),
                Assign(Variable("x"), Int64Const(128)),
                Assign(Variable("x"), Int64Const(91))
        )))
        
        val patternBlock = PatternBlock("block_name", listOf(
                Assign(Variable("$1"), Int64Const(42)),
                Assign(Variable("$2"), Int64Const(69)),
                Assign(Variable("$3"), Int64Const(128)),
                Assign(Variable("$4"), Int64Const(91))
        ))
        
        val result = SSATransformer.partlyTransformToSSA(listOf(block))
        val patternSubstitution = assertBlocksMatch(result, listOf(patternBlock))
        
        val resultPattern = PatternBlock("block_name", listOf(
                Assign(Variable(patternSubstitution["$1"]!!), Int64Const(42)),
                Assign(Variable(patternSubstitution["$2"]!!), Int64Const(69)),
                Assign(Variable(patternSubstitution["$3"]!!), Int64Const(128)),
                Assign(Variable(patternSubstitution["$4"]!!), Int64Const(91))
        ))
        
        val result2 = SSATransformer.partlyTransformToSSA(result)
        assertBlocksMatch(result2, listOf(resultPattern))
    }
}