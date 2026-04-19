# Android Projesini Çalıştırma Rehberi

Sınıfta bu Android uygulamasını sunmak için Android Studio kullanmalısınız:

1. **Android Studio'yu Açın** ve "New Project" (Yeni Proje) -> "Empty Views Activity" seçeneğini seçin.
2. Proje Adı olarak `ChatApp` veya istediğiniz bir şey yazın. Dili `Java` olarak seçin.
3. Proje açıldıktan sonra sol taraftan `app/res/layout/activity_main.xml` dosyasını açın, ve bu klasörde yer alan `activity_main.xml` içindekileri kopyalayıp yapıştırın.
4. Yeniden sol taraftan `app/java/com.example.chat/MainActivity.java` (projenize göre paket adı değişebilir) dosyasını açın. Bu klasörde yer alan `MainActivity.java` içindekileri kopyalayıp yapıştırın. *(En üstteki `package com.sizin.paket.adiniz;` satırına dokunmayın)*.
5. **ÖNEMLİ İZİN:** Sol taraftan `app/manifests/AndroidManifest.xml` dosyasını açın. `<application ...>` etiketinin **hemen üstüne** şu izni ekleyin:
   ```xml
   <uses-permission android:name="android.permission.INTERNET" />
   ```
6. Bilgisayarınızda (veya Codeblocks üzerinden) C server'ını çalıştırdıktan sonra Android Studio'dan "Run" butonuna basın. (Emülatör üzerinden PC'nizdeki server'a bağlanmak için IP adresi `10.0.2.2` olmalıdır ve ben bunu varsayılan olarak yazdım).
