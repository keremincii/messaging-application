# PROJE TANIMLAMA DOKÜMANI


1. **Adı Soyadı:** Mehmet Kerem İnci **Öğrenci Numarası:** 230206057
2. **Adı Soyadı:** Erenalp Tunay **Öğrenci Numarası:**

## b. Projenin Konusu

Bu proje; C programlama dilinde geliştirilmiş çok iş parçacıklı (multithreaded) bir sunucu ve Java ile geliştirilmiş Android tabanlı bir mobil istemci (client) arasında TCP/IP soketleri üzerinden haberleşen, AES-128 ile uçtan uca şifrelenmiş, gerçek zamanlı bir anlık mesajlaşma uygulamasının geliştirilmesidir.

## c. Hedefleri

Projenin temel hedefleri şunlardır:

- **Online (İnternet Üzerinden) Çalışma:** Uygulamanın bir VPS (Sanal Özel Sunucu) üzerine kurularak internet bağlantısı olan herhangi bir noktadan erişilebilir olması.
- **Güvenli İletişim:** Gönderilen tüm metin ve görsellerin ağ dinlemelerine karşı AES-128-CBC şifreleme algoritması ile korunması.
- **Eşzamanlılık (Concurrency):** C dilinde yazılan sunucunun, `pthreads` kullanarak aynı anda çok sayıda istemciye asenkron olarak ve çökmeden hizmet verebilmesi.
- **Kullanıcı Tanımlama:** Uygulamanın ilk açılışında kullanıcı adı doğrulaması (Authentication) yapılarak benzersiz cihaz kimliği (Device Token) ile eşleştirme yapılması.
- **Çevrimdışı Destek:** İnternet bağlantısı koptuğunda yazılan mesajların kuyruğa alınıp, bağlantı tekrar sağlandığında sunucuya otomatik iletilmesi.

## d. Kullanılan Metot ve Metodolojiler

Projenin geliştirilme sürecinde aşağıdaki metotlar uygulanmıştır:

1. **İstemci-Sunucu (Client-Server) Mimarisi:** İletişim için TCP protokolü kullanılmış, veri kaybı en aza indirilmiştir. Sunucu tarafı C/C++ ile kodlanmış olup, mobil istemci Android SDK kullanılarak Java ile yazılmıştır.
2. **Çok İş Parçacıklı Programlama (Multithreading):** Sunucu, her yeni bağlantı için bağımsız bir iş parçacığı (thread) oluşturur. Ortak verilere (bağlı kullanıcılar listesi gibi) erişimde veri yarışını (data race) önlemek için `Mutex` (Mutual Exclusion) kilitleri kullanılmıştır.
3. **Kriptografik Şifreleme (AES-128):** C tarafında `OpenSSL`, Android tarafında `javax.crypto` kütüphaneleri kullanılarak simetrik anahtarlı şifreleme uygulanmıştır.
4. **Konteynerizasyon:** Sunucunun üretim (production) ortamında işletim sistemi bağımlılıklarından etkilenmeden hatasız çalışması için `Docker` kullanılmış ve `docker-compose` ile mimari paketlenmiştir.
5. **Veri Serileştirme:** İstemciler arası resim transferleri için medya dosyaları Base64 formatında metne dönüştürülüp şifrelenerek paketlere sığdırılmıştır.

## e. Sonuç, Yorumlar ve Kazanımlar

Bu proje sürecinde sadece bir kod yazmanın ötesinde, uçtan uca bir sistemin nasıl tasarlanıp canlı ortama (internete) alınacağı pratik olarak tecrübe edilmiştir.

**Kazanımlar ve Öğrenilenler:**

- C dilinde düşük seviyeli (low-level) soket programlama pratiği kazanılmış, işletim sistemlerinin ağ yapılarına dair derinlemesine bilgi edinilmiştir.
- Eşzamanlı (Concurrency) programlamanın zorlukları görülmüş, "Deadlock" ve "Race Condition" gibi kavramların teoriden pratiğe nasıl yansıdığı ve `Mutex` ile nasıl çözüldüğü bizzat uygulanarak öğrenilmiştir.
- Mobil geliştirme (Android) sürecinde arayüz (UI) tasarımı, cihazın yerel veritabanına (SharedPreferences) veri kaydetme süreçleri deneyimlenmiştir.
- Bir yazılımın Docker ortamına geçirilmesi (Containerization) ve uzak sunucuya (VPS) deploy edilmesi süreci tecrübe edilmiştir.

**Faydalanılan Kaynaklar:**

- Linux man pages (POSIX Threads ve Sockets dokümantasyonu).
- OpenSSL Resmi Dokümantasyonları.
- Android Developers Resmi Kılavuzu.
- Github ve StackOverflow topluluklarındaki benzer ağ mimarisi tartışmaları.

**Karşılaşılan Zorluklar ve Çözümleri:**

1. **Çapraz Platform Şifreleme Uyuşmazlığı:** Android (Java) ve C tarafında aynı metnin şifrelenip çözülmesi aşamasında padding (doldurma) hataları ile karşılaşıldı. Her iki platformun AES şifrelemesinde birebir aynı başlatma vektörünü (IV) ve `PKCS5Padding` algoritmasını kullanması sağlanarak aşılıldı.
2. **Bağlantı Kopmalarında Hayalet Kullanıcılar:** Mobil cihazların interneti kesildiğinde C sunucusunun bunu hemen fark etmemesi sebebiyle kullanıcılar sürekli "online" kalıyordu. Bunun çözümü olarak "Keepalive (PING/PONG)" mekanizması kodlanarak sunucu-istemci arası sürekli nabız yoklaması yapıldı.
3. **Docker İşletim Sistemi Bağımlılıkları:** C kodumuzun Windows ve Mac'te farklı, Alpine Linux'te (Docker) farklı derlenme sorunu `gcc` bağımlılıkları manuel ayarlanarak ve `openssl-dev` kütüphanesi Dockerfile'a dahil edilerek aşıldı.
