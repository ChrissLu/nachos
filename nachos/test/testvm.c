#include "syscall.h"

#define N 10

int
main (int argc, char *argv[])
{
    char *prog = "write10.coff";
    int pid_list[N];
    int status;

    char prefix[10] = "aa";
    char *argv[1];

    for(int i=0;i<N;++i){
        char num[2];
        num[0] = i+'0';
        num[1] = '\0';
        prefix[2] = '\0';
        argv[0] = strcat(prefix, num);

        pid_list[i] =  exec (prog, 1, argv);
        if(pid_list[i] <= 0){
            exit(-1);
        }
    }
    for(int i=0;i<N;++i){
        int r = join (pid_list[i], &status);
        if(r==0)
            exit(-2);
        if(r<0)
            exit(-3);
        //printf("%d\n", status);
    }

    return 0;
}