/*
 * exec3.c
 * test wrong prog
 */

#include "syscall.h"

int
main (int argc, char *argv[])
{
    char *prog = "doesnotexist.coff";
    int pid;

    pid = exec (prog, 0, 0);
    if (pid < 0) {
	exit (-1);
    }
    exit (999);
}