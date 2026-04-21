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

Client clients[MAX_CLIENTS];
pthread_mutex_t clients_mutex = PTHREAD_MUTEX_INITIALIZER;

#ifdef _WIN32
#define close_socket closesocket
#else
#define close_socket close
#endif

/* Belirli bir kullaniciya online kullanici listesini gonder */
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

/* Herkese birinin online oldugunu bildir */
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

/* Herkese birinin offline oldugunu bildir */
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

/* Ozel mesaj gonder: sadece aliciya ilet */
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
        snprintf(buffer, sizeof(buffer), "SYS:%s su an cevrim disi.\n", recipient);
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
    char buffer[BUFFER_SIZE];
    int bytes_read;

    /* Ilk mesaj: NAME:kullaniciadi */
    memset(buffer, 0, sizeof(buffer));
    bytes_read = recv(sock, buffer, sizeof(buffer) - 1, 0);
    if (bytes_read <= 0) {
        remove_client(current_index);
        return NULL;
    }
    buffer[strcspn(buffer, "\r\n")] = 0;

    if (strncmp(buffer, "NAME:", 5) == 0) {
        pthread_mutex_lock(&clients_mutex);
        strncpy(clients[current_index].username, buffer + 5, MAX_USERNAME - 1);
        pthread_mutex_unlock(&clients_mutex);
    } else {
        pthread_mutex_lock(&clients_mutex);
        strncpy(clients[current_index].username, buffer, MAX_USERNAME - 1);
        pthread_mutex_unlock(&clients_mutex);
    }

    printf("[+] %s baglandi.\n", clients[current_index].username);
    fflush(stdout);

    /* Yeni kullaniciya mevcut kullanici listesini gonder */
    send_userlist(current_index);

    /* Herkese bu kullanicinin online oldugunu bildir */
    notify_online(clients[current_index].username);

    /* Mesaj dongusu */
    while (1) {
        memset(buffer, 0, sizeof(buffer));
        bytes_read = recv(sock, buffer, sizeof(buffer) - 1, 0);
        if (bytes_read <= 0) {
            printf("[-] %s ayrildi.\n", clients[current_index].username);
            fflush(stdout);
            notify_offline(clients[current_index].username);
            break;
        }

        buffer[strcspn(buffer, "\r\n")] = 0;
        if (strlen(buffer) == 0) continue;

        /* Keepalive PING - sessizce yoksay */
        if (strcmp(buffer, "PING") == 0) {
            continue;
        }

        /* Format: TO:alici:mesaj */
        if (strncmp(buffer, "TO:", 3) == 0) {
            char* recipient_start = buffer + 3;
            char* msg_start = strchr(recipient_start, ':');
            if (msg_start) {
                *msg_start = '\0';
                msg_start++;
                printf("[%s -> %s]: %s\n", clients[current_index].username, recipient_start, msg_start);
                fflush(stdout);
                send_to_user(clients[current_index].username, recipient_start, msg_start);
            }
        }
    }

    remove_client(current_index);
    return NULL;
}
