# Secure Messaging Application (C Server & Android Client)

Bu proje, **Bil314 Bilgisayar Ağları** dersi final projesi kapsamında geliştirilmiş, uçtan uca şifreli ve merkezi sunucu tabanlı bir mesajlaşma ekosistemidir. 

## 🚀 Temel Özellikler
- **Çok İş Parçacıklı Sunucu (C):** POSIX Threads (pthreads) ile yüksek eşzamanlılık.
- **Uçtan Uca Şifreleme:** Mesajlar istemci tarafında AES-128-CBC ile şifrelenir.
- **Modern Android UI:** Java/Android SDK ile geliştirilmiş şık ve kullanıcı dostu arayüz.
- **Resim Paylaşımı:** Fotoğrafları Base64 formatında şifreli olarak iletebilme.
- **Çevrimdışı Mesaj Kuyruğu:** İnternet yokken mesajları kaydedip, bağlantı gelince otomatik gönderme.
- **Docker Desteği:** Sunucuyu tek komutla her ortamda ayağa kaldırabilme.

---

## 🛠️ Mimari Yapı
Proje 2 ana bileşenden oluşur:

### 1. Backend (C Server)
- **Sockets:** TCP/IP protokolü ile güvenilir veri iletimi.
- **Pthreads:** Her kullanıcı için ayrı bir thread yönetimi.
- **Docker:** Sunucu `docker-compose.yml` ile konteynerize edilmiştir.
- **Gereksinimler:** `OpenSSL`, `gcc`, `make`.

### 2. Frontend (Android App)
- **Socket Client:** Sunucuyla sürekli bağlantıda kalan asenkron yapı.
- **Encryption:** `javax.crypto` ile güvenli veri iletişimi.
- **Persistence:** SharedPreferences ile sohbet geçmişi ve çevrimdışı kuyruk yönetimi.

---

## ⚙️ Kurulum ve Çalıştırma

### Sunucuyu Başlatma (Docker ile)
Sunucuyu VPS veya yerel makinenizde başlatmak için Docker yüklü olması yeterlidir:
```bash
# Proje ana dizininde
docker compose up -d --build
```
*Sunucu varsayılan olarak **8080** portunu kullanır.*

### Android Uygulamasını Kurma
1. `android_app` klasörünü Android Studio ile açın.
2. `MainActivity.java` içindeki `DEFAULT_HOST` değişkenini sunucu IP adresinizle güncelleyin.
3. Projeyi derleyip telefonunuza yükleyin.

---

## 📂 Proje Dokümantasyonu
Hoca için hazırlanan detaylı dokümanlara `Dokumanlar/` klasöründen ulaşabilirsiniz:
- [Proje Tanımlama Dokümanı](Dokumanlar/Proje_Tanimlama_Dokumani.md)
- [Teknik Dokümantasyon](Dokumanlar/Proje_Dokumantasyonu.md)
- [Proje Sunumu (Slayt Taslağı)](Dokumanlar/Proje_Sunumu.md)

---

## 👥 Ekip
- **Mehmet Kerem İnci** (230206057)
- **Erenalp Tunay**

---
**Github Repo:** [https://github.com/keremincii/messaging-application](https://github.com/keremincii/messaging-application)
