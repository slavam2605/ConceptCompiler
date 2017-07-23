fun prime_sieve(size: i64): i64 {
    var a: i64;
    a = malloc((size + 1) * 8);
    var i: i64;
    for (i = 2;; i < size; i = i + 1;) {
        if (*(a + 8 * i) == 0) {
            var j: i64;
            for (j = 2;; i * j <= size; j = j + 1;) {
                *(a + 8 * i * j) = 1;
            }
        }
    }
    for (i = 2;; i <= size; i = i + 1;) {
        if (*(a + 8 * i) == 0) {
            printInt(i);
        }
    }
    free(a);
    return 0;
}