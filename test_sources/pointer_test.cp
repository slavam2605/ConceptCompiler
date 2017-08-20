fun pointer_test(): i64 {
    var a: i64;
    var p: i64*;
    p = &a;
    *p = 42;
    return a;
}