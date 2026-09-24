package ir.chidari.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/** نتیجه پردازش یک تصویر. */
sealed interface ImageResult {
    data class Success(
        val file: File,
        val width: Int,
        val height: Int,
        val sizeBytes: Long,
        val quality: Int
    ) : ImageResult {
        val sizeLabel: String
            get() = if (sizeBytes >= 1024 * 1024)
                "%.1f مگابایت".format(sizeBytes / 1024.0 / 1024.0)
            else "%d کیلوبایت".format(sizeBytes / 1024)
    }

    data class Error(val message: String) : ImageResult
}

/**
 * موتور آماده‌سازی تصویر محصول.
 *
 * هدف: **بیشترین کیفیت ممکن زیر سقف ۱ مگابایت**.
 *
 * راهبرد سه‌مرحله‌ای:
 *  ۱. رمزگشایی کم‌حافظه — با `inSampleSize` تصویر بزرگ مستقیم در حافظه باز
 *     نمی‌شود تا روی گوشی‌های ضعیف OutOfMemory ندهد.
 *  ۲. تغییر اندازه به بلندترین ضلع مجاز، فقط اگر تصویر بزرگ‌تر باشد
 *     (تصویر کوچک هرگز بزرگ‌نمایی نمی‌شود چون کیفیت را خراب می‌کند).
 *  ۳. **جست‌وجوی دودویی روی کیفیت JPEG** تا بالاترین کیفیتی پیدا شود که
 *     حجمش زیر سقف بماند. اگر حتی در پایین‌ترین کیفیت هم جا نشد، ابعاد
 *     کم می‌شود و دوباره تلاش می‌گردد.
 *
 * چرخش EXIF هم اصلاح می‌شود؛ عکس‌های دوربین اغلب چرخیده ذخیره می‌شوند.
 */
class ImageProcessor(private val context: Context) {

    /**
     * یک تصویر را از Uri می‌خواند، برش می‌زند، اصلاح و فشرده می‌کند.
     *
     * **ترتیب مهم است:** کادر برشی که کاربر انتخاب می‌کند در فضای
     * «تصویر دیده‌شده» است (یعنی بعد از اعمال چرخش EXIF). ولی فایل روی
     * دیسک نچرخیده ذخیره شده. پس ابتدا کادر به فضای فایل نگاشت می‌شود،
     * سپس همان ناحیه خوانده و در آخر چرخانده می‌شود.
     * بدون این نگاشت، برای عکس‌های دوربین ناحیه‌ی اشتباهی بریده می‌شد.
     */
    suspend fun process(
        source: Uri,
        cropRect: Rect? = null,
        maxDimension: Int = MAX_DIMENSION,
        maxBytes: Long = MAX_BYTES
    ): ImageResult = withContext(Dispatchers.IO) {
        try {
            val rotation = readRotation(source)
            val stored = storedSize(source)
                ?: return@withContext ImageResult.Error("تصویر خوانده نشد.")

            // کادر از فضای نمایش به فضای فایل
            val storedRect = cropRect?.let {
                mapDisplayRectToStored(it, rotation, stored.first, stored.second)
            }

            val decoded = decodeScaled(source, maxDimension, storedRect)
                ?: return@withContext ImageResult.Error("تصویر خوانده نشد.")

            // چرخش پس از برش اعمال می‌شود
            val rotated = rotate(decoded, rotation)
            val resized = limitDimension(rotated, maxDimension)
            val (bytes, quality) = compressUnder(resized, maxBytes)

            val out = File(imageDir(), "product_${System.currentTimeMillis()}.jpg")
            FileOutputStream(out).use { it.write(bytes) }

            val w = resized.width
            val h = resized.height
            if (resized != rotated) resized.recycle()
            if (rotated != decoded) rotated.recycle()
            decoded.recycle()

            ImageResult.Success(out, w, h, out.length(), quality)
        } catch (e: OutOfMemoryError) {
            ImageResult.Error("تصویر برای حافظه گوشی خیلی بزرگ است. تصویر کوچک‌تری انتخاب کنید.")
        } catch (e: Exception) {
            ImageResult.Error("پردازش تصویر ناموفق بود: ${e.message}")
        }
    }

