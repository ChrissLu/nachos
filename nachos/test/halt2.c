/*
 * halt2.c
 * test halt() called by a non-root process
 */

#include "syscall.h"

int
main (int argc, char *argv[])
{
    char *prog = "halt1.coff";

    exec (prog, 0, 0);
    return 0;
}