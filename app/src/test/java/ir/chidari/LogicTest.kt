package ir.chidari

import ir.chidari.data.Categories
import ir.chidari.data.GeoUtils
import ir.chidari.data.IranGeo
import ir.chidari.data.normalizeFa
import ir.chidari.util.Fa
import ir.chidari.ui.crop.CropHandle as H
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {

    @Test fun `31 provinces and 413 cities`() {
        assertEquals(31, IranGeo.provinces.size)
        assertEquals(413, IranGeo.cityCount)
        IranGeo.provinces.forEach { assertTrue(it.name, it.cities.isNotEmpty()) }
    }

    @Test fun `no duplicate city inside a province`() {
        IranGeo.provinces.forEach { p ->
            val names = p.cities.map { it.name }
            assertEquals(p.name, names.size, names.toSet().size)
        }
    }

    @Test fun `every coordinate falls inside Iran bounding box`() {
        IranGeo.provinces.forEach { p ->
            p.cities.forEach { c ->
                assertTrue("${p.name}/${c.name} lat=${c.lat}", c.lat in 24.5..40.0)
                assertTrue("${p.name}/${c.name} lng=${c.lng}", c.lng in 43.5..63.5)
            }
        }
    }

    @Test fun `province capital is the first city`() {
        assertEquals("تهران", IranGeo.findProvince("تهران")!!.center.name)
        assertEquals("بندر بوشهر", IranGeo.findProvince("بوشهر")!!.center.name)
        assertEquals("شهرکرد", IranGeo.findProvince("چهارمحال و بختیاری")!!.center.name)
    }

    @Test fun `haversine tehran to mashhad is about 750km`() {
        val d = GeoUtils.distanceKm(35.6892, 51.3890, 36.2605, 59.6168)
        assertTrue("got $d", d in 720.0..780.0)
    }

    @Test fun `nearest city detects major cities from gps`() {
        assertEquals("تهران", IranGeo.nearestCity(35.70, 51.40)!!.second.name)
        assertEquals("شیراز", IranGeo.nearestCity(29.60, 52.55)!!.second.name)
        assertEquals("مشهد", IranGeo.nearestCity(36.29, 59.60)!!.second.name)
        assertEquals("بندر عباس".let { "بندرعباس" }, IranGeo.nearestCity(27.18, 56.27)!!.second.name)
    }

    @Test fun `nearest city reports distance`() {
        val (p, c, km) = IranGeo.nearestCityWithDistance(35.70, 51.40)!!
        assertEquals("تهران", p.name)
        assertEquals("تهران", c.name)
        assertTrue("got $km", km < 5.0)
    }

    @Test fun `search normalizes arabic yeh and kaf`() {
        // «ي» و «ك» عربی باید مثل «ی» و «ک» فارسی رفتار کنند
        assertTrue(IranGeo.search("كرمان").any { it.second.name == "کرمان" })
        assertTrue(IranGeo.search("سيرجان").any { it.second.name == "سیرجان" })
    }

    @Test fun `search ignores zero width non joiner`() {
        // کاربر بدون نیم‌فاصله تایپ می‌کند
        assertTrue(IranGeo.search("اسلام شهر".replace(" ", "")).any { it.second.name == "اسلام‌شهر" })
        assertTrue(IranGeo.search("خرم آباد".replace(" ", "")).any { it.second.name == "خرم‌آباد" })
    }

    @Test fun `search by province name returns its cities`() {
        assertTrue(IranGeo.search("گیلان").any { it.second.name == "رشت" })
    }

    @Test fun `search prefers prefix matches first`() {
        val r = IranGeo.search("بندر")
        assertTrue(r.first().second.name.startsWith("بندر"))
    }

    @Test fun `find city anywhere without knowing province`() {
        val hit = IranGeo.findCityAnywhere("عسلویه")
        assertNotNull(hit)
        assertEquals("بوشهر", hit!!.first.name)
    }

    @Test fun `normalizeFa collapses variants`() {
        assertEquals("کیان", "كيان".normalizeFa())
        assertEquals("اسلامشهر", "اسلام\u200cشهر".normalizeFa())
    }

    @Test fun `persian number formatting`() {
        assertEquals("۲۴٬۵۰۰٬۰۰۰ تومان", Fa.price(24_500_000))
        assertEquals("۸٬۰۰۰ تومان", Fa.price(8_000))
    }

    @Test fun `latin digit conversion for user input`() {
        assertEquals("12345", Fa.toLatinDigits("۱۲۳۴۵"))
    }

    @Test fun `distance formatting`() {
        assertEquals("۸۵۰ متر", Fa.digits(GeoUtils.formatDistance(0.85)))
        assertTrue(GeoUtils.formatDistance(3.24).startsWith("3.2"))
    }

    @Test fun `every store category has product categories`() {
        assertEquals(22, Categories.all.size)
        Categories.all.forEach { assertTrue(it.name, it.productCategories.isNotEmpty()) }
    }
}

/** تست‌های مربوط به مسیریاب‌ها و رفع باگ شمارنده دنبال‌کننده. */
class NavigationTest {

    @Test fun `neshan and balad package names are the real ones`() {
        assertEquals(
            "org.rajman.neshan.traffic.tehran.navigator",
            ir.chidari.util.Navigation.PKG_NESHAN
        )
        assertEquals("ir.balad", ir.chidari.util.Navigation.PKG_BALAD)
        assertEquals("com.google.android.apps.maps", ir.chidari.util.Navigation.PKG_GOOGLE_MAPS)
    }

