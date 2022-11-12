#include "syscall.h"


// create a new file, write something into it and close the file
int
main (int argc, char *argv[])
{
    char *file_name = "testfile.txt";
    int fd = creat(file_name);
    if(fd >= 0){
        char *str = "write something in new file!\nHello World!\n";
        int len = strlen (str);
        int cnt = write(fd, str, len);
        if(cnt != len){
            printf("...failed (writeCnt = %d, expected to be %d)\n", cnt, len);
            exit(-1);
        } else{
            int r = close(fd);
            if(r < 0){
                printf("...failed (error occured when close file)\n");
                exit(-1);
            }
        }
        
    } else{
        printf("...failed (%d)\n", fd);
        exit(-1);
    }

    printf("...passed\n");
    return 0;
}