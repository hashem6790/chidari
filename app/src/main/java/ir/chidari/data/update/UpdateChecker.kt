package ir.chidari.data.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import ir.chidari.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** اطلاعات نسخه تازه‌ای که روی گیت‌هاب منتشر شده است. */
data class UpdateInfo(
    val versionName: String,
    val notes: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val publishedAt: String
) {
    val sizeLabel: String
        get() = if (sizeBytes > 0) "%.1f مگابایت".format(sizeBytes / 1024.0 / 1024.0) else ""
}

/** وضعیت دانلود گزارش‌شده توسط DownloadManager. */
sealed interface DownloadState {
    data class Running(val percent: Int) : DownloadState
    data object Done : DownloadState
    data class Failed(val reason: Int) : DownloadState
    data object Unknown : DownloadState
}

/** نتیجه بررسی به‌روزرسانی. */
sealed interface UpdateResult {
    data class Available(val info: UpdateInfo) : UpdateResult
    data object UpToDate : UpdateResult
    data class Error(val message: String) : UpdateResult
}

/**
 * بررسی و دریافت به‌روزرسانی از **GitHub Releases**.
 *
 * برنامه در فروشگاه گوگل‌پلی نیست، پس به‌روزرسانی خودکار ندارد. این کلاس
 * آخرین Release مخزن را می‌خواند و اگر نسخه‌اش از نسخه نصب‌شده بالاتر بود،
 * فایل نصبی را دانلود و نصب‌کننده اندروید را باز می‌کند.
 *
 * شرط نصب روی نسخه قبلی: هر دو APK با **یک کلید** امضا شده باشند.
 */
