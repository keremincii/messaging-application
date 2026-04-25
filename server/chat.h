#ifndef CHAT_H
#define CHAT_H

#include <pthread.h>

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
#define BUFFER_SIZE 100000
#define MAX_USERNAME 64

typedef struct {
    socket_t sock;
    char username[MAX_USERNAME];
    int is_active;
} Client;

extern Client clients[MAX_CLIENTS];
extern pthread_mutex_t clients_mutex;

void* handle_client(void* arg);
void send_to_user(const char* sender, const char* recipient, const char* message);
void send_userlist(int client_index);
void notify_online(const char* username);
void notify_offline(const char* username);

#endif
