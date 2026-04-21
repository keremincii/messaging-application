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

void broadcast_message(const char* sender, const char* message) {
    char buffer[BUFFER_SIZE + 128];
    snprintf(buffer, sizeof(buffer), "%s: %s\n", sender, message);

    pthread_mutex_lock(&clients_mutex);
    for (int i = 0; i < MAX_CLIENTS; ++i) {
        if (clients[i].is_active && strcmp(clients[i].username, sender) != 0) {
            send(clients[i].sock, buffer, strlen(buffer), 0);
        }
    }
    pthread_mutex_unlock(&clients_mutex);
}

void remove_client(int index) {
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

    // İlk mesaj olarak kullanıcı adını bekle
    // Android uygulama bağlanınca otomatik olarak "NAME:kerem" gibi gönderecek
    memset(buffer, 0, sizeof(buffer));
    bytes_read = recv(sock, buffer, sizeof(buffer) - 1, 0);
    if (bytes_read <= 0) {
        remove_client(current_index);
        return NULL;
    }
    buffer[strcspn(buffer, "\r\n")] = 0;

    // "NAME:kullanici_adi" formatını kontrol et
    if (strncmp(buffer, "NAME:", 5) == 0) {
        pthread_mutex_lock(&clients_mutex);
        strncpy(clients[current_index].username, buffer + 5, 63);
        pthread_mutex_unlock(&clients_mutex);
    } else {
        // NAME: olmadan direkt isim gelmişse
        pthread_mutex_lock(&clients_mutex);
        strncpy(clients[current_index].username, buffer, 63);
        pthread_mutex_unlock(&clients_mutex);
    }

    printf("[+] %s sohbete katildi.\n", clients[current_index].username);
    fflush(stdout);

    // Kullanıcıya hoşgeldin mesajı gönder
    char welcome[256];
    snprintf(welcome, sizeof(welcome), "Hosgeldin %s! Mesaj yazmaya baslayabilirsin.\n", clients[current_index].username);
    send(sock, welcome, strlen(welcome), 0);

    // Herkese bilgi ver
    char join_msg[128];
    snprintf(join_msg, sizeof(join_msg), "%s sohbete katildi.", clients[current_index].username);
    broadcast_message("Sistem", join_msg);

    // Mesajlaşma döngüsü
    while (1) {
        memset(buffer, 0, sizeof(buffer));
        bytes_read = recv(sock, buffer, sizeof(buffer) - 1, 0);
        if (bytes_read <= 0) {
            printf("[-] %s ayrildi.\n", clients[current_index].username);
            fflush(stdout);
            char leave_msg[128];
            snprintf(leave_msg, sizeof(leave_msg), "%s sohbetten ayrildi.", clients[current_index].username);
            broadcast_message("Sistem", leave_msg);
            break;
        }

        buffer[strcspn(buffer, "\r\n")] = 0;
        if (strlen(buffer) == 0) continue;

        // Terminalde göster
        printf("[%s]: %s\n", clients[current_index].username, buffer);
        fflush(stdout);

        // Herkese yayınla
        broadcast_message(clients[current_index].username, buffer);
    }

    remove_client(current_index);
    return NULL;
}
