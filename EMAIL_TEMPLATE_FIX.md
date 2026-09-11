# 🔧 رفع مشکل «ایمیل دریافتی را نمی‌توان تأیید کرد»

> **درباره طول کد:** Supabase بسته به تنظیمات پروژه ممکن است کد **۶ رقمی یا
> ۸ رقمی** بفرستد. برنامه هر دو را می‌پذیرد — کافی است کد را کامل از ایمیل
> کپی کنید. اگر می‌خواهید طولش را تغییر دهید:
> **Authentication → Sign In / Providers → Email → Email OTP Length**

## چرا لینک کار نمی‌کند

لینک داخل ایمیل، بعد از تأیید، کاربر را به آدرسی هدایت می‌کند که به‌صورت
پیش‌فرض روی `http://localhost:3000` تنظیم است — یعنی **یک وب‌سایت روی همان
کامپیوتر**. چنین آدرسی روی گوشی وجود ندارد، پس کلیک روی لینک به صفحه‌ی خطا
می‌رسد یا اصلاً باز نمی‌شود.

این ایراد راهنمای قبلی من بود: گفتم `chidari://auth-callback` را در
Redirect URLs اضافه کنید، ولی اپ آن زمان اصلاً deep link را مدیریت نمی‌کرد.

## راه‌حل: کد عددی به‌جای لینک

به‌جای اینکه کاربر را از اپ بیرون بفرستیم به مرورگر و برگردانیم، از **کد
عددی** استفاده می‌کنیم. این روش:

- به مرورگر و deep link نیازی ندارد
- در ایران که ممکن است لینک‌ها فیلتر شوند مطمئن‌تر است
- تجربه‌ی آشناتری دارد (مثل کد پیامکی)

نسخه‌ی جدید اپ صفحه‌ی «کد تأیید را وارد کنید» دارد. فقط باید **قالب ایمیل**
را طوری تنظیم کنید که کد را نشان دهد.

---

## کاری که باید بکنید (۲ دقیقه)

در پنل Supabase به این مسیر بروید:

**Authentication → Emails → Confirm signup**

محتوای قالب را **کامل پاک کنید** و کد زیر را جایگزین کنید:

```html
<div style="font-family:Tahoma,Arial,sans-serif;direction:rtl;text-align:right;
            max-width:520px;margin:0 auto;padding:28px;background:#F8F5F2;
            border-radius:16px;color:#1B1B1B">

  <div style="text-align:center;margin-bottom:22px">
    <div style="font-size:44px">🏪</div>
    <h1 style="margin:8px 0 0;font-size:22px;color:#00695C">چی داری؟</h1>
  </div>

  <p style="font-size:15px;line-height:2;margin:0 0 18px">
    سلام! برای تکمیل ثبت‌نام، کد زیر را در برنامه وارد کنید:
  </p>

  <div style="background:#fff;border:2px dashed #00695C;border-radius:14px;
              padding:22px;text-align:center;margin:20px 0">
    <div style="font-size:13px;color:#6B6560;margin-bottom:8px">کد تأیید شما</div>
    <div style="font-size:36px;font-weight:bold;letter-spacing:10px;
                color:#00695C;font-family:monospace">{{ .Token }}</div>
  </div>

  <p style="font-size:13px;color:#6B6560;line-height:1.9;margin:18px 0 0">
    این کد تا <b>یک ساعت</b> معتبر است.<br>
    اگر شما درخواست ثبت‌نام نداده‌اید، این ایمیل را نادیده بگیرید.
  </p>

  <hr style="border:0;border-top:1px solid #E3DDD6;margin:22px 0">

  <p style="font-size:12px;color:#9A948E;text-align:center;margin:0">
    این ایمیل به‌صورت خودکار ارسال شده است.
  </p>
</div>
```

روی **Save** بزنید.

> 🔑 نکته‌ی کلیدی: متغیر `{{ .Token }}` همان کد است.
> قالب پیش‌فرض Supabase از `{{ .ConfirmationURL }}` استفاده می‌کند که لینک است.
> حتماً باید `{{ .Token }}` در قالب باشد، وگرنه کاربر کدی نمی‌بیند.

---

## قالب بازیابی رمز عبور

مسیر: **Authentication → Emails → Reset password**

محتوای قالب را کامل پاک کنید و این را جایگزین کنید:

