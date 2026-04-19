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
#include "auth.h"

Client clients[MAX_CLIENTS];
pthread_mutex_t clients_mutex = PTHREAD_MUTEX_INITIALIZER;

#ifdef _WIN32
#define close_socket closesocket
#else
#define close_socket close
#endif

void broadcast_message(const char* sender, const char* message) {
    char buffer[BUFFER_SIZE + 128];
    snprintf(buffer, sizeof(buffer), "[Herkes] %s: %s\n", sender, message);

    pthread_mutex_lock(&clients_mutex);
    for (int i = 0; i < MAX_CLIENTS; ++i) {
        if (clients[i].is_active && clients[i].is_logged_in && strcmp(clients[i].username, sender) != 0) {
            send(clients[i].sock, buffer, strlen(buffer), 0);
        }
    }
    pthread_mutex_unlock(&clients_mutex);
}

void send_private_message(const char* sender, const char* recipient, const char* message) {
    char buffer[BUFFER_SIZE + 128];
    snprintf(buffer, sizeof(buffer), "[Ozel] %s: %s\n", sender, message);

    int found = 0;
    pthread_mutex_lock(&clients_mutex);
    for (int i = 0; i < MAX_CLIENTS; ++i) {
        if (clients[i].is_active && clients[i].is_logged_in && strcmp(clients[i].username, recipient) == 0) {
            send(clients[i].sock, buffer, strlen(buffer), 0);
            found = 1;
            break;
        }
    }
    pthread_mutex_unlock(&clients_mutex);

    if (!found) {
        char err_msg[128];
        snprintf(err_msg, sizeof(err_msg), "Sistem: %s adinda aktif bir kullanici bulunamadi.\n", recipient);
        // We don't have the sender's socket directly here, but we can search for it
        pthread_mutex_lock(&clients_mutex);
        for (int i = 0; i < MAX_CLIENTS; ++i) {
            if (clients[i].is_active && strcmp(clients[i].username, sender) == 0) {
                send(clients[i].sock, err_msg, strlen(err_msg), 0);
                break;
            }
        }
        pthread_mutex_unlock(&clients_mutex);
    }
}

void remove_client(int index) {
    pthread_mutex_lock(&clients_mutex);
    clients[index].is_active = 0;
    clients[index].is_logged_in = 0;
    close_socket(clients[index].sock);
    pthread_mutex_unlock(&clients_mutex);
}

void* handle_client(void* arg) {
    int current_index = *((int*)arg);
    free(arg);
    
    socket_t sock = clients[current_index].sock;
    char buffer[BUFFER_SIZE];
    int bytes_read;

    // Kimlik Doğrulama Döngüsü
    char welcome_msg[] = "Sisteme Hosgeldiniz!\n1- Giris Yap\n2- Kayit Ol\nSecim: ";
    if(send(sock, welcome_msg, strlen(welcome_msg), 0) <= 0) {
        remove_client(current_index);
        return NULL;
    }

    while (!clients[current_index].is_logged_in) {
        memset(buffer, 0, sizeof(buffer));
        bytes_read = recv(sock, buffer, sizeof(buffer) - 1, 0);
        if (bytes_read <= 0) {
            remove_client(current_index);
            return NULL;
        }

        buffer[strcspn(buffer, "\r\n")] = 0; // Yeni satır karakterini temizle
        int option = atoi(buffer);

        char user[MAX_USERNAME], pass[MAX_PASSWORD];
        char prompt_user[] = "Kullanici Adi: ";
        send(sock, prompt_user, strlen(prompt_user), 0);
        memset(buffer, 0, sizeof(buffer));
        if(recv(sock, buffer, sizeof(buffer)-1, 0) <= 0) break;
        buffer[strcspn(buffer, "\r\n")] = 0;
        strncpy(user, buffer, MAX_USERNAME - 1);

        char prompt_pass[] = "Sifre: ";
        send(sock, prompt_pass, strlen(prompt_pass), 0);
        memset(buffer, 0, sizeof(buffer));
        if(recv(sock, buffer, sizeof(buffer)-1, 0) <= 0) break;
        buffer[strcspn(buffer, "\r\n")] = 0;
        strncpy(pass, buffer, MAX_PASSWORD - 1);

        if (option == 1) { // Giriş
            if (login_user(user, pass)) {
                pthread_mutex_lock(&clients_mutex);
                strncpy(clients[current_index].username, user, MAX_USERNAME - 1);
                clients[current_index].is_logged_in = 1;
                pthread_mutex_unlock(&clients_mutex);
                
                char success_msg[] = "Giris Basarili! Mesajlasmaya baslayabilirsiniz.\nKullanim: @kullanici_adi mesajiniz VEYA direkt mesajiniz (Ortak alan)\n";
                send(sock, success_msg, strlen(success_msg), 0);
                
                char join_msg[128];
                snprintf(join_msg, sizeof(join_msg), "%s sohbete katildi.", user);
                broadcast_message("Sistem", join_msg);
            } else {
                char fail_msg[] = "Hatali kullanici adi veya sifre. Secim (1/2): ";
                send(sock, fail_msg, strlen(fail_msg), 0);
            }
        } else if (option == 2) { // Kayıt
            if (register_user(user, pass)) {
                char reg_msg[] = "Kayit Basarili! Lutfen yeniden giris yapiniz.\nSecim (1/2): ";
                send(sock, reg_msg, strlen(reg_msg), 0);
            } else {
                char reg_fail[] = "Kayit basarisiz! Kullanici adi mevcul olabilir.\nSecim (1/2): ";
                send(sock, reg_fail, strlen(reg_fail), 0);
            }
        } else {
            char invalid_msg[] = "Gecersiz secim. Secim (1/2): ";
            send(sock, invalid_msg, strlen(invalid_msg), 0);
        }
    }

    // Mesajlaşma Döngüsü
    while (1) {
        memset(buffer, 0, sizeof(buffer));
        bytes_read = recv(sock, buffer, sizeof(buffer) - 1, 0);
        if (bytes_read <= 0) {
            char leave_msg[128];
            snprintf(leave_msg, sizeof(leave_msg), "%s sohbetten ayrildi.", clients[current_index].username);
            broadcast_message("Sistem", leave_msg);
            break;
        }

        buffer[strcspn(buffer, "\r\n")] = 0;
        if (strlen(buffer) == 0) continue;

        // Özel mesaj kontrolü: @kullanici mesaj
        if (buffer[0] == '@') {
            char recipient[MAX_USERNAME];
            int i = 1, j = 0;
            while(buffer[i] != ' ' && buffer[i] != '\0' && j < MAX_USERNAME - 1) {
                recipient[j++] = buffer[i++];
            }
            recipient[j] = '\0';

            if (buffer[i] == ' ') {
                send_private_message(clients[current_index].username, recipient, &buffer[i + 1]);
            }
        } else {
            // Herkese mesaj
            broadcast_message(clients[current_index].username, buffer);
        }
    }

    remove_client(current_index);
    return NULL;
}
