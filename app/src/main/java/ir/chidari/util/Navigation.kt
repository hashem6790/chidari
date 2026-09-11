package ir.chidari.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/** یک برنامه مسیریاب نصب‌شده روی گوشی. */
data class NavigatorApp(
    val name: String,
    val packageName: String,
    val emoji: String,
    /** ساخت Intent مخصوص همین برنامه. */
    val intentFor: (lat: Double, lng: Double, label: String) -> Intent
)

/**
 * مسیریابی با برنامه‌های نقشه.
 *
 * روی اندروید ۱۱ به بالا، به‌دلیل «package visibility» نمی‌توان بدون اعلام
 * صریح در مانیفست فهمید چه برنامه‌ای نصب است. برای همین بسته‌های شناخته‌شده
 * ایرانی و گوگل در `<queries>` مانیفست اعلام شده‌اند.
 */
object Navigation {

    // شناسه بسته برنامه‌های مسیریاب
    const val PKG_NESHAN = "org.rajman.neshan.traffic.tehran.navigator"
    const val PKG_BALAD = "ir.balad"
    const val PKG_GOOGLE_MAPS = "com.google.android.apps.maps"
    const val PKG_WAZE = "com.waze"

    /** فهرست همه مسیریاب‌های پشتیبانی‌شده. */
    private val known: List<NavigatorApp> = listOf(
        NavigatorApp(
            name = "نشان",
            packageName = PKG_NESHAN,
            emoji = "🧭"
        ) { lat, lng, label ->
            // نشان از طرح استاندارد geo پشتیبانی می‌کند
            Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(label)})"))
                .setPackage(PKG_NESHAN)
        },
        NavigatorApp(
            name = "بلد",
            packageName = PKG_BALAD,
            emoji = "🗺️"
        ) { lat, lng, label ->
            Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(label)})"))
                .setPackage(PKG_BALAD)
        },
        NavigatorApp(
            name = "گوگل مپ",
            packageName = PKG_GOOGLE_MAPS,
            emoji = "📍"
        ) { lat, lng, _ ->
            Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$lat,$lng"))
                .setPackage(PKG_GOOGLE_MAPS)
        },
        NavigatorApp(
            name = "ویز",
            packageName = PKG_WAZE,
            emoji = "🚗"
        ) { lat, lng, _ ->
            Intent(Intent.ACTION_VIEW, Uri.parse("waze://?ll=$lat,$lng&navigate=yes"))
                .setPackage(PKG_WAZE)
        }
    )

    private fun isInstalled(context: Context, pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** مسیریاب‌هایی که واقعاً روی این گوشی نصب‌اند. */
    fun installedApps(context: Context): List<NavigatorApp> =
        known.filter { isInstalled(context, it.packageName) }

    /**
     * سایر برنامه‌هایی که به `geo:` پاسخ می‌دهند ولی در فهرست ما نیستند
     * (مثلاً OsmAnd یا نسخه‌های دیگر). به‌صورت «سایر برنامه‌ها» عرضه می‌شوند.
     */
    fun hasGenericHandler(context: Context, lat: Double, lng: Double): Boolean {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng"))
        return context.packageManager.queryIntentActivities(i, 0).isNotEmpty()
    }

    /**
     * باز کردن مستقیم فهرست برنامه‌های مسیریاب نصب‌شده روی دستگاه.
     *
     * از `geo:` استفاده می‌شود که نشان، بلد، گوگل‌مپ و بقیه به آن پاسخ می‌دهند.
     * اندروید خودش فهرست را نشان می‌دهد؛ نیازی به برگه پیشنهاد سفارشی نیست.
     *
     * اگر هیچ برنامه‌ای نصب نباشد، به نقشه وب برمی‌گردیم.
     */
    fun openNavigation(context: Context, lat: Double, lng: Double, label: String) {
        val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(label)})")
        val intent = Intent(Intent.ACTION_VIEW, uri)

        // آیا اصلاً برنامه‌ای هست که به geo پاسخ دهد؟
        val handlers = context.packageManager.queryIntentActivities(intent, 0)
        if (handlers.isEmpty()) {
            openInBrowser(context, lat, lng)
            return
        }

        // اگر فقط یک برنامه هست، مستقیم بازش کن؛ وگرنه فهرست انتخاب
        runCatching {
            if (handlers.size == 1) {
                context.startActivity(intent)
            } else {
                context.startActivity(Intent.createChooser(intent, "مسیریابی با"))
            }
        }.onFailure { openInBrowser(context, lat, lng) }
    }

    /** نام قدیمی، برای سازگاری. */
    fun openSystemChooser(context: Context, lat: Double, lng: Double, label: String) =
        openNavigation(context, lat, lng, label)

    /** اجرای مسیریابی با یک برنامه مشخص. */
    fun launch(context: Context, app: NavigatorApp, lat: Double, lng: Double, label: String) {
        runCatching {
            context.startActivity(app.intentFor(lat, lng, label))
        }.onFailure {
            // اگر طرح اختصاصی کار نکرد، با geo استاندارد تلاش می‌کنیم
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng"))
                        .setPackage(app.packageName)
                )
            }.onFailure { openInBrowser(context, lat, lng) }
        }
    }

    /** آخرین راه: نمایش روی نقشه وب. */
    fun openInBrowser(context: Context, lat: Double, lng: Double) {
        runCatching {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/maps/search/?api=1&query=$lat,$lng")
                )
            )
        }
    }

    /** گزینه «نمایش روی نشان وب» — برای وقتی هیچ برنامه‌ای نصب نیست. */
    fun openNeshanWeb(context: Context, lat: Double, lng: Double) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://neshan.org/maps/@$lat,$lng,16z"))
            )
        }
    }
}
