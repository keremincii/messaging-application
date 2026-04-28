## SLAYT 1: Kapak
**Proje Adı:** Güvenli ve Eşzamanlı Mesajlaşma Uygulaması
**Hazırlayanlar:** 
- Mehmet Kerem İnci (Öğrenci No: 230206057)
- Erenalp Tunay (Öğrenci No: ...)
**Dersin Adı:** [BİL 314 Bilgisayar Ağları]

---

## SLAYT 2: Proje Konusu ve Hedefleri
**Neler Hedefledik?**
- İnternet ortamında gerçek zamanlı, hızlı iletişim sağlama.
- C dilinde güvenilir ve çoklu istemci destekli bir Server oluşturma.
- Android işletim sistemli cihazlar için modern ve akıcı bir mobil uygulama tasarlama.
- Veri iletişimini kriptografi ile güvenli hale getirme.

---

## SLAYT 3: Kullanılan Teknolojiler
- **Backend (Sunucu):** C Programlama Dili, POSIX Threads (pthreads), TCP/IP Sockets.
- **Frontend (Mobil):** Java, Android Studio, Android SDK.
- **Güvenlik:** AES-128 (Advanced Encryption Standard).
- **Altyapı / Deployment:** Docker, Docker Compose, Linux VPS.

---

## SLAYT 4: Mimari Yapı (Client - Server)
- **Haberleşme Protokolü:** TCP tabanlı yapı kullanılarak veri kaybı önlendi.
- Sunucu sadece "Router (Yönlendirici)" görevi üstlenir. İstemciden gelen "TO:[KULLANICI_ADI]" direktifine göre paketi hedef cihaza ulaştırır.
- Tüm mimari asenkron çalışır (Non-blocking I/O).

---

## SLAYT 5: Kullanıcı Tanımlama ve Yetkilendirme
- Kullanıcı uygulamayı ilk açtığında benzersiz cihaz kimliği (Device Token) oluşturulur.
- İsim (Username) alınarak sunucuya **"AUTH"** protokolüyle yetkilendirme talebi gönderilir.
- Sunucu aynı isimde online birisi varsa isteği reddeder (**AUTH_FAIL**).
- Başarılı girişte cihaz yetkilendirilir ve oturum açılır (**AUTH_OK**).

---

## SLAYT 6: Güvenlik - Uçtan Uca Şifreleme (AES-128)
- Ağ üzerindeki tüm iletişim şifrelidir.
- Mobil cihazda mesaj metni gönderilmeden saniyeler önce `javax.crypto` kullanılarak AES-128-CBC yöntemiyle şifrelenir.
- Sunucu şifreli mesajın içeriğini bilmez, sadece alıcıya yönlendirir.
- Alıcının telefonu şifreyi çözer. (Veri tablosu çalınsa bile okunamaz).

---

## SLAYT 7: Çevrimdışı (Offline) Destek ve Kuyruk Mimarisi
- Mobil cihazın internet bağlantısı kesildiğinde girilen mesajlar kaybolmaz.
- Mesajlar JSON formatında yerel veritabanına (SharedPreferences) "Offline Queue" olarak dizilir.
- Soket yeniden bağlandığı anda (Keepalive tespitinde), tüm kuyruk otomatik olarak arka planda sunucuya iletilir.

---

## SLAYT 8: Multithreading ve Veri Senkronizasyonu
- C ile yazılan sunucu, `while` döngüsünde sürekli bağlantı dinler.
- Her bağlanan cihaz için `pthread_create` ile izole bir iş parçacığı açılır.
- Kullanıcıların depolandığı listeye (Online listesi) aynı anda iki thread'in yazmasını engellemek için kod blokları `pthread_mutex_lock` ile kilitlenerek "Data Race" önlenmiştir.

---

## SLAYT 9: Docker Entegrasyonu ve Üretime Alma
- Yazılımı "Benim bilgisayarımda çalışıyordu" sorunundan kurtarmak için Dockerize ettik.
- C kaynak kodları Alpine Linux üzerinde `gcc` ile derlenecek şekilde Dockerfile oluşturuldu.
- `docker-compose up` ile uygulama internette 7/24 hizmet verecek bir sunucuya (VPS) deploy edildi.

---

## SLAYT 10: Sonuç ve Kazanımlar
- TCP soketlerinin çalışma mantığını en temel (low-level) düzeyde öğrendik.
- Eşzamanlılık (Concurrency) ve Mutex kavramlarını pratik ederek donanımları anladık.
- İki farklı platform (C ve Android/Java) arasında ortak bir haberleşme standardı kurma yeteneği kazandık.
- Geliştirdiğimiz kodu Production (Canlı) ortama çıkararak yazılımın tüm yaşam döngüsünü deneyimledik.

*Teşekkür ederiz! Soru ve Cevaplar...*
