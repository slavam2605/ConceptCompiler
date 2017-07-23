fun deref_test(base: i64): i64 {
  var ptr: i64;
  ptr = malloc(10 * 8);
  var i: i64;
  var result: i64;
  result = 1;
  for (i = 0;; i < 10; i = i + 1;) {
    *(ptr + 8 * i) = result;
    result = result * base;
  }
  return ptr;
}