fun index(a: i64*, i: i64): i64* {
    return (i64*) (a + 8 * i);
}

fun prime_sieve(size: i64): i64 {
    var a: i64*;
    a = (i64*) malloc((size + 1) * 8);
    var i: i64;
    for (i = 2;; i < size; i = i + 1;) {
        if (*index(a, i) == 0) {
            var j: i64;
            for (j = 2;; i * j <= size; j = j + 1;) {
                *index(a, i * j) = 1;
            }
        }
    }
    for (i = 2;; i <= size; i = i + 1;) {
        if (*index(a, i) == 0) {
            printInt(i);
        }
    }
    free(a);
    return 0;
}