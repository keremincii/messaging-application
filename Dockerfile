FROM alpine:latest

# Gerekli araçları yüklüyoruz (C derleyicisi, Make aracı ve OpenSSL)
RUN apk add --no-cache gcc make musl-dev openssl-dev

# Proje dosyalarını kopyalıyoruz
WORKDIR /app
COPY server/ ./server/

# Sunucuyu derliyoruz
WORKDIR /app/server
RUN make

# Sunucunun çalışacağı 8080 portunu dışarı açıyoruz
EXPOSE 8080

# Sunucuyu başlatıyoruz
CMD ["./server"]
