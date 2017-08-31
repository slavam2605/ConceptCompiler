struct MyStruct {
    first: i64;
    second: i64;
    third: i64;
}

fun foo(): i64 {
    var a: MyStruct;
    a.first = 1;
    a.second = 2;
    a.third = 3;
    var p: i64*;
    p = &(a.first);
    *p = 42;
    return a.first + a.second + a.third;
}