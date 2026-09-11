# 📤 انتقال پروژه به گیت‌هاب

مخزن گیت آماده شده و commit اول ساخته شده است. فقط باید آن را به گیت‌هاب بفرستید.

---

## ✅ کارهایی که از قبل انجام شده

| کار | وضعیت |
|---|---|
| `git init` و ساخت شاخه `main` | ✅ |
| `.gitignore` کامل (شامل کلیدها و APK) | ✅ |
| commit اول با ۷۵ فایل | ✅ |
| **خارج کردن کلیدهای Supabase از سورس** | ✅ |
| پاک کردن کلیدها از فایل‌های تست HTML | ✅ |

---

## 🔑 مهم‌ترین تغییر: کلیدها دیگر در کد نیستند

قبلاً کلیدهای پروژه شما مستقیم داخل `SupabaseConfig.kt` بود. چون مخزن شما
**عمومی** است، این یعنی هر کسی می‌توانست آن‌ها را بردارد.

حالا کلیدها از فایل **`secrets.properties`** خوانده می‌شوند که در `.gitignore`
است و روی گیت نمی‌رود:

```
ChiDari/
├── secrets.properties          ← کلیدهای واقعی شما (روی گیت نمی‌رود)
└── secrets.properties.example  ← فایل نمونه (روی گیت می‌رود)
```

Gradle موقع ساخت، این مقادیر را داخل `BuildConfig` تزریق می‌کند. اگر فایل
نباشد، برنامه در «حالت مهمان» ساخته می‌شود و کرش نمی‌کند.

---

## 🚀 فرستادن به گیت‌هاب

### گام ۱ — ساخت Personal Access Token

گیت‌هاب دیگر رمز عبور را قبول نمی‌کند و به توکن نیاز دارد:

1. به [github.com/settings/tokens](https://github.com/settings/tokens) بروید
2. **Generate new token** → **Generate new token (classic)**
3. یک نام بگذارید (مثلاً `chidari-push`)
4. مدت اعتبار: مثلاً ۹۰ روز
5. تیک **`repo`** را بزنید (کل دسترسی مخزن)
6. **Generate token** و توکن را کپی کنید — فقط یک‌بار نشان داده می‌شود

### گام ۲ — اجرای دستورها

در پوشه پروژه، این سه دستور را بزنید:

```bash
git remote add origin https://github.com/hashem6790/chidari.git
git branch -M main
git push -u origin main
```

وقتی پرسید:
- **Username:** `hashem6790`
- **Password:** ← **توکن** را بچسبانید (نه رمز حساب)

### روش جایگزین: توکن داخل آدرس

اگر نمی‌خواهید هر بار بپرسد:

```bash
git remote add origin https://hashem6790:YOUR_TOKEN@github.com/hashem6790/chidari.git
git push -u origin main
```

> ⚠️ در این حالت توکن در `.git/config` ذخیره می‌شود. روی کامپیوتر شخصی
> اشکالی ندارد، ولی روی سیستم مشترک از روش اول استفاده کنید.

---

## 📦 فایل APK

فایل نصبی (۴۱ مگابایت) عمداً در `.gitignore` است. گیت‌هاب برای فایل‌های
بزرگ‌تر از ۵۰ مگابایت هشدار می‌دهد و نگه داشتن باینری در تاریخچه گیت،
حجم مخزن را با هر نسخه بالا می‌برد.

**راه درست: بخش Releases**

1. در صفحه مخزن → **Releases** → **Create a new release**
2. تگ: `v1.9`
3. عنوان: `نسخه ۱.۹`
4. فایل `ChiDari-debug.apk` را بکشید و رها کنید
5. **Publish release**

حالا کاربران می‌توانند مستقیم دانلود کنند بدون اینکه مخزن سنگین شود.

---

## 🔄 برای تغییرات بعدی

```bash
git add -A
git commit -m "توضیح تغییر"
git push
```

---

## ⚠️ اگر اشتباهاً کلیدی commit شد

فقط حذف کردن فایل کافی نیست — کلید در تاریخچه می‌ماند. در آن صورت:

1. **فوراً** از پنل Supabase → Settings → API → **Reset** کلید را عوض کنید
2. سپس تاریخچه را پاک کنید:

```bash
git filter-branch --force --index-filter \
  "git rm --cached --ignore-unmatch secrets.properties" \
  --prune-empty --tag-name-filter cat -- --all
git push --force
```

---

## 📁 ساختار مخزن

```
chidari/
├── app/src/main/java/ir/chidari/   کد اصلی (۴۲ فایل، ~۱۱۸۰۰ خط)
├── app/src/test/                    ۲۶ تست واحد
├── supabase-schema.sql              اسکیمای پایگاه داده و RLS
├── supabase-test*.html              صفحات تست مرورگر
├── SUPABASE_SETUP.md                راهنمای راه‌اندازی سرور و SMTP
├── EMAIL_TEMPLATE_FIX.md            قالب‌های ایمیل فارسی
├── secrets.properties.example       نمونه فایل کلیدها
└── README.md                        مستندات پروژه
```
