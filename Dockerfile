FROM alpine:latest

# Gerekli araçları yüklüyoruz
RUN apk add --no-cache gcc make musl-dev openssl-dev

# Proje dosyalarını kopyalıyoruz
WORKDIR /app
COPY server/ ./server/

# Sunucuyu derliyoruz
WORKDIR /app/server
RUN gcc -Wall -Wextra -pthread -o server main.c chat.c crypto.c auth.c -lssl -lcrypto

# Sunucunun çalışacağı 8080 portunu dışarı açıyoruz
EXPOSE 8080

# Sunucuyu başlatıyoruz
CMD ["./server"]
