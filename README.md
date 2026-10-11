# Xolida — shaxsiy ovozli yordamchi (Android)

"Xolida" deb chaqirasiz → signal tovushi chiqadi → buyruqni aytasiz → Xolida bajaradi va ovoz bilan javob beradi.

## Nimalarni qila oladi

| Ayting | Nima bo'ladi |
|---|---|
| "Onamga qo'ng'iroq qil" | Kontaktlardan "Onam"ni topib, qo'ng'iroq qiladi |
| "Akmalga SMS yoz: 10 daqiqada chiqaman" | SMS yuboradi |
| "Ertaga soat 6:30 ga budilnik qo'y" | Soat ilovasiga budilnik qo'yadi |
| "Ish kunlari soat 7 ga budilnik" | Takrorlanadigan budilnik |
| "15 daqiqaga taymer qo'y" | Taymer |
| "Dushanba va chorshanba soat 9 da matematika darsim bor" | Kalendarga har haftalik dars yozadi, 10 daqiqa oldin eslatadi |
| "Juma kuni soat 3 da uchrashuvni yozib qo'y" | Kalendarga reja |
| "Ertaga nima rejalarim bor?" | Kalendardagi rejalarni o'qib beradi |
| "Toshkentda nechta tuman bor?" | Oddiy savolga javob beradi |

## Qanday ishlaydi (qisqacha)

1. **"Xolida" so'zi** telefonning o'zida, internetsiz tinglanadi (Vosk o'zbek modeli, birinchi ishga tushishda ~50 MB yuklanadi).
2. **Buyrug'ingiz** Google ovozni tanish xizmati orqali o'zbekcha matnga aylanadi.
3. **Claude** (Anthropic AI) gapingizni tushunib, qaysi amalni bajarish kerakligini aytadi.
4. Ilova amalni bajaradi va **ovoz bilan** javob beradi.

---

## O'rnatish: qadamma-qadam (dasturlash bilmasangiz ham)

### 1-qadam. Claude API kalitini olish
1. https://console.anthropic.com saytiga kiring va ro'yxatdan o'ting.
2. **Billing** bo'limida hisobni to'ldiring (eng kam miqdor yetarli — har bir buyruq bir sentdan ancha arzon).
3. **API Keys** → **Create Key**. `sk-ant-...` bilan boshlanadigan kalitni nusxalab, xavfsiz joyga saqlang. Uni hech kimga bermang.

### 2-qadam. Kompyuterga Android Studio o'rnatish
1. https://developer.android.com/studio dan yuklab oling (Windows, Mac yoki Linux).
2. O'rnatishda hamma narsani standart holatda qoldiring ("Next" bosib boring). Birinchi ochilishda u kerakli fayllarni o'zi yuklaydi — biroz kuting.

### 3-qadam. Loyihani ochish
1. `Xolida.zip` arxivini oching (masalan, `Hujjatlar/Xolida` papkasiga).
2. Android Studio → **Open** → `Xolida` papkasini tanlang.
3. Pastda "Gradle sync" jarayoni boradi (birinchi marta 5–15 daqiqa, internet kerak). Agar Android Studio Gradle yoki plaginlarni yangilashni taklif qilsa — rozi bo'ling.

### 4-qadam. Telefonni tayyorlash
1. Telefonda: **Sozlamalar → Telefon haqida → Build raqami (Номер сборки)** ustiga 7 marta bosing. "Siz endi dasturchisiz" degan yozuv chiqadi.
2. **Sozlamalar → Tizim → Dasturchilar uchun** bo'limida **USB orqali sozlash (USB debugging)** ni yoqing.
3. Telefonni kompyuterga USB kabel bilan ulang va telefonda chiqqan so'rovga **Ruxsat berish** ni bosing.

### 5-qadam. Ilovani telefonga o'rnatish
Android Studio tepasida telefoningiz nomi ko'rinadi. Yashil **▶ Run** tugmasini bosing. Bir necha daqiqadan so'ng telefonda **Xolida** ilovasi ochiladi.

### 6-qadam. Xolidani sozlash (telefonda)
1. **API kaliti** maydoniga kalitni qo'ying → **Saqlash**.
2. **Ruxsatlarni berish** → hammasiga "Ruxsat berish".
3. **Boshqa ilovalar ustidan ko'rsatish** → Xolida uchun yoqing (budilnik va taymer fonda ishlashi uchun kerak).
4. **Batareya cheklovini o'chirish** → "Ruxsat berish" (aks holda telefon Xolidani o'chirib qo'yishi mumkin).
5. Avval **Yozma sinov** maydonida tekshiring: "ertaga soat 7 ga budilnik qo'y" deb yozib, **Yuborish**ni bosing.
6. Hammasi ishlasa → **Xolidani ishga tushirish**. Birinchi marta ovoz modeli yuklanadi (Wi-Fi tavsiya etiladi). "Tayyor!" yozuvi chiqqach, "Xolida" deb chaqiring.

---

## APK ni kompyutersiz, GitHub orqali tayyor olish (eng oson yo'l)

Android Studio o'rnatmasdan ham bo'ladi. GitHub bepul serverlarida APK yig'iladi:

1. https://github.com da ro'yxatdan o'ting (bepul).
2. O'ng yuqoridagi **+** → **New repository** → nom: `Xolida` → **Private** → **Create repository**.
3. Ochilgan sahifada **uploading an existing file** havolasini bosing.
4. `Xolida.zip` ni kompyuterda ochib, ichidagi `Xolida` papkasining **ichidagi hamma narsani** (`app`, `gradle`, `.github`, `build.gradle.kts` va boshqalar) sahifaga sudrab tashlang → **Commit changes**.
   - Diqqat: `.github` papkasi yashirin bo'lishi mumkin. Windows'da "Ko'rinish → Yashirin elementlar", Mac'da `Cmd+Shift+.` bilan ko'rsating.
5. Yuqoridagi **Actions** bo'limiga o'ting. "APK yasash" jarayoni 5–10 daqiqa ishlaydi.
6. Yashil belgi chiqqach, jarayon nomini bosing → pastdagi **Artifacts** bo'limidan **Xolida-APK** ni yuklab oling.
7. Ichidagi `app-debug.apk` ni telefonga o'tkazib (Telegram "Saved Messages" orqali ham bo'ladi), ustiga bosing. Telefon "noma'lum manbadan o'rnatish"ga ruxsat so'rasa — ruxsat bering.
8. Keyin yuqoridagi **6-qadam**dagi sozlashga o'ting.

