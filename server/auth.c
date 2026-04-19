#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "auth.h"

#define USERS_FILE "users.txt"

// Kullanıcının sistemde var olup olmadığını kontrol eder. (1 varsa, 0 yoksa)
static int user_exists(const char* username) {
    FILE* file = fopen(USERS_FILE, "r");
    if (!file) return 0;

    char line[256];
    while (fgets(line, sizeof(line), file)) {
        char file_user[MAX_USERNAME], file_pass[MAX_PASSWORD];
        if (sscanf(line, "%63[^:]:%63s", file_user, file_pass) == 2) {
            if (strcmp(file_user, username) == 0) {
                fclose(file);
                return 1;
            }
        }
    }
    
    fclose(file);
    return 0;
}

int register_user(const char* username, const char* password) {
    if (user_exists(username)) {
        return 0; // Kullanıcı zaten var
    }

    FILE* file = fopen(USERS_FILE, "a");
    if (!file) {
        return 0; // Dosya açılamadı
    }

    fprintf(file, "%s:%s\n", username, password);
    fclose(file);
    return 1;
}

int login_user(const char* username, const char* password) {
    FILE* file = fopen(USERS_FILE, "r");
    if (!file) return 0;

    char line[256];
    while (fgets(line, sizeof(line), file)) {
        char file_user[MAX_USERNAME], file_pass[MAX_PASSWORD];
        if (sscanf(line, "%63[^:]:%63[^\n]", file_user, file_pass) == 2) {
            if (strcmp(file_user, username) == 0 && strcmp(file_pass, password) == 0) {
                fclose(file);
                return 1; // Giriş başarılı
            }
        }
    }

    fclose(file);
    return 0; // Giriş başarısız
}
