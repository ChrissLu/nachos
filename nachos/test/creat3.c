#include "syscall.h"

// create a existing file and truncate it
int
main (int argc, char *argv[])
{
    char *file_name = "testfile.txt";
    unlink(file_name);

    int fd = creat(file_name);
    if(fd < 0){
        printf("...failed (%d)\n", fd);
        exit(-1);
    }

    char *str = "write something in new file!\nHello World!\n";
    int len = strlen (str);
    int cnt = write(fd, str, len);
    if(cnt != len){
        printf("...failed (writeCnt = %d, expected to be %d)\n", cnt, len);
        exit(-1);
    }
    
    if(close(fd) < 0){
        printf("...failed (error occured when close file)\n");
        exit(-1);
    }
    
    int new_fd = creat(file_name);
    if(new_fd != fd){
        printf("...failed (fd does not the same)\n");
        exit(-1);
    }

    char buf[50];
    int rc = read(fd, buf, len);
    if(rc < 0){
        printf("...failed (error occured when read file)\n");
        exit(-1);
    }

    const char* expected = "";
    if(strcmp(buf, expected) != 0){
        printf("...failed (the content should be null)\n");
        exit(-1);
    }

    printf("...passed\n");
    return 0;
}
