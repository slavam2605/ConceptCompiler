all: runner.o
	yasm -felf out.asm -o out.o -m amd64
	gcc runner.o out.o -o main

debug: runner.o
	yasm -felf out.asm -o out.o -m amd64 -g dwarf2
	gcc runner.o out.o -o main -g

runner.o: runner.c
	gcc -c runner.c -o runner.o