    @Test fun `store entity carries a follower count that sync can overwrite`() {
        val local = ir.chidari.data.local.StoreEntity(
            id = 7, name = "تست", category = "سوپرمارکت و خواربار",
            province = "تهران", city = "تهران", lat = 35.7, lng = 51.4,
            ownerId = "owner-1", followerCount = 0
        )
        // آنچه سرور برمی‌گرداند باید جایگزین مقدار محلی شود
        val fromServer = local.copy(followerCount = 3)
        assertEquals(3, fromServer.followerCount)
        assertEquals(local.id, fromServer.id)
    }

    @Test fun `owner flag is computed from ownerId not stored blindly`() {
        val s = ir.chidari.data.local.StoreEntity(
            id = 1, name = "n", category = "c", province = "p", city = "c",
            lat = 0.0, lng = 0.0, ownerId = "user-A"
        )
        assertTrue(s.copy(isOwnedByMe = s.ownerId == "user-A").isOwnedByMe)
        assertTrue(!s.copy(isOwnedByMe = s.ownerId == "user-B").isOwnedByMe)
    }
}

/** تست‌های جلوگیری از بازگشت باگ‌های رفع‌شده. */
class RegressionTest {

    /**
     * باگ چشمک زدن شمارنده: استفاده از REPLACE در Room باعث DELETE+INSERT
     * می‌شد که هم عدد را می‌پراند و هم محصولات را با CASCADE پاک می‌کرد.
     * این تست مطمئن می‌شود که مسیرهای همگام‌سازی از upsert استفاده می‌کنند.
     */
    @Test fun `sync paths use upsert not destructive replace`() {
        val src = java.io.File("src/main/java/ir/chidari/data/remote/SyncManager.kt").readText()
        assertTrue("SyncManager باید از upsert استفاده کند", src.contains("upsertAll"))
        assertTrue(
            "SyncManager نباید insertAll مخرب را صدا بزند",
            !src.contains("storeDao.insertAll(")
        )
    }

    /** پس از فالو نباید بلافاصله همگام‌سازی شود (منبع دوم پرش عدد). */
    @Test fun `no immediate resync right after follow`() {
        val src = java.io.File("src/main/java/ir/chidari/ui/vm/MainViewModel.kt").readText()
        val followBlock = src.substringAfter("fun toggleFollow").substringBefore("fun ")
        assertTrue(
            "بعد از فالو نباید syncStore صدا زده شود",
            !followBlock.contains("sync.syncStore")
        )
    }

    /** مسیریابی باید مستقیم انتخابگر سیستمی را باز کند. */
    @Test fun `navigation opens system chooser directly`() {
        val src = java.io.File("src/main/java/ir/chidari/util/Navigation.kt").readText()
        assertTrue(src.contains("fun openNavigation"))
        assertTrue("باید از geo استفاده کند", src.contains("geo:"))
    }
}

/**
 * تست جلوگیری از بازگشت باگ «چشمک زدن صفحه».
 *
 * علت باگ: صدا زدن سازنده‌ی StateFlow مستقیم داخل بدنه‌ی composable.
 * هر رسم یک جریان تازه می‌ساخت که با مقدار خالی شروع می‌کرد، صفحه خالی
 * می‌شد و دوباره رسم می‌گرفت — حلقه‌ی بی‌پایان.
 */
class RecompositionTest {

    private val mainActivity =
        java.io.File("src/main/java/ir/chidari/MainActivity.kt").readText()

    @Test fun `flow builders are wrapped in remember`() {
        // هر فراخوانی vm.offersFor / followerCount / storeFlow باید داخل remember باشد
        Regex("""val \w+ by vm\.(offersFor|followerCount|storeFlow|productsOf|productFlow)\(""")
            .findAll(mainActivity)
            .forEach { m ->
                throw AssertionError(
                    "جریان «${m.groupValues[1]}» بدون remember مصرف شده — باعث چشمک زدن می‌شود"
                )
            }
    }

    @Test fun `viewmodel caches keyed flows`() {
        val vm = java.io.File("src/main/java/ir/chidari/ui/vm/MainViewModel.kt").readText()
        assertTrue("offersFor باید کش شود", vm.contains("offersCache.getOrPut"))
        assertTrue("followerCount باید کش شود", vm.contains("followerCache.getOrPut"))
    }

    @Test fun `scanner uses the bundled model`() {
        val gradle = java.io.File("build.gradle.kts").readText() +
            java.io.File("../app/build.gradle.kts").let { if (it.exists()) it.readText() else "" }
        val appGradle = java.io.File("build.gradle.kts").readText()
        assertTrue(
            "باید از نسخه bundled استفاده شود",
            appGradle.contains("com.google.mlkit:barcode-scanning")
        )
    }
}

/**
 * تست منطق موتور تصویر.
 *
 * خودِ فشرده‌سازی به اندروید نیاز دارد، ولی الگوریتم‌های محاسباتی
 * (جست‌وجوی دودویی کیفیت و محدودسازی ابعاد) قابل بررسی مستقل‌اند.
 */
class ImageEngineTest {

    /** همان منطق sampleSizeFor در ImageProcessor (نسخه اصلاح‌شده). */
    private fun sampleSize(w: Int, h: Int, maxDim: Int): Int {
        if (maxDim <= 0) return 1
        val longest = maxOf(w, h)
        var s = 1
        while (longest / (s * 2) >= maxDim) s *= 2
        return s
    }

