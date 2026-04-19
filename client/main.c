#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <pthread.h>

#ifdef _WIN32
#include <winsock2.h>
#include <windows.h>
typedef SOCKET socket_t;
#else
#include <sys/socket.h>
#include <arpa/inet.h>
#include <unistd.h>
typedef int socket_t;
#define INVALID_SOCKET -1
#define SOCKET_ERROR -1
#endif

#define PORT 8080
#define SERVER_IP "127.0.0.1"
#define BUFFER_SIZE 1024

// Sunucudan gelen mesajları dinleyecek thread
void* receive_messages(void* arg) {
    socket_t sock = *((socket_t*)arg);
    char buffer[BUFFER_SIZE];
    int bytes_read;

    while (1) {
        memset(buffer, 0, sizeof(buffer));
        bytes_read = recv(sock, buffer, sizeof(buffer) - 1, 0);
        
        if (bytes_read <= 0) {
            printf("\nSunucu ile baglanti koptu veya kapatildi.\n");
            exit(0);
        }
        
        printf("%s", buffer);
        fflush(stdout);
    }
    return NULL;
}

int main() {
    socket_t sock;
    struct sockaddr_in server_addr;

#ifdef _WIN32
    WSADATA wsa;
    if (WSAStartup(MAKEWORD(2, 2), &wsa) != 0) {
        printf("Winsock baslatilamadi. Hata Kodu : %d\n", WSAGetLastError());
        return 1;
    }
#endif

    // Soket oluştur
    if ((sock = socket(AF_INET, SOCK_STREAM, 0)) == INVALID_SOCKET) {
        printf("Soket olusturulamadi.\n");
        return 1;
    }

    server_addr.sin_family = AF_INET;
    server_addr.sin_addr.s_addr = inet_addr(SERVER_IP);
    server_addr.sin_port = htons(PORT);

    // Sunucuya bağlan
    if (connect(sock, (struct sockaddr*)&server_addr, sizeof(server_addr)) < 0) {
        printf("Baglanti hatasi. Sunucu calisiyor mu?\n");
        return 1;
    }

    // Mesaj alma thread'ini başlat
    pthread_t recv_thread;
    if (pthread_create(&recv_thread, NULL, receive_messages, (void*)&sock) < 0) {
        printf("Thread olusturulamadi.\n");
        return 1;
    }

    // Ana döngü: Kullanıcıdan girdi al ve sunucuya gönder
    char buffer[BUFFER_SIZE];
    while (1) {
        memset(buffer, 0, sizeof(buffer));
        if (fgets(buffer, sizeof(buffer), stdin) != NULL) {
            // Buffer'ı direkt gönder (seçimler veya doğrudan mesaj)
            send(sock, buffer, strlen(buffer), 0);
        }
    }

#ifdef _WIN32
    closesocket(sock);
    WSACleanup();
#else
    close(sock);
#endif

    return 0;
}
