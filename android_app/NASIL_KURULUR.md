# Android Projesini Kurma ve Telefona Yükleme Rehberi

Bu rehber, mevcut kaynak kodlarını Android Studio'ya nasıl entegre edeceğinizi ve uygulamayı kendi fiziksel telefonunuzda nasıl çalıştıracağınızı adım adım açıklar.

## 1. Adım: Android Studio'da Projeyi Oluşturma

1.  **Android Studio'yu açın** ve "New Project" butonuna tıklayın.
2.  **Empty Views Activity** (Boş Görünümlü Aktivite) şablonunu seçin ve "Next" deyin.
3.  **Proje Detayları:**
    *   **Name:** `ChatApp`
    *   **Package Name:** `com.example.chat` (Eğer farklı yaparsanız `MainActivity.java`'nın en üstündeki satırı da ona göre güncellemeniz gerekir).
    *   **Language:** **Java** olarak seçin.
    *   **Minimum SDK:** API 24 (Android 7.0) veya üzeri seçebilirsiniz.
4.  "Finish" diyerek projenin hazır olmasını bekleyin (sağ altta Gradle işlemleri bitene kadar bekleyin).

## 2. Adım: Dosyaları Kopyalama

1.  **Tasarım (XML):** Sol panelde `app > res > layout > activity_main.xml` dosyasını bulun. Bu klasördeki `activity_main.xml` içeriğinin tamamını kopyalayıp oraya yapıştırın.
2.  **Kod (Java):** Sol panelde `app > java > com.example.chat > MainActivity` dosyasını bulun. Bu klasördeki `MainActivity.java` içindekileri kopyalayın.
    *   *Dikkat:* Dosyanın en üstündeki `package com.example.chat;` satırı sizin projenizle aynı olmalıdır.
3.  **İnternet İzni:** Sol panelde `app > manifests > AndroidManifest.xml` dosyasını açın. `<application ...>` etiketinin hemen üstüne şu satırı ekleyin:
    ```xml
    <uses-permission android:name="android.permission.INTERNET" />
    ```

## 3. Adım: Telefonu Hazırlama ve Bağlama

Uygulamayı telefonda çalıştırmak için telefonunuzun "Geliştirici" olması gerekir:

1.  **Geliştirici Seçeneklerini Açın:** Telefonunuzda *Ayarlar > Telefon Hakkında* kısmına gidin. "Yapım Numarası" (Build Number) yazan yere **7 kez üst üste** dokunun. Artık geliştiricisiniz!
2.  **USB Hata Ayıklama:** *Ayarlar > Sistem > Geliştirici Seçenekleri* (veya Ek Ayarlar içinde) kısmına gidin ve **USB Hata Ayıklama** (USB Debugging) özelliğini açın.
3.  **Bağlantı:** Telefonunuzu USB kablosuyla bilgisayara bağlayın. Ekranda "USB Hata Ayıklamaya izin verilsin mi?" sorusu gelirse "Her zaman izin ver"i seçip onaylayın.

## 4. Adım: Uygulamayı Telefonda Çalıştırma

1.  Android Studio'nun üst toolbarında, "Run" (Yeşil Oynat butonu) yanındaki cihaz listesinde telefonunuzun adını görmelisiniz.
2.  Telefonunuzu seçin ve **Run** butonuna basın. Uygulama derlenecek ve telefonunuza yüklenecektir.

## 5. Adım: Sunucuya Bağlanma (Kritik Nokta)

*   **Server Bilgisayarda Çalışıyorsa (Örn: C Server):** 
    *   Bilgisayarınızın yerel IP adresini bulun (Windows için CMD'ye `ipconfig` yazın, `IPv4 Address` kısmına bakın, örn: `192.168.1.50`).
    *   Telefondaki uygulamada "Sunucu IP" kısmına emülatör IP'si (`10.0.2.2`) **yazmayın**. Kendi bilgisayarınızın IP adresini yazın.
    *   **Aynı Wi-Fi:** Telefonunuzun ve bilgisayarınızın aynı Wi-Fi ağına bağlı olduğundan emin olun.
*   **Port:** C server hangi portu dinliyorsa (genelde `8080`) onu yazın ve "Bağlan"a basın.
