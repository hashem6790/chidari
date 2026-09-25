package ir.chidari.ui.crop

/**
 * مستطیل کادر برش در مختصات صفحه (پیکسل).
 *
 * عمداً از `androidx.compose.ui.geometry.Rect` استفاده نشده تا این منطق
 * کاملاً خالص بماند و بشود بدون شبیه‌ساز اندروید تست شود.
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
 * شکل کادر برش.
 *
 * `ratio` فقط **اندازه‌ی اولیه** را تعیین می‌کند؛ بعد از آن هر دستگیره آزاد
 * است و فقط همان ضلع را جابه‌جا می‌کند. استثنا دایره است که باید گرد بماند،
 * وگرنه بیضی می‌شود.
 */
enum class CropShape(val label: String, val ratio: Float?, val forceSquare: Boolean) {
    SQUARE("مربع", 1f, false),
    CIRCLE("دایره", 1f, true),
    PORTRAIT("۳:۴", 3f / 4f, false),
    LANDSCAPE("۴:۳", 4f / 3f, false),
    FREE("آزاد", null, false)
}

/**
 * ریاضیات کادر برش.
 *
 * قاعده‌ی کلی همه‌ی توابع: **اگر نتیجه معتبر نبود، مقدار قبلی برگردانده
 * می‌شود.** کادر هرگز از محدوده‌ی تصویر بیرون نمی‌زند و هرگز از حداقل
 * کوچک‌تر نمی‌شود.
 *
 * نکته: مرزها «محدوده‌ی خودِ تصویر» است، نه کل صفحه. تصویر با Fit کشیده
 * می‌شود و اطرافش نوار خالی می‌ماند؛ اگر مرز را کل صفحه می‌گرفتیم، کاربر
 * می‌توانست ناحیه‌ی خالی را هم داخل برش بیندازد.
 */
object CropGeometry {

    /** کادر اولیه: بزرگ‌ترین مستطیل با نسبت خواسته‌شده، وسط محدوده. */
    fun initialFrame(bounds: FrameRect, ratio: Float?, margin: Float): FrameRect {
        var w = (bounds.width - margin * 2).coerceAtLeast(1f)
        var h = (bounds.height - margin * 2).coerceAtLeast(1f)
        if (ratio != null && ratio > 0f) {
            if (w / h > ratio) w = h * ratio else h = w / ratio
        }
        val l = bounds.left + (bounds.width - w) / 2f
        val t = bounds.top + (bounds.height - h) / 2f
        return FrameRect(l, t, l + w, t + h)
    }

    /**
     * کدام دستگیره زیر انگشت است؟
     * گوشه‌ها اولویت دارند چون در کادرهای کوچک ناحیه‌ی لمس گوشه و ضلع
     * روی هم می‌افتد و کاربر تقریباً همیشه قصد گوشه را دارد.
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

    /** جابه‌جایی کل کادر بدون تغییر اندازه، محدود به مرزهای تصویر. */
    fun move(f: FrameRect, dx: Float, dy: Float, bounds: FrameRect): FrameRect {
        val ddx = dx.coerceIn(bounds.left - f.left, bounds.right - f.right)
        val ddy = dy.coerceIn(bounds.top - f.top, bounds.bottom - f.bottom)
        return FrameRect(f.left + ddx, f.top + ddy, f.right + ddx, f.bottom + ddy)
    }

    /**
     * تغییر اندازه با کشیدن یک دستگیره — **فقط همان ضلع یا گوشه**.
     *
     * تنها استثنا `forceSquare` (دایره) است: آنجا قطر از روی بیشترین تغییر
     * حساب می‌شود و ضلع مقابلِ دستگیره لنگر می‌ماند تا دایره گرد بماند.
     */
    fun resize(
        f: FrameRect,
        handle: CropHandle,
        dx: Float,
        dy: Float,
        bounds: FrameRect,
        minSize: Float,
        forceSquare: Boolean = false
    ): FrameRect {
        if (forceSquare) return resizeSquare(f, handle, dx, dy, bounds, minSize)

        var l = f.left
        var t = f.top
        var r = f.right
        var b = f.bottom

        if (handle.movesLeft) l = (l + dx).coerceIn(bounds.left, r - minSize)
        if (handle.movesRight) r = (r + dx).coerceIn(l + minSize, bounds.right)
        if (handle.movesTop) t = (t + dy).coerceIn(bounds.top, b - minSize)
        if (handle.movesBottom) b = (b + dy).coerceIn(t + minSize, bounds.bottom)

        if (r - l < minSize || b - t < minSize) return f
        return FrameRect(l, t, r, b)
    }

