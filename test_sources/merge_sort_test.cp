fun mergeSort(a: i64*, b: i64*, l: i64, r: i64): i64 { 
    if r - l > 1 {
        var m: i64 = (l + r) / 2;
        mergeSort(a, b, l, m);
        mergeSort(a, b, m, r);
        var p1: i64 = l;
        var p2: i64 = m;
        var p: i64 = l;
        for (1;; p1 < m && p2 < r; 1;) {
            var a1: i64 = *(i64*)(a + 8 * p1);
            var a2: i64 = *(i64*)(a + 8 * p2);
            if a1 <= a2 {
                *(i64*)(b + 8 * p) = a1;
                p1 = p1 + 1;
                p = p + 1;
            } else {
                *(i64*)(b + 8 * p) = a2;
                p2 = p2 + 1;
                p = p + 1;
            }
        }
        for (1;; p1 < m; 1;) {
            *(i64*)(b + 8 * p) = *(i64*)(a + 8 * p1);
            p1 = p1 + 1;
            p = p + 1;
        }
        for (1;; p2 < r; 1;) {
            *(i64*)(b + 8 * p) = *(i64*)(a + 8 * p2);
            p2 = p2 + 1;
            p = p + 1;
        }
        var i: i64;
        for (i = l;; i < r; i = i + 1;) {
            *(i64*)(a + 8 * i) = *(i64*)(b + 8 * i);
        }
    }
    return 0;
}

fun foo(a: i64*, b: i64*, size: i64): i64 {
    var i: i64;
    for (i = 0;; i < size; i = i + 1;) {
        printInt(*(i64*)(a + 8 * i));
    }
    mergeSort(a, b, 0, size);
    for (i = 0;; i < size; i = i + 1;) {
        printInt(*(i64*)(a + 8 * i));
    }
    return 0;
}