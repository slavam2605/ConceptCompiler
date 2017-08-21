package compiler.integrational

import org.junit.Test

/**
 * @author Moklev Vyacheslav
 */
internal class FunctionArgumentsTest : RunnerTestBase() {
    @Test
    fun manyArgumentsTest() = assertIntResults("""
        fun f(a: i64, b: i64, c: i64,
              d: i64, e: i64, f: i64,
              g: i64, h: i64, i: i64, j: i64): i64 {
            return a + b + c + d + e + f + g + h + i + j;
        }

        fun main(): i64 {
            printInt(f(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
            return 0;
        }
    """, 55)
}