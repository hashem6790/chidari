package ir.chidari.data.remote

import ir.chidari.data.local.ProductEntity
import ir.chidari.data.local.StoreEntity
import org.json.JSONArray
import org.json.JSONObject

/**
 * تبدیل بین ردیف‌های Supabase (snake_case) و موجودیت‌های محلی Room.
 *
 * توجه: شناسه‌ها در سرور و روی گوشی یکی هستند. وقتی فروشگاهی روی سرور
 * ساخته می‌شود، شناسه‌ی سرور همان شناسه‌ای است که در Room ذخیره می‌شود
 * تا ارجاع‌ها (محصولات، فالوها) به‌هم نریزند.
 */
object RemoteMappers {

    // ---------- فروشگاه ----------

    fun storeFromJson(o: JSONObject): StoreEntity = StoreEntity(
        id = o.optLong("id"),
        name = o.optString("name"),
        category = o.optString("category"),
        province = o.optString("province"),
        city = o.optString("city"),
        address = o.optStringSafe("address"),
        phone = o.optStringSafe("phone"),
        bio = o.optStringSafe("bio"),
        lat = o.optDouble("lat", 0.0),
        lng = o.optDouble("lng", 0.0),
        coverEmoji = o.optStringSafe("cover_emoji").ifBlank { "🏪" },
        imageUri = o.optStringSafe("image_url"),
        openHours = o.optStringSafe("open_hours"),
        instagram = o.optStringSafe("instagram"),
        rating = o.optDouble("rating", 0.0),
        ratingCount = o.optInt("rating_count", 0),
        isOwnedByMe = false,               // در لایه بالاتر بر اساس ownerId تعیین می‌شود
        ownerId = o.optStringSafe("owner_id"),
        followerCount = o.optInt("follower_count", 0),
        createdAt = System.currentTimeMillis(),
        isSynced = true
    )

    /** بدنه‌ی درخواست برای ساخت یا به‌روزرسانی فروشگاه. */
    fun storeToJson(s: StoreEntity, ownerId: String, includeId: Boolean = false): JSONObject =
        JSONObject().apply {
            if (includeId && s.id > 0) put("id", s.id)
            put("owner_id", ownerId)
            put("name", s.name)
            put("category", s.category)
            put("province", s.province)
            put("city", s.city)
            put("address", s.address)
            put("phone", s.phone)
            put("bio", s.bio)
            put("lat", s.lat)
            put("lng", s.lng)
            put("cover_emoji", s.coverEmoji)
            put("image_url", s.imageUri)
            put("open_hours", s.openHours)
            put("instagram", s.instagram)
        }

    // ---------- محصول ----------

    fun productFromJson(o: JSONObject): ProductEntity = ProductEntity(
        id = o.optLong("id"),
        storeId = o.optLong("store_id"),
        title = o.optString("title"),
        category = o.optString("category"),
        price = o.optLong("price"),
        unit = o.optStringSafe("unit").ifBlank { "عدد" },
        description = o.optStringSafe("description"),
        specs = o.optStringSafe("specs"),
        available = o.optBoolean("available", true),
        discountPercent = o.optInt("discount_percent", 0),
        emoji = o.optStringSafe("emoji").ifBlank { "📦" },
        imageUri = o.optStringSafe("image_url"),
        barcode = o.optStringSafe("barcode"),
        updatedAt = System.currentTimeMillis(),
        isSynced = true
    )

    fun productToJson(p: ProductEntity, includeId: Boolean = false): JSONObject =
        JSONObject().apply {
            if (includeId && p.id > 0) put("id", p.id)
            put("store_id", p.storeId)
            put("title", p.title)
            put("category", p.category)
            put("price", p.price)
            put("unit", p.unit)
            put("description", p.description)
            put("specs", p.specs)
            put("available", p.available)
            put("discount_percent", p.discountPercent)
            put("emoji", p.emoji)
            put("image_url", p.imageUri)
            put("barcode", p.barcode)
        }

    fun <T> parseArray(text: String, mapper: (JSONObject) -> T): List<T> {
        val arr = runCatching { JSONArray(text) }.getOrNull() ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { add(mapper(it)) }
            }
        }
    }
}

/** `optString` استاندارد برای null رشته‌ی "null" برمی‌گرداند؛ این نسخه امن است. */
internal fun JSONObject.optStringSafe(key: String): String {
    if (isNull(key)) return ""
    return optString(key, "")
}

/** نتیجه‌ی یک عملیات همگام‌سازی. */
sealed interface SyncResult {
    data class Success(val storeCount: Int, val productCount: Int) : SyncResult
    data class Error(val message: String) : SyncResult
    data object NotConfigured : SyncResult
}
