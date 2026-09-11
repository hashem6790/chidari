package ir.chidari.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * فروشگاه یا ارائه‌دهنده خدمات.
 * هر فروشنده می‌تواند یک یا چند فروشگاه بسازد؛ فروشگاه مانند «پیج» عمل می‌کند.
 */
@Entity(
    tableName = "stores",
    indices = [Index("province"), Index("city"), Index("category"),
        Index(value = ["province", "city"]), Index("rating")]
)
data class StoreEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String,               // دسته‌بندی فروشگاه: سوپرمارکت، رستوران، خدمات فنی و ...
    val province: String,
    val city: String,
    val address: String = "",
    val phone: String = "",
    val bio: String = "",               // معرفی کوتاه فروشگاه (مثل بیوی اینستاگرام)
    val lat: Double,
    val lng: Double,
    val coverEmoji: String = "🏪",
    val imageUri: String = "",          // مسیر تصویر (اختیاری)
    val openHours: String = "",
    val instagram: String = "",
    val rating: Double = 0.0,
    val ratingCount: Int = 0,
    @ColumnInfo(defaultValue = "0") val isOwnedByMe: Boolean = false, // فروشگاه ثبت‌شده توسط کاربر همین گوشی
    val ownerId: String = "",           // شناسه کاربر Supabase که مالک فروشگاه است
    val followerCount: Int = 0,
    /** آیا این ردیف با سرور همگام است؟ false یعنی منتظر ارسال. */
    @ColumnInfo(defaultValue = "1") val isSynced: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

/** محصول یا خدمتی که فروشگاه ارائه می‌دهد. */
@Entity(
    tableName = "products",
    foreignKeys = [ForeignKey(
        entity = StoreEntity::class,
        parentColumns = ["id"],
        childColumns = ["storeId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("storeId"), Index("category"), Index("title"),
        Index(value = ["storeId", "category"]), Index("price")]
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val storeId: Long,
    val title: String,
    val category: String,               // دسته‌بندی محصول
    val price: Long,                    // قیمت به تومان
    val unit: String = "عدد",           // واحد: عدد، کیلوگرم، متر، ساعت، سرویس ...
    val description: String = "",
    val specs: String = "",             // مشخصات فنی؛ خط‌به‌خط: «کلید: مقدار»
    val available: Boolean = true,
    val discountPercent: Int = 0,
    val emoji: String = "📦",
    val imageUri: String = "",
    /** بارکد کالا (EAN/UPC) — برای افزودن سریع و جست‌وجوی دقیق. */
    @ColumnInfo(defaultValue = "") val barcode: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    /** آیا این ردیف با سرور همگام است؟ */
    @ColumnInfo(defaultValue = "1") val isSynced: Boolean = true
)

/**
 * علاقه‌مندی‌ها (فروشگاه یا محصول) و فالوها — هر کدام متعلق به یک کاربر.
 * کلید اصلی ترکیبی است تا هر حساب فهرست جداگانه خود را داشته باشد.
 */
@Entity(tableName = "favorites", primaryKeys = ["userId", "key"], indices = [Index("userId")])
data class FavoriteEntity(
    val userId: String,                 // شناسه کاربر Supabase
    val key: String,                    // "store:12" یا "product:34"
    val addedAt: Long = System.currentTimeMillis()
)

/** فالو کردن یک فروشگاه توسط کاربر. */
@Entity(tableName = "follows", primaryKeys = ["userId", "storeId"], indices = [Index("userId"), Index("storeId")])
data class FollowEntity(
    val userId: String,
    val storeId: Long,
    val followedAt: Long = System.currentTimeMillis()
)

/** مدل ترکیبی برای نمایش محصول همراه اطلاعات فروشگاه (برای مقایسه قیمت). */
data class ProductWithStore(
    val id: Long,
    val storeId: Long,
    val title: String,
    val category: String,
    val price: Long,
    val unit: String,
    val description: String,
    val specs: String,
    val available: Boolean,
    val discountPercent: Int,
    val emoji: String,
    val imageUri: String,
    val storeName: String,
    val storeCategory: String,
    val province: String,
    val city: String,
    val lat: Double,
    val lng: Double,
    val rating: Double,
    val phone: String,
    val address: String
) {
    /** قیمت نهایی پس از اعمال تخفیف */
    val finalPrice: Long
        get() = if (discountPercent > 0) price * (100 - discountPercent) / 100 else price
}

/**
 * آمار یک عنوان کالا: در چند فروشگاه عرضه شده و ارزان‌ترین قیمتش چند است.
 * توسط پایگاه داده محاسبه می‌شود تا رابط کاربری مجبور به پیمایش کل فهرست نشود.
 */
data class TitleStats(
    val title: String,
    val offerCount: Int,
    val minPrice: Long
)

/** فروشگاه به همراه تعداد محصولات و کمترین قیمت (برای کارت‌های فهرست). */
data class StoreWithStats(
    val store: StoreEntity,
    val productCount: Int,
    val minPrice: Long?
)
