#include <stdio.h>

int main() {
    char name[100];
    char language[100]; 
    
    printf("What's your name? ");
    scanf("%99s", name);
    
    printf("What's your favorite programming language? ");
    scanf("%99s", &language);
    
    printf("Hello %s! Your favorite programming language is %s.\n", name, language);
    return 0;
}