package ir.chidari.data

/** دسته‌بندی فروشگاه‌ها و خدمات. */
data class StoreCategory(val name: String, val emoji: String, val productCategories: List<String>)

object Categories {

    val all: List<StoreCategory> = listOf(
        StoreCategory("سوپرمارکت و خواربار", "🛒", listOf("لبنیات", "نوشیدنی", "خشکبار", "کنسرو و غذای آماده", "شوینده", "تنقلات")),
        StoreCategory("میوه و تره‌بار", "🍎", listOf("میوه", "سبزیجات", "صیفی‌جات")),
        StoreCategory("نانوایی و شیرینی", "🥖", listOf("نان", "شیرینی تر", "شیرینی خشک", "کیک")),
        StoreCategory("قصابی و پروتئین", "🥩", listOf("گوشت قرمز", "مرغ", "ماهی", "فرآورده گوشتی")),
        StoreCategory("رستوران و فست‌فود", "🍽️", listOf("غذای ایرانی", "فست‌فود", "پیتزا", "کباب", "صبحانه")),
        StoreCategory("کافه و آبمیوه", "☕", listOf("قهوه", "دمنوش", "آبمیوه و بستنی")),
        StoreCategory("پوشاک", "👕", listOf("پوشاک مردانه", "پوشاک زنانه", "پوشاک بچگانه", "کیف و کفش")),
        StoreCategory("لوازم خانگی", "🔌", listOf("لوازم برقی آشپزخانه", "صوتی و تصویری", "یخچال و لباسشویی", "بخاری و کولر")),
        StoreCategory("موبایل و کامپیوتر", "📱", listOf("گوشی موبایل", "لپ‌تاپ", "لوازم جانبی", "کنسول بازی")),
        StoreCategory("داروخانه و بهداشت", "💊", listOf("دارو", "مکمل", "لوازم بهداشتی", "تجهیزات پزشکی")),
        StoreCategory("لوازم‌التحریر و کتاب", "📚", listOf("نوشت‌افزار", "کتاب", "لوازم هنری")),
        StoreCategory("ابزار و یراق", "🔧", listOf("ابزار دستی", "ابزار برقی", "یراق‌آلات", "رنگ و ساختمانی")),
        StoreCategory("خدمات فنی و تعمیرات", "🛠️", listOf("تعمیر لوازم خانگی", "تعمیر موبایل", "لوله‌کشی", "برق‌کاری", "نقاشی ساختمان")),
        StoreCategory("خودرو و موتور", "🚗", listOf("قطعات یدکی", "روغن و فیلتر", "لاستیک", "تعمیرگاه", "کارواش")),
        StoreCategory("آرایشی و بهداشتی", "💄", listOf("لوازم آرایش", "مراقبت پوست", "عطر و ادکلن")),
        StoreCategory("آرایشگاه و زیبایی", "💇", listOf("خدمات مو", "خدمات ناخن", "پوست و زیبایی")),
        StoreCategory("گل و گیاه", "🌸", listOf("گل شاخه‌بریده", "گیاه آپارتمانی", "خاک و کود")),
        StoreCategory("ورزشی", "⚽", listOf("پوشاک ورزشی", "تجهیزات بدنسازی", "دوچرخه")),
        StoreCategory("اسباب‌بازی و کودک", "🧸", listOf("اسباب‌بازی", "سیسمونی", "لوازم کودک")),
        StoreCategory("خدمات آموزشی", "🎓", listOf("کلاس زبان", "کنکور", "موسیقی", "کامپیوتر")),
        StoreCategory("املاک", "🏠", listOf("فروش آپارتمان", "رهن و اجاره", "زمین و کلنگی")),
        StoreCategory("سایر", "🏪", listOf("متفرقه"))
    )

    val names: List<String> by lazy { all.map { it.name } }

    fun emojiOf(category: String): String =
        all.firstOrNull { it.name == category }?.emoji ?: "🏪"

    fun productCategoriesOf(store: String): List<String> =
        all.firstOrNull { it.name == store }?.productCategories ?: listOf("متفرقه")

    val allProductCategories: List<String> by lazy {
        all.flatMap { it.productCategories }.distinct().sorted()
    }
}

/** واحدهای رایج برای قیمت‌گذاری. */
object Units {
    val all = listOf("عدد", "کیلوگرم", "گرم", "بسته", "جعبه", "متر", "متر مربع", "لیتر", "ساعت", "سرویس", "پرس", "جلد")
}
