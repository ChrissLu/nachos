#include "syscall.h"

int
main (int argc, char *argv[]){

    char *pipe_name = argv[0];

    int p1 = open(pipe_name);
    if(p1 < 0){
        printf("consumer ...failed (fd = %d, expected to be 2)\n", p1);
        exit(-1);
    }

    char *str = argv[1];

    char buf[6];

    for(int i=0;i<1000;++i){
        read(p1, buf, 5);
        if(strcmp(str, buf) != 0)
            printf("Error!\n");
    }
    printf("...passed\n");

    //printf("%s\n", buf);

    //printf("read %d chars from pipe\n", nr);

    return 0;
}