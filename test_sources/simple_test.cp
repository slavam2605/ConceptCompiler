fun main(): i64 {
    var a: i64 = 123;
    var p: i64* = &a;
    *p = 789;
    printInt(a);
    return 0;
}