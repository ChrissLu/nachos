#include "syscall.h"

int
main (int argc, char *argv[])
{
    char *pipe_name = argv[0];
    char *str = argv[1];

    int p1 = open(pipe_name);
    if(p1 < 0){
        printf("producer %s ...failed (fd = %d, expected to be 2)\n", str, p1);
        exit(-1);
    }
    
    int len = strlen(str);
    
    int nw = write(p1, str, len);

    return 0;
}