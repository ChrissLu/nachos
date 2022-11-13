#include "syscall.h"

int
main (int argc, char *argv[]){

    char *pipe_name = argv[0];

    int p1 = open(pipe_name);
    if(p1 < 0){
        printf("...failed (fd = %d, expected to be 2)\n", p1);
        exit(-1);
    }

    char str[4100];
    for(int i=0;i<4100;++i)
        str[i] = i%26+'a';
    str[4099] = '\0';

    char buf[4100];

    int nr;
    int tot = 0;
    do{
        nr = read(p1, buf + tot, 4099);
        tot+=nr;
    }while(nr && tot<4099);

    printf("%d\n", strcmp(str, buf));

    //printf("%s\n", buf);

    //printf("read %d chars from pipe\n", nr);

    return 0;
}