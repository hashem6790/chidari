package ir.chidari.data.remote

import ir.chidari.data.auth.AuthClient
import ir.chidari.data.auth.SupabaseConfig
import ir.chidari.data.local.ProductEntity
import ir.chidari.data.local.StoreEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * دسترسی به جدول‌های Supabase از طریق PostgREST.
 *
 * خواندن با کلید عمومی anon انجام می‌شود (همه می‌توانند فروشگاه‌ها را ببینند)،
 * ولی نوشتن با توکن کاربر انجام می‌شود تا سیاست‌های RLS روی سرور اعمال شوند.
 */
class RemoteDataSource(private val auth: AuthClient) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = "application/json; charset=utf-8".toMediaType()

    val isConfigured: Boolean get() = SupabaseConfig.isConfigured

    private val restUrl: String get() = "${SupabaseConfig.URL.trimEnd('/')}/rest/v1"

    private data class Res(val code: Int, val body: String) {
        val ok: Boolean get() = code in 200..299
    }

    /** ساخت درخواست با هدرهای لازم. توکن کاربر در صورت وجود اضافه می‌شود. */
    private fun build(
        url: String,
        method: String,
        payload: String? = null,
        token: String? = null,
        prefer: String? = null
    ): Request {
        val b = Request.Builder()
            .url(url)
            .addHeader("apikey", SupabaseConfig.ANON_KEY)
            .addHeader("Authorization", "Bearer ${token ?: SupabaseConfig.ANON_KEY}")
            .addHeader("Content-Type", "application/json")
        prefer?.let { b.addHeader("Prefer", it) }

        val body = payload?.toRequestBody(json)
        when (method) {
            "GET" -> b.get()
            "POST" -> b.post(body ?: "".toRequestBody(json))
            "PATCH" -> b.patch(body ?: "".toRequestBody(json))
            "DELETE" -> if (body != null) b.delete(body) else b.delete()
        }
        return b.build()
    }

    private fun call(req: Request): Res = try {
        http.newCall(req).execute().use { r ->
            Res(r.code, r.body?.string().orEmpty())
        }
    } catch (e: IOException) {
        // پیام واقعی نگه داشته می‌شود تا بتوان علت را تشخیص داد
        Res(0, e.javaClass.simpleName + ": " + e.message.orEmpty())
    }

    private fun errorOf(r: Res): String {
        if (r.code == 0) return describeNetworkError(r.body)
        val obj = runCatching { JSONObject(r.body) }.getOrNull()
        val msg = obj?.optStringSafe("message").orEmpty()
        val hint = obj?.optStringSafe("hint").orEmpty()
        return when {
            r.code == 404 || msg.contains("does not exist", true) ->
                "جدول‌ها روی سرور ساخته نشده‌اند. فایل supabase-schema.sql را در SQL Editor اجرا کنید."
            r.code == 401 || r.code == 403 ->
                "اجازه دسترسی ندارید. ممکن است نشست شما منقضی شده باشد؛ دوباره وارد شوید."
            msg.contains("row-level security", true) ->
                "این تغییر مجاز نیست (فقط مالک فروشگاه می‌تواند ویرایش کند)."
            msg.isNotBlank() -> if (hint.isNotBlank()) "$msg ($hint)" else msg
            else -> "خطای سرور (کد ${r.code})"
        }
    }

    private fun enc(v: String): String = URLEncoder.encode(v, "UTF-8")

    /**
     * ترجمه خطای شبکه به پیامی که واقعاً به کاربر کمک کند.
     *
     * مهم‌ترین حالت: اگر نام دامنه پیدا نشود (UnknownHost) در حالی که گوشی
     * اینترنت دارد، معمولاً یعنی پروژه Supabase متوقف (paused) شده است —
     * طرح رایگان بعد از چند روز بی‌فعالیتی پروژه را می‌خواباند.
     * پیام «اینترنت وصل نیست» در آن حالت گمراه‌کننده بود.
     */
    private fun describeNetworkError(raw: String): String {
        val m = raw.lowercase()
        return when {
            m.contains("unknownhost") ->
                "سرور برنامه در دسترس نیست. اگر اینترنت شما وصل است، احتمالاً " +
                    "پروژه Supabase متوقف شده — از پنل supabase.com دوباره فعالش کنید."
            m.contains("sockettimeout") || m.contains("timeout") ->
                "پاسخی از سرور نرسید. اینترنت کند است یا سرور موقتاً در دسترس نیست."
            m.contains("ssl") || m.contains("certpath") ->
                "ارتباط امن برقرار نشد. ساعت و تاریخ گوشی را بررسی کنید."
            m.contains("network is unreachable") || m.contains("econnrefused") ->
                "اتصال به اینترنت برقرار نیست."
            else -> "ارتباط با سرور ممکن نشد. اینترنت خود را بررسی کنید."
        }
    }

    // ================= خواندن (عمومی، بدون نیاز به ورود) =================

    /**
     * دریافت فروشگاه‌های یک شهر یا استان.
     * پارامتر خالی یعنی بدون محدودیت.
     */
    suspend fun fetchStores(
        province: String = "",
        city: String = "",
        limit: Int = 500
    ): Result<List<StoreEntity>> = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext Result.failure(IllegalStateException(NOT_CONFIGURED))

        val filters = buildString {
            append("?select=*&is_active=eq.true")
            if (province.isNotBlank()) append("&province=eq.${enc(province)}")
            if (city.isNotBlank()) append("&city=eq.${enc(city)}")
            append("&order=updated_at.desc&limit=$limit")
        }
        val r = call(build("$restUrl/stores$filters", "GET"))
        if (!r.ok) return@withContext Result.failure(IOException(errorOf(r)))
        Result.success(RemoteMappers.parseArray(r.body, RemoteMappers::storeFromJson))
    }

    /** دریافت محصولات مجموعه‌ای از فروشگاه‌ها. */
    suspend fun fetchProductsForStores(storeIds: List<Long>): Result<List<ProductEntity>> =
        withContext(Dispatchers.IO) {
            if (!isConfigured) return@withContext Result.failure(IllegalStateException(NOT_CONFIGURED))
            if (storeIds.isEmpty()) return@withContext Result.success(emptyList())

            val all = mutableListOf<ProductEntity>()
            // برای جلوگیری از طولانی شدن URL، درخواست‌ها را تکه‌تکه می‌فرستیم
            storeIds.chunked(60).forEach { chunk ->
                val inList = chunk.joinToString(",")
                val url = "$restUrl/products?select=*&store_id=in.($inList)&limit=2000"
                val r = call(build(url, "GET"))
                if (!r.ok) return@withContext Result.failure(IOException(errorOf(r)))
                all += RemoteMappers.parseArray(r.body, RemoteMappers::productFromJson)
            }
            Result.success(all)
        }

    /** یک فروشگاه مشخص (برای تازه کردن شمارنده دنبال‌کننده و امتیاز). */
    suspend fun fetchStore(storeId: Long): Result<StoreEntity?> = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext Result.failure(IllegalStateException(NOT_CONFIGURED))
        val r = call(build("$restUrl/stores?select=*&id=eq.$storeId", "GET"))
        if (!r.ok) return@withContext Result.failure(IOException(errorOf(r)))
        Result.success(RemoteMappers.parseArray(r.body, RemoteMappers::storeFromJson).firstOrNull())
    }

    /** فروشگاه‌های متعلق به یک کاربر (مستقل از شهر انتخابی). */
    suspend fun fetchStoresOfOwner(ownerId: String): Result<List<StoreEntity>> =
        withContext(Dispatchers.IO) {
            if (!isConfigured) return@withContext Result.failure(IllegalStateException(NOT_CONFIGURED))
            val r = call(build("$restUrl/stores?select=*&owner_id=eq.$ownerId", "GET"))
            if (!r.ok) return@withContext Result.failure(IOException(errorOf(r)))
            Result.success(RemoteMappers.parseArray(r.body, RemoteMappers::storeFromJson))
        }

    /** فروشگاه‌هایی که کاربر دنبال کرده، با اطلاعات کامل. */
    suspend fun fetchFollowedStores(userId: String): Result<List<StoreEntity>> =
        withContext(Dispatchers.IO) {
            val t = token() ?: return@withContext Result.success(emptyList())
            val ids = fetchFollowedIds(userId).getOrElse { return@withContext Result.failure(it) }
            if (ids.isEmpty()) return@withContext Result.success(emptyList())
            val inList = ids.joinToString(",")
            val r = call(build("$restUrl/stores?select=*&id=in.($inList)", "GET", null, t))
            if (!r.ok) return@withContext Result.failure(IOException(errorOf(r)))
            Result.success(RemoteMappers.parseArray(r.body, RemoteMappers::storeFromJson))
        }

    /** محصولات یک فروشگاه. */
    suspend fun fetchProducts(storeId: Long): Result<List<ProductEntity>> =
        withContext(Dispatchers.IO) {
            if (!isConfigured) return@withContext Result.failure(IllegalStateException(NOT_CONFIGURED))
            val r = call(build("$restUrl/products?select=*&store_id=eq.$storeId", "GET"))
            if (!r.ok) return@withContext Result.failure(IOException(errorOf(r)))
            Result.success(RemoteMappers.parseArray(r.body, RemoteMappers::productFromJson))
        }

    // ================= نوشتن (نیازمند ورود) =================

    private suspend fun token(): String? = auth.validAccessToken()

    /** ساخت فروشگاه روی سرور. شناسه‌ی تولیدشده برگردانده می‌شود. */
    suspend fun createStore(store: StoreEntity, ownerId: String): Result<StoreEntity> =
        withContext(Dispatchers.IO) {
            val t = token() ?: return@withContext Result.failure(IllegalStateException(NEEDS_LOGIN))
            val payload = RemoteMappers.storeToJson(store, ownerId).toString()
            val r = call(
                build("$restUrl/stores", "POST", payload, t, prefer = "return=representation")
            )
            if (!r.ok) return@withContext Result.failure(IOException(errorOf(r)))
            val created = RemoteMappers.parseArray(r.body, RemoteMappers::storeFromJson).firstOrNull()
                ?: return@withContext Result.failure(IOException("پاسخ سرور خالی بود"))
            Result.success(created)
        }

    suspend fun updateStore(store: StoreEntity, ownerId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val t = token() ?: return@withContext Result.failure(IllegalStateException(NEEDS_LOGIN))
            val payload = RemoteMappers.storeToJson(store, ownerId).toString()
            val r = call(build("$restUrl/stores?id=eq.${store.id}", "PATCH", payload, t))
            if (!r.ok) return@withContext Result.failure(IOException(errorOf(r)))
            Result.success(Unit)
        }

    suspend fun deleteStore(storeId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        val t = token() ?: return@withContext Result.failure(IllegalStateException(NEEDS_LOGIN))
        val r = call(build("$restUrl/stores?id=eq.$storeId", "DELETE", null, t))
        if (!r.ok) return@withContext Result.failure(IOException(errorOf(r)))
        Result.success(Unit)
    }

    suspend fun createProduct(product: ProductEntity): Result<ProductEntity> =
        withContext(Dispatchers.IO) {
            val t = token() ?: return@withContext Result.failure(IllegalStateException(NEEDS_LOGIN))
            val payload = RemoteMappers.productToJson(product).toString()
            val r = call(
                build("$restUrl/products", "POST", payload, t, prefer = "return=representation")
            )
            if (!r.ok) return@withContext Result.failure(IOException(errorOf(r)))
            val created = RemoteMappers.parseArray(r.body, RemoteMappers::productFromJson).firstOrNull()
                ?: return@withContext Result.failure(IOException("پاسخ سرور خالی بود"))
            Result.success(created)
        }

    suspend fun updateProduct(product: ProductEntity): Result<Unit> = withContext(Dispatchers.IO) {
        val t = token() ?: return@withContext Result.failure(IllegalStateException(NEEDS_LOGIN))
        val payload = RemoteMappers.productToJson(product).toString()
        val r = call(build("$restUrl/products?id=eq.${product.id}", "PATCH", payload, t))
        if (!r.ok) return@withContext Result.failure(IOException(errorOf(r)))
        Result.success(Unit)
    }

    suspend fun deleteProduct(productId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        val t = token() ?: return@withContext Result.failure(IllegalStateException(NEEDS_LOGIN))
        val r = call(build("$restUrl/products?id=eq.$productId", "DELETE", null, t))
        if (!r.ok) return@withContext Result.failure(IOException(errorOf(r)))
        Result.success(Unit)
    }

    // ---------- فالو و امتیاز ----------

    suspend fun follow(storeId: Long, userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val t = token() ?: return@withContext Result.failure(IllegalStateException(NEEDS_LOGIN))
        val payload = JSONObject()
            .put("user_id", userId)
            .put("store_id", storeId)
            .toString()
        val r = call(
            build("$restUrl/follows", "POST", payload, t, prefer = "resolution=merge-duplicates")
        )
        if (!r.ok) return@withContext Result.failure(IOException(errorOf(r)))
        Result.success(Unit)
    }

    suspend fun unfollow(storeId: Long, userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val t = token() ?: return@withContext Result.failure(IllegalStateException(NEEDS_LOGIN))
        val r = call(
            build("$restUrl/follows?user_id=eq.$userId&store_id=eq.$storeId", "DELETE", null, t)
        )
        if (!r.ok) return@withContext Result.failure(IOException(errorOf(r)))
        Result.success(Unit)
    }

    /** فروشگاه‌هایی که کاربر فالو کرده (فقط شناسه‌ها). */
    suspend fun fetchFollowedIds(userId: String): Result<List<Long>> = withContext(Dispatchers.IO) {
        val t = token() ?: return@withContext Result.success(emptyList())
        val r = call(build("$restUrl/follows?select=store_id&user_id=eq.$userId", "GET", null, t))
        if (!r.ok) return@withContext Result.failure(IOException(errorOf(r)))
        Result.success(RemoteMappers.parseArray(r.body) { it.optLong("store_id") })
    }

    suspend fun rateStore(storeId: Long, userId: String, score: Int): Result<Unit> =
        withContext(Dispatchers.IO) {
            val t = token() ?: return@withContext Result.failure(IllegalStateException(NEEDS_LOGIN))
            val payload = JSONObject()
                .put("user_id", userId)
                .put("store_id", storeId)
                .put("score", score)
                .toString()
            val r = call(
                build("$restUrl/ratings", "POST", payload, t, prefer = "resolution=merge-duplicates")
            )
            if (!r.ok) return@withContext Result.failure(IOException(errorOf(r)))
            Result.success(Unit)
        }

    // ================= تصاویر (Supabase Storage) =================

    private val storageUrl: String
        get() = "${SupabaseConfig.URL.trimEnd('/')}/storage/v1"

    /**
     * بارگذاری تصویر محصول روی سرور.
     *
     * فایل در مسیر `{userId}/{نام یکتا}.jpg` ذخیره می‌شود؛ سیاست‌های
     * سرور اجازه می‌دهند هر کاربر فقط داخل پوشه خودش بنویسد.
     *
     * نشانی عمومی فایل برگردانده می‌شود تا در جدول محصولات ذخیره شود.
     */
    suspend fun uploadProductImage(
        file: File,
        userId: String,
        /** درصد پیشرفت بین ۰ تا ۱ — برای نوار پیشرفت روی بندانگشتی. */
        onProgress: (Float) -> Unit = {}
    ): Result<String> =
        withContext(Dispatchers.IO) {
            if (!isConfigured) return@withContext Result.failure(IllegalStateException(NOT_CONFIGURED))
            if (!file.exists()) return@withContext Result.failure(IOException("فایل تصویر پیدا نشد."))

            val token = token()
                ?: return@withContext Result.failure(IllegalStateException(NEEDS_LOGIN))

            val objectPath = "$userId/${System.currentTimeMillis()}_${file.name}"
            val body = ProgressRequestBody(file, "image/jpeg".toMediaType(), onProgress)

            val req = Request.Builder()
                .url("$storageUrl/object/$BUCKET/$objectPath")
                .addHeader("apikey", SupabaseConfig.ANON_KEY)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "image/jpeg")
                // اگر فایلی با همین نام بود، جایگزین شود
                .addHeader("x-upsert", "true")
                .post(body)
                .build()

            val r = try {
                http.newCall(req).execute().use { Res(it.code, it.body?.string().orEmpty()) }
            } catch (e: IOException) {
                Res(0, e.javaClass.simpleName + ": " + e.message.orEmpty())
            }

            if (!r.ok) {
                val msg = when {
                    r.code == 404 || r.body.contains("Bucket not found", true) ->
                        "فضای ذخیره تصاویر روی سرور ساخته نشده. فایل supabase-storage.sql را اجرا کنید."
                    r.code == 413 || r.body.contains("too large", true) ->
                        "حجم تصویر بیش از حد مجاز است."
                    r.code == 401 || r.code == 403 ->
                        "اجازه بارگذاری ندارید. دوباره وارد شوید."
                    else -> errorOf(r)
                }
                return@withContext Result.failure(IOException(msg))
            }

            Result.success("$storageUrl/object/public/$BUCKET/$objectPath")
        }

    /** حذف تصویر از سرور (هنگام برداشتن عکس محصول). */
    suspend fun deleteProductImage(publicUrl: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isConfigured || !publicUrl.contains("/object/public/$BUCKET/")) {
            return@withContext Result.success(Unit)
        }
        val token = token() ?: return@withContext Result.success(Unit)
        val objectPath = publicUrl.substringAfter("/object/public/$BUCKET/")

        val req = Request.Builder()
            .url("$storageUrl/object/$BUCKET/$objectPath")
            .addHeader("apikey", SupabaseConfig.ANON_KEY)
            .addHeader("Authorization", "Bearer $token")
            .delete()
            .build()
        runCatching { http.newCall(req).execute().close() }
        Result.success(Unit)
    }

    /** بررسی اینکه جدول‌ها روی سرور ساخته شده‌اند یا نه. */
    suspend fun checkSchema(): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext Result.failure(IllegalStateException(NOT_CONFIGURED))
        val r = call(build("$restUrl/stores?select=id&limit=1", "GET"))
        if (r.code == 404) return@withContext Result.success(false)
        if (!r.ok) return@withContext Result.failure(IOException(errorOf(r)))
        Result.success(true)
    }

    companion object {
        /** نام سطل ذخیره تصاویر روی Supabase Storage. */
        const val BUCKET = "product-images"

        const val NOT_CONFIGURED = "اتصال به سرور تنظیم نشده است."
        const val NEEDS_LOGIN = "برای این کار باید وارد حساب خود شوید."
    }
}

