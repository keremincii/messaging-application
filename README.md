# C Socket Tabanlı Merkezi Mesajlaşma Uygulaması

Bu proje, Bil314 Bilgisayar Ağları dersi kapsamında geliştirilmiş, "Client-Server" (İstemci-Sunucu) mimarisine dayalı eşzamanlı bir mesajlaşma uygulamasıdır.

## Proje Bileşenleri

Proje 3 temel bileşenden oluşmaktadır:
1. **Server (Sunucu - C Dili):** Sistemin ana omurgasıdır. TCP/IP soketleri üzerinden istemcilerle haberleşir. Çoklu kullanıcı desteği için Pthreads (POSIX Threads) kullanılmıştır.
2. **Client (CLI İstemci - C Dili):** Konsol üzerinden mesajlaşmayı sağlayan temel istemcidir.
3. **Android Client (Mobil İstemci - Java):** Kullanıcı deneyimini artırmak amacıyla ek olarak geliştirilmiş, cihaz kimliği (token) tabanlı yetkilendirme (Passwordless Auth) kullanan modern mobil istemcidir.

## Kurulum ve Çalıştırma

### 1. Sunucunun Çalıştırılması (Linux / WSL / macOS)
Sunucu C dili ile geliştirilmiş olup `make` aracı ile kolayca derlenebilir.

```bash
cd server
make
./chat_server
```
*Sunucu varsayılan olarak 8080 portunda dinlemeye başlar.*

### 2. C CLI İstemcisinin Çalıştırılması
Terminal üzerinden sunucuya bağlanmak için:
```bash
cd client
gcc main.c -o client -lpthread
./client
```

### 3. Android İstemcisinin Çalıştırılması
- `android_app` klasörünü Android Studio ile açın.
- Telefonunuzu veya emülatörü bağlayıp projeyi derleyerek (Run) çalıştırın.
- Uygulama ilk açıldığında sunucu IP adresini ve Port numarasını "Ayarlar" butonundan güncelleyebilirsiniz.

## Güvenlik ve Yetkilendirme
Projede **Şifresiz Token Yetkilendirmesi (Passwordless Device-Token Auth)** kullanılmıştır. 
- İstemci sunucuya bağlandığında arka planda eşsiz bir UUID gönderir (`AUTH:KullaniciAdi:Token`).
- Sunucu bunu `users.txt` dosyasına kaydeder (`auth.c`). 
- Aynı kullanıcı adıyla başka bir cihazdan girilmeye çalışıldığında token eşleşmeyeceği için sunucu bağlantıyı reddeder.

## Github Reposu
Projenin en güncel kaynak kodlarına aşağıdaki bağlantıdan ulaşabilirsiniz:
**[Github Repo Linki Buraya Eklenecek]**
