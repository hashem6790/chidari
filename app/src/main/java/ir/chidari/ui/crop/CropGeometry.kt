package ir.chidari.ui.crop

/**
 * مستطیل کادر برش در مختصات صفحه (پیکسل).
 *
 * عمداً از `androidx.compose.ui.geometry.Rect` استفاده نشده تا این منطق
 * کاملاً خالص بماند و بشود بدون شبیه‌ساز اندروید تست شود — همان اشتباهی
 * که قبلاً باعث شد باگ نگاشت کادر تا روی دستگاه کاربر برود.
 */
data class FrameRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun contains(x: Float, y: Float): Boolean =
        x >= left && x <= right && y >= top && y <= bottom
}

/** هشت دستگیره: چهار گوشه و وسط چهار ضلع. */
enum class CropHandle {
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
    TOP, BOTTOM, LEFT, RIGHT;

    val movesLeft: Boolean get() = this == TOP_LEFT || this == BOTTOM_LEFT || this == LEFT
    val movesRight: Boolean get() = this == TOP_RIGHT || this == BOTTOM_RIGHT || this == RIGHT
    val movesTop: Boolean get() = this == TOP_LEFT || this == TOP_RIGHT || this == TOP
    val movesBottom: Boolean get() = this == BOTTOM_LEFT || this == BOTTOM_RIGHT || this == BOTTOM
    val isCorner: Boolean get() = this == TOP_LEFT || this == TOP_RIGHT ||
        this == BOTTOM_LEFT || this == BOTTOM_RIGHT
}

/**
 * ریاضیات کادر برش.
 *
 * قاعده‌ی کلی همه‌ی توابع: **اگر نتیجه معتبر نبود، مقدار قبلی برگردانده
 * می‌شود.** این باعث می‌شود کادر هنگام کشیدن سریع «بپرد» یا از صفحه بیرون
 * بزند، به‌جای اینکه به حالت نامعتبر برود.
 */
object CropGeometry {

    /** کادر اولیه: بزرگ‌ترین مستطیل با نسبت خواسته‌شده، وسط صفحه. */
    fun initialFrame(boxW: Float, boxH: Float, ratio: Float?, margin: Float): FrameRect {
        var w = (boxW - margin * 2).coerceAtLeast(1f)
        var h = (boxH - margin * 2).coerceAtLeast(1f)
        if (ratio != null && ratio > 0f) {
            if (w / h > ratio) w = h * ratio else h = w / ratio
        }
        val l = (boxW - w) / 2f
        val t = (boxH - h) / 2f
        return FrameRect(l, t, l + w, t + h)
    }

    /**
     * کدام دستگیره زیر انگشت است؟
     *
     * گوشه‌ها اولویت دارند: در گوشه، ناحیه‌ی لمس گوشه و ضلع روی هم می‌افتند و
     * کاربر تقریباً همیشه قصد گوشه را دارد.
     */
    fun hitTest(x: Float, y: Float, f: FrameRect, radius: Float): CropHandle? {
        fun near(px: Float, py: Float) =
            kotlin.math.abs(x - px) <= radius && kotlin.math.abs(y - py) <= radius

        if (near(f.left, f.top)) return CropHandle.TOP_LEFT
        if (near(f.right, f.top)) return CropHandle.TOP_RIGHT
        if (near(f.left, f.bottom)) return CropHandle.BOTTOM_LEFT
        if (near(f.right, f.bottom)) return CropHandle.BOTTOM_RIGHT
        if (near(f.centerX, f.top)) return CropHandle.TOP
        if (near(f.centerX, f.bottom)) return CropHandle.BOTTOM
        if (near(f.left, f.centerY)) return CropHandle.LEFT
        if (near(f.right, f.centerY)) return CropHandle.RIGHT
        return null
    }

    /** جابه‌جایی کادر بدون تغییر اندازه، محدود به مرزهای صفحه. */
    fun move(f: FrameRect, dx: Float, dy: Float, boxW: Float, boxH: Float): FrameRect {
        val clampedDx = dx.coerceIn(-f.left, boxW - f.right)
        val clampedDy = dy.coerceIn(-f.top, boxH - f.bottom)
        return FrameRect(
            f.left + clampedDx, f.top + clampedDy,
            f.right + clampedDx, f.bottom + clampedDy
        )
    }