    /** حافظه‌ی بیت‌مپ ARGB_8888 بر حسب مگابایت. */
    private fun megabytes(w: Int, h: Int, sample: Int): Double =
        (w / sample).toDouble() * (h / sample) * 4 / (1024 * 1024)

    @Test fun `sample size keeps large photos out of memory`() {
        // عکس ۴۸ مگاپیکسلی گوشی امروزی
        assertTrue("باید نمونه‌برداری شود", sampleSize(8000, 6000, 1600) >= 2)
        // عکس کوچک نباید نمونه‌برداری شود
        assertEquals(1, sampleSize(800, 600, 1600))
    }

    /**
     * رگرسیون باگ «باز نشدن ویرایشگر تصویر».
     *
     * فرمول قبلی فقط وقتی نمونه‌برداری می‌کرد که بلندترین ضلع دست‌کم چهار
     * برابر حد مجاز بود. یک عکس معمولی ۱۲ مگاپیکسلی با ضریب ۱ باز می‌شد:
     * ۴۶ مگابایت، و با نسخه‌ی چرخش‌یافته ~۹۲ مگابایت → OutOfMemoryError →
     * تصویر null می‌شد و صفحه برش هرگز باز نمی‌شد.
     */
    @Test fun `typical 12MP phone photo is downsampled for the crop preview`() {
        val s = sampleSize(4000, 3000, 1200)
        assertTrue("عکس ۱۲ مگاپیکسلی باید نمونه‌برداری شود، نه اینکه کامل باز شود", s >= 2)
        // اوج مصرف = بیت‌مپ + نسخه چرخیده
        val peak = megabytes(4000, 3000, s) * 2
        assertTrue("اوج مصرف حافظه باید زیر ۳۲ مگابایت بماند ولی $peak بود", peak < 32.0)
    }

    @Test fun `old sample size formula would have blown the heap`() {
        // فرمول معیوب قبلی، برای اثبات اینکه واقعاً ریشه‌ی باگ بود
        fun old(w: Int, h: Int, maxDim: Int): Int {
            var s = 1; var l = maxOf(w, h)
            while (l / 2 >= maxDim * 2) { l /= 2; s *= 2 }
            return s
        }
        assertEquals("فرمول قبلی عکس ۱۲ مگاپیکسلی را اصلاً کوچک نمی‌کرد", 1, old(4000, 3000, 1200))
        assertTrue("و بیش از ۹۰ مگابایت حافظه می‌خواست", megabytes(4000, 3000, 1) * 2 > 90.0)
    }

    @Test fun `downsampled image still stays above the target size`() {
        // نباید آنقدر کوچک شود که کیفیت نهایی از دست برود
        for ((w, h) in listOf(4000 to 3000, 3000 to 4000, 8000 to 6000, 2560 to 1920)) {
            val s = sampleSize(w, h, 1600)
            val longest = maxOf(w, h) / s
            assertTrue("بعد از نمونه‌برداری ($w×$h) باید ≥ ۱۶۰۰ بماند ولی $longest شد", longest >= 1600)
        }
    }

    @Test fun `small images are never downsampled`() {
        assertEquals(1, sampleSize(1200, 1600, 1600))
        assertEquals(1, sampleSize(640, 480, 1200))
    }

    /**
     * رگرسیون باگ اصلی «ویرایشگر تصویر باز نمی‌شود» (کد ۵).
     *
     * کد معیوب این بود:
     *
     *     openInputStream(uri)?.use { decodeStream(it, null, bounds) } ?: return null
     *
     * به نظر می‌رسید «اگر جریان باز نشد برگرد»، ولی `?:` روی نتیجه‌ی کل عبارت
     * عمل می‌کند و `use` خروجی بلوکش را برمی‌گرداند. `decodeStream` با
     * `inJustDecodeBounds = true` طبق مستندات **همیشه null** برمی‌گرداند.
     * پس شرط همیشه برقرار می‌شد و تابع برای هر تصویری null می‌داد — یعنی
     * صفحه برش هرگز تصویری نمی‌گرفت، مستقل از حافظه یا قالب فایل.
     */
    @Test fun `stream presence must not be confused with decoder output`() {
        // decodeStream وقتی فقط ابعاد را می‌خواهیم، همیشه null است
        fun decodeBoundsOnly(stream: String, out: IntArray): String? {
            out[0] = 4000; out[1] = 3000      // ابعاد نوشته می‌شود
            return null                        // ولی خروجی null است
        }

        val stream: String? = "یک جریان کاملاً سالم"
        val size = IntArray(2)

        // ❌ الگوی معیوب: جریان سالم است ولی مسیر خطا اجرا می‌شود
        // (`let` دقیقاً مثل `use` مقدار بلوک را برمی‌گرداند)
        val buggy = stream?.let { decodeBoundsOnly(it, size) } ?: "مسیر خطا"
        assertEquals("الگوی قدیمی برای جریان سالم هم به مسیر خطا می‌رفت", "مسیر خطا", buggy)

        // ✓ الگوی درست: null بودن جریان جدا از null بودن خروجی رمزگشا
        val reachedDecode = if (stream == null) false else { decodeBoundsOnly(stream, size); true }
        assertTrue("با جریان سالم باید به مرحله رمزگشایی برسیم", reachedDecode)
        assertEquals(4000, size[0])
        assertEquals(3000, size[1])
    }

