package ir.chidari.data.auth

import ir.chidari.BuildConfig

/**
 * تنظیمات اتصال به Supabase.
 *
 * مقادیر از فایل **secrets.properties** در ریشه پروژه خوانده می‌شوند
 * (که در `.gitignore` است و هرگز روی گیت نمی‌رود).
 *
 * برای راه‌اندازی:
 *   ۱. فایل `secrets.properties.example` را به `secrets.properties` کپی کنید
 *   ۲. مقادیر پروژه خود را از Supabase → Project Settings → API بردارید
 *   ۳. پروژه را دوباره بسازید
 *
 * راهنمای کامل (شامل تنظیم SMTP جیمیل) در `SUPABASE_SETUP.md` است.
 *
 * تا زمانی که این مقادیر پر نشوند، برنامه در «حالت مهمان» کار می‌کند:
 * همه می‌توانند فروشگاه‌ها و محصولات را ببینند، ولی ثبت‌نام غیرفعال است.
 */
object SupabaseConfig {

    /** نشانی پروژه، مثلاً https://abcdefghijk.supabase.co */
    val URL: String = BuildConfig.SUPABASE_URL

    /** کلید عمومی anon (این کلید برای استفاده در اپ طراحی شده است). */
    val ANON_KEY: String = BuildConfig.SUPABASE_ANON_KEY

    /** آیا برنامه به Supabase وصل است؟ */
    val isConfigured: Boolean
        get() = URL.isNotBlank() && ANON_KEY.isNotBlank()

    val authUrl: String get() = "${URL.trimEnd('/')}/auth/v1"
}
