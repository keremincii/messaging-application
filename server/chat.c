#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <pthread.h>
#ifdef _WIN32
#include <winsock2.h>
#else
#include <sys/socket.h>
#include <unistd.h>
#endif

#include "chat.h"
#include "crypto.h"
#include "auth.h"

Client clients[MAX_CLIENTS];
pthread_mutex_t clients_mutex = PTHREAD_MUTEX_INITIALIZER;

#ifdef _WIN32
#define close_socket closesocket
#else
#define close_socket close
#endif

/* ── Bekleyen Mesajlar (Offline kullanicilara) ─────────────── */
#define MAX_PENDING 500

typedef struct {
    char sender[MAX_USERNAME];
    char recipient[MAX_USERNAME];
    char message[BUFFER_SIZE];
    int active;
} PendingMsg;

static PendingMsg pending[MAX_PENDING];
static pthread_mutex_t pending_mutex = PTHREAD_MUTEX_INITIALIZER;

/* Bekleyen mesaj kaydet */
static void store_pending(const char* sender, const char* recipient, const char* message) {
    pthread_mutex_lock(&pending_mutex);
    for (int i = 0; i < MAX_PENDING; i++) {
        if (!pending[i].active) {
            strncpy(pending[i].sender, sender, MAX_USERNAME - 1);
            strncpy(pending[i].recipient, recipient, MAX_USERNAME - 1);
            strncpy(pending[i].message, message, BUFFER_SIZE - 1);
            pending[i].active = 1;
            printf("[*] Mesaj kuyruga alindi: %s -> %s\n", sender, recipient);
            fflush(stdout);
            break;
        }
    }
    pthread_mutex_unlock(&pending_mutex);
}

/* Kullanici online olunca bekleyen mesajlarini ilet */
static void deliver_pending(const char* username, socket_t sock) {
    pthread_mutex_lock(&pending_mutex);
    for (int i = 0; i < MAX_PENDING; i++) {
        if (pending[i].active && strcmp(pending[i].recipient, username) == 0) {
            char buffer[BUFFER_SIZE];
            snprintf(buffer, sizeof(buffer), "MSG:%s:%s\n", pending[i].sender, pending[i].message);
            send(sock, buffer, strlen(buffer), 0);
            printf("[*] Bekleyen mesaj iletildi: %s -> %s\n", pending[i].sender, username);
            fflush(stdout);
            pending[i].active = 0;
        }
    }
    pthread_mutex_unlock(&pending_mutex);
}

/* ── Kullanici Listesi Gonder ──────────────────────────────── */
void send_userlist(int client_index) {
    char buffer[BUFFER_SIZE];
    strcpy(buffer, "USERLIST:");

    pthread_mutex_lock(&clients_mutex);
    int first = 1;
    for (int i = 0; i < MAX_CLIENTS; i++) {
        if (clients[i].is_active && i != client_index && strlen(clients[i].username) > 0) {
            if (!first) strcat(buffer, ",");
            strcat(buffer, clients[i].username);
            first = 0;
        }
    }
    strcat(buffer, "\n");
    send(clients[client_index].sock, buffer, strlen(buffer), 0);
    pthread_mutex_unlock(&clients_mutex);
}

/* ── Online/Offline Bildir ─────────────────────────────────── */
void notify_online(const char* username) {
    char buffer[256];
    snprintf(buffer, sizeof(buffer), "ONLINE:%s\n", username);

    pthread_mutex_lock(&clients_mutex);
    for (int i = 0; i < MAX_CLIENTS; i++) {
        if (clients[i].is_active && strlen(clients[i].username) > 0
            && strcmp(clients[i].username, username) != 0) {
            send(clients[i].sock, buffer, strlen(buffer), 0);
        }
    }
    pthread_mutex_unlock(&clients_mutex);
}

void notify_offline(const char* username) {
    char buffer[256];
    snprintf(buffer, sizeof(buffer), "OFFLINE:%s\n", username);

    pthread_mutex_lock(&clients_mutex);
    for (int i = 0; i < MAX_CLIENTS; i++) {
        if (clients[i].is_active && strlen(clients[i].username) > 0
            && strcmp(clients[i].username, username) != 0) {
            send(clients[i].sock, buffer, strlen(buffer), 0);
        }
    }
    pthread_mutex_unlock(&clients_mutex);
}

/* ── Ozel Mesaj Gonder ─────────────────────────────────────── */
void send_to_user(const char* sender, const char* recipient, const char* message) {
    char buffer[BUFFER_SIZE];
    snprintf(buffer, sizeof(buffer), "MSG:%s:%s\n", sender, message);

    int found = 0;
    pthread_mutex_lock(&clients_mutex);
    for (int i = 0; i < MAX_CLIENTS; i++) {
        if (clients[i].is_active && strcmp(clients[i].username, recipient) == 0) {
            send(clients[i].sock, buffer, strlen(buffer), 0);
            found = 1;
            break;
        }
    }
    pthread_mutex_unlock(&clients_mutex);

    if (!found) {
        /* Alici offline - mesaji kuyruga al */
        store_pending(sender, recipient, message);

        /* Gondericiye bildir */
        snprintf(buffer, sizeof(buffer), "SYS:%s cevrim disi. Mesaj online olunca iletilecek.\n", recipient);
        pthread_mutex_lock(&clients_mutex);
        for (int i = 0; i < MAX_CLIENTS; i++) {
            if (clients[i].is_active && strcmp(clients[i].username, sender) == 0) {
                send(clients[i].sock, buffer, strlen(buffer), 0);
                break;
            }
        }
        pthread_mutex_unlock(&clients_mutex);
    }
}