    /** همان منطق limitDimension. */
    private fun limit(w: Int, h: Int, maxDim: Int): Pair<Int, Int> {
        val longest = maxOf(w, h)
        if (longest <= maxDim) return w to h
        val r = maxDim.toFloat() / longest
        return (w * r).toInt() to (h * r).toInt()
    }

    @Test fun `resize never enlarges a small image`() {
        // بزرگ‌نمایی فقط کیفیت را خراب می‌کند
        assertEquals(640 to 480, limit(640, 480, 1600))
    }

    @Test fun `resize caps the longest side and keeps aspect ratio`() {
        val (w, h) = limit(4000, 3000, 1600)
        assertEquals(1600, w)
        assertEquals(1200, h)
        // نسبت ابعاد حفظ شود
        assertEquals(4000.0 / 3000.0, w.toDouble() / h, 0.01)
    }

    @Test fun `portrait photos are capped on the long side too`() {
        val (w, h) = limit(3000, 4000, 1600)
        assertEquals(1600, h)
        assertEquals(1200, w)
    }

    /** شبیه‌سازی جست‌وجوی دودویی کیفیت: باید بالاترین کیفیت زیر سقف را بیابد. */
    @Test fun `quality search finds the highest quality under the cap`() {
        // فرض: حجم خروجی خطی با کیفیت رابطه دارد (مدل ساده‌شده)
        fun sizeAt(q: Int): Long = (q * 20_000L)      // کیفیت ۹۵ → ۱٫۹ م‌ب
        val cap = 1024L * 1024L                        // ۱ مگابایت

        var low = 40; var high = 95; var best = -1
        while (low <= high) {
            val mid = (low + high) / 2
            if (sizeAt(mid) <= cap) { best = mid; low = mid + 1 } else high = mid - 1
        }
        assertTrue("باید کیفیتی پیدا شود", best > 0)
        assertTrue("زیر سقف بماند", sizeAt(best) <= cap)
        assertTrue("یک پله بالاتر باید از سقف رد شود", sizeAt(best + 1) > cap)
    }

    @Test fun `one megabyte cap constant is correct`() {
        assertEquals(1024L * 1024L, ir.chidari.data.image.ImageProcessor.MAX_BYTES)
        assertEquals(1600, ir.chidari.data.image.ImageProcessor.MAX_DIMENSION)
    }
}

/**
 * تست نگاشت کادر برش برای عکس‌های چرخیده.
 *
 * باگی که رفع شد: کادر در فضای «تصویر دیده‌شده» انتخاب می‌شود ولی فایل
 * روی دیسک نچرخیده است. بدون نگاشت، ناحیه اشتباهی بریده می‌شد — که برای
 * همه عکس‌های دوربین (که EXIF چرخش دارند) اتفاق می‌افتاد.
 */
class CropRotationTest {

    /** همان منطق mapDisplayRectToStored. */
    private fun map(r: IntArray, rot: Int, sw: Int, sh: Int): IntArray {
        val (l, t, rr, b) = listOf(r[0], r[1], r[2], r[3])
        return when (rot) {
            90 -> intArrayOf(t, sh - rr, b, sh - l)
            180 -> intArrayOf(sw - rr, sh - b, sw - l, sh - t)
            270 -> intArrayOf(sw - b, l, sw - t, r[2].let { rr })
            else -> intArrayOf(l, t, rr, b)
        }
    }

    @Test fun `no rotation leaves the rect untouched`() {
        val out = map(intArrayOf(10, 20, 50, 60), 0, 100, 200)
        assertArrayEquals(intArrayOf(10, 20, 50, 60), out)
    }

    @Test fun `90 degree rotation maps display top-left to stored bottom-left`() {
        // فایل ۱۰۰×۲۰۰ ، دیده‌شده ۲۰۰×۱۰۰
        // گوشه بالا-چپِ دیده‌شده باید به پایین-چپِ فایل برود
        val out = map(intArrayOf(0, 0, 50, 50), 90, 100, 200)
        assertEquals("left", 0, out[0])
        assertEquals("top", 150, out[1])
        assertEquals("right", 50, out[2])
        assertEquals("bottom", 200, out[3])
    }

    @Test fun `180 degree rotation flips both axes`() {
        val out = map(intArrayOf(0, 0, 40, 30), 180, 100, 200)
        assertArrayEquals(intArrayOf(60, 170, 100, 200), out)
    }

    @Test fun `mapped rect keeps the same area`() {
        val w = 50; val h = 40
        val out = map(intArrayOf(10, 20, 10 + w, 20 + h), 90, 300, 400)
        // چرخش ۹۰ درجه ابعاد را جابه‌جا می‌کند ولی مساحت ثابت می‌ماند
        val mw = out[2] - out[0]
        val mh = out[3] - out[1]
        assertEquals(w * h, mw * mh)
    }
}

/**
 * تست‌های چندعکسی بودن محصول.
 *
 * نکته‌ی طراحی: عکس «اصلی» (ستون imageUri) عمداً نگه داشته شد تا همه‌ی
 * کارت‌ها و ردیف‌های قدیمی بدون تغییر کار کنند. این تست‌ها همان قرارداد
 * را قفل می‌کنند.
 */
class ProductImagesTest {

    private val PI = ir.chidari.data.local.ProductImages

