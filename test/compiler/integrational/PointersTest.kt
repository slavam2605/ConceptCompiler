package compiler.integrational

import org.junit.Test

/**
 * @author Moklev Vyacheslav
 */
internal class PointersTest : RunnerTestBase() {
    @Test
    fun localVariableReference1() = assertIntResults("""
        fun main(): i64 {
            var a: i64;
            a = 123;
            var p: i64*;
            p = &a;
            *p = 789;
            printInt(a);
            return 0;
        }
    """, 789)
    
    @Test
    fun localVariableReference2() = assertIntResults("""
        fun foo(p: i64*): i64 {
            *p = 190;
            return 0;
        }

        fun main(): i64 {
            var a: i64;
            a = 10;
            foo(&a);
            printInt(a);
            return 0;
        }
    """, 190)
    
    @Test
    fun localVariableReference3() = assertIntResults("""
        fun main(): i64 {
            var x: i64;
            var y: i64;
            var z: i64;
            var t: i64;
            var p: i64*;
            var q: i64*;
            var w: i64**;
            x = 10;
            y = 20;
            z = 30;
            t = 40;
            setOverPointer(&p, &x, 1);
            setOverPointer(&q, &x, 2);
            setOverPointer(&p, &y, 3);
            setOverPointer(&q, &z, 1);
            p = &t;
            w = &p;
            **w = 42;
            printInt(x);
            printInt(y);
            printInt(z);
            printInt(t);
            printInt(*p);
            printInt(*q);
            printInt(**w);
            return 0;
        }

        fun setOverPointer(pointer: i64**, address: i64*, value: i64): i64 {
            *pointer = address;
            **pointer = value;
            return 0;
        }
    """, 2, 3, 1, 42, 42, 1, 42)
    
    @Test
    fun externalMemoryPointerTest() = assertIntResults("""
        fun main(): i64 {
            var p: i64*;
            p = (i64*) malloc(10 * 8);
            var i: i64;
            var currentElement: i64;
            currentElement = 1;
            for (i = 0;; i < 10; i = i + 1;) {
                *(i64*)((i64) p + 8 * i) = currentElement;
                currentElement = currentElement * 2;
            }
            for (i = 1;; i < 10; i = i + 1;) {
                *(i64*)((i64) p + 8 * i) = *(i64*)((i64) p + 8 * i) + *(i64*)((i64) p + 8 * i - 8);
            }
            printInt(*(i64*)((i64) p + 8 * 9));
            return 0;
        }
    """, 1023)
}