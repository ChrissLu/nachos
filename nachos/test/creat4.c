#include "syscall.h"

// create the same file multiple times
int
main (int argc, char *argv[])
{
    char *file_name = "testfile.txt";
    unlink(file_name);

    for(int i=2;i<=16;++i){
        int fd = creat(file_name);
        if(fd == -1){
            if(i != 16){
                printf("...failed (error occured when create file)\n");
                exit(-1);
            }
        } else if(fd != i){
            printf("...failed (fd = %d, expected to be %d)\n", fd, i);
            exit(-1);
        }
    }
    
    for(int i=2;i<=15;++i){
        if(close(i) < 0){
            printf("...failed (error occured when close file)\n");
            exit(-1);
        }
    }

    printf("...passed\n");
    return 0;
}