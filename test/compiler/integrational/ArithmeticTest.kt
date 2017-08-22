package compiler.integrational

import org.junit.Test

/**
 * @author Moklev Vyacheslav
 */
internal class ArithmeticTest: RunnerTestBase() {
    @Test
    fun divTest1() = assertIntResults("""
        fun foo(a: i64*, b: i64*, c: i64*): i64 {
            return *a / (*b + *c);
        }
    
        fun main(): i64 {
            var x: i64; 
            var y: i64; 
            var z: i64;
            x = 100;
            y = 17;
            z = 3;
            printInt(foo(&x, &y, &z));
            printInt(foo(&x, &y, &z));
            printInt(foo(&x, &y, &z));
            return 0;
        }
    """, 5, 5, 5)
}