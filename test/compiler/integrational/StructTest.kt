package compiler.integrational

import org.junit.Test

/**
 * @author Moklev Vyacheslav
 */
internal class StructTest : RunnerTestBase() {
    @Test
    fun simpleStructTest() = assertIntResults("""
        struct MyStruct {
            first: i64;
            second: i64;
            third: i64;
        }

        fun main(): i64 {
            var a: MyStruct;
            a.first = 1;
            a.second = 2;
            a.third = 3;
            printInt(a.first + a.second + a.third);
            return 0;
        }
    """, 6)
    
    @Test
    fun simpleStructFieldReferenceTest() = assertIntResults("""
        struct SuperStruct {
            a: i64;
            b: i64;
            c: i64;
        }

        fun main(): i64 {
            var struc: SuperStruct;
            struc.a = 1;
            struc.b = 2;
            struc.c = 45;
            var p: i64*;
            p = &(struc.b);
            *p = 169;
            printInt(struc.a + struc.b + struc.c);
            return 0;
        }
    """, 215)
    
    @Test
    fun nestedStructsTest() = assertIntResults("""
        struct MyBasicStruct {
            a: i64;
            b: i64;
        }

        struct MyComplexStruct {
            a: i64;
            b: MyBasicStruct;
            c: i64;
        }

        fun main(): i64 {
            var complexStruct: MyComplexStruct;
            complexStruct.a = 10;
            complexStruct.b.a = 3;
            complexStruct.b.b = 4;
            complexStruct.c = 20;
            printInt(complexStruct.a + complexStruct.b.a + complexStruct.b.b + complexStruct.c);
            return 0;
        }
    """, 37)
    
    @Test
    fun escapedStructPointerTest() = assertIntResults("""
        struct Pair {
            a: i64;
            b: i64;
        }

        fun modifyStruct(pointer: Pair*): i64 {
            (*pointer).a = 10;
            return 0;
        }

        fun main(): i64 {
            var st: Pair;
            st.a = 1;
            st.b = 2;
            modifyStruct(&st);
            printInt(st.a + st.b);
            return 0;
        }
    """, 12)
    
    @Test
    fun notEscapedStructPointerTest() = assertIntResults("""
        struct Pair {
            a: i64;
            b: i64;
        }

        fun main(): i64 {
            var pair: Pair;
            var pairPointer: Pair*;
            pairPointer = &pair;
            (*pairPointer).a = 10;
            (*pairPointer).b = 20;
            printInt(pair.a + pair.b);
            return 0;
        }
    """, 30)
}