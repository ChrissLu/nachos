#include "syscall.h"

//unlink a file
int
main (int argc, char *argv[])
{
    char *file_name = "testfile.txt";
    int fd = creat(file_name);
    char* str = "hello";
    int len = strlen(str);
    int wc = write(fd, str, strlen(str));
    if(wc != len){
        exit(-1);
    }

    if(fd >= 0){
        int r = unlink(file_name);
        if(r < 0){
            printf("...failed (error occured when unlink)\n");
            exit(-1);
        }
    } else{
        printf("...failed (%d)\n", fd);
        exit(-1);
    }
    if(close(fd) < 0){
        exit(-1);
    }
    fd = open(file_name);
    printf("new fd: %d\n", fd);
    char buf[6];
    int rc = read(fd, buf, len);
    if(rc != len){
        exit(-1);
    }
    printf("str: %s\n", buf);
    int r = close(fd);
    printf("%d\n", r);
    printf("...passed\n");
    return 0;
}