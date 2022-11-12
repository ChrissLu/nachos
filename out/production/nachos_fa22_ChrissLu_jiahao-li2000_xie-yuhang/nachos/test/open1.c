#include "syscall.h"

// open a non-existing file
int
main (int argc, char *argv[])
{
    char *file_name = "testfile.txt";
    unlink(file_name);

    int fd = open(file_name);
    if(fd != -1){
        printf("...failed (fd = %d, expected to be -1)\n", fd);
        exit(-1);
    }

    printf("...passed\n");
    return 0;
}