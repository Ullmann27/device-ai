#include <stdlib.h>
#include <time.h>
int main(void) {
    const size_t bytes = 64 * 1024 * 1024;
    volatile unsigned char *memory = malloc(bytes);
    if (!memory) return 2;
    for (size_t i = 0; i < bytes; i += 4096) memory[i] = 1;
    const clock_t start = clock();
    while ((double)(clock() - start) / CLOCKS_PER_SEC < 1.5) memory[0]++;
    free((void *)memory);
    return 0;
}
