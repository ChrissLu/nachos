/*
 * exec2.c
 *
 */

#include "syscall.h"

int
main (int argc, char *argv[])
{
    char *prog = "creat5.coff";
    int pid;
    char *childargv[] = {
	"test.txt"};

    pid = exec (prog, 1, childargv);
    if (pid < 0) {
	exit (-1);
    }
    exit (0);
}