#include "syscall.h"

// test the pipe
int
main (int argc, char *argv[])
{
    char* pipe1 = "/pipe/p1";
    char* pipe2 = "/pipe/p2";

    int p1 = creat(pipe1);
    int p2 = creat(pipe2);

    char* str1 = "Hello";
    char* str2 = "World";

    char* argv1[2];
    argv1[0] = pipe1;
    argv1[1] = str1;

    char* argv2[2];
    argv2[0] = pipe2;
    argv2[1] = str2;

    int producer1 = exec("producer.coff", 2, argv1);
    if(producer1 < 0){
        printf("producer1 = %d\n", producer1);
    }
    int consumer1 = exec("consumer1.coff", 2, argv1);
    if(consumer1 < 0){
        printf("consumer1 = %d\n", consumer1);
    }

    int producer2 = exec("producer.coff", 2, argv2);
    if(producer2 < 0){
        printf("producer2 = %d\n", producer2);
    }
    int consumer2 = exec("consumer1.coff", 2, argv2);
    if(consumer2 < 0){
        printf("consumer2 = %d\n", consumer2);
    }

    join(producer1, 0);
    join(consumer1, 0);
    join(producer2, 0);
    join(consumer2, 0);

    return 0;
}