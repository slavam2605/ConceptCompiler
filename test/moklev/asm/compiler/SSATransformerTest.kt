package moklev.asm.compiler

import moklev.asm.compiler.SSATransformer.Block
import moklev.asm.instructions.*
import moklev.asm.interfaces.Instruction
import moklev.asm.interfaces.Label
import moklev.asm.utils.Int64Const
import moklev.asm.utils.Type
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

                    if (patternValue.startsWith("$")) {
                        val lastValue = patternVariableMap[patternValue]
                        assertTrue("Different variables are mapped into the same pattern $patternValue") {
                            lastValue == null || lastValue == blockValue
                        }
                        patternVariableMap[patternValue] = blockValue
                    } else {
                        assertEquals(patternValue, blockValue, "Instruction variables")
                    }
                }
            }
        }
        return patternVariableMap
    }

    private fun assertBlocksMatchOrdered(blocks: List<Block>, patterns: List<PatternBlock>): Map<String, String> {
        assertEquals(patterns.size, blocks.size, "Number of blocks")

        val patternMap = mutableMapOf<String, String>()
        for (blockIndex in 0 until patterns.size) {
            val block = blocks[blockIndex]
            val pattern = patterns[blockIndex]
            assertEquals(pattern.instructions.size, block.instructions.size, "Number of instructions in $blockIndex-th block")

            if (pattern.name.startsWith("$")) {
                val lastValue = patternMap[pattern.name]
                assertTrue("Different labels are mapped into the same pattern ${pattern.name}") {
                    lastValue == null || lastValue == block.label
                }
                patternMap[pattern.name] = block.label
            } else {
                assertEquals(pattern.name, block.label, "Block labels")
            }

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

                    if (patternValue.startsWith("$")) {
                        val lastValue = patternMap[patternValue]
                        assertTrue("Different variables are mapped into the same pattern $patternValue") {
                            lastValue == null || lastValue == blockValue
                        }
                        patternMap[patternValue] = blockValue
                    } else {
                        assertEquals(patternValue, blockValue, "Instruction variables")
                    }
                }
            }
        }
        return patternMap
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
                Phi(Type.Int64, Variable("$3"), listOf(
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
                Add(Type.Int64, Variable("i"), Variable("i"), Int64Const(1)),
                BinaryCompareJump(">", Variable("i"), Int64Const(10), "after_loop"),
                Jump("loop")
        )))
        val blockAfterLoop = Block("after_loop", ArrayDeque(listOf<Instruction>(
                Return(Type.Int64, Variable("x"))
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
                Phi(Type.Int64, Variable("$2"), listOf(
                        "loop_start" to Variable("$1"),
                        "loop" to Variable("$3")
                )),
                Add(Type.Int64, Variable("$3"), Variable("$2"), Int64Const(1)),
                BinaryCompareJump(">", Variable("$3"), Int64Const(10), "after_loop"),
                Jump("loop")
        ))
        val patternAfterLoop = PatternBlock("after_loop", listOf(
                Phi(Type.Int64, Variable("$4"), listOf(
                        "loop" to Variable("$3")
                )),
                Return(Type.Int64, Variable("x"))
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

    @Test
    fun extractBlocksTest1() {
        val instructions = listOf(
                Assign(Variable("x"), Int64Const(42)),
                Assign(Variable("x"), Int64Const(69)),
                Assign(Variable("x"), Int64Const(128)),
                Assign(Variable("x"), Int64Const(91))
        )

        val blocks = SSATransformer.extractBlocks(instructions)
        val blockLabel = blocks[0].label

        val patternBlock = PatternBlock(blockLabel, instructions)
        assertBlocksMatch(blocks, listOf(patternBlock))
    }

    @Test
    fun extractBlocksTest2() {
        val instructions = listOf(
                Label("my_own_label"),
                Assign(Variable("x"), Int64Const(100)),
                BinaryCompareJump(">", Int64Const(0), Int64Const(1), "second_label"),
                Assign(Variable("x"), Int64Const(200)),
                Jump("second_label"),
                Label("second_label"),
                Assign(Variable("y"), Int64Const(0))
        )

        val blocks = SSATransformer.extractBlocks(instructions)

        val patternBlock1 = PatternBlock("my_own_label", listOf(
                Assign(Variable("x"), Int64Const(100)),
                BinaryCompareJump(">", Int64Const(0), Int64Const(1), "second_label"),
                Jump("$1")
        ))
        val patternBlock2 = PatternBlock("$1", listOf(
                Assign(Variable("x"), Int64Const(200)),
                Jump("second_label")
        ))
        val patternBlock3 = PatternBlock("second_label", listOf(
                Assign(Variable("y"), Int64Const(0))
        ))
        assertBlocksMatchOrdered(blocks, listOf(patternBlock1, patternBlock2, patternBlock3))
    }
}