Concept compiler
================

This repository contains several tools for compiling some languages to x86-64 assembly 
(with System V calling convention). For now consists of two parts:
* Concept ASM
    * Intermediate representation, three address code
    * Transforms to SSA form
    * Performs some optimizations
        * Constant/variable propagation
        * Constant folding / branch elimination
        * Removal of unused expressions
    * Register allocation
        * Graph coloring
        * Spilling
        * Coloring with regard to loops
        * Targeting
        * Register avoiding
        * Node coalescing
* Dummy lang
    * Quite simple higher-level language to test capabilities of Concept ASM