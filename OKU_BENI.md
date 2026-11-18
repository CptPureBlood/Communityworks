# Ping AI

## APK'yı GitHub'dan indirme (bilgisayarsız)
1. Bu projeyi GitHub reposuna yükle (main dalına).
2. Repoda Actions sekmesi > "Build APK" çalışır (ilk seferde 5-10 dk). Elle başlatmak için: Run workflow.
3. Çalışma bitince (yeşil tik) içine gir, en altta Artifacts > "PingAI-debug-apk" indir (zip içinden app-debug.apk çıkar).
4. APK'ya dokun, "bilinmeyen kaynaklardan yükleme"ye izin ver, kur.
Bu bir debug APK'dır: kurulur ama Play Store'a yüklenemez.
Kod DERLENMEDİ/test edilmedi; Actions hata verirse log'u bana yapıştır.

## İlk kullanım
Gemini (Ücretsiz) seç > aistudio.google.com/apikey'den key al, yapıştır > konu ve içerik dili seç > ana ekrana "Ping AI" widget'ını ekle.

## Reklam (AdMob)
App ID: AndroidManifest.xml (APPLICATION_ID). Ad Unit ID: ads/AdManager.kt. Şu an Google test ID'leri var.

## Pro
Ayarlar'da kod: PINGAI-PRO (SettingsScreen.kt içinde PRO_UNLOCK_CODE). Gerçek ödeme yok.

## Bilinen sınırlar
- Gemini/Claude model adları eskirse GeminiClient.kt / ClaudeClient.kt içindeki MODEL değiştirilmeli.
- 10/15/30 dk seçenekleri pil tasarrufu yüzünden gecikebilir.
- Widget kalan süresi sadece widget yenilenince güncellenir.
- Arayüz dili sisteme göre otomatik (TR/EN); ayrı seçici yok. İçerik dili ayrı seçilir.