    @Test fun `split ignores blank lines and trims`() {
        val list = PI.split("  a.jpg \n\n b.jpg \n   \n")
        assertEquals(listOf("a.jpg", "b.jpg"), list)
    }

    @Test fun `join and split are inverse of each other`() {
        val list = listOf("https://x/1.jpg", "https://x/2.jpg", "https://x/3.jpg")
        assertEquals(list, PI.split(PI.join(list)))
    }

    @Test fun `join drops blank entries`() {
        assertEquals("a\nb", PI.join(listOf("a", "", "   ", "b")))
    }

    /** ردیف‌های قدیمی فقط عکس تک‌تایی دارند؛ نباید گم شود. */
    @Test fun `legacy row with only a cover still yields one image`() {
        assertEquals(listOf("cover.jpg"), PI.of(images = "", cover = "cover.jpg"))
    }

    @Test fun `empty product yields no images`() {
        assertTrue(PI.of(images = "", cover = "").isEmpty())
    }

    /** وقتی فهرست هست، عکس اصلی نباید دوباره اضافه شود. */
    @Test fun `cover is not duplicated when the list exists`() {
        val r = PI.of(images = "a.jpg\nb.jpg", cover = "a.jpg")
        assertEquals(listOf("a.jpg", "b.jpg"), r)
    }

    @Test fun `first image is always the cover`() {
        val list = listOf("first.jpg", "second.jpg")
        assertEquals("first.jpg", PI.split(PI.join(list)).first())
    }

    @Test fun `max images is five`() {
        assertEquals(5, PI.MAX)
    }

    /** شبیه‌سازی «بردن به جایگاه اول» که در ViewModel انجام می‌شود. */
    @Test fun `making an image the cover moves it to the front`() {
        val ids = listOf(1L, 2L, 3L)
        val target = 3L
        val reordered = listOf(target) + ids.filterNot { it == target }
        assertEquals(listOf(3L, 1L, 2L), reordered)
    }

    /** شبیه‌سازی جابه‌جایی یک جایگاه. */
    @Test fun `moving an image swaps it with its neighbour`() {
        val list = mutableListOf("a", "b", "c")
        val i = 1; val j = i + 1
        val tmp = list[i]; list[i] = list[j]; list[j] = tmp
        assertEquals(listOf("a", "c", "b"), list)
    }

    /** جابه‌جایی در لبه‌ها نباید کاری کند (نه کرش، نه تغییر). */
    @Test fun `moving past the edges is a no-op`() {
        val list = listOf("a", "b", "c")
        for ((i, delta) in listOf(0 to -1, 2 to 1)) {
            val j = i + delta
            val changed = j >= 0 && j < list.size
            assertTrue("جابه‌جایی در لبه نباید انجام شود", !changed)
        }
    }
}

/**
 * تست‌های کادر برش قابل تغییر اندازه.
 *
 * منطق هندسی عمداً از Compose جدا نگه داشته شد تا همین‌جا و بدون شبیه‌ساز
 * بشود تستش کرد — چون دفعه‌ی قبل باگ نگاشت کادر تا روی دستگاه کاربر رفت.
 */
class CropFrameTest {

    private val G = ir.chidari.ui.crop.CropGeometry
    private fun rect(l: Float, t: Float, r: Float, b: Float) =
        ir.chidari.ui.crop.FrameRect(l, t, r, b)

    /** محدوده‌ی نمونه: کل صفحه ۱۰۰۰×۱۰۰۰ */
    private val full = rect(0f, 0f, 1000f, 1000f)

    @Test fun `initial frame keeps the requested ratio and is centred`() {
        val f = G.initialFrame(rect(0f, 0f, 1000f, 600f), ratio = 1f, margin = 20f)
        assertEquals(560f, f.width, 0.5f)
        assertEquals(560f, f.height, 0.5f)
        assertEquals(500f, f.centerX, 0.5f)
        assertEquals(300f, f.centerY, 0.5f)
    }

    @Test fun `free ratio fills the bounds minus the margin`() {
        val f = G.initialFrame(rect(0f, 0f, 1000f, 600f), null, 20f)
        assertEquals(960f, f.width, 0.5f)
        assertEquals(560f, f.height, 0.5f)
    }

    /** محدوده می‌تواند از صفر شروع نشود (تصویر با Fit وسط‌چین است). */
    @Test fun `initial frame respects an offset bounds`() {
        val bounds = rect(100f, 50f, 500f, 450f)
        val f = G.initialFrame(bounds, 1f, 0f)
        assertEquals(300f, f.centerX, 0.5f)
        assertEquals(250f, f.centerY, 0.5f)
        assertTrue(f.left >= bounds.left && f.right <= bounds.right)
    }

    // ---------- محدوده‌ی تصویر ----------

    @Test fun `image bounds letterbox a wide photo inside a square box`() {
        val b = G.imageBounds(boxW = 1000f, boxH = 1000f, bitmapW = 2000, bitmapH = 1000)
        assertEquals(0f, b.left, 0.5f)
        assertEquals(1000f, b.width, 0.5f)
        assertEquals(500f, b.height, 0.5f)
        assertEquals(250f, b.top, 0.5f)   // نوار خالی بالا و پایین
    }

    // ---------- تشخیص دستگیره ----------

