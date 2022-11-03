#include "syscall.h"

// open the same file multiple times and write with different fds
int
main (int argc, char *argv[])
{
    char *file_name = "testfile.txt";
    int fd = creat(file_name);
    if(fd < 0){
        printf("...failed (error occured when create file)\n");
        exit(-1);
    }
    if(close(fd) < 0){
        printf("...failed (error occured when close file)\n");
        exit(-1);
    }

    int fd1 = open(file_name);
    if(fd1 != 2){
        printf("...failed (fd1 = %d, expected to be 2)\n", fd1);
        exit(-1);
    }
    const char* str1 = "Hello World!";
    int len1 = strlen(str1);
    int wc1 = write(fd1, str1, len1);
    if(wc1 != len1){
        printf("...failed (wc1 = %d, expected to be %d)\n", wc1, len1);
        exit(-1);
    }

    int fd2 = open(file_name);
    if(fd2 != 3){
        printf("...failed (fd2 = %d, expected to be 3)\n", fd2);
        exit(-1);
    }
    const char* str2 = "Banana";
    int len2 = strlen(str2);
    int wc2 = write(fd2, str2, len2);
    if(wc2 != len2){
        printf("...failed (wc2 = %d, expected to be %d)\n", wc2, len2);
        exit(-1);
    }

    int fd3 = open(file_name);
    if(fd3 != 4){
        printf("...failed (fd3 = %d, expected to be 4)\n", fd3);
        exit(-1);
    }
    const char* str3 = "XXX";
    int len3 = strlen(str3);
    int wc3 = write(fd3, str3, len3);
    if(wc3 != len3){
        printf("...failed (wc3 = %d, expected to be %d)\n", wc3, len3);
        exit(-1);
    }

    char buf[20];
    const char* str = "XXXanaWorld!";
    int len = strlen(str);
    fd = open(file_name);
    int rc = read(fd, buf, len);
    if(rc != len){
        printf("...failed (rc = %d, expected to be %d)\n", rc, len);
        exit(-1);
    }
    if(strcmp(buf, str) != 0){
        printf("...failed (str = %s, expected to be %s)\n", buf, str);
        exit(-1);
    }

    printf("...passed\n");
    return 0;
}