    /** ابعاد تصویر همان‌طور که روی دیسک ذخیره شده (بدون چرخش). */
    fun storedSize(uri: Uri): Pair<Int, Int>? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, o)
            }
        }
        return if (o.outWidth > 0 && o.outHeight > 0) o.outWidth to o.outHeight else null
    }

    /**
     * ابعاد تصویر آن‌طور که کاربر می‌بیند (بعد از چرخش).
     * صفحه برش باید از این استفاده کند، نه ابعاد فایل.
     */
    fun displaySize(uri: Uri): Pair<Int, Int>? {
        val s = storedSize(uri) ?: return null
        val r = readRotation(uri)
        return if (r == 90 || r == 270) s.second to s.first else s
    }

    /** زاویه چرخش ثبت‌شده در EXIF، بر حسب درجه. */
    fun readRotation(uri: Uri): Int {
        val o = runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        return when (o) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }

    /**
     * نگاشت کادر از فضای نمایش (چرخیده) به فضای فایل (نچرخیده).
     *
     * مثال ۹۰ درجه: تصویر ذخیره‌شده ۱۰۰×۲۰۰ بعد از چرخش ۲۰۰×۱۰۰ دیده می‌شود.
     * گوشه بالا-چپِ دیده‌شده در واقع گوشه پایین-چپِ فایل است.
     */
    internal fun mapDisplayRectToStored(r: Rect, rotation: Int, sw: Int, sh: Int): Rect {
        val out = when (rotation) {
            90 -> Rect(r.top, sh - r.right, r.bottom, sh - r.left)
            180 -> Rect(sw - r.right, sh - r.bottom, sw - r.left, sh - r.top)
            270 -> Rect(sw - r.bottom, r.left, sw - r.top, r.right)
            else -> Rect(r)
        }
        // محدود کردن به مرزهای فایل
        out.left = out.left.coerceIn(0, sw - 1)
        out.top = out.top.coerceIn(0, sh - 1)
        out.right = out.right.coerceIn(out.left + 1, sw)
        out.bottom = out.bottom.coerceIn(out.top + 1, sh)
        return out
    }

    /** چرخاندن بیت‌مپ به اندازه زاویه داده‌شده. */
    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val m = Matrix().apply { postRotate(degrees.toFloat()) }
        return runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
        }.getOrDefault(bitmap)
    }

    /** فقط خواندن تصویر برای نمایش در صفحه برش (با اندازه کنترل‌شده). */
    suspend fun loadForCrop(source: Uri, maxDimension: Int = CROP_PREVIEW): Bitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                val bmp = decodeScaled(source, maxDimension, null) ?: return@runCatching null
                rotate(bmp, readRotation(source))
            }.getOrNull()
        }

    // ---------------- مراحل داخلی ----------------

    /**
     * رمزگشایی با نمونه‌برداری.
     *
     * ابتدا فقط ابعاد خوانده می‌شود (`inJustDecodeBounds`) تا بدون مصرف حافظه
     * بفهمیم تصویر چقدر بزرگ است، سپس با ضریب مناسب رمزگشایی می‌شود.
     * اگر ناحیه برش داده شده باشد، فقط همان ناحیه خوانده می‌شود.
     */
    private fun decodeScaled(uri: Uri, maxDim: Int, cropRect: Rect?): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: return null

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val targetW = cropRect?.width() ?: bounds.outWidth
        val targetH = cropRect?.height() ?: bounds.outHeight

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(targetW, targetH, maxDim)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        // برش ناحیه‌ای: فقط بخش انتخاب‌شده رمزگشایی می‌شود (کم‌مصرف‌تر)
        if (cropRect != null) {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val region = BitmapRegionDecoderCompat.create(stream) ?: return@use
                return runCatching { region.decodeRegion(cropRect, opts) }
                    .also { region.recycle() }
                    .getOrNull()
            }
        }

        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
    }

    /** بزرگ‌ترین توان ۲ که تصویر را زیر حد مطلوب می‌آورد. */
    private fun sampleSizeFor(w: Int, h: Int, maxDim: Int): Int {
        var sample = 1
        var longest = maxOf(w, h)
        // تا دو برابر حد مجاز پایین می‌آییم تا جا برای تغییر اندازه دقیق بماند
        while (longest / 2 >= maxDim * 2) {
            longest /= 2
            sample *= 2
        }
        return sample
    }

    /**
     * محدود کردن بلندترین ضلع.
     * تصویر کوچک‌تر از حد، دست‌نخورده می‌ماند — بزرگ‌نمایی فقط کیفیت را
     * پایین می‌آورد و حجم را بی‌دلیل زیاد می‌کند.
     */
    private fun limitDimension(bitmap: Bitmap, maxDim: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxDim) return bitmap
        val ratio = maxDim.toFloat() / longest
        val w = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val h = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        // فیلتر دوخطی برای کاهش پله‌پله شدن لبه‌ها
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    /**
     * فشرده‌سازی با **جست‌وجوی دودویی روی کیفیت**.
     *
     * به‌جای امتحان کردن کیفیت‌ها یکی‌یکی (کند) یا انتخاب یک عدد ثابت
     * (کیفیت هدررفته)، بالاترین کیفیتی پیدا می‌شود که زیر سقف جا شود.
     * معمولاً با ۷ بار فشرده‌سازی به جواب می‌رسد.
     */
    private fun compressUnder(bitmap: Bitmap, maxBytes: Long): Pair<ByteArray, Int> {
        // اگر با بالاترین کیفیت هم جا شد، همان بهترین است
        var best = compress(bitmap, MAX_QUALITY)
        if (best.size <= maxBytes) return best to MAX_QUALITY

        var low = MIN_QUALITY
        var high = MAX_QUALITY
        var bestQuality = MIN_QUALITY
        var bestBytes: ByteArray? = null

        while (low <= high) {
            val mid = (low + high) / 2
            val data = compress(bitmap, mid)
            if (data.size <= maxBytes) {
                bestBytes = data           // جا شد، کیفیت بالاتر را امتحان کن
                bestQuality = mid
                low = mid + 1
            } else {
                high = mid - 1             // بزرگ بود، پایین‌تر بیا
            }
        }

        if (bestBytes != null) return bestBytes!! to bestQuality

        // حتی پایین‌ترین کیفیت هم جا نشد → ابعاد را کم کن و دوباره
        val smaller = limitDimension(bitmap, (maxOf(bitmap.width, bitmap.height) * 0.75f).toInt())
        return if (smaller != bitmap) {
            val r = compressUnder(smaller, maxBytes)
            smaller.recycle()
            r
        } else {
            compress(bitmap, MIN_QUALITY) to MIN_QUALITY
        }
    }

    private fun compress(bitmap: Bitmap, quality: Int): ByteArray =
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        }

    /** پوشه نگهداری تصاویر محصولات. */
    fun imageDir(): File =
        File(context.filesDir, "product_images").apply { if (!exists()) mkdirs() }

    /** فایل موقت برای عکس گرفته‌شده با دوربین. */
    fun newCameraFile(): File =
        File(imageDir(), "camera_${System.currentTimeMillis()}.jpg")

    /** حذف فایل تصویر (هنگام برداشتن عکس محصول). */
    fun delete(path: String) {
        runCatching { if (path.isNotBlank()) File(path).delete() }
    }

    companion object {
        /**
         * بلندترین ضلع تصویر نهایی.
         * ۱۶۰۰ پیکسل برای نمایش محصول روی موبایل و تبلت کافی است و
         * با کیفیت JPEG بالا معمولاً زیر ۴۰۰ کیلوبایت می‌ماند.
         */
        const val MAX_DIMENSION = 1600

        /** سقف حجم فایل نهایی: ۱ مگابایت. */
        const val MAX_BYTES = 1024L * 1024L

        /** اندازه پیش‌نمایش در صفحه برش (کم‌حافظه ولی روان). */
        const val CROP_PREVIEW = 1200

        private const val MAX_QUALITY = 95
        private const val MIN_QUALITY = 40
    }
}

/** پوشش سازگاری برای BitmapRegionDecoder در نسخه‌های مختلف اندروید. */
private object BitmapRegionDecoderCompat {
    fun create(stream: java.io.InputStream): android.graphics.BitmapRegionDecoder? =
        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S)
                android.graphics.BitmapRegionDecoder.newInstance(stream)
            else
                @Suppress("DEPRECATION")
                android.graphics.BitmapRegionDecoder.newInstance(stream, false)
        }.getOrNull()
}
