#ifndef AUTH_H
#define AUTH_H

#define MAX_USERNAME 64
#define MAX_PASSWORD 64

// Kullanıcı kayıt işlemi. Başarılıysa 1, kullanıcı zaten varsa veya hata olursa 0 döner.
int register_user(const char* username, const char* password);

// Kullanıcı giriş işlemi. Başarılıysa 1, hatalıysa 0 döner.
int login_user(const char* username, const char* password);

#endif