    /**
     * تغییر اندازه با کشیدن یک دستگیره.
     *
     * با نسبت قفل‌شده، ضلع مقابلِ دستگیره ثابت می‌ماند و بعد مقدار دیگر از
     * روی نسبت حساب می‌شود؛ برای دستگیره‌های ضلعی، محور عمود حول مرکز
     * متقارن باز و بسته می‌شود تا کادر «نلغزد».
     */
    fun resize(
        f: FrameRect,
        handle: CropHandle,
        dx: Float,
        dy: Float,
        boxW: Float,
        boxH: Float,
        ratio: Float?,
        minSize: Float
    ): FrameRect {
        var l = f.left
        var t = f.top
        var r = f.right
        var b = f.bottom

        if (handle.movesLeft) l += dx
        if (handle.movesRight) r += dx
        if (handle.movesTop) t += dy
        if (handle.movesBottom) b += dy

        // داخل صفحه بماند
        l = l.coerceIn(0f, boxW)
        r = r.coerceIn(0f, boxW)
        t = t.coerceIn(0f, boxH)
        b = b.coerceIn(0f, boxH)

        if (r - l < minSize || b - t < minSize) return f

        if (ratio == null || ratio <= 0f) {
            return FrameRect(l, t, r, b)
        }

        // ---- نسبت قفل‌شده ----
        var w = r - l
        var h = b - t

        if (handle.isCorner) {
            // بُعدی که بیشتر کشیده شده تعیین‌کننده است
            if (kotlin.math.abs(dx) >= kotlin.math.abs(dy)) h = w / ratio else w = h * ratio
        } else if (handle == CropHandle.LEFT || handle == CropHandle.RIGHT) {
            h = w / ratio
        } else {
            w = h * ratio
        }

        if (w < minSize || h < minSize) return f

        // لنگر: گوشه/ضلع مقابل دستگیره ثابت می‌ماند
        val newLeft = if (handle.movesLeft) r - w
        else if (handle.movesRight) l
        else f.centerX - w / 2f

        val newTop = if (handle.movesTop) b - h
        else if (handle.movesBottom) t
        else f.centerY - h / 2f

        val out = FrameRect(newLeft, newTop, newLeft + w, newTop + h)

        // اگر با نسبت از صفحه بیرون زد، تغییر را نمی‌پذیریم
        if (out.left < 0f || out.top < 0f || out.right > boxW || out.bottom > boxH) return f
        return out
    }

    /**
     * تبدیل کادر صفحه به مستطیل روی تصویر **اصلی** روی دیسک.
     *
     * تصویر با `ContentScale.Fit` کشیده می‌شود، بعد با `scale` بزرگ و با
     * `offset` جابه‌جا. این تابع همان تبدیل را معکوس می‌کند.
     */
    fun toSourceRect(
        frame: FrameRect,
        bitmapW: Int,
        bitmapH: Int,
        sourceW: Int,
        sourceH: Int,
        boxW: Float,
        boxH: Float,
        scale: Float,
        offsetX: Float,
        offsetY: Float
    ): IntArray {
        if (boxW <= 0f || boxH <= 0f || bitmapW <= 0 || bitmapH <= 0) {
            return intArrayOf(0, 0, sourceW, sourceH)
        }

        val fitScale = minOf(boxW / bitmapW, boxH / bitmapH)
        val drawn = fitScale * scale
        val drawnW = bitmapW * drawn
        val drawnH = bitmapH * drawn
        val imgLeft = (boxW - drawnW) / 2f + offsetX
        val imgTop = (boxH - drawnH) / 2f + offsetY

        // مختصات روی بیت‌مپ پیش‌نمایش
        val px = (frame.left - imgLeft) / drawn
        val py = (frame.top - imgTop) / drawn
        val pw = frame.width / drawn
        val ph = frame.height / drawn

        // پیش‌نمایش → تصویر اصلی
        val k = sourceW.toFloat() / bitmapW

        var l = Math.round(px * k)
        var t = Math.round(py * k)
        var r = Math.round((px + pw) * k)
        var b = Math.round((py + ph) * k)

        l = l.coerceIn(0, sourceW - 1)
        t = t.coerceIn(0, sourceH - 1)
        r = r.coerceIn(l + 1, sourceW)
        b = b.coerceIn(t + 1, sourceH)
        return intArrayOf(l, t, r, b)
    }
}

/**
 * انتخاب رنگ کادر بر اساس روشنایی تصویر.
 *
 * چرا لازم است: کادر سفید روی عکس یک محصول سفید (مثل دستمال کاغذی یا
 * یخچال) عملاً نامرئی می‌شود.
 */
object Luma {

    /** روشنایی ادراکی یک پیکسل ARGB، بین ۰ و ۱. */
    fun of(argb: Int): Float {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        // ضرایب استاندارد ITU-R BT.601 — چشم به سبز حساس‌تر است
        return (0.299f * r + 0.587f * g + 0.114f * b) / 255f
    }

    /** میانگین روشنایی یک آرایه پیکسل. */
    fun average(pixels: IntArray): Float {
        if (pixels.isEmpty()) return 0f
        var sum = 0.0
        for (p in pixels) sum += of(p)
        return (sum / pixels.size).toFloat()
    }

    /**
     * آیا تصویر روشن است؟
     *
     * آستانه کمی بالاتر از وسط گرفته شده: در حالت شک، کادر سفید روی پرده‌ی
     * تیره‌ی اطراف بهتر دیده می‌شود تا کادر تیره.
     */
    fun isBright(average: Float): Boolean = average > 0.62f
}
