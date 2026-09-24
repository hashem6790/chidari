package ir.chidari.ui.vm

import android.graphics.Bitmap
import android.net.Uri

/** مرحله‌ای که افزودن تصویر محصول در آن قرار دارد. */
sealed interface ProductImageState {
    data object Idle : ProductImageState

    /** در حال خواندن تصویر انتخاب‌شده. */
    data object Loading : ProductImageState

    /** آماده برش: پیش‌نمایش و ابعاد فایل اصلی. */
    data class Cropping(
        val source: Uri,
        val preview: Bitmap,
        val sourceWidth: Int,
        val sourceHeight: Int
    ) : ProductImageState

    /** در حال برش و فشرده‌سازی. */
    data object Processing : ProductImageState

    data class Failed(val message: String) : ProductImageState
}
