#include "syscall.h"

#define N 10

int
main (int argc, char *argv[])
{
    char *prog = "swap5.coff";
    int pid_list[N];
    int status;

    for(int i=0;i<N;++i){
        pid_list[i] =  exec (prog, 0, 0);
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
        printf("%d\n", status);
    }

    return 0;
}