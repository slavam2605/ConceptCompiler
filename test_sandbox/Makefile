all:
	yasm -felf out.asm -o out.o -m amd64
	gcc runner.c out.o -o main

debug:
	yasm -felf out.asm -o out.o -m amd64 -g dwarf2
	gcc runner.c out.o -o main -g
