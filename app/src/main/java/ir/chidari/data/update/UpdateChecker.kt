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

    /** آخرین نسخه منتشرشده را بررسی می‌کند. */
    suspend fun check(): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("https://api.github.com/repos/$REPO/releases/latest")
                .addHeader("Accept", "application/vnd.github+json")
                .build()

            val body = http.newCall(req).execute().use { res ->
                if (res.code == 404) {
                    return@withContext UpdateResult.Error("هنوز نسخه‌ای منتشر نشده است.")
                }
                if (!res.isSuccessful) {
                    return@withContext UpdateResult.Error("پاسخ سرور: ${res.code}")
                }
                res.body?.string().orEmpty()
            }

            val json = JSONObject(body)
            // تگ معمولاً به شکل v2.0 است؛ v ابتدایی حذف می‌شود
            val tag = json.optString("tag_name").removePrefix("v").trim()
            if (tag.isBlank()) return@withContext UpdateResult.Error("نسخه در Release مشخص نشده است.")

            // یافتن فایل APK بین پیوست‌ها
            val assets = json.optJSONArray("assets")
            var url = ""
            var size = 0L
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.optJSONObject(i) ?: continue
                    if (a.optString("name").endsWith(".apk", true)) {
                        url = a.optString("browser_download_url")
                        size = a.optLong("size")
                        break
                    }
                }
            }
            if (url.isBlank()) {
                return@withContext UpdateResult.Error(
                    "فایل نصبی به این Release پیوست نشده است."
                )
            }

            if (!isNewer(tag, BuildConfig.VERSION_NAME)) {
                return@withContext UpdateResult.UpToDate
            }

            UpdateResult.Available(
                UpdateInfo(
                    versionName = tag,
                    notes = json.optString("body").take(600),
                    downloadUrl = url,
                    sizeBytes = size,
                    publishedAt = json.optString("published_at").take(10)
                )
            )
        } catch (e: IOException) {
            UpdateResult.Error(
                if (e.message.orEmpty().contains("UnknownHost", true))
                    "اتصال به اینترنت برقرار نیست."
                else "بررسی به‌روزرسانی ممکن نشد."
            )
        } catch (e: Exception) {
            UpdateResult.Error("خطا در بررسی به‌روزرسانی.")
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

    /** مسیر فایل دانلودشده، اگر موجود باشد. */
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
