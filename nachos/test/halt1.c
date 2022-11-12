/*
 * halt1.c
 *
 * Simple program for testing halt.  
 */

#include "syscall.h"

int 
main (int argc, char *argv[])
{
    halt();
    return 0;
}