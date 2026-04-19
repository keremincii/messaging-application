# Sunum: C Tabanlı Ağ Mesajlaşma Uygulaması

*Not: Her bir başlık bir PowerPoint yansısı (slayt) olarak tasarlanmıştır.*

---

## 1. Yansı: Başlık
**C & TCP Soket Tabanlı Ağ Mesajlaşma Sistemi**
**Ders:** Bilgisayar Ağları
**Hazırlayanlar:** 
- [Adınız Soyadınız]
- [Grup Arkadaşınızın Adı Soyadı]

---

## 2. Yansı: Projenin Amacı
- Teorik olarak öğrenilen ağ bilgilerinin (TCP/IP, İstemci-Sunucu mimarisi) pratik koda dökülmesi.
- Gerçek zamanlı mesaj gönderimini ve yetkilendirmeyi sağlayan çalışan bir ürün ortaya çıkarmak.

---

## 3. Yansı: Mimari Tasarım
- **Merkezi Sunucu:** Tüm istemcilerin ve mesajların toplandığı ve dağıtıldığı Linux/Windows destekli C programı.
- **İstemciler:**
  - C dili ile yazılmış masaüstü (CLI) İstemcisi.
  - Java dili ile yazılmış Android Mobil İstemci.
- **Protokol:** Veri kaybını en aza indiren ve güvenli iletişim kuran TCP kullanılmıştır.

---

## 4. Yansı: Kullanıcı Tanımlama ve Yetkilendirme
- **Kayıt Olma (Registration):** İlk kez bağlanan kullanıcıların sunucu üzerinde bir hesap oluşturması (kullanıcı adı ve şifre kontrolü).
- **Giriş Yapma (Login):** Aktif sohbet alanına erişim için güvenlik doğrulaması katmanı.
- Bu veriler C dilindeki `fopen()`, `sscanf` komutlarıyla sunucu tarafında işlenir.

---

## 5. Yansı: Alt Yapı ve Multi-threading (Çoklu-işlem)
- Klasik soket programlama, bir kullanıcı veri gönderirken diğerini bekletebilir (Block).
- Biz bunu çözmek için `pthreads` (POSIX Threads) modülünü kullandık. 
- Sunucuya her gelen bağlantı kendine ait özel bir iş parçacığında çalışır.

---

## 6. Yansı: Karşılaşılan Zorluklar
- **Mutex (Mutual Exclusion):** Her thread'in aynı değişkene/dosyaya yazmaya çalışması çökmelere yol açtı.
- Çözüm olarak kilit sistemini (`pthread_mutex_lock`) kullandık. Aynı anda sadece bir kişi mesaj yayınlayabiliyor (saniyenin milyonda biri kadar kitler ve bırakır).
- **Satır Sonu Problemi:** Mobil (Android) ile C sunucusun (Server) string parse ediş farklılıkları nedeniyle veri kopmaları, `(null)` karakteri ile çözüldü.

---

## 7. Yansı: Mobil(Android) İstemci Arayüzü
*Bu slaytta proje çalışırken çektiğiniz bir Android ekran görüntüsünü veya uygulamanın arayüzünü gösterebilirsiniz.*

- Mesaj Gönderme Ekranı
- Sunucu IP ve Port tanımlaması
- Canlı bağlantı ve mesaj akışı

---

## 8. Yansı: Projeden Kazanımlar
- TCP protokolünün C dilinde `socket()`, `bind()`, `listen()`, ve `accept()` süreçlerini derinlemesine öğrendik.
- Bir uygulamanın donmadan eş zamanlı olarak binlerce mesajı nasıl işlediğinin algoritmasını kavradık.

---

## 9. Yansı: Soru & Cevap
- Dinlediğiniz için teşekkür ederiz.
- Sorularınız var mı?
