#include "syscall.h"

// make sure always return the smallest available fd
int
main (int argc, char *argv[])
{
    char *file_name1 = "testfile1.txt";
    int fd1 = creat(file_name1);
    if(fd1 != 2){
        printf("...failed (fd1 = %d, expected to be 2)\n", fd1);
        exit(-1);
    }

    char *file_name2 = "testfile2.txt";
    int fd2 = creat(file_name2);
    if(fd2 != 3){
        printf("...failed (fd2 = %d, expected to be 3)\n", fd2);
        exit(-1);
    }

    if(close(0) < 0){
        printf("...failed (error occured when close stdin)\n");
        exit(-1);
    }

    if(close(fd1) < 0){
        printf("...failed (error occured when close file)\n");
        exit(-1);
    }

    char *file_name3 = "testfile3.txt";
    int fd3 = creat(file_name3);
    if(fd3 != 0){
        printf("...failed (fd3 = %d, expected to be 2)\n", fd3);
        exit(-1);
    }

    printf("...passed\n");
    return 0;
}
