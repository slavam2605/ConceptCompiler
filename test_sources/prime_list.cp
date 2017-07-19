fun prime_test(x: i64): i64 {
    var i: i64;
    for (i = 2;; i <= x / 2; i = i + 1;) {
        var rem: i64;
        rem = x % i;
        if rem == 0 {
            return i;
        }
    }
    return 1;
}

fun keks(n: i64): i64 {
    var i: i64;
    for (i = 2;; i <= n; i = i + 1;) {
        if prime_test(i) == 1 {
            printInt(i);
        }
    }
    return 0;
}
