Concept-ASM
===========

Motivation
----------

A high-level language used to operate with potentially infinite number of
variables. But a processor has only limited amount of registers -- areas of
fast memory. A problem of assigning this finite amount of registers to 
unbounded amount of language variables is called the register allocation 
problem. It is very inconveniently to solve this problem with a high-level 
language, so the Concept-ASM is used as intermediate representation of the code.

SSA form
--------

SSA (static single assignment) form of code means that any variable can have only
initialization, but not modification. Example of code not in SSA form:
```
a = 1
b = 2
a = a + b
b = a - b
c = a + b
```
But this code may be modified to the SSA form equivalently:
```
a = 1
b = 2
a.1 = a + b
b.1 = a.1 - b
c = a.1 + b.1
```

But not any code can be transformed in such way. To make SSA form equivalent to 
any program one can use the following approach. All code is divided into *basic blocks*:
blocks of instructions with labels in the beginning. All jump instructions can jump only
on label, so only to the beginning of some basic block. Another thing to add is the `phi`
instruction. This instruction takes list of all possible incoming labels (labels of 
blocks that possibly can jump ti the current block) and the value for each label:
```
L0:
a.1 = 1
jump L2
L1:
a.2 = 2
jump L2
L2:
a.3 = phi [L0, a.1], [L1, a.2]
```

This instruction assign `a.3 = a.1` if control flow came from `L0` to `L2` and
`a.3 = a.2` otherwise. This instruction is typically used with `if`-like and `while`-like
structures. For example, this code:
```
a = 1
while (a < 10) {
   a = a + 1
}
```

can be compiled in this SSA code:
```
L_start:
a = 1
if (a < 10) jump L_loop
jump L_after
L_loop:
a.1 = phi [L_start, a], [L_loop, a.2]
a.2 = a.1 + 1
if (a.2 < 10) jump L_loop
jump L_after
L_after:
```

So `phi` instruction can take even values from future (`a.2` is declared after
`a.1`). Also a restriction is that all `phi` instructions in a basic block cannot be 
preceded with other instructions (basic block must have an optional prefix of
`phi` instructions and no `phi` instructions inside). 

In this part of text you can ask -- "is it your own attempt to implement LLVM"? Yes, it is.

Type system
-----------

Concept-ASM is a strongly typed language. Types are used in verification of code, code 
generation and optimizations. In Concept-ASM there are 5 groups of types:
* Primitive integer types
* Primitive floating types
* Pointer types
* Struct types
* Blob types

Primitive integer types are predefined integer types: `int64`, `int32`, `int16`, `int8`.
Primitive floating types are predefined floating types: `double`, `float`.
Pointer types are the ordinary pointers to a memory of arbitrary type. Examples of 
pointer types: `int64*`, `double*`, `{int64, int32}*`.
Struct types or tuples are the types that contain some list of other types. Examples of
struct types: `{int64, int64}`, `{double, int64, int64*}`. Struct types can be nested:
`{int64, {float, float}, int64*}` and even empty: `{}`. Blob types are types of fixed 
size and with unknown structure. For example type `blob(128)` is a type of size 128 bit.

Calling convention
------------------
Concept-ASM functions follow System V calling convention for primitive arguments of size 8:
integers are passed in registers RDI, RSI, RDX, RCX, R8, R9, the rest are passed in stack,
doubles are passed in registers XMM[0-7]. Any integer of size less than 8 is passed in 
the entire register, so 6 parameters of 1-byte integer type will be passed in the lower 
byte of RDI, RSI, RDX, RCX, R8, R9. The same is for floating numbers of size less than 8.

Complex values like structures and big values are passed recursively: they are treated as
'unwrapped' values (struct(int64, int32) will be treated as two arguments of type int64
and int32 respectively). Big values (bigger than 8 bytes) are cut in pieces of 8 bytes
and passed like integers. The example:
```
struct A {
    x: i64;
    y: double;
    z: i32;
}

fun foo(arg1: i64, arg2: A, arg3: double, arg4: i128, arg5: i256) 
```
For this function `arg1` will be passed in RDI, `arg2.x` in RSI, `arg2.y` in XMM0, 
`arg2.z` in EDX (the higher part of RDX will remain not initialized), `arg3` in XMM1,
`arg4` in RCX and R8, `arg5` in R9 and rest 24 bytes of `arg5` will be passed in stack.

The return value is passed in RAX, RDX, RDI, RSI, RCX, R8, R9, R10, R11 and the rest 
on stack. Floating parts of the return value are passed in XMM[0-7] and the rest
on stack.

Instructions
------------

This table contains a full set of Concept-ASM instructions:

| Instruction                | Mnemonic               |            Description            |
|----------------------------|:----------------------:|-----------------------------------|
| Add(x, y, z)               | `x = y + z`            | Sum of two numbers                |
| Sub(x, y, z)               | `x = y - z`            | Subtraction of two numbers        |
| Mul(x, y, z)               | `x = y * z`            | Product of two numbers            |
| Div(x, y, z)               | `x = y / z`            | Quotient of two numbers           |
| Mod(x, y, z)               | `x = y / z`            | Remainder of two numbers          |
| Jump(label)                | `goto label`           | Unconditional branch              |
| BinaryCompareJump(x, y, op, label) | `if (x op y) goto label`| Conditional (>, >=, <, <=, ==, !=) branch |
| Assign(x, y)               | `x = y`                | Assignment of variables           |
| Call(f, x, y, z, ...)      | `f(x, y, z, ...)`      | Call of subroutine                |
| AssignCall(f, a, x, y, ...)| `a = f(x, y, ...)`     | Get value of function             |
| Phi(x, [L1, y1], ..., [Ln, yn]) | —                | Phi node in SSA graph             |
| Return(x)                  | `return x`             | Return the value from function    |
| Load(x, y)                 | `x = *y`               | Load from memory                  |
| Store(x, y)                | `*x = y`               | Store to memory                   |

Internal instructions:

| Instruction                |                       Description                          |
|----------------------------|------------------------------------------------------------|
| ExternalAssign(x)          | Marks `x` as externally initialized variable               |
| NotPropagatableAssign(x, y)| Assign `x := y` and not propagation due to optimization phase|