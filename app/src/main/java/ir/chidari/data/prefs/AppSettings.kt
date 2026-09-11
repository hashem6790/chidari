package ir.chidari.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "app_settings")

/** حالت نمایش برنامه. */
enum class ThemeMode(val label: String) {
    SYSTEM("هماهنگ با سیستم"),
    LIGHT("روشن"),
    DARK("تیره")
}

/** واحد نمایش فاصله. */
enum class DistanceUnit(val label: String) {
    AUTO("خودکار (متر و کیلومتر)"),
    KM("همیشه کیلومتر"),
    METER("همیشه متر")
}

/** ترتیب پیش‌فرض فهرست‌ها هنگام باز شدن برنامه. */
enum class DefaultSort(val label: String) {
    NEAREST("نزدیک‌ترین"),
    CHEAPEST("ارزان‌ترین"),
    TOP_RATED("بیشترین امتیاز"),
    NEWEST("جدیدترین")
}

/** اندازه قلم رابط کاربری. */
enum class FontScale(val label: String, val scale: Float) {
    SMALL("کوچک", 0.9f),
    NORMAL("معمولی", 1.0f),
    LARGE("بزرگ", 1.15f),
    XLARGE("خیلی بزرگ", 1.3f)
}

/** تبی که برنامه با آن باز می‌شود. */
enum class StartTab(val label: String) {
    HOME("فروشگاه‌ها"),
    PRODUCTS("محصولات"),
    FAVORITES("علاقه‌مندی"),
    PROFILE("حساب من")
}

/** چگالی نمایش فهرست‌ها. */
enum class ListDensity(val label: String) {
    COMFORTABLE("راحت"),
    COMPACT("فشرده")
}

/** رفتار دکمه بازگشت در صفحه اصلی. */
enum class BackBehavior(val label: String) {
    ASK("پرسیدن پیش از خروج"),
    IMMEDIATE("خروج بی‌درنگ")
}

/** شعاع پیش‌فرض جست‌وجو بر حسب کیلومتر (۰ یعنی بدون محدودیت). */
enum class SearchRadius(val label: String, val km: Float) {
    OFF("بدون محدودیت", 0f),
    R2("۲ کیلومتر", 2f),
    R5("۵ کیلومتر", 5f),
    R10("۱۰ کیلومتر", 10f),
    R25("۲۵ کیلومتر", 25f),
    R50("۵۰ کیلومتر", 50f)
}

/**
 * همه تنظیمات کاربر در یک شیء.
 * هر مقدار پیش‌فرضی که اینجا هست، رفتار برنامه پیش از دست‌کاری کاربر را تعیین می‌کند.
 */
data class AppSettings(
    // ---------- نمایش ----------
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val fontScale: FontScale = FontScale.NORMAL,
    /** نمایش قیمت به‌صورت خلاصه (۲٫۴ میلیون به‌جای ۲٬۴۰۰٬۰۰۰). */
    val compactPrices: Boolean = false,
    /** نمایش ارقام فارسی؛ خاموش یعنی ۱۲۳ به‌جای ۱۲۳. */
    val persianDigits: Boolean = true,
    /** نمایش نشان تخفیف روی کارت‌ها. */
    val showDiscountBadges: Boolean = true,
    /** چگالی فهرست‌ها. */
    val listDensity: ListDensity = ListDensity.COMFORTABLE,
    /** نمایش فاصله روی کارت فروشگاه‌ها و محصولات. */
    val showDistanceOnCards: Boolean = true,
    /** نمایش امتیاز روی کارت‌ها. */
    val showRatings: Boolean = true,

    // ---------- جست‌وجو و فهرست ----------
    val distanceUnit: DistanceUnit = DistanceUnit.AUTO,
    val defaultSort: DefaultSort = DefaultSort.NEAREST,
    val searchRadius: SearchRadius = SearchRadius.OFF,
    /** نمایش فقط کالاهای موجود به‌صورت پیش‌فرض. */
    val onlyAvailableByDefault: Boolean = false,
    /** تبی که برنامه با آن باز می‌شود. */
    val startTab: StartTab = StartTab.HOME,
    /** ذخیره و نمایش جست‌وجوهای اخیر. */
    val saveSearchHistory: Boolean = true,
    /** پنهان کردن فروشگاه‌هایی که هیچ محصولی ندارند. */
    val hideEmptyStores: Boolean = false,
    /** تعداد نتایج در هر بار بارگذاری. */
    val resultsPerPage: Int = 300,

    // ---------- موقعیت ----------
    /** هنگام باز شدن برنامه، خودکار موقعیت را با GPS بگیر. */
    val autoDetectLocation: Boolean = false,

    // ---------- داده و شبکه ----------
    val autoSync: Boolean = true,
    val syncOnWifiOnly: Boolean = false,
    /** بارگذاری تصاویر فروشگاه‌ها و محصولات (خاموش = صرفه‌جویی حجم). */
    val loadImages: Boolean = true,

    // ---------- رفتار ----------
    /** پرسیدن تأیید پیش از حذف محصول یا فروشگاه. */
    val confirmBeforeDelete: Boolean = true,
    /** لرزش کوتاه هنگام فالو، علاقه‌مندی و امتیاز. */
    val hapticFeedback: Boolean = true,
    /** پیش از تماس تلفنی تأیید بگیر. */
    val confirmBeforeCall: Boolean = false,
    /** رفتار دکمه بازگشت در صفحه اصلی. */
    val backBehavior: BackBehavior = BackBehavior.ASK,
    /** نگه داشتن صفحه روشن هنگام مشاهده فروشگاه. */
    val keepScreenOn: Boolean = false,

    // ---------- اعلان (آماده برای آینده) ----------
    /** خبر دادن محصول تازه در فروشگاه‌های دنبال‌شده. */
    val notifyNewProducts: Boolean = true,
    /** خبر دادن تخفیف‌های فروشگاه‌های دنبال‌شده. */
    val notifyDiscounts: Boolean = true,
    /** خبر دادن فروشگاه تازه در شهر شما. */
    val notifyNewStores: Boolean = false,

    // ---------- حریم خصوصی ----------
    /** ذخیره موقعیت مکانی روی دستگاه. */
    val storeLocationHistory: Boolean = true,
    /** نمایش فروشگاه‌های من به‌صورت عمومی. */
    val publicProfile: Boolean = true
)

