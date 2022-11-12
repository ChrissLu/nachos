/*
 * exec4.c
 *
 */

#include "syscall.h"

int
main (int argc, char *argv[])
{
    char *prog = "except1.coff";
    int pid;

    pid = exec (prog, 0, 0);
    if (pid < 0) {
	exit (-1);
    }
    exit (0);
}
