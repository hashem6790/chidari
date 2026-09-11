package ir.chidari.util

/** ابزارهای قالب‌بندی متن و عدد فارسی. */
object Fa {

    private val digits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

    /**
     * تنظیمات نمایشی که از منوی برنامه کنترل می‌شوند.
     * چون قالب‌بندی در ده‌ها جای رابط کاربری استفاده می‌شود، به‌جای عبور دادن
     * تنظیمات از همه‌جا، اینجا نگه داشته می‌شوند و با تغییر تنظیمات به‌روز می‌گردند.
     */
    @Volatile var usePersianDigits: Boolean = true
    @Volatile var useCompactPrices: Boolean = false
    /** ۰=خودکار ۱=همیشه کیلومتر ۲=همیشه متر */
    @Volatile var distanceUnitMode: Int = 0
    @Volatile var showDiscountBadges: Boolean = true
    @Volatile var hapticFeedback: Boolean = true
    @Volatile var confirmBeforeCall: Boolean = false
    @Volatile var showDistanceOnCards: Boolean = true
    @Volatile var showRatings: Boolean = true

    /** تبدیل ارقام لاتین به فارسی (در صورت فعال بودن تنظیم). */
    fun digits(input: String): String {
        if (!usePersianDigits) return input
        return buildString {
            input.forEach { ch ->
                if (ch in '0'..'9') append(digits[ch - '0']) else append(ch)
            }
        }
    }

    /** تبدیل ارقام فارسی/عربی به لاتین (برای ورودی کاربر). */
    fun toLatinDigits(input: String): String = buildString {
        input.forEach { ch ->
            when (ch) {
                in '۰'..'۹' -> append(ch - '۰')
                in '٠'..'٩' -> append(ch - '٠')
                else -> append(ch)
            }
        }
    }

    /** جداکننده هزارگان. */
    fun number(value: Long): String {
        val s = value.toString()
        val neg = s.startsWith("-")
        val body = if (neg) s.substring(1) else s
        val sep = if (usePersianDigits) "٬" else ","
        val grouped = body.reversed().chunked(3).joinToString(sep).reversed()
        return digits(if (neg) "-$grouped" else grouped)
    }

    /** قیمت به تومان — اگر «نمایش خلاصه» روشن باشد، کوتاه‌شده برمی‌گردد. */
    fun price(value: Long): String =
        if (useCompactPrices) priceShort(value) else "${number(value)} تومان"

    /** قیمت کوتاه‌شده برای کارت‌های فشرده: مثلاً «۲۴٫۵ میلیون تومان». */
    fun priceShort(value: Long): String = when {
        value >= 1_000_000_000 -> digits(String.format("%.1f", value / 1_000_000_000.0)) + " میلیارد تومان"
        value >= 1_000_000 -> digits(String.format("%.1f", value / 1_000_000.0)) + " میلیون تومان"
        else -> price(value)
    }

    /** امتیاز با یک رقم اعشار. */
    fun rating(value: Double): String = digits(String.format("%.1f", value))

    /** درصد. */
    fun percent(value: Int): String = "${digits(value.toString())}٪"
}
