package ir.chidari.data.repo

import ir.chidari.data.GeoUtils
import ir.chidari.data.local.AppDatabase
import ir.chidari.data.local.FavoriteEntity
import ir.chidari.data.local.FollowEntity
import ir.chidari.data.local.ProductEntity
import ir.chidari.data.local.ProductWithStore
import ir.chidari.data.local.StoreEntity
import ir.chidari.data.local.TitleStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** فروشگاه به همراه فاصله محاسبه‌شده تا کاربر. */
data class StoreWithDistance(
    val store: StoreEntity,
    val distanceKm: Double?
) {
    val distanceLabel: String? get() = distanceKm?.let { GeoUtils.formatDistance(it) }
}

/** محصول به همراه فاصله فروشگاه ارائه‌دهنده. */
data class ProductOffer(
    val product: ProductWithStore,
    val distanceKm: Double?
) {
    val distanceLabel: String? get() = distanceKm?.let { GeoUtils.formatDistance(it) }
}

/** معیار مرتب‌سازی نتایج. */
enum class SortMode(val label: String) {
    NEAREST("نزدیک‌ترین"),
    CHEAPEST("ارزان‌ترین"),
    TOP_RATED("بیشترین امتیاز"),
    NEWEST("جدیدترین")
}

class AppRepository(private val db: AppDatabase) {

    private val storeDao = db.storeDao()
    private val productDao = db.productDao()
    private val favoriteDao = db.favoriteDao()
    private val followDao = db.followDao()

    // ---------- فروشگاه‌ها ----------

    fun searchStores(
        province: String,
        city: String,
        category: String,
        query: String
    ): Flow<List<StoreEntity>> = storeDao.search(province, city, category, query)

    fun observeStore(id: Long): Flow<StoreEntity?> = storeDao.observeById(id)

    fun observeMyStores(ownerId: String): Flow<List<StoreEntity>> = storeDao.observeMine(ownerId)

    suspend fun getStore(id: Long): StoreEntity? = storeDao.getById(id)

    suspend fun saveStore(store: StoreEntity): Long =
        if (store.id == 0L) storeDao.insert(store) else { storeDao.update(store); store.id }

    suspend fun deleteStore(store: StoreEntity) = storeDao.delete(store)

    suspend fun rateStore(id: Long, rating: Double) = storeDao.updateRating(id, rating)

    /** پاک کردن فروشگاه‌ها و محصولات ذخیره‌شده (علاقه‌مندی و فالو دست‌نخورده می‌مانند). */
    suspend fun clearCachedData() = storeDao.deleteAllNotOwned()

    /**
     * تغییر فوری شمارنده دنبال‌کننده در پایگاه داده محلی.
     * باعث می‌شود عدد بی‌درنگ در رابط کاربری به‌روز شود، پیش از پاسخ سرور.
     */
    suspend fun bumpFollowerCount(storeId: Long, delta: Int) =
        storeDao.bumpFollowerCount(storeId, delta)

    /** پس از ورود/خروج کاربر، پرچم مالکیت فروشگاه‌ها بازنویسی می‌شود. */
    suspend fun refreshOwnership(userId: String) = storeDao.refreshOwnership(userId)

    // ---------- محصولات ----------

    fun observeProducts(storeId: Long): Flow<List<ProductEntity>> = productDao.observeByStore(storeId)

    fun observeProduct(id: Long): Flow<ProductEntity?> = productDao.observeById(id)

    suspend fun getProduct(id: Long): ProductEntity? = productDao.getById(id)

    /** جست‌وجوی محصول بر اساس بارکد — برای پرکردن خودکار فرم افزودن. */
    suspend fun findByBarcode(barcode: String): ProductEntity? = productDao.findByBarcode(barcode)

    suspend fun saveProduct(product: ProductEntity): Long =
        if (product.id == 0L) productDao.insert(product) else { productDao.update(product); product.id }

    suspend fun deleteProduct(product: ProductEntity) = productDao.delete(product)