```html
<div style="font-family:Tahoma,Arial,sans-serif;direction:rtl;text-align:right;
            max-width:520px;margin:0 auto;padding:28px;background:#F8F5F2;
            border-radius:16px;color:#1B1B1B">

  <div style="text-align:center;margin-bottom:22px">
    <div style="font-size:44px">🔑</div>
    <h1 style="margin:8px 0 0;font-size:22px;color:#00695C">چی داری؟</h1>
  </div>

  <p style="font-size:15px;line-height:2;margin:0 0 18px">
    درخواست بازیابی رمز عبور برای حساب شما ثبت شد.<br>
    کد زیر را در برنامه وارد کنید تا رمز تازه‌ای بسازید:
  </p>

  <div style="background:#fff;border:2px dashed #00695C;border-radius:14px;
              padding:22px;text-align:center;margin:20px 0">
    <div style="font-size:13px;color:#6B6560;margin-bottom:8px">کد بازیابی رمز عبور</div>
    <div style="font-size:36px;font-weight:bold;letter-spacing:10px;
                color:#00695C;font-family:monospace">{{ .Token }}</div>
  </div>

  <div style="background:#FFF4E0;border:1px solid #F0D9A8;border-radius:12px;
              padding:14px;margin:20px 0">
    <p style="font-size:13px;color:#8A6100;line-height:1.9;margin:0">
      ⚠️ <b>اگر شما این درخواست را نداده‌اید</b>، این ایمیل را نادیده بگیرید.
      رمز فعلی شما تغییر نمی‌کند و حسابتان امن است.
    </p>
  </div>

  <p style="font-size:13px;color:#6B6560;line-height:1.9;margin:18px 0 0">
    این کد تا <b>یک ساعت</b> معتبر است و فقط یک‌بار قابل استفاده است.
  </p>

  <hr style="border:0;border-top:1px solid #E3DDD6;margin:22px 0">

  <p style="font-size:12px;color:#9A948E;text-align:center;margin:0">
    این ایمیل به‌صورت خودکار ارسال شده است.
  </p>
</div>
```

روی **Save** بزنید.

### تفاوت‌ها با قالب ثبت‌نام

| | ثبت‌نام | بازیابی رمز |
|---|---|---|
| آیکون | 🏪 | 🔑 |
| متغیر کد | `{{ .Token }}` | `{{ .Token }}` (یکسان) |
| هشدار امنیتی | ندارد | دارد — مهم است |

هشدار امنیتی را حذف نکنید: اگر کسی ایمیل شما را بداند می‌تواند درخواست
بازیابی بفرستد، و کاربر باید بداند که تا کد را وارد نکند رمزش عوض نمی‌شود.

> ⚠️ **توجه:** با گذاشتن این قالب، کاربر یک کد دریافت می‌کند — ولی
> نسخه‌ی فعلی اپ **صفحه‌ی وارد کردن این کد و ساختن رمز جدید را ندارد**؛
> فقط ایمیل را می‌فرستد. یعنی فعلاً بن‌بست است.
>
> اگر می‌خواهید این قابلیت کامل شود، بگویید تا صفحه‌ی «رمز جدید» را در اپ
> بسازم (وارد کردن کد ← رمز تازه ← ورود خودکار).

---

## تنظیم Redirect URL (پاک‌سازی)

چون دیگر از لینک استفاده نمی‌کنیم، تنظیم قبلی لازم نیست. ولی برای اینکه
اگر کسی روی لینک زد به صفحه‌ی خطا نرسد، در:

**Authentication → URL Configuration → Site URL**

می‌توانید یک آدرس واقعی بگذارید (مثلاً سایت خودتان). اگر سایتی ندارید،
همان `http://localhost:3000` را بگذارید — دیگر اهمیتی ندارد چون کاربر
از کد استفاده می‌کند.

---

## ✅ آزمایش

1. اپ جدید را نصب کنید.
2. تب **حساب من** → **ثبت‌نام**
3. ایمیل و رمز بزنید → صفحه‌ی «کد تأیید را وارد کنید» می‌آید.
4. ایمیل را باز کنید — باید یک **کد درشت** (۶ یا ۸ رقمی) ببینید.
5. کد را وارد کنید → مستقیم وارد حساب می‌شوید. ✅

اگر در ایمیل کد نبود و فقط دکمه/لینک بود، یعنی قالب ذخیره نشده — مرحله‌ی
بالا را دوباره انجام دهید.

---

## اگر حساب قبلی‌تان تأیید نشده مانده

کاربرانی که قبلاً ثبت‌نام کردند ولی نتوانستند تأیید کنند، دو راه دارند:

- **راه اول:** در اپ دوباره با همان ایمیل «ثبت‌نام» بزنند — کد جدید برایشان
  می‌آید و می‌توانند تأیید کنند.
- **راه دوم:** شما از پنل **Authentication → Users** آن کاربر را حذف کنید
  تا از نو ثبت‌نام کند.

> می‌توانید از همان صفحه، با دکمه‌ی سه‌نقطه‌ی کنار هر کاربر، گزینه‌ی
> **Confirm email** را بزنید تا دستی تأییدش کنید.
