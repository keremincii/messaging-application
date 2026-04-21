#ifndef CHAT_H
#define CHAT_H

#ifdef _WIN32
#include <winsock2.h>
#include <windows.h>
typedef SOCKET socket_t;
#else
typedef int socket_t;
#define INVALID_SOCKET -1
#define SOCKET_ERROR -1
#endif

#define MAX_CLIENTS 100
#define BUFFER_SIZE 1024

typedef struct {
    socket_t sock;
    char username[64];
    int is_active;
} Client;

extern Client clients[MAX_CLIENTS];

// İstemci için thread fonksiyonu
void* handle_client(void* arg);

// Herkese mesaj gönderme
void broadcast_message(const char* sender, const char* message);

#endif