---

## Foydalanish maslahatlari

- **"Xolida" deng, signal tovushini kuting**, keyin buyruqni ayting.
- Kontaktlar telefonda qanday saqlangan bo'lsa, shunday ayting ("Onam", "Dadam", "Akmal aka").
- Darslar va rejalar telefoningizdagi **Google Kalendar**ga yoziladi — telefonda Google hisobi bo'lishi kerak.
- Telefon qayta yoqilgandan keyin Xolida ilovasini bir marta ochib, **ishga tushirish**ni bosing.

## Muammolar va yechimlar

**"Xolida" deganda javob bermayapti.** Ilovadagi **Jurnal**ga qarang: "Eshitildi: ..." qatorida telefon sizni qanday eshitganini ko'rasiz. Masalan "holda" deb eshitsa, chaqiruv so'zlari maydoniga `, holda` ni qo'shib **Saqlash** bosing va Xolidani qayta ishga tushiring.

**"O'zbek tilini tanish yoqilmagan".** Telefonda Google ilovasi o'rnatilgan va yangilangan bo'lsin. Sozlamalar → Google → Ovoz bo'limida o'zbek tilini qo'shing.

**Xolida g'alati talaffuzda gapiradi.** Google o'zbekcha ovozli o'qishni hali yaxshi qo'llab-quvvatlamaydi, shuning uchun ilova turk ovozidan foydalanadi. Sozlamalar → Tizim → Til → Matnni ovozga aylantirish (Text-to-speech) bo'limida boshqa dvigatel tanlab ko'rish mumkin.

**Budilnik qo'yilmadi.** "Boshqa ilovalar ustidan ko'rsatish" ruxsatini tekshiring.

**"API kalit noto'g'ri" / "mablag' yetarli emas".** Kalitni qayta nusxalang yoki console.anthropic.com da balansni tekshiring.

**Batareya.** Xolida doim tinglagani uchun batareya biroz tezroq sarflanadi. Kerak bo'lmaganda bildirishnomadan ilovani ochib to'xtatib qo'yishingiz mumkin.

---

## Fayllar (qiziquvchilar uchun)

```
app/src/main/java/uz/xolida/assistant/
  MainActivity.kt     — sozlash oynasi
  AssistantService.kt — fonda tinglash, buyruq, javob
  Brain.kt            — Claude bilan ishlash (model nomi shu yerda)
  ActionRunner.kt     — qo'ng'iroq, SMS, budilnik, kalendar
  ModelManager.kt     — o'zbekcha ovoz modelini yuklash
  Prefs.kt            — sozlamalar
```

Yangi imkoniyat qo'shmoqchi bo'lsangiz (masalan, musiqa yoqish yoki ilova ochish), `Brain.kt` dagi ko'rsatmaga yangi amal turini va `ActionRunner.kt` ga uni bajaradigan kodni qo'shish kifoya.
