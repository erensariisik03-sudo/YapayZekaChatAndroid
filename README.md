# Yapay Zeka Chat – Android

Flask, WhatsApp endpoint'i ve Termux arayüzü bu projede yoktur. Uygulama doğrudan Gemini REST API'ye bağlanır.

## Özellikler

- Modern Jetpack Compose arayüzü
- Sohbetleri Room veritabanında kalıcı saklama
- Eski konuşmaya tıklayıp kaldığın yerden devam etme
- Android dosya seçici ile birden fazla dosya ekleme
- Seçilen dosyaları uygulama içine kopyalayıp konuşma ile ilişkilendirme
- Gemini `generateContent` REST API entegrasyonu
- Model listesini API'den yenileme ve manuel model seçimi
- Hata durumunda aynı isteği otomatik olarak 3 denemeye kadar tekrar etme
- 3 denemeden sonra "Tekrar gönder" ve "Model değiştir" önerileri
- API anahtarını kaynak koduna gömmeme; cihaz ayarlarında tutma
- GitHub Actions ile otomatik debug APK derleme ve artifact yayınlama

## Yerelde çalıştırma

Android Studio'da projeyi açıp `app` modülünü çalıştır.

## GitHub

`.github/workflows/build.yml` her `push` ve `pull_request` sonrasında debug APK derler ve artifact olarak yükler.

> Not: Debug APK build için API anahtarına gerek yoktur. API anahtarı uygulamanın Ayarlar ekranından girilir.
## Custom Modu

Ayarlar bölümünden veya üst bardaki `</>` simgesinden Custom Modu açılabilir. Bu modda istekler `gemini-2.5-flash` modeline, düşük `temperature` (`0.2`) ve `maxOutputTokens` (`500`) ile gönderilir; doğrudan kod üretmeye odaklanan sistem talimatı kullanılır.

