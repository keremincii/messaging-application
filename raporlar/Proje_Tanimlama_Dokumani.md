# Proje Tanımlama Dükomani

**Ders:** Bil314 - Bilgisayar Ağları
**Proje Adı:** C Socket Tabanlı Merkezi Mesajlaşma Uygulaması ve Android İstemcisi
**Hazırlayanlar:**

- [Adınız Soyadınız] - [Öğrenci Numaranız]
- [Grup Arkadaşınızın Adı Soyadı] - [Öğrenci Numarası]

---

## 1. Konusu

Bu proje, standart "Client-Server" (İstemci-Sunucu) mimarisini kullanarak ağ üzerinde birden fazla kullanıcının eşzamanlı olarak birbirleriyle haberleşmesini sağlayan bir mesajlaşma uygulamasıdır. Proje C standart kütüphaneleri kullanılarak geliştirilmiş bir ana sunucu (Server) ile standart komut satırı (CLI) ve Android işletim sisteminde çalışan istemci (Client) yazılımlarından oluşmaktadır.

## 2. Hedefleri

Projenin temel hedefleri şunlardır:

- **Çevrimiçi Bağlantı:** İnternet/Yerel ağ üzerinden bir soket bağlantısı kurarak veri transferi yapmak.
- **Kullanıcı Tanımlama ve Yetkilendirme:** Sisteme bağlanan her bir istemcinin öncelikle kayıt (register) olmasını veya var olan hesapları ile giriş (login) yapmasını sağlayarak güvenlik ve tanımlama süreçlerini gerçekleştirmek.
- **Çoklu Kullanıcı (Multi-threading):** Sunucunun aynı anda birden fazla istemciye hizmet verebilmesi, mesajları yalnızca hedeflenen kullanıcılara veya herkese (broadcast) iletebilmesi.

## 3. Kullanılan Metot ve Metodolojiler

Projenin geliştirilmesinde aşağıdaki metodolojiler kullanılmıştır:

- **TCP/IP Socket Programlama:** C standart ağ kütüphaneleri kullanılarak sunucu ile istemci arasında kesintisiz çift yönlü veri akışı (TCP) sağlandı.
- **POSIX Threads (Pthreads):** Sunucuya bağlanan her yeni istemci için ayrı bir iş parçacığı (thread) oluşturularak performans izolasyonu sağlandı ve sistem darboğazı (bottleneck) engellendi.
- **Dosya Yöntemi ile Veritabanı:** Karmaşık bir veritabanı sürücüsü kullanmak yerine veriler (kullanıcı adları ve şifreler) standart dosya I/O işlemleri ile tutulmaktadır (users.txt).

## 4. Sonuç, Yorumlar ve Kazanımlar

### 4.1. Projede Neler Öğrendik?

Bu proje sayesinde teoride işlediğimiz "Ağ Katmanı, TCP/IP İletişimi ve Port Yönlendirmeleri" konularının pratikte tam olarak nasıl kodlandığını öğrendik. Özellikle bir mesajın sunucuya gelip, sunucu tarafından pars edilerek uygun alıcıya yönlendirilmesi süreci veri yapılarının ne kadar önemli olduğunu gösterdi.

### 4.2. Nerelerde Zorlandık ve Nasıl Aştık?

- **Multi-threading Senkronizasyon Problemleri:** İlk denemelerde, aynı anda mesaja basan kullanıcılar sunucunun hata vermesine sebep oluyordu. Bu sorunu `mutex` kilitleri kullanarak (aynı anda kaynak dosyalarına sadece tek thread'in erişmesine izin vererek) aştık.
- **Android - C Entegrasyonu:** Java temelli çalışan Android uygulaması ile C dilinde çalışan sunucunun string (metin) parse etmesindeki Satır Sonu (`\n` veya `\r\n`) karakter farkları bağlantının kopmasına sebep oldu. Çözüm olarak gönderdiğimiz string'lerin sonundaki boşluk ve satır sonu karakterlerini temizleyerek `(null terminator)` ile stabil iletişim sağladık.

### 4.3. Kaynaklarımız

- Beej's Guide to Network Programming (Socket Referansları)
- Ders Notları
- C standart kütüphaneleri dokümantasyonları ve POSIX kılavuzları.
