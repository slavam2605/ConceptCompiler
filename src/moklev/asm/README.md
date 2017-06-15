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

In this part of value you can ask -- "is it your own attempt to implement LLVM"? Yes, it is.

Instructions
------------

This table contains a full set of Concept-ASM instructions:

| Instruction                | Mnemonic               |            Description            |
|----------------------------|:----------------------:|-----------------------------------|
| Add(x, y, z)               | x = y + z              | Sum of two numbers                |
| Sub(x, y, z)               | x = y - z              | Difference of two numbers         |
| Mul(x, y, z)               | x = y * z              | Product of two numbers            |
| Div(x, y, z)               | x = y / z              | Quotient of two numbers           |
| Jump(label)                | goto label             | Unconditional branch              |
| IfGreaterJump(x, y, label) | if (x > y) goto label  | Conditional (if greater) branch   |
| IfLessJump(x, y, label)    | if (x < y) goto label  | Conditional (if less) branch      |
| IfEqualsJump(x, y, label)  | if (x == y) goto label | Conditional (if equals) branch    |
| IfNotEqJump(x, y, label)   | if (x != y) goto label | Conditional (if not equals) branch |
| IfGtEqJump(x, y, label)    | if (x >= y) goto label | Conditional (if greater or equals) branch |
| IfLtEqJump(x, y, label)    | if (x <= y) goto label | Conditional (if less or equals) branch |
| Phi(x, [L1, y1], ..., [Ln, yn]) | -                 | Phi node in SSA graph             |
