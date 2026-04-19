#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <pthread.h>
#ifdef _WIN32
#include <winsock2.h>
#else
#include <sys/socket.h>
#include <arpa/inet.h>
#include <unistd.h>
#endif

#include "chat.h"

#define PORT 8080

int main() {
    socket_t server_sock;
    struct sockaddr_in server_addr, client_addr;

#ifdef _WIN32
    WSADATA wsa;
    if (WSAStartup(MAKEWORD(2, 2), &wsa) != 0) {
        printf("Winsock baslatilamadi. Hata Kodu : %d", WSAGetLastError());
        return 1;
    }
#endif

    // Soket oluştur
    if ((server_sock = socket(AF_INET, SOCK_STREAM, 0)) == INVALID_SOCKET) {
        printf("Soket olusturulamadi.\n");
        return 1;
    }

    server_addr.sin_family = AF_INET;
    server_addr.sin_addr.s_addr = INADDR_ANY;
    server_addr.sin_port = htons(PORT);

    // Bind
    if (bind(server_sock, (struct sockaddr*)&server_addr, sizeof(server_addr)) == SOCKET_ERROR) {
        printf("Bind hatasi.\n");
        return 1;
    }

    // Dinle
    listen(server_sock, 10);
    printf("Sunucu %d portunda dinleniyor...\n", PORT);

    int c = sizeof(struct sockaddr_in);
    
    // İstemci döngüsü
    while (1) {
        socket_t new_sock = accept(server_sock, (struct sockaddr*)&client_addr, (socklen_t*)&c);
        if (new_sock == INVALID_SOCKET) {
            printf("Bağlantı kabul edilemedi.\n");
            continue;
        }

        // Boş istemci slotu bul
        int slot_found = -1;
        extern pthread_mutex_t clients_mutex;
        pthread_mutex_lock(&clients_mutex);
        for (int i = 0; i < MAX_CLIENTS; ++i) {
            if (!clients[i].is_active) {
                clients[i].sock = new_sock;
                clients[i].is_active = 1;
                clients[i].is_logged_in = 0;
                memset(clients[i].username, 0, sizeof(clients[i].username));
                slot_found = i;
                break;
            }
        }
        pthread_mutex_unlock(&clients_mutex);

        if (slot_found != -1) {
            pthread_t sn_thread;
            int* arg = malloc(sizeof(*arg));
            *arg = slot_found;
            if (pthread_create(&sn_thread, NULL, handle_client, (void*)arg) < 0) {
                printf("Thread olusturulamadi.\n");
                free(arg);
            }
            // Thread'i detach et (bittiğinde kaynakları otomatik temizlesin)
            pthread_detach(sn_thread);
        } else {
            char full_msg[] = "Sunucu dolu.\n";
            send(new_sock, full_msg, strlen(full_msg), 0);
#ifdef _WIN32
            closesocket(new_sock);
#else
            close(new_sock);
#endif
        }
    }

#ifdef _WIN32
    closesocket(server_sock);
    WSACleanup();
#else
    close(server_sock);
#endif

    return 0;
}
