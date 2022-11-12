#include "syscall.h"


// create a new file for exec test
int
main (int argc, char *argv[])
{
    char *file_name = argv[argc-1];
    int fd = creat(file_name);
    if(fd >= 0){
        if(fd != 2){
            printf("...failed (fd = %d, expected to be 2)\n", fd);
            exit(-1);
        }
        printf("...passed (fd = %d)\n", fd);
    } else{
        printf("...failed (%d)\n", fd);
        exit(-1);
    }

    return 0;
}