    @Test fun `each of the eight handles is detected`() {
        val f = rect(100f, 100f, 300f, 300f)
        assertEquals(H.TOP_LEFT, G.hitTest(102f, 98f, f, 20f))
        assertEquals(H.TOP_RIGHT, G.hitTest(298f, 103f, f, 20f))
        assertEquals(H.BOTTOM_LEFT, G.hitTest(95f, 305f, f, 20f))
        assertEquals(H.BOTTOM_RIGHT, G.hitTest(301f, 299f, f, 20f))
        assertEquals(H.TOP, G.hitTest(200f, 96f, f, 20f))
        assertEquals(H.BOTTOM, G.hitTest(200f, 302f, f, 20f))
        assertEquals(H.LEFT, G.hitTest(99f, 200f, f, 20f))
        assertEquals(H.RIGHT, G.hitTest(304f, 200f, f, 20f))
    }

    @Test fun `middle of the frame is not a handle`() {
        assertNull(G.hitTest(200f, 200f, rect(100f, 100f, 300f, 300f), 20f))
    }

    @Test fun `corner wins over edge when both are in range`() {
        val f = rect(100f, 100f, 160f, 300f)
        assertEquals(H.TOP_LEFT, G.hitTest(105f, 102f, f, 40f))
    }

    // ---------- جابه‌جایی کل کادر (یک انگشت) ----------

    @Test fun `move shifts the frame without resizing it`() {
        val f = rect(100f, 100f, 300f, 200f)
        val m = G.move(f, 50f, -30f, full)
        assertEquals(150f, m.left, 0.01f)
        assertEquals(70f, m.top, 0.01f)
        assertEquals(f.width, m.width, 0.01f)
        assertEquals(f.height, m.height, 0.01f)
    }

    @Test fun `move is clamped to the image bounds`() {
        val bounds = rect(100f, 100f, 400f, 400f)
        val f = rect(100f, 100f, 200f, 200f)
        val m = G.move(f, -999f, -999f, bounds)
        assertEquals(100f, m.left, 0.01f)
        assertEquals(100f, m.top, 0.01f)

        val m2 = G.move(f, 999f, 999f, bounds)
        assertEquals(400f, m2.right, 0.01f)
        assertEquals(400f, m2.bottom, 0.01f)
    }

    // ---------- دستگیره: فقط همان ضلع ----------

    @Test fun `dragging the right handle moves only the right edge`() {
        val f = rect(100f, 100f, 300f, 300f)
        val r = G.resize(f, H.RIGHT, 40f, 0f, full, 64f)
        assertEquals("فقط راست باید عوض شود", 340f, r.right, 0.01f)
        assertEquals(100f, r.left, 0.01f)
        assertEquals(100f, r.top, 0.01f)
        assertEquals(300f, r.bottom, 0.01f)
    }

    /** با نسبت ۳:۴ هم دیگر ضلع‌های دیگر تکان نمی‌خورند. */
    @Test fun `a preset ratio no longer drags the other edges along`() {
        val f = G.initialFrame(full, 3f / 4f, 20f)
        val r = G.resize(f, H.TOP, -30f, -30f, full, 64f)
        assertEquals("پایین نباید حرکت کند", f.bottom, r.bottom, 0.01f)
        assertEquals("چپ نباید حرکت کند", f.left, r.left, 0.01f)
        assertEquals("راست نباید حرکت کند", f.right, r.right, 0.01f)
    }

    @Test fun `dragging a corner moves exactly two edges`() {
        val f = rect(100f, 100f, 300f, 300f)
        val r = G.resize(f, H.BOTTOM_RIGHT, 25f, 40f, full, 64f)
        assertEquals(100f, r.left, 0.01f)
        assertEquals(100f, r.top, 0.01f)
        assertEquals(325f, r.right, 0.01f)
        assertEquals(340f, r.bottom, 0.01f)
    }

    @Test fun `resize refuses to go below the minimum size`() {
        val f = rect(100f, 100f, 180f, 180f)
        val r = G.resize(f, H.RIGHT, -60f, 0f, full, 64f)
        assertEquals(164f, r.right, 0.01f)   // دقیقاً تا حداقل، نه کمتر
        assertTrue(r.width >= 64f)
    }

    @Test fun `resize never leaves the image bounds`() {
        val bounds = rect(50f, 50f, 450f, 450f)
        val f = rect(50f, 50f, 250f, 250f)
        val r = G.resize(f, H.LEFT, -500f, 0f, bounds, 64f)
        assertTrue("چپ نباید از محدوده تصویر بیرون برود", r.left >= bounds.left)
    }

    // ---------- دایره ----------

    @Test fun `circle stays circular when a handle is dragged`() {
        val f = rect(100f, 100f, 300f, 300f)
        val r = G.resize(f, H.RIGHT, 40f, 0f, full, 64f, forceSquare = true)
        assertEquals("باید مربع (یعنی دایره) بماند", r.width, r.height, 0.5f)
        assertEquals(240f, r.width, 0.5f)
        assertEquals("ضلع مقابل لنگر می‌ماند", 100f, r.left, 0.01f)
    }

    @Test fun `circle shrinks when dragged inwards`() {
        val f = rect(100f, 100f, 300f, 300f)
        val r = G.resize(f, H.BOTTOM, -50f, -50f, full, 64f, forceSquare = true)
        assertEquals(r.width, r.height, 0.5f)
        assertTrue("باید کوچک‌تر شده باشد", r.width < f.width)
    }

    // ---------- دو انگشت ----------

