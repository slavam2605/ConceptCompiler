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

    @Test
    fun divTest2() = assertIntResults("""
        fun divALot(a: i64, b: i64, c: i64, d: i64, e: i64, f: i64, g: i64, h: i64): i64 {
            var x: i64;
            var y: i64;
            var z: i64;
            var t: i64;
            x = 156; 
            y = 405483668029440;              
            z = e / f / g / 3;                 // 88704
            t = (y / d) / (z / 3);             // 1371359808
            return (y + t) / (x + z);          // 4563189729
        }

        fun main(): i64 {
            printInt(divALot(2432902008176640000, 30, 20, 10, 479001600, 120, 15, 3));
            return 0;
        }
    """, 4563189729)

    @Test
    fun divTest3() = assertIntResults("""
        fun divStackArguments(a: i64, b: i64, c: i64, d: i64, e: i64, f: i64, g: i64, h: i64): i64 {
            return g / h;
        }

        fun main(): i64 {
            printInt(divStackArguments(0, 0, 0, 0, 0, 0, 15, 3));
            return 0;
        }
    """, 5)

    @Test
    fun divTest4() = assertIntResults("""
        fun divALot(a: i64, b: i64, c: i64, d: i64, e: i64, f: i64, g: i64, h: i64): i64 {
            var x: i64;
            var y: i64;
            var z: i64;
            var t: i64;
            x = a / b / c / d / e / f / g / h; // 156
            y = a / b / c / d;                 // 405483668029440
            z = e / f / g / h;                 // 88704
            t = (y / d) / (z / h);             // 1371359808
            printInt(x);
            printInt(y);
            printInt(z);
            printInt(t);
            return (y + t) / (x + z);          // 4563189729
        }

        fun main(): i64 {
            printInt(divALot(2432902008176640000, 30, 20, 10, 479001600, 120, 15, 3));
            return 0;
        }
    """, 156, 405483668029440, 88704, 1371359808, 4563189729)
}