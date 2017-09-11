struct MyStruct {
    a: i64;
    b: i64;
}

fun foo(s: MyStruct): i64 {
    s.a = 10;
    printInt(s.a);
    printInt(s.b);
    return 0;
}

fun main(): i64 {
    var ss: MyStruct;
    ss.a = 42;
    ss.b = 69;
    foo(ss);
    printInt(ss.a);
    printInt(ss.b);
    return 0;
}