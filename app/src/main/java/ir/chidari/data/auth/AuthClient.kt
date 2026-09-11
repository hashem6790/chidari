package ir.chidari.data.auth

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * کلاینت احراز هویت Supabase بر پایه GoTrue REST API.
 *
 * از فراخوانی مستقیم REST استفاده شده تا وابستگی سنگینی به برنامه اضافه نشود
 * و حجم و زمان اجرای اپ کم بماند.
 *
 * نشست (توکن‌ها) در SharedPreferences نگهداری می‌شود و در صورت انقضا
 * به‌صورت خودکار با refresh token تمدید می‌گردد.
 */
class AuthClient(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("chidari_auth", Context.MODE_PRIVATE)

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val json = "application/json; charset=utf-8".toMediaType()

    // ---------- نشست ذخیره‌شده ----------

    private object Keys {
        const val ACCESS = "access_token"
        const val REFRESH = "refresh_token"
        const val EXPIRES_AT = "expires_at"
        const val USER_ID = "user_id"
        const val EMAIL = "email"
        const val NAME = "display_name"
        const val CONFIRMED = "confirmed"
    }

    val isConfigured: Boolean get() = SupabaseConfig.isConfigured

    /** کاربر ذخیره‌شده روی دستگاه (بدون تماس با شبکه). */
    fun cachedUser(): AuthUser? {
        val id = prefs.getString(Keys.USER_ID, null) ?: return null
        val email = prefs.getString(Keys.EMAIL, null) ?: return null
        return AuthUser(
            id = id,
            email = email,
            displayName = prefs.getString(Keys.NAME, "").orEmpty(),
            emailConfirmed = prefs.getBoolean(Keys.CONFIRMED, false)
        )
    }

    /** توکن دسترسی معتبر؛ در صورت نیاز تمدید می‌شود. */
    suspend fun validAccessToken(): String? = withContext(Dispatchers.IO) {
        val token = prefs.getString(Keys.ACCESS, null) ?: return@withContext null
        val expiresAt = prefs.getLong(Keys.EXPIRES_AT, 0L)
        // ۶۰ ثانیه حاشیه امن
        if (System.currentTimeMillis() < expiresAt - 60_000) return@withContext token
        refreshSession()
    }

    private fun saveSession(body: JSONObject) {
        val user = body.optJSONObject("user")
        val expiresIn = body.optLong("expires_in", 3600L)
        prefs.edit().apply {
            putString(Keys.ACCESS, body.optString("access_token"))
            putString(Keys.REFRESH, body.optString("refresh_token"))
            putLong(Keys.EXPIRES_AT, System.currentTimeMillis() + expiresIn * 1000)
            if (user != null) {
                putString(Keys.USER_ID, user.optString("id"))
                putString(Keys.EMAIL, user.optString("email"))
                putBoolean(Keys.CONFIRMED, user.optString("email_confirmed_at").isNotBlank() &&
                        user.optString("email_confirmed_at") != "null")
                val meta = user.optJSONObject("user_metadata")
                putString(Keys.NAME, meta?.optString("display_name").orEmpty())
            }
        }.apply()
    }

    fun clearSession() {
        prefs.edit().clear().apply()
    }

    private fun userFrom(obj: JSONObject): AuthUser {
        val meta = obj.optJSONObject("user_metadata")
        val confirmedAt = obj.optString("email_confirmed_at")
        return AuthUser(
            id = obj.optString("id"),
            email = obj.optString("email"),
            displayName = meta?.optString("display_name").orEmpty(),
            emailConfirmed = confirmedAt.isNotBlank() && confirmedAt != "null"
        )
    }

    // ---------- تماس‌های شبکه ----------

    private data class HttpResult(val code: Int, val body: JSONObject?, val raw: String)

    private fun post(path: String, payload: JSONObject, bearer: String? = null): HttpResult {
        return try {
            val req = Request.Builder()
                .url("${SupabaseConfig.authUrl}$path")
                .addHeader("apikey", SupabaseConfig.ANON_KEY)
                .addHeader("Authorization", "Bearer ${bearer ?: SupabaseConfig.ANON_KEY}")
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(json))
                .build()
            http.newCall(req).execute().use { res ->
                val text = res.body?.string().orEmpty()
                val obj = runCatching { JSONObject(text) }.getOrNull()
                HttpResult(res.code, obj, text)
            }
        } catch (e: IOException) {
            HttpResult(0, null, e.javaClass.simpleName + ": " + e.message.orEmpty())
        }
    }

    /** درخواست PUT — برای تغییر مشخصات کاربر لازم است. */
    private fun put(path: String, payload: JSONObject, bearer: String): HttpResult {
        return try {
            val req = Request.Builder()
                .url("${SupabaseConfig.authUrl}$path")
                .addHeader("apikey", SupabaseConfig.ANON_KEY)
                .addHeader("Authorization", "Bearer $bearer")
                .addHeader("Content-Type", "application/json")
                .put(payload.toString().toRequestBody(json))
                .build()
            http.newCall(req).execute().use { res ->
                val text = res.body?.string().orEmpty()
                HttpResult(res.code, runCatching { JSONObject(text) }.getOrNull(), text)
            }
        } catch (e: IOException) {
            HttpResult(0, null, e.javaClass.simpleName + ": " + e.message.orEmpty())
        }
    }

    private fun errorMessage(r: HttpResult): String {
        val msg = r.body?.optString("msg").takeIf { !it.isNullOrBlank() && it != "null" }
            ?: r.body?.optString("error_description").takeIf { !it.isNullOrBlank() && it != "null" }
            ?: r.body?.optString("message").takeIf { !it.isNullOrBlank() && it != "null" }
            ?: r.body?.optString("error").takeIf { !it.isNullOrBlank() && it != "null" }
        return translateAuthError(msg, r.code)
    }

    /** ثبت‌نام با ایمیل و رمز عبور. ایمیل تأیید از طریق SMTP پروژه ارسال می‌شود. */
    suspend fun signUp(email: String, password: String, displayName: String): AuthResult =
        withContext(Dispatchers.IO) {
            if (!isConfigured) return@withContext AuthResult.Error(NOT_CONFIGURED)

            val payload = JSONObject().apply {
                put("email", email.trim())
                put("password", password)
                put("data", JSONObject().put("display_name", displayName.trim()))
            }
            val r = post("/signup", payload)
            if (r.code !in 200..299) return@withContext AuthResult.Error(errorMessage(r))

            val body = r.body ?: return@withContext AuthResult.Error("پاسخ نامعتبر از سرور")

            // اگر تأیید ایمیل لازم باشد، Supabase توکن برنمی‌گرداند
            val hasToken = body.optString("access_token").isNotBlank()
            if (!hasToken) {
                return@withContext AuthResult.NeedsEmailConfirmation(email.trim())
            }
            saveSession(body)
            val user = body.optJSONObject("user")?.let { userFrom(it) }
                ?: return@withContext AuthResult.Error("اطلاعات کاربر دریافت نشد")
            AuthResult.Success(user)
        }

    /** ورود با ایمیل و رمز عبور. */
    suspend fun signIn(email: String, password: String): AuthResult =
        withContext(Dispatchers.IO) {
            if (!isConfigured) return@withContext AuthResult.Error(NOT_CONFIGURED)

            val payload = JSONObject().apply {
                put("email", email.trim())
                put("password", password)
            }
            val r = post("/token?grant_type=password", payload)
            if (r.code !in 200..299) return@withContext AuthResult.Error(errorMessage(r))

            val body = r.body ?: return@withContext AuthResult.Error("پاسخ نامعتبر از سرور")
            saveSession(body)
            val user = body.optJSONObject("user")?.let { userFrom(it) }
                ?: return@withContext AuthResult.Error("اطلاعات کاربر دریافت نشد")
            AuthResult.Success(user)
        }

    /**
     * تأیید حساب با **کدی** که در ایمیل آمده است.
     *
     * طول کد را Supabase تعیین می‌کند (معمولاً ۶ یا ۸ رقم) و ممکن است
     * در پروژه‌های مختلف فرق کند؛ بنابراین اینجا طول ثابتی تحمیل نمی‌شود
     * و اعتبارسنجی نهایی بر عهده سرور است.
     *
     * این روش جایگزین کلیک روی لینک است. لینک ایمیل به یک آدرس وب هدایت
     * می‌شود که روی گوشی باز نمی‌شود؛ ولی کد عددی را کاربر مستقیم در
     * برنامه وارد می‌کند و نیازی به مرورگر نیست.
     *
     * در صورت موفقیت، کاربر بلافاصله وارد حساب می‌شود.
     */
    suspend fun verifyOtp(email: String, code: String): AuthResult = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext AuthResult.Error(NOT_CONFIGURED)

        val digits = code.filter { it.isDigit() }
        if (digits.length < MIN_OTP_LENGTH) {
            return@withContext AuthResult.Error("کد تأیید را کامل وارد کنید.")
        }

        val payload = JSONObject().apply {
            put("type", "signup")
            put("email", email.trim())
            put("token", digits)
        }
        val r = post("/verify", payload)
        if (r.code !in 200..299) {
            val raw = r.body?.optString("msg").orEmpty() +
                    " " + r.body?.optString("error_code").orEmpty()
            val msg = when {
                raw.contains("expired", true) || raw.contains("otp_expired", true) ->
                    "کد وارد شده اشتباه است یا منقضی شده. «ارسال دوباره» را بزنید."
                raw.contains("not found", true) ->
                    "کاربری با این ایمیل پیدا نشد."
                else -> translateAuthError(r.body?.optString("msg"), r.code)
            }
            return@withContext AuthResult.Error(msg)
        }

        val body = r.body ?: return@withContext AuthResult.Error("پاسخ نامعتبر از سرور")
        saveSession(body)
        val user = body.optJSONObject("user")?.let { userFrom(it) }
            ?: return@withContext AuthResult.Error("اطلاعات کاربر دریافت نشد")
        AuthResult.Success(user)
    }

    /** ارسال دوباره ایمیل تأیید. */
    suspend fun resendConfirmation(email: String): AuthResult = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext AuthResult.Error(NOT_CONFIGURED)
        val payload = JSONObject().apply {
            put("type", "signup")
            put("email", email.trim())
        }
        val r = post("/resend", payload)
        if (r.code !in 200..299) return@withContext AuthResult.Error(errorMessage(r))
        AuthResult.Message("ایمیل تأیید دوباره ارسال شد. صندوق ورودی و پوشه اسپم را ببینید.")
    }

    /**
     * گام ۱ از بازیابی رمز: تأیید کد بازیابی.
     *
     * برخلاف تأیید ثبت‌نام، این کد کاربر را وارد حساب نمی‌کند بلکه یک
     * **نشست موقت** می‌دهد که فقط برای تغییر رمز به کار می‌آید.
     * توکن به‌صورت داخلی برگردانده می‌شود تا در گام بعد استفاده شود.
     */
    suspend fun verifyRecoveryCode(email: String, code: String): RecoveryResult =
        withContext(Dispatchers.IO) {
            if (!isConfigured) return@withContext RecoveryResult.Error(NOT_CONFIGURED)

            val digits = code.filter { it.isDigit() }
            if (digits.length < MIN_OTP_LENGTH) {
                return@withContext RecoveryResult.Error("کد بازیابی را کامل وارد کنید.")
            }

            val payload = JSONObject().apply {
                put("type", "recovery")
                put("email", email.trim())
                put("token", digits)
            }
            val r = post("/verify", payload)
            if (r.code !in 200..299) {
                val raw = r.body?.optString("msg").orEmpty() + " " +
                        r.body?.optString("error_code").orEmpty()
                val msg = when {
                    raw.contains("expired", true) || raw.contains("otp_expired", true) ->
                        "کد وارد شده اشتباه است یا منقضی شده. «ارسال دوباره» را بزنید."
                    raw.contains("not found", true) -> "کاربری با این ایمیل پیدا نشد."
                    else -> translateAuthError(r.body?.optString("msg"), r.code)
                }
                return@withContext RecoveryResult.Error(msg)
            }

            val token = r.body?.optString("access_token").orEmpty()
            if (token.isBlank()) {
                return@withContext RecoveryResult.Error("نشست بازیابی دریافت نشد. دوباره تلاش کنید.")
            }
            RecoveryResult.Verified(token)
        }

    /**
     * گام ۲ از بازیابی رمز: ثبت رمز تازه با نشست موقت.
     * پس از موفقیت، همان نشست به‌عنوان نشست عادی ذخیره می‌شود و
     * کاربر بدون نیاز به ورود دوباره، وارد حساب می‌شود.
     */
    suspend fun updatePassword(recoveryToken: String, newPassword: String): AuthResult =
        withContext(Dispatchers.IO) {
            if (!isConfigured) return@withContext AuthResult.Error(NOT_CONFIGURED)
            if (newPassword.length < 6) {
                return@withContext AuthResult.Error("رمز عبور باید حداقل ۶ نویسه باشد.")
            }

            val r = put("/user", JSONObject().put("password", newPassword), recoveryToken)
            if (r.code !in 200..299) {
                val raw = r.body?.optString("msg").orEmpty().lowercase()
                val msg = when {
                    raw.contains("different from the old") || raw.contains("same as the old") ->
                        "رمز جدید نباید با رمز قبلی یکی باشد."
                    raw.contains("should be at least") ->
                        "رمز عبور باید حداقل ۶ نویسه باشد."
                    r.code == 401 || raw.contains("expired") ->
                        "مهلت بازیابی تمام شد. لطفاً از ابتدا دوباره تلاش کنید."
                    else -> translateAuthError(r.body?.optString("msg"), r.code)
                }
                return@withContext AuthResult.Error(msg)
            }

            // پاسخ فقط شامل اطلاعات کاربر است؛ نشست موقت را نگه می‌داریم
            val userObj = r.body ?: return@withContext AuthResult.Error("پاسخ نامعتبر از سرور")
            val user = userFrom(userObj)
            prefs.edit()
                .putString(Keys.ACCESS, recoveryToken)
                .putLong(Keys.EXPIRES_AT, System.currentTimeMillis() + 3600_000)
                .putString(Keys.USER_ID, user.id)
                .putString(Keys.EMAIL, user.email)
                .putString(Keys.NAME, user.displayName)
                .putBoolean(Keys.CONFIRMED, true)
                .apply()
            AuthResult.Success(user)
        }

    /** ارسال ایمیل بازیابی رمز عبور. */
    suspend fun resetPassword(email: String): AuthResult = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext AuthResult.Error(NOT_CONFIGURED)
        val r = post("/recover", JSONObject().put("email", email.trim()))
        if (r.code !in 200..299) return@withContext AuthResult.Error(errorMessage(r))
        AuthResult.Message("لینک بازیابی رمز به ایمیل شما ارسال شد.")
    }

    /** تمدید نشست با refresh token. */
    private fun refreshSession(): String? {
        val refresh = prefs.getString(Keys.REFRESH, null) ?: return null
        val r = post("/token?grant_type=refresh_token", JSONObject().put("refresh_token", refresh))
        if (r.code !in 200..299 || r.body == null) {
            clearSession()
            return null
        }
        saveSession(r.body)
        return r.body.optString("access_token").takeIf { it.isNotBlank() }
    }

    /** بررسی نشست ذخیره‌شده هنگام اجرای برنامه. */
    suspend fun restoreSession(): AuthUser? = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext null
        val cached = cachedUser() ?: return@withContext null
        // اگر توکن قابل تمدید نبود، نشست باطل است
        if (validAccessToken() == null) {
            clearSession()
            return@withContext null
        }
        cached
    }

    /** خروج از حساب. */
    suspend fun signOut() = withContext(Dispatchers.IO) {
        val token = prefs.getString(Keys.ACCESS, null)
        if (isConfigured && token != null) {
            runCatching { post("/logout", JSONObject(), bearer = token) }
        }
        clearSession()
    }

    companion object {
        /** کوتاه‌ترین کدی که ارزش ارسال به سرور را دارد. */
        const val MIN_OTP_LENGTH = 6

        const val NOT_CONFIGURED =
            "اتصال به سرور تنظیم نشده است. مقادیر URL و ANON_KEY را در فایل SupabaseConfig.kt وارد کنید."
    }
}
