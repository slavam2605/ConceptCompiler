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
    
    @Test
    fun manyArgumentsRecursiveTest() = assertIntResults("""
        fun foo(a: i64, b: i64, c: i64,
              d: i64, e: i64, f: i64,
              g: i64, h: i64, i: i64, j: i64): i64 {
            if a > 0 {
                return foo(a - 1, b - 1, c - 1, d - 1, e - 1, 
                           f - 1, g - 1, h - 1, i - 1, j - 1);
            }
            return a + b + c + d + e + f + g + h + i + j;
        }

        fun main(): i64 {
            printInt(foo(10, 12, 13, 14, 15, 16, 17, 18, 19, 22));
            return 0;
        }
    """, 56)
}