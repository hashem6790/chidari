package ir.chidari.ui.nav

import android.net.Uri

/** مسیرهای ناوبری برنامه. */
object Routes {
    const val ONBOARDING = "onboarding"
    const val MAIN = "main"
    const val LOCATION = "location"
    const val STORE = "store/{storeId}"
    const val PRODUCT = "product/{productId}"
    const val COMPARE = "compare/{title}"
    const val AUTH = "auth"
    const val SCANNER = "scanner"
    const val CROP = "crop"
    const val IMAGE_VIEWER = "image_viewer/{imageId}"
    const val STORE_EDITOR = "store_editor?storeId={storeId}"
    const val PRODUCT_EDITOR = "product_editor?storeId={storeId}&productId={productId}"

    fun imageViewer(imageId: Long) = "image_viewer/$imageId"
    fun store(id: Long) = "store/$id"
    fun product(id: Long) = "product/$id"
    fun compare(title: String) = "compare/${Uri.encode(title)}"
    fun storeEditor(id: Long? = null) = "store_editor?storeId=${id ?: -1L}"
    fun productEditor(storeId: Long, productId: Long? = null) =
        "product_editor?storeId=$storeId&productId=${productId ?: -1L}"
}

/** تب‌های نوار پایین. */
enum class Tab(val route: String, val label: String, val emoji: String) {
    HOME("tab_home", "فروشگاه‌ها", "🏪"),
    PRODUCTS("tab_products", "محصولات", "🔎"),
    FAVORITES("tab_favorites", "علاقه‌مندی", "🤍"),
    PROFILE("tab_profile", "حساب من", "👤")
}
