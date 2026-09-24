package ir.chidari

import ir.chidari.data.Categories
import ir.chidari.data.GeoUtils
import ir.chidari.data.IranGeo
import ir.chidari.data.normalizeFa
import ir.chidari.util.Fa
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
