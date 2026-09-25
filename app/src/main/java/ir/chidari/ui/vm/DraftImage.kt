package ir.chidari.ui.vm

/** وضعیت بارگذاری یک تصویر روی سرور. */
enum class UploadState { UPLOADING, DONE, FAILED, LOCAL_ONLY }

/**
 * یک تصویر محصول در حال ویرایش.
 *
 * چرا در ViewModel نگه داشته می‌شود و نه در پایگاه داده: هنگام افزودن یک
 * محصول تازه هنوز شناسه‌ای وجود ندارد که تصاویر به آن گره بخورند. اگر
 * ردیف‌هایی در جدول می‌ساختیم و کاربر منصرف می‌شد، ردیف‌های یتیم می‌ماندند.
 * این فهرست موقت است و فقط هنگام ذخیره‌ی محصول به ستون `images` می‌نشیند.
 */
data class DraftImage(
    /** شناسه‌ی محلی و یکتا؛ فقط برای تشخیص در رابط کاربری. */
    val id: Long,
    /** فایل برش‌خورده و فشرده روی گوشی. */
    val localPath: String,
    /** نسخه‌ی دست‌نخورده برای «برش دوباره» (ممکن است خالی باشد). */
    val originalPath: String = "",
    /** نشانی عمومی روی سرور، بعد از بارگذاری موفق. */
    val remoteUrl: String = "",
    val state: UploadState = UploadState.UPLOADING,
    /** درصد پیشرفت بین ۰ تا ۱. */
    val progress: Float = 0f,
    val error: String = ""
) {
    /** چیزی که باید نمایش داده شود: ترجیحاً فایل محلی (سریع‌تر و آفلاین). */
    val displayModel: Any
        get() = if (localPath.isNotBlank() && java.io.File(localPath).exists())
            java.io.File(localPath) else remoteUrl

    /** مقداری که در ستون `images` ذخیره می‌شود. */
    val storedValue: String
        get() = if (remoteUrl.isNotBlank()) remoteUrl else localPath

    val canRecrop: Boolean
        get() = originalPath.isNotBlank() && java.io.File(originalPath).exists()
}
