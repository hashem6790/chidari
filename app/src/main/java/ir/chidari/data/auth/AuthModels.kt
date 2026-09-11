package ir.chidari.data.auth

/** کاربر احراز هویت‌شده. */
data class AuthUser(
    val id: String,
    val email: String,
    val displayName: String = "",
    val emailConfirmed: Boolean = false
) {
    /** نامی که در رابط کاربری نشان داده می‌شود. */
    val label: String
        get() = displayName.ifBlank { email.substringBefore("@") }
}

/** وضعیت احراز هویت در سراسر برنامه. */
sealed interface AuthState {
    /** در حال بررسی نشست ذخیره‌شده هنگام اجرای برنامه. */
    data object Loading : AuthState

    /** کاربر وارد نشده — می‌تواند فقط تماشا کند. */
    data object Guest : AuthState

    /** کاربر وارد شده است. */
    data class SignedIn(val user: AuthUser) : AuthState
}

/** نتیجه یک عملیات احراز هویت. */
sealed interface AuthResult {
    data class Success(val user: AuthUser) : AuthResult

    /** ثبت‌نام انجام شد ولی ایمیل باید تأیید شود. */
    data class NeedsEmailConfirmation(val email: String) : AuthResult

    /** پیام موفقیت بدون ورود (مثلاً ارسال ایمیل بازیابی رمز). */
    data class Message(val text: String) : AuthResult

    data class Error(val message: String) : AuthResult
}

/** نتیجه گام اول بازیابی رمز (تأیید کد). */
sealed interface RecoveryResult {
    /** کد درست بود؛ توکن موقت برای تغییر رمز آماده است. */
    data class Verified(val recoveryToken: String) : RecoveryResult
    data class Error(val message: String) : RecoveryResult
}

/** خطاهای Supabase به پیام فارسی قابل فهم ترجمه می‌شوند. */
internal fun translateAuthError(raw: String?, httpCode: Int): String {
    val m = raw.orEmpty().lowercase()
    return when {
        m.contains("invalid login credentials") ->
            "ایمیل یا رمز عبور اشتباه است."
        m.contains("email not confirmed") ->
            "ایمیل شما هنوز تأیید نشده است. لطفاً صندوق ورودی خود را بررسی کنید."
        m.contains("user already registered") || m.contains("already been registered") ->
            "این ایمیل قبلاً ثبت شده است. وارد شوید یا رمز خود را بازیابی کنید."
        m.contains("password should be at least") ->
            "رمز عبور باید حداقل ۶ نویسه باشد."
        m.contains("unable to validate email") || m.contains("invalid email") ->
            "قالب ایمیل معتبر نیست."
        m.contains("email rate limit") || m.contains("rate limit") || httpCode == 429 ->
            "تعداد درخواست‌ها زیاد بود. چند دقیقه دیگر تلاش کنید."
        m.contains("signups not allowed") ->
            "ثبت‌نام در حال حاضر غیرفعال است."
        m.contains("error sending confirmation") ->
            "ارسال ایمیل تأیید ناموفق بود. تنظیمات SMTP پروژه را بررسی کنید."
        httpCode == 0 && m.contains("unknownhost") ->
            "سرور در دسترس نیست. اگر اینترنت وصل است، احتمالاً پروژه Supabase " +
                "متوقف شده و باید از پنل دوباره فعال شود."
        httpCode == 0 && m.contains("timeout") ->
            "پاسخی از سرور نرسید. کمی بعد دوباره تلاش کنید."
        httpCode == 0 ->
            "اتصال به اینترنت برقرار نیست."
        raw.isNullOrBlank() ->
            "خطای ناشناخته در ارتباط با سرور (کد $httpCode)"
        else -> raw
    }
}