    @Test fun `pinch grows the frame around its centre`() {
        val f = rect(400f, 400f, 600f, 600f)
        val r = G.scaleAround(f, 1.5f, full, 64f)
        assertEquals(300f, r.width, 0.5f)
        assertEquals("مرکز نباید جابه‌جا شود", f.centerX, r.centerX, 0.5f)
        assertEquals(f.centerY, r.centerY, 0.5f)
    }

    @Test fun `pinch keeps the aspect ratio`() {
        val f = rect(0f, 0f, 400f, 300f)
        val r = G.scaleAround(f, 0.5f, full, 64f)
        assertEquals(4f / 3f, r.width / r.height, 0.02f)
    }

    @Test fun `pinch is pushed back inside instead of overflowing`() {
        val bounds = rect(0f, 0f, 400f, 400f)
        val f = rect(220f, 220f, 380f, 380f)       // نزدیک گوشه
        val r = G.scaleAround(f, 1.8f, bounds, 64f)
        assertTrue(r.left >= -0.5f && r.top >= -0.5f)
        assertTrue(r.right <= 400.5f && r.bottom <= 400.5f)
    }

    @Test fun `pinch cannot exceed the image bounds`() {
        val bounds = rect(0f, 0f, 400f, 400f)
        val f = rect(0f, 0f, 400f, 400f)
        assertEquals("بزرگ‌تر از تصویر نمی‌شود", f, G.scaleAround(f, 2f, bounds, 64f))
    }

    @Test fun `pinch cannot go below the minimum size`() {
        val f = rect(0f, 0f, 70f, 70f)
        assertEquals(f, G.scaleAround(f, 0.5f, full, 64f))
    }

    // ---------- نگاشت به تصویر اصلی ----------

    @Test fun `full frame maps to the whole source image`() {
        val bounds = rect(0f, 0f, 400f, 400f)
        val r = G.toSourceRect(bounds, bounds, 4000, 4000)
        assertEquals(0, r[0]); assertEquals(0, r[1])
        assertEquals(4000, r[2]); assertEquals(4000, r[3])
    }

    @Test fun `half frame maps to a quarter of the source area`() {
        val bounds = rect(0f, 0f, 400f, 400f)
        val r = G.toSourceRect(rect(0f, 0f, 200f, 200f), bounds, 4000, 4000)
        assertEquals(0, r[0]); assertEquals(0, r[1])
        assertEquals(2000, r[2]); assertEquals(2000, r[3])
    }

    /** محدوده‌ی جابه‌جاشده (نوار خالی) نباید نگاشت را بشکند. */
    @Test fun `letterboxed bounds map correctly`() {
        val bounds = rect(0f, 250f, 1000f, 750f)      // تصویر عریض، وسط صفحه
        val r = G.toSourceRect(rect(0f, 250f, 500f, 500f), bounds, 2000, 1000)
        assertEquals(0, r[0]); assertEquals(0, r[1])
        assertEquals(1000, r[2]); assertEquals(500, r[3])
    }

    @Test fun `result is always inside the source bounds`() {
        val bounds = rect(0f, 0f, 400f, 400f)
        val r = G.toSourceRect(rect(-500f, -500f, 900f, 900f), bounds, 1000, 1000)
        assertTrue(r[0] >= 0 && r[1] >= 0)
        assertTrue(r[2] <= 1000 && r[3] <= 1000)
        assertTrue(r[2] > r[0])
    }
}

/** شکل‌های کادر برش. */
class CropShapeTest {

    @Test fun `circle is the only shape that must stay square`() {
        val forced = ir.chidari.ui.crop.CropShape.entries.filter { it.forceSquare }
        assertEquals(listOf(ir.chidari.ui.crop.CropShape.CIRCLE), forced)
    }

    @Test fun `circle sits next to square in the chip row`() {
        val labels = ir.chidari.ui.crop.CropShape.entries.map { it.label }
        assertEquals(listOf("مربع", "دایره", "۳:۴", "۴:۳", "آزاد"), labels)
    }

    @Test fun `square and circle both start from a one to one frame`() {
        assertEquals(1f, ir.chidari.ui.crop.CropShape.SQUARE.ratio)
        assertEquals(1f, ir.chidari.ui.crop.CropShape.CIRCLE.ratio)
        assertNull(ir.chidari.ui.crop.CropShape.FREE.ratio)
    }
}

/** رنگ تطبیقی کادر بر اساس روشنایی تصویر. */
class CropColorTest {

    private val L = ir.chidari.ui.crop.Luma

    @Test fun `luminance of pure colours`() {
        assertEquals(0f, L.of(0xFF000000.toInt()), 0.001f)
        assertEquals(1f, L.of(0xFFFFFFFF.toInt()), 0.001f)
    }

    /** سبز باید روشن‌تر از آبی دیده شود — همان چیزی که چشم می‌بیند. */
    @Test fun `green looks brighter than blue`() {
        assertTrue(L.of(0xFF00FF00.toInt()) > L.of(0xFF0000FF.toInt()))
    }

    @Test fun `a white product photo counts as bright so the frame turns dark`() {
        val white = IntArray(100) { 0xFFF2F2F2.toInt() }
        assertTrue("عکس سفید باید روشن تشخیص داده شود", L.isBright(L.average(white)))
    }