/**
 * بدنه‌ی درخواست که هنگام ارسال، درصد پیشرفت را گزارش می‌دهد.
 *
 * OkHttp به‌طور پیش‌فرض چنین چیزی ندارد؛ فایل را بایت‌به‌بایت (در بسته‌های
 * ۸ کیلوبایتی) می‌نویسیم و بعد از هر بسته درصد را اعلام می‌کنیم. بدون این،
 * نوار پیشرفت روی تصویر فقط یک انیمیشن تزئینی می‌بود.
 */
private class ProgressRequestBody(
    private val file: File,
    private val type: okhttp3.MediaType,
    private val onProgress: (Float) -> Unit
) : okhttp3.RequestBody() {

    override fun contentType(): okhttp3.MediaType = type
    override fun contentLength(): Long = file.length()

    override fun writeTo(sink: okio.BufferedSink) {
        val total = file.length().coerceAtLeast(1)
        var written = 0L
        var lastReported = -1
        file.inputStream().use { input ->
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                sink.write(buffer, 0, read)
                written += read
                // فقط وقتی درصد صحیح عوض شد گزارش می‌دهیم تا رابط کاربری
                // با صدها به‌روزرسانی در ثانیه بمباران نشود
                val percent = (written * 100 / total).toInt()
                if (percent != lastReported) {
                    lastReported = percent
                    onProgress(percent / 100f)
                }
            }
        }
    }
}