    fun searchProducts(
        province: String,
        city: String,
        category: String,
        query: String,
        onlyAvailable: Boolean
    ): Flow<List<ProductWithStore>> =
        productDao.search(province, city, category, query, if (onlyAvailable) 1 else 0)

    /** آمار تجمیعی «چند فروشگاه / ارزان‌ترین قیمت» برای هر عنوان کالا. */
    fun observeOfferStats(province: String, city: String): Flow<List<TitleStats>> =
        productDao.observeOfferStats(province, city)

    /** همه ارائه‌های یک محصول در فروشگاه‌های مختلف (صفحه مقایسه). */
    fun offersFor(title: String): Flow<List<ProductWithStore>> = productDao.observeOffersFor(title)

    // ---------- علاقه‌مندی ----------

    fun observeFavorites(userId: String): Flow<Set<String>> =
        favoriteDao.observeKeys(userId).map { it.toSet() }

    fun observeFavoriteStores(userId: String): Flow<List<StoreEntity>> =
        favoriteDao.observeFavoriteStores(userId)

    fun observeFavoriteProducts(userId: String): Flow<List<ProductWithStore>> =
        favoriteDao.observeFavoriteProducts(userId)

    suspend fun toggleFavorite(userId: String, key: String, current: Boolean) {
        if (current) favoriteDao.remove(userId, key)
        else favoriteDao.add(FavoriteEntity(userId = userId, key = key))
    }

    // ---------- فالو کردن فروشگاه ----------

    fun observeFollowedIds(userId: String): Flow<Set<Long>> =
        followDao.observeFollowedIds(userId).map { it.toSet() }

    fun observeFollowedStores(userId: String): Flow<List<StoreEntity>> =
        followDao.observeFollowedStores(userId)

    fun observeFollowerCount(storeId: Long): Flow<Int> = followDao.observeFollowerCount(storeId)

    /** شمارنده ذخیره‌شده روی خود فروشگاه (بی‌درنگ به‌روز می‌شود). */
    fun observeStoreFollowerCount(storeId: Long): Flow<Int> =
        storeDao.observeStoreFollowerCount(storeId).map { it ?: 0 }

    suspend fun toggleFollow(userId: String, storeId: Long, current: Boolean) {
        if (current) followDao.unfollow(userId, storeId)
        else followDao.follow(FollowEntity(userId = userId, storeId = storeId))
    }

    // ---------- ابزار فاصله و مرتب‌سازی ----------

    fun attachDistance(
        stores: List<StoreEntity>,
        lat: Double?,
        lng: Double?
    ): List<StoreWithDistance> = stores.map { s ->
        StoreWithDistance(
            store = s,
            distanceKm = if (lat != null && lng != null)
                GeoUtils.distanceKm(lat, lng, s.lat, s.lng) else null
        )
    }

    fun sortStores(items: List<StoreWithDistance>, mode: SortMode): List<StoreWithDistance> =
        when (mode) {
            SortMode.NEAREST -> items.sortedBy { it.distanceKm ?: Double.MAX_VALUE }
            SortMode.TOP_RATED -> items.sortedByDescending { it.store.rating }
            SortMode.NEWEST -> items.sortedByDescending { it.store.createdAt }
            SortMode.CHEAPEST -> items.sortedBy { it.distanceKm ?: Double.MAX_VALUE }
        }

    fun attachDistanceToOffers(
        offers: List<ProductWithStore>,
        lat: Double?,
        lng: Double?
    ): List<ProductOffer> = offers.map { p ->
        ProductOffer(
            product = p,
            distanceKm = if (lat != null && lng != null)
                GeoUtils.distanceKm(lat, lng, p.lat, p.lng) else null
        )
    }

    fun sortOffers(items: List<ProductOffer>, mode: SortMode): List<ProductOffer> =
        when (mode) {
            SortMode.NEAREST -> items.sortedBy { it.distanceKm ?: Double.MAX_VALUE }
            SortMode.CHEAPEST -> items.sortedBy { it.product.finalPrice }
            SortMode.TOP_RATED -> items.sortedByDescending { it.product.rating }
            SortMode.NEWEST -> items.sortedByDescending { it.product.id }
        }
}