    /** تغییر اندازه با حفظ مربع بودن (برای کادر دایره‌ای). */
    private fun resizeSquare(
        f: FrameRect,
        handle: CropHandle,
        dx: Float,
        dy: Float,
        bounds: FrameRect,
        minSize: Float
    ): FrameRect {
        // تغییر قطر: علامت بر اساس جهت دستگیره
        val deltaX = when {
            handle.movesRight -> dx
            handle.movesLeft -> -dx
            else -> 0f
        }
        val deltaY = when {
            handle.movesBottom -> dy
            handle.movesTop -> -dy
            else -> 0f
        }
        val delta = if (kotlin.math.abs(deltaX) >= kotlin.math.abs(deltaY)) deltaX else deltaY
        val size = (f.width + delta).coerceAtLeast(minSize)

        // لنگر: ضلع مقابل دستگیره ثابت می‌ماند
        val left = when {
            handle.movesLeft -> f.right - size
            handle.movesRight -> f.left
            else -> f.centerX - size / 2f
        }
        val top = when {
            handle.movesTop -> f.bottom - size
            handle.movesBottom -> f.top
            else -> f.centerY - size / 2f
        }

        val out = FrameRect(left, top, left + size, top + size)
        return if (inside(out, bounds)) out else f
    }

    /**
     * بزرگ/کوچک کردن کادر حول مرکز — حرکت دو انگشتی.
     * شکل کادر (نسبت ابعاد) حفظ می‌شود.
     */
    fun scaleAround(
        f: FrameRect,
        factor: Float,
        bounds: FrameRect,
        minSize: Float
    ): FrameRect {
        if (factor <= 0f || factor == 1f) return f
        val w = f.width * factor
        val h = f.height * factor
        if (w < minSize || h < minSize) return f
        if (w > bounds.width || h > bounds.height) return f

        val cx = f.centerX
        val cy = f.centerY
        var out = FrameRect(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)

        // اگر بیرون زد، به‌جای رد کردن، به داخل هل می‌دهیم — طبیعی‌تر است
        var shiftX = 0f
        var shiftY = 0f
        if (out.left < bounds.left) shiftX = bounds.left - out.left
        if (out.right > bounds.right) shiftX = bounds.right - out.right
        if (out.top < bounds.top) shiftY = bounds.top - out.top
        if (out.bottom > bounds.bottom) shiftY = bounds.bottom - out.bottom
        out = FrameRect(out.left + shiftX, out.top + shiftY, out.right + shiftX, out.bottom + shiftY)

        return if (inside(out, bounds)) out else f
    }

    private fun inside(f: FrameRect, b: FrameRect): Boolean =
        f.left >= b.left - 0.5f && f.top >= b.top - 0.5f &&
            f.right <= b.right + 0.5f && f.bottom <= b.bottom + 0.5f

    /**
     * محدوده‌ای که تصویر با `ContentScale.Fit` روی صفحه اشغال می‌کند.
     * کادر برش نباید از این بیرون برود وگرنه ناحیه‌ی خالی هم بریده می‌شود.
     */
    fun imageBounds(boxW: Float, boxH: Float, bitmapW: Int, bitmapH: Int): FrameRect {
        if (boxW <= 0f || boxH <= 0f || bitmapW <= 0 || bitmapH <= 0) {
            return FrameRect(0f, 0f, boxW.coerceAtLeast(1f), boxH.coerceAtLeast(1f))
        }
        val s = minOf(boxW / bitmapW, boxH / bitmapH)
        val w = bitmapW * s
        val h = bitmapH * s
        val l = (boxW - w) / 2f
        val t = (boxH - h) / 2f
        return FrameRect(l, t, l + w, t + h)
    }

    /**
     * تبدیل کادر صفحه به مستطیل روی تصویر **اصلی** روی دیسک.
     *
     * چون کادر همیشه داخل `imageBounds` است، کافی است نسبت آن را بگیریم و
     * روی ابعاد فایل اصلی اعمال کنیم.
     */
    fun toSourceRect(
        frame: FrameRect,
        imageBounds: FrameRect,
        sourceW: Int,
        sourceH: Int
    ): IntArray {
        if (imageBounds.width <= 0f || imageBounds.height <= 0f) {
            return intArrayOf(0, 0, sourceW, sourceH)
        }
        val fx = (frame.left - imageBounds.left) / imageBounds.width
        val fy = (frame.top - imageBounds.top) / imageBounds.height
        val fw = frame.width / imageBounds.width
        val fh = frame.height / imageBounds.height

        var l = Math.round(fx * sourceW)
        var t = Math.round(fy * sourceH)
        var r = Math.round((fx + fw) * sourceW)
        var b = Math.round((fy + fh) * sourceH)

        l = l.coerceIn(0, sourceW - 1)
        t = t.coerceIn(0, sourceH - 1)
        r = r.coerceIn(l + 1, sourceW)
        b = b.coerceIn(t + 1, sourceH)
        return intArrayOf(l, t, r, b)
    }
}

/**
 * انتخاب رنگ کادر بر اساس روشنایی تصویر.
 * کادر سفید روی عکس یک محصول سفید عملاً نامرئی می‌شود.
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
     * در حالت شک، کادر سفید روی پرده‌ی تیره‌ی اطراف بهتر دیده می‌شود.
     */
    fun isBright(average: Float): Boolean = average > 0.62f
}
