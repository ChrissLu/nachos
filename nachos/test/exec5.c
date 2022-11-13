#include "syscall.h"

int
main (int argc, char *argv[])
{
    char *prog = "echo.coff";
    int pid;
    char *ptr = (char *) 0xBADFFFFF;
    pid = exec (prog, 5, ptr);
    printf("pid: %d\n", pid);
    if (pid < 0) {
	exit (-1);
    }
    int r = join(pid, 0);
    printf("r: %d\n", r);
    exit (0);
}