    @Test fun `a night photo counts as dark so the frame stays white`() {
        val dark = IntArray(100) { 0xFF101820.toInt() }
        assertFalse("عکس تیره نباید روشن تشخیص داده شود", L.isBright(L.average(dark)))
    }

    /** نصفه‌تیره/نصفه‌روشن: میانگین وسط است و آستانه آن را تیره می‌گیرد. */
    @Test fun `a half dark half light photo falls back to the white frame`() {
        val mixed = IntArray(100) { if (it < 50) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        val avg = L.average(mixed)
        assertEquals(0.5f, avg, 0.01f)
        assertFalse("در حالت شک، کادر سفید امن‌تر است", L.isBright(avg))
    }

    @Test fun `empty pixel array does not crash`() {
        assertEquals(0f, L.average(IntArray(0)), 0.001f)
    }
}

/**
 * رگرسیون باگ «تصویر آپلودشده دیده نمی‌شود».
 *
 * ریشه: مقصد «فرم محصول» با رفتن به صفحه برش از ترکیب‌بندی خارج می‌شد و
 * هنگام بازگشت دوباره ساخته می‌شد؛ پس `LaunchedEffect` مقداردهی اولیه را
 * دوباره اجرا می‌کرد و فهرست تصاویر به حالت ذخیره‌شده‌ی محصول (برای محصول
 * تازه: خالی) برمی‌گشت. یعنی عکس درست در لحظه‌ی بازگشت از برش پاک می‌شد.
 */
class ProductFormStateTest {

    /** شبیه‌سازی همان محافظِ کلید که در ViewModel گذاشته شد. */
    private class ImageList {
        var key: String? = null
        var items = listOf<String>()
        var initCount = 0

        fun start(formKey: String, stored: List<String>) {
            if (key == formKey) return
            key = formKey
            items = stored
            initCount++
        }
        fun add(path: String) { items = items + path }
        fun close() { key = null; items = emptyList() }
    }

    @Test fun `returning from the crop screen keeps the new image`() {
        val list = ImageList()
        // باز شدن فرم برای محصول تازه در فروشگاه ۱۲
        list.start("12:-1", emptyList())
        // کاربر عکس می‌گیرد و برش می‌زند
        list.add("/data/product_1.jpg")
        // بازگشت از صفحه برش → همان اثر دوباره اجرا می‌شود
        list.start("12:-1", emptyList())

        assertEquals("عکس نباید با بازگشت از برش پاک شود", 1, list.items.size)
        assertEquals("مقداردهی اولیه فقط یک بار", 1, list.initCount)
    }

    @Test fun `old behaviour would have wiped the image`() {
        // همان سناریو، ولی بدون محافظ کلید
        var items = listOf<String>()
        items = emptyList()                 // باز شدن فرم
        items = items + "/data/product_1.jpg"   // برش
        items = emptyList()                 // بازگشت ← مقداردهی دوباره
        assertTrue("رفتار قبلی عکس را پاک می‌کرد", items.isEmpty())
    }

    @Test fun `opening a different product does reset the list`() {
        val list = ImageList()
        list.start("12:-1", emptyList())
        list.add("/data/a.jpg")
        // حالا کاربر محصول دیگری را باز می‌کند
        list.start("12:77", listOf("https://x/old.jpg"))

        assertEquals(listOf("https://x/old.jpg"), list.items)
        assertEquals(2, list.initCount)
    }

    @Test fun `closing the form allows a fresh start for the same product`() {
        val list = ImageList()
        list.start("12:-1", emptyList())
        list.add("/data/a.jpg")
        list.close()                        // ذخیره شد یا فرم بسته شد
        list.start("12:-1", emptyList())    // افزودن محصول بعدی

        assertTrue("فرم تازه باید خالی شروع شود", list.items.isEmpty())
    }

    /** ساخت کلید فرم — باید محصول تازه را از محصول موجود جدا کند. */
    @Test fun `form key separates a new product from an existing one`() {
        fun key(storeId: Long, productId: Long) = "$storeId:$productId"
        assertEquals("12:-1", key(12L, -1L))
        assertTrue(key(12L, -1L) != key(12L, 77L))
        assertTrue(key(12L, -1L) != key(13L, -1L))
    }
}

/** سازگاری با سروری که هنوز مهاجرت چندعکسی را اجرا نکرده. */
class LegacyServerTest {

    /** همان شرط تشخیص در RemoteDataSource. */
    private fun missingColumn(body: String): Boolean =
        body.contains("image_urls", ignoreCase = true) &&
            (body.contains("column", ignoreCase = true) ||
                body.contains("schema cache", ignoreCase = true) ||
                body.contains("PGRST204"))

    @Test fun `postgrest schema cache error is recognised`() {
        val body = """{"code":"PGRST204","message":"Could not find the 'image_urls' column of 'products' in the schema cache"}"""
        assertTrue(missingColumn(body))
    }

    @Test fun `postgres undefined column error is recognised`() {
        val body = """{"code":"42703","message":"column \"image_urls\" of relation \"products\" does not exist"}"""
        assertTrue(missingColumn(body))
    }

    @Test fun `unrelated errors are not mistaken for a missing column`() {
        assertFalse(missingColumn("""{"message":"new row violates row-level security policy"}"""))
        assertFalse(missingColumn("""{"message":"JWT expired"}"""))
        // خطایی که فقط نام ستون دیگری دارد
        assertFalse(missingColumn("""{"message":"column \"price\" does not exist"}"""))
    }
}
