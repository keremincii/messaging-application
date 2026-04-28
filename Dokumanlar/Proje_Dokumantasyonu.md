# PROJE DOKÜMANTASYONU VE TEKNİK RAPOR

## 1. Proje Genel Bakış
"Güvenli ve Eşzamanlı Mesajlaşma Uygulaması", kullanıcıların internet üzerinden gerçek zamanlı iletişim kurmalarını sağlayan, yetkilendirme mekanizmasına sahip bir platformdur. 

**Proje Kaynak Kodları (Github):**
[https://github.com/keremincii/messaging-application](https://github.com/keremincii/messaging-application)

## 2. Mimari Yapı
Uygulama iki temel bileşenden oluşmaktadır:
1. **C Backend (Sunucu):** Gelen TCP bağlantılarını dinler, kullanıcıları kaydeder, istemciler arasındaki mesajları ve yetkilendirmeleri yönlendirir.
2. **Android İstemci:** Kullanıcı arayüzünü (UI) barındırır. Soket bağlantısını yönetir, mesajları ekranda görüntüler ve şifreleme işlemlerini istemci tarafında yapar.

## 3. Kullanıcı Tanımlama ve Yetkilendirme (İster 2)
Uygulama açıldığında cihazda kayıtlı bir oturum yoksa, kullanıcıdan kendisine bir "Kullanıcı Adı" belirlemesi istenir. 
- Aynı anda arka planda cihaza özgü benzersiz bir "Device Token" (UUID) oluşturulur.
- İstemci, sunucuya bağlanırken `AUTH:[Kullanıcı_Adı]:[Device_Token]` komutunu gönderir.
- Sunucu bu kullanıcı adı daha önce bağlanmış mı diye kontrol eder. Eğer ağda zaten bu isimde başka biri varsa `AUTH_FAIL` döndürerek kullanıcının başka isim seçmesini ister. Başarılı olursa `AUTH_OK` döner.

## 4. Teknik İşlevler ve Özellikler
- **Mesaj Şifreleme (E2EE Yaklaşımı):** Gönderilen tüm mesaj metinleri (ve Base64 resimler) Android uygulamasında `AES-128-CBC` ile şifrelenir. Ağ üzerindeki paket dinleyicileri (Sniffer) sadece şifrelenmiş anlamsız metinler görür.
- **Resim Gönderme:** Kullanıcı galeriden fotoğraf seçtiğinde fotoğraf önce sıkıştırılır, ardından Base64 stringine dönüştürülür ve şifrelenip sunucuya iletilir. Karşı taraf bu yapıyı algılayıp ekrana resim olarak çizer.
- **Çevrimdışı (Offline) Kuyruk:** Android cihaz internete bağlı değilse, kullanıcının yazıp gönderdiği mesajlar yerel hafızaya (SharedPreferences) "offline_queue" adıyla kaydedilir. İnternet gelir gelmez arka planda sunucuya otomatik olarak aktarılır.
- **Geçmiş Sohbetler ve Kişiler:** Uygulamayı o an kimlerin kullandığı (online listesi) dinamik olarak güncellenir. Ayrıca uygulamaya daha önce girmiş herkes "Kişiler" sekmesinde kaydedilir.
- **Sohbet Silme:** Kişiler listesinde bir kullanıcının üzerine uzun basılarak "Sohbeti Sil" menüsü açılabilir. Bu işlem mesaj geçmişini kalıcı olarak temizler.

## 5. Deployment (Dağıtım) ve Docker (İster: Online Çalışma)
Sunucu tarafı C/C++ dilinde (İster 3) geliştirilmiş olup, internet ortamında (VPS) çalışabilmesi için Docker imajı haline getirilmiştir. 
Sunucuda çalıştırmak için şu komutlar kullanılır:
```bash
# Proje dizinine gidilir
cd server
# Docker ile imaj derlenip arka planda başlatılır
docker compose up -d --build
```
Bu sayede uygulama çökse bile sunucu ortamında "restart: always" kuralı sayesinde otomatik olarak yeniden başlar.
