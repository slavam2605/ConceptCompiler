package compiler.integrational

import org.junit.Test

/**
 * @author Moklev Vyacheslav
 */
internal class DifferentTypesTest : RunnerTestBase() {
    @Test
    fun typeCastTest1() = assertIntResults("""
        fun main(): i64 {
            var x: i32 = (i32) 2000000000;
            var y: i32 = (i32) 2000000000;
            var z: i64 = (i64) (x + y);
            printInt(z);
            return 0;
        }
    """, -294967296)
    
    @Test
    fun differentTypesArguments() = assertIntResults("""
        fun sum(a: i64, b: i32, c: i64, d: i32, e: i64, f: i64, g: i32, h: i32, i: i64, j: i32): i64 {
            return a + (i64) b + c + (i64) d + e + f + (i64) g + (i64) h + i + (i64) j;
        }

        fun main(): i64 {
            printInt(sum(1, 1, 1, 1, 1, 1, 1, 1, 1, 1));
            return 0;
        }
    """, 10)
}