/** خواندن و نوشتن تنظیمات برنامه. */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val theme = stringPreferencesKey("theme_mode")
        val font = stringPreferencesKey("font_scale")
        val compactPrices = booleanPreferencesKey("compact_prices")
        val persianDigits = booleanPreferencesKey("persian_digits")
        val discountBadges = booleanPreferencesKey("discount_badges")
        val density = stringPreferencesKey("list_density")
        val showDistance = booleanPreferencesKey("show_distance_cards")
        val showRatings = booleanPreferencesKey("show_ratings")

        val unit = stringPreferencesKey("distance_unit")
        val sort = stringPreferencesKey("default_sort")
        val radius = stringPreferencesKey("search_radius")
        val onlyAvailable = booleanPreferencesKey("only_available_default")
        val startTab = stringPreferencesKey("start_tab")
        val searchHistory = booleanPreferencesKey("save_search_history")
        val hideEmpty = booleanPreferencesKey("hide_empty_stores")
        val perPage = intPreferencesKey("results_per_page")

        val autoLocate = booleanPreferencesKey("auto_detect_location")

        val autoSync = booleanPreferencesKey("auto_sync")
        val wifiOnly = booleanPreferencesKey("sync_wifi_only")
        val loadImages = booleanPreferencesKey("load_images")

        val confirmDelete = booleanPreferencesKey("confirm_delete")
        val haptic = booleanPreferencesKey("haptic_feedback")
        val confirmCall = booleanPreferencesKey("confirm_call")
        val backBehavior = stringPreferencesKey("back_behavior")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")

        val notifyProducts = booleanPreferencesKey("notify_new_products")
        val notifyDiscounts = booleanPreferencesKey("notify_discounts")
        val notifyStores = booleanPreferencesKey("notify_new_stores")
        val locHistory = booleanPreferencesKey("store_location_history")
        val publicProfile = booleanPreferencesKey("public_profile")
    }

    private inline fun <reified T : Enum<T>> parse(raw: String?, fallback: T): T =
        runCatching { enumValueOf<T>(raw ?: fallback.name) }.getOrDefault(fallback)

    val settings: Flow<AppSettings> = context.settingsStore.data.map { p ->
        AppSettings(
            themeMode = parse(p[Keys.theme], ThemeMode.SYSTEM),
            fontScale = parse(p[Keys.font], FontScale.NORMAL),
            compactPrices = p[Keys.compactPrices] ?: false,
            persianDigits = p[Keys.persianDigits] ?: true,
            showDiscountBadges = p[Keys.discountBadges] ?: true,
            listDensity = parse(p[Keys.density], ListDensity.COMFORTABLE),
            showDistanceOnCards = p[Keys.showDistance] ?: true,
            showRatings = p[Keys.showRatings] ?: true,

            distanceUnit = parse(p[Keys.unit], DistanceUnit.AUTO),
            defaultSort = parse(p[Keys.sort], DefaultSort.NEAREST),
            searchRadius = parse(p[Keys.radius], SearchRadius.OFF),
            onlyAvailableByDefault = p[Keys.onlyAvailable] ?: false,
            startTab = parse(p[Keys.startTab], StartTab.HOME),
            saveSearchHistory = p[Keys.searchHistory] ?: true,
            hideEmptyStores = p[Keys.hideEmpty] ?: false,
            resultsPerPage = p[Keys.perPage] ?: 300,

            autoDetectLocation = p[Keys.autoLocate] ?: false,

            autoSync = p[Keys.autoSync] ?: true,
            syncOnWifiOnly = p[Keys.wifiOnly] ?: false,
            loadImages = p[Keys.loadImages] ?: true,

            confirmBeforeDelete = p[Keys.confirmDelete] ?: true,
            hapticFeedback = p[Keys.haptic] ?: true,
            confirmBeforeCall = p[Keys.confirmCall] ?: false,
            backBehavior = parse(p[Keys.backBehavior], BackBehavior.ASK),
            keepScreenOn = p[Keys.keepScreenOn] ?: false,

            notifyNewProducts = p[Keys.notifyProducts] ?: true,
            notifyDiscounts = p[Keys.notifyDiscounts] ?: true,
            notifyNewStores = p[Keys.notifyStores] ?: false,
            storeLocationHistory = p[Keys.locHistory] ?: true,
            publicProfile = p[Keys.publicProfile] ?: true
        )
    }

    // ---------- نمایش ----------
    suspend fun setTheme(v: ThemeMode) = context.settingsStore.edit { it[Keys.theme] = v.name }
    suspend fun setFontScale(v: FontScale) = context.settingsStore.edit { it[Keys.font] = v.name }
    suspend fun setCompactPrices(v: Boolean) = context.settingsStore.edit { it[Keys.compactPrices] = v }
    suspend fun setPersianDigits(v: Boolean) = context.settingsStore.edit { it[Keys.persianDigits] = v }
    suspend fun setDiscountBadges(v: Boolean) = context.settingsStore.edit { it[Keys.discountBadges] = v }
    suspend fun setListDensity(v: ListDensity) = context.settingsStore.edit { it[Keys.density] = v.name }
    suspend fun setShowDistance(v: Boolean) = context.settingsStore.edit { it[Keys.showDistance] = v }
    suspend fun setShowRatings(v: Boolean) = context.settingsStore.edit { it[Keys.showRatings] = v }

    // ---------- جست‌وجو ----------
    suspend fun setDistanceUnit(v: DistanceUnit) = context.settingsStore.edit { it[Keys.unit] = v.name }
    suspend fun setDefaultSort(v: DefaultSort) = context.settingsStore.edit { it[Keys.sort] = v.name }
    suspend fun setSearchRadius(v: SearchRadius) = context.settingsStore.edit { it[Keys.radius] = v.name }
    suspend fun setOnlyAvailableByDefault(v: Boolean) = context.settingsStore.edit { it[Keys.onlyAvailable] = v }
    suspend fun setStartTab(v: StartTab) = context.settingsStore.edit { it[Keys.startTab] = v.name }
    suspend fun setSaveSearchHistory(v: Boolean) = context.settingsStore.edit { it[Keys.searchHistory] = v }
    suspend fun setHideEmptyStores(v: Boolean) = context.settingsStore.edit { it[Keys.hideEmpty] = v }
    suspend fun setResultsPerPage(v: Int) = context.settingsStore.edit { it[Keys.perPage] = v }

    // ---------- موقعیت ----------
    suspend fun setAutoDetectLocation(v: Boolean) = context.settingsStore.edit { it[Keys.autoLocate] = v }

    // ---------- داده ----------
    suspend fun setAutoSync(v: Boolean) = context.settingsStore.edit { it[Keys.autoSync] = v }
    suspend fun setSyncOnWifiOnly(v: Boolean) = context.settingsStore.edit { it[Keys.wifiOnly] = v }
    suspend fun setLoadImages(v: Boolean) = context.settingsStore.edit { it[Keys.loadImages] = v }

    // ---------- رفتار ----------
    suspend fun setConfirmBeforeDelete(v: Boolean) = context.settingsStore.edit { it[Keys.confirmDelete] = v }
    suspend fun setHapticFeedback(v: Boolean) = context.settingsStore.edit { it[Keys.haptic] = v }
    suspend fun setConfirmBeforeCall(v: Boolean) = context.settingsStore.edit { it[Keys.confirmCall] = v }
    suspend fun setBackBehavior(v: BackBehavior) = context.settingsStore.edit { it[Keys.backBehavior] = v.name }
    suspend fun setKeepScreenOn(v: Boolean) = context.settingsStore.edit { it[Keys.keepScreenOn] = v }

    // ---------- اعلان ----------
    suspend fun setNotifyNewProducts(v: Boolean) = context.settingsStore.edit { it[Keys.notifyProducts] = v }
    suspend fun setNotifyDiscounts(v: Boolean) = context.settingsStore.edit { it[Keys.notifyDiscounts] = v }
    suspend fun setNotifyNewStores(v: Boolean) = context.settingsStore.edit { it[Keys.notifyStores] = v }
    suspend fun setStoreLocationHistory(v: Boolean) = context.settingsStore.edit { it[Keys.locHistory] = v }
    suspend fun setPublicProfile(v: Boolean) = context.settingsStore.edit { it[Keys.publicProfile] = v }

    /** بازگرداندن همه تنظیمات به حالت پیش‌فرض. */
    suspend fun resetAll() = context.settingsStore.edit { it.clear() }
}