static void remove_client(int index) {
    pthread_mutex_lock(&clients_mutex);
    clients[index].is_active = 0;
    close_socket(clients[index].sock);
    pthread_mutex_unlock(&clients_mutex);
}

void* handle_client(void* arg) {
    int current_index = *((int*)arg);
    free(arg);

    socket_t sock = clients[current_index].sock;
    char* buffer = malloc(BUFFER_SIZE);
    if (!buffer) { remove_client(current_index); return NULL; }
    int bytes_read;

    /* Ilk mesaj: AUTH:kullaniciadi:token (veya geriye donuk uyumluluk icin NAME:kullaniciadi) */
    memset(buffer, 0, sizeof(buffer));
    bytes_read = recv(sock, buffer, sizeof(buffer) - 1, 0);
    if (bytes_read <= 0) {
        remove_client(current_index);
        return NULL;
    }
    buffer[strcspn(buffer, "\r\n")] = 0;

    char temp_username[MAX_USERNAME] = {0};
    char temp_token[64] = {0};

    if (strncmp(buffer, "AUTH:", 5) == 0) {
        char* name_start = buffer + 5;
        char* token_start = strchr(name_start, ':');
        if (token_start) {
            *token_start = '\0';
            token_start++;
            strncpy(temp_username, name_start, MAX_USERNAME - 1);
            strncpy(temp_token, token_start, 63);
        } else {
            strncpy(temp_username, name_start, MAX_USERNAME - 1);
            strcpy(temp_token, "legacy");
        }
    } else if (strncmp(buffer, "NAME:", 5) == 0) {
        strncpy(temp_username, buffer + 5, MAX_USERNAME - 1);
        strcpy(temp_token, "legacy");
    } else {
        strncpy(temp_username, buffer, MAX_USERNAME - 1);
        strcpy(temp_token, "legacy");
    }

    /* Yetkilendirme (Authentication) Islemi */
    int auth_success = 0;
    if (register_user(temp_username, temp_token)) {
        auth_success = 1; /* Yeni kullanici, basariyla kaydedildi */
    } else if (login_user(temp_username, temp_token)) {
        auth_success = 1; /* Mevcut kullanici, token eslesti */
    }

    if (!auth_success) {
        /* Kullanici adi baskasina ait */
        char fail_msg[] = "AUTH_FAIL\n";
        send(sock, fail_msg, strlen(fail_msg), 0);
        printf("[-] Yetkilendirme reddedildi: %s (Hatali cihaz/token)\n", temp_username);
        fflush(stdout);
        remove_client(current_index);
        return NULL;
    }

    /* Basarili giris */
    char ok_msg[] = "AUTH_OK\n";
    send(sock, ok_msg, strlen(ok_msg), 0);

    pthread_mutex_lock(&clients_mutex);
    strncpy(clients[current_index].username, temp_username, MAX_USERNAME - 1);
    pthread_mutex_unlock(&clients_mutex);

    printf("[+] %s baglandi.\n", clients[current_index].username);
    fflush(stdout);

    /* Yeni kullaniciya mevcut kullanici listesini gonder */
    send_userlist(current_index);

    /* Herkese bu kullanicinin online oldugunu bildir */
    notify_online(clients[current_index].username);

    /* Bekleyen mesajlari ilet */
    deliver_pending(clients[current_index].username, sock);

    /* Mesaj dongusu (TCP Fragmentasyonunu ve Birlestirmeyi cozmek icin stream yontemi) */
    int total_bytes = 0;
    while (1) {
        bytes_read = recv(sock, buffer + total_bytes, BUFFER_SIZE - 1 - total_bytes, 0);
        if (bytes_read <= 0) {
            printf("[-] %s ayrildi.\n", clients[current_index].username);
            fflush(stdout);
            notify_offline(clients[current_index].username);
            break;
        }
        total_bytes += bytes_read;
        buffer[total_bytes] = '\0';

        char* line_start = buffer;
        char* newline_pos;
        
        while ((newline_pos = strchr(line_start, '\n')) != NULL) {
            *newline_pos = '\0'; // Satiri kopar
            char* line = line_start;
            int len = strlen(line);
            if (len > 0 && line[len-1] == '\r') line[len-1] = '\0';
            
            if (strlen(line) > 0 && strcmp(line, "PING") != 0) {
                if (strncmp(line, "TO:", 3) == 0) {
                    char* recipient_start = line + 3;
                    char* msg_start = strchr(recipient_start, ':');
                    if (msg_start) {
                        *msg_start = '\0';
                        msg_start++;

                        /* Sifreli mesaj mi kontrol et */
                        if (strncmp(msg_start, "ENC:", 4) == 0) {
                            char* encrypted_body = msg_start + 4;
                            char* plaintext = aes_decrypt(encrypted_body);
                            if (plaintext) {
                                printf("[%s -> %s]: (Sifreli Mesaj/Foto %d bytes)\n", clients[current_index].username, recipient_start, (int)strlen(plaintext));
                                fflush(stdout);
                                free(plaintext);
                            }
                            send_to_user(clients[current_index].username, recipient_start, msg_start);
                        } else {
                            printf("[%s -> %s]: %s\n", clients[current_index].username, recipient_start, msg_start);
                            fflush(stdout);
                            send_to_user(clients[current_index].username, recipient_start, msg_start);
                        }
                    }
                }
            }
            line_start = newline_pos + 1;
        }

        int remaining = total_bytes - (line_start - buffer);
        if (remaining > 0) {
            memmove(buffer, line_start, remaining);
        }
        total_bytes = remaining;
    }

    free(buffer);
    remove_client(current_index);
    return NULL;
}
