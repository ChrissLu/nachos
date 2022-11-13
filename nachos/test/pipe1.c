#include "syscall.h"

// main is producer and main exec a consumer
int
main (int argc, char *argv[])
{
    char *pipe_name = "/pipe/p1";

    int p1 = creat(pipe_name);
    if(p1 < 0){
        printf("...failed (fd = %d, expected to be 2)\n", p1);
        exit(-1);
    }

    char str[4100];
    for(int i=0;i<4100;++i)
        str[i] = i%26+'a';
    str[4099] = '\0';

    int len = strlen(str);
    int cpid = exec("consumer.coff", 0, 0);
    if(cpid < 0){
        printf("...failed (child pid = %d)\n", cpid);
        exit(-1);
    }

    int nw = write(p1, str, len);
    //printf("write %d chars to pipe\n", nw);

    int cret;
    int r = join(cpid, &cret);
    if (r > 0) {
	    printf ("...passed (status from child = %d)\n", cret);
    } else if (r == 0) {
        printf ("...child exited with unhandled exception\n");
        exit (-1);
    } else {
        printf ("...failed (r = %d)\n", r);
        exit (-1);
    }

    // printf("...passed\n");
    return 0;
}