class UpdateChecker(private val context: Context) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * آخرین نسخه منتشرشده را بررسی می‌کند.
     *
     * دو مسیر امتحان می‌شود:
     *  ۱. **API گیت‌هاب** — اطلاعات کامل (توضیحات نسخه، حجم فایل) می‌دهد
     *     ولی برای هر IP ساعتی ۶۰ درخواست سقف دارد. چون اپراتورهای موبایل
     *     IP مشترک دارند، این سقف زود پر می‌شود.
     *  ۲. **ریدایرکت صفحه Releases** — بدون محدودیت نرخ و روی دامنه github.com
     *     (نه api.github.com). وقتی مسیر اول شکست بخورد، از این استفاده می‌شود.
     */
    suspend fun check(): UpdateResult = withContext(Dispatchers.IO) {
        val viaApi = runCatching { checkViaApi() }.getOrElse {
            UpdateResult.Error(describe(it))
        }
        // اگر API به هر دلیلی نشد (سقف نرخ، فیلترینگ، قطعی)، مسیر دوم
        if (viaApi is UpdateResult.Error) {
            val viaWeb = runCatching { checkViaRedirect() }.getOrNull()
            if (viaWeb != null && viaWeb !is UpdateResult.Error) return@withContext viaWeb
        }
        viaApi
    }

    /** مسیر اول: API رسمی. */
    private fun checkViaApi(): UpdateResult {
        val req = Request.Builder()
            .url("https://api.github.com/repos/$REPO/releases/latest")
            .addHeader("Accept", "application/vnd.github+json")
            .addHeader("User-Agent", "ChiDari-Android")
            .build()

        val (code, body) = http.newCall(req).execute().use { it.code to it.body?.string().orEmpty() }

        if (code == 404) return UpdateResult.Error("هنوز نسخه‌ای منتشر نشده است.")
        if (code == 403 || code == 429) {
            return UpdateResult.Error("سقف درخواست گیت‌هاب پر شده؛ کمی بعد دوباره تلاش کنید.")
        }
        if (code !in 200..299) return UpdateResult.Error("پاسخ سرور: $code")

        val json = JSONObject(body)
        val tag = json.optString("tag_name").removePrefix("v").trim()
        if (tag.isBlank()) return UpdateResult.Error("نسخه در Release مشخص نشده است.")

        var url = ""
        var size = 0L
        json.optJSONArray("assets")?.let { arr ->
            for (i in 0 until arr.length()) {
                val a = arr.optJSONObject(i) ?: continue
                if (a.optString("name").endsWith(".apk", true)) {
                    url = a.optString("browser_download_url")
                    size = a.optLong("size")
                    break
                }
            }
        }
        if (url.isBlank()) return UpdateResult.Error("فایل نصبی به این Release پیوست نشده است.")

        if (!isNewer(tag, BuildConfig.VERSION_NAME)) return UpdateResult.UpToDate

        return UpdateResult.Available(
            UpdateInfo(
                versionName = tag,
                notes = json.optString("body").take(600),
                downloadUrl = url,
                sizeBytes = size,
                publishedAt = json.optString("published_at").take(10)
            )
        )
    }

    /**
     * مسیر دوم: از ریدایرکت صفحه Releases نسخه را می‌خوانیم.
     * `github.com/OWNER/REPO/releases/latest` به `/releases/tag/vX.Y` هدایت می‌شود.
     */
    private fun checkViaRedirect(): UpdateResult {
        // کلاینت بدون دنبال کردن ریدایرکت تا فقط هدر Location را بخوانیم
        val noRedirect = http.newBuilder().followRedirects(false).build()
        val req = Request.Builder()
            .url("https://github.com/$REPO/releases/latest")
            .addHeader("User-Agent", "ChiDari-Android")
            .head()
            .build()

        val location = noRedirect.newCall(req).execute().use { res ->
            res.header("Location") ?: res.request.url.toString()
        }

        val tag = location.substringAfterLast("/tag/", "").trim()
        if (tag.isBlank()) return UpdateResult.Error("نسخه‌ای پیدا نشد.")

        val version = tag.removePrefix("v")
        if (!isNewer(version, BuildConfig.VERSION_NAME)) return UpdateResult.UpToDate

        // نام فایل طبق قرارداد workflow انتشار ساخته می‌شود
        val apk = "ChiDari-$version.apk"
        return UpdateResult.Available(
            UpdateInfo(
                versionName = version,
                notes = "",
                downloadUrl = "https://github.com/$REPO/releases/download/$tag/$apk",
                sizeBytes = 0L,
                publishedAt = ""
            )
        )
    }

    private fun describe(t: Throwable): String {
        val m = t.message.orEmpty().lowercase()
        return when {
            m.contains("unknownhost") -> "اتصال به اینترنت برقرار نیست."
            m.contains("timeout") -> "پاسخی از گیت‌هاب نرسید."
            else -> "بررسی به‌روزرسانی ممکن نشد."
        }
    }

    /**
     * مقایسه نسخه‌ها به‌صورت عددی و بخش‌به‌بخش.
     * مثلاً «۱.۱۰» از «۱.۹» بالاتر است (مقایسه متنی این را اشتباه می‌فهمید).
     */
    private fun isNewer(remote: String, local: String): Boolean {
        fun parts(v: String) = v.split(".", "-")
            .mapNotNull { it.filter(Char::isDigit).toIntOrNull() }
        val r = parts(remote)
        val l = parts(local)
        val n = maxOf(r.size, l.size)
        for (i in 0 until n) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    /**
     * دانلود فایل نصبی با DownloadManager سیستم.
     * شناسه دانلود برگردانده می‌شود تا بتوان پایانش را دنبال کرد.
     */
    fun startDownload(info: UpdateInfo): Long {
        val fileName = "ChiDari-${info.versionName}.apk"
        // فایل قبلی با همین نام پاک می‌شود تا نسخه کهنه نصب نشود
        File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName).delete()

        val request = DownloadManager.Request(Uri.parse(info.downloadUrl))
            .setTitle("چی داری؟ نسخه ${info.versionName}")
            .setDescription("در حال دانلود به‌روزرسانی…")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(
                context, Environment.DIRECTORY_DOWNLOADS, fileName
            )
            .setMimeType("application/vnd.android.package-archive")

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return dm.enqueue(request)
    }

    /**
     * وضعیت دانلود را از خود DownloadManager می‌پرسد.
     *
     * صرفِ وجود فایل کافی نیست: DownloadManager فایل را از همان ابتدا
     * می‌سازد و کم‌کم پر می‌کند. اگر فقط وجود فایل را چک کنیم، نصب روی
     * فایل نیمه‌کاره اجرا می‌شود و با خطای «بسته نامعتبر» شکست می‌خورد.
     */
    fun downloadStatus(downloadId: Long): DownloadState {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val q = DownloadManager.Query().setFilterById(downloadId)
        dm.query(q).use { c ->
            if (c == null || !c.moveToFirst()) return DownloadState.Unknown
            val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val soFar = c.getLong(
                c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            )
            val total = c.getLong(
                c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            )
            return when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> DownloadState.Done
                DownloadManager.STATUS_FAILED -> {
                    val reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    DownloadState.Failed(reason)
                }
                DownloadManager.STATUS_PAUSED, DownloadManager.STATUS_PENDING,
                DownloadManager.STATUS_RUNNING -> {
                    val pct = if (total > 0) ((soFar * 100) / total).toInt() else 0
                    DownloadState.Running(pct)
                }
                else -> DownloadState.Unknown
            }
        }
    }

    /** مسیر فایل دانلودشده (فقط پس از تأیید کامل شدن استفاده شود). */
    fun downloadedFile(versionName: String): File? {
        val f = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "ChiDari-$versionName.apk"
        )
        return if (f.exists() && f.length() > 0) f else null
    }

    /** باز کردن نصب‌کننده اندروید برای فایل دانلودشده. */
    fun installApk(file: File) {
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** آیا اجازه نصب برنامه از منابع ناشناس داده شده؟ (اندروید ۸ به بالا) */
    fun canInstallPackages(): Boolean =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
            context.packageManager.canRequestPackageInstalls()
        else true

    /** باز کردن تنظیمات برای دادن اجازه نصب. */
    fun openInstallPermissionSettings() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            runCatching {
                context.startActivity(
                    Intent(
                        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    /** باز کردن صفحه Releases در مرورگر (راه جایگزین). */
    fun openReleasesPage() {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/$REPO/releases/latest"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    companion object {
        /** مخزن گیت‌هاب که نسخه‌ها از آن خوانده می‌شوند. */
        const val REPO = "hashem6790/chidari"
    }
}
