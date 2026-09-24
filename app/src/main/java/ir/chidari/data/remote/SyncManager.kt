package ir.chidari.data.remote

import androidx.room.withTransaction
import ir.chidari.data.local.AppDatabase
import ir.chidari.data.local.ProductEntity
import ir.chidari.data.local.StoreEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** وضعیت همگام‌سازی برای نمایش در رابط کاربری. */
sealed interface SyncStatus {
    data object Idle : SyncStatus
    data object Running : SyncStatus
    data class Done(val storeCount: Int, val at: Long = System.currentTimeMillis()) : SyncStatus
    data class Failed(val message: String) : SyncStatus
    /** جدول‌ها روی سرور ساخته نشده‌اند. */
    data object SchemaMissing : SyncStatus
}

/**
 * همگام‌سازی داده‌های سرور با پایگاه داده محلی.
 *
 * راهبرد: **سرور منبع حقیقت است، Room حافظه‌ی پنهان (cache) آفلاین**.
 * - هنگام باز شدن برنامه یا تغییر شهر، فروشگاه‌های آن منطقه از سرور گرفته
 *   و در Room جایگزین می‌شوند.
 * - رابط کاربری همیشه از Room می‌خواند، پس بدون اینترنت هم کار می‌کند
 *   و سرعتش تحت تأثیر شبکه نیست.
 * - نوشتن (ساخت فروشگاه/محصول) اول روی سرور انجام می‌شود و بعد در Room
 *   ذخیره می‌گردد تا شناسه‌ها یکی باشند.
 */
class SyncManager(
    private val db: AppDatabase,
    private val remote: RemoteDataSource
) {
    private val storeDao = db.storeDao()
    private val productDao = db.productDao()
    private val followDao = db.followDao()

    private val mutex = Mutex()

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    /** آخرین منطقه‌ای که همگام شد، برای جلوگیری از درخواست تکراری. */
    private var lastKey: String? = null
    private var lastSyncAt: Long = 0

    val isConfigured: Boolean get() = remote.isConfigured

    /**
     * دریافت فروشگاه‌ها و محصولات یک منطقه و ذخیره در Room.
     *
     * @param force اگر true باشد، حتی اگر همین منطقه اخیراً همگام شده باشد دوباره می‌گیرد.
     * @param currentUserId برای علامت‌گذاری فروشگاه‌های متعلق به کاربر.
     */
    suspend fun syncRegion(
        province: String,
        city: String,
        currentUserId: String = "",
        force: Boolean = false
    ): SyncResult = mutex.withLock {
        if (!remote.isConfigured) {
            _status.value = SyncStatus.Idle
            return SyncResult.NotConfigured
        }

        val key = "$province|$city"
        val fresh = System.currentTimeMillis() - lastSyncAt < CACHE_TTL_MS
        if (!force && key == lastKey && fresh) {
            return SyncResult.Success(0, 0)   // به‌تازگی همگام شده
        }

        _status.value = SyncStatus.Running

        // ۱) فروشگاه‌ها
        val storesResult = remote.fetchStores(province, city)
        val remoteStores = storesResult.getOrElse { e ->
            val msg = e.message.orEmpty()
            _status.value =
                if (msg.contains("supabase-schema.sql")) SyncStatus.SchemaMissing
                else SyncStatus.Failed(msg)
            return SyncResult.Error(msg)
        }

        // ۲) محصولات آن فروشگاه‌ها
        val ids = remoteStores.map { it.id }
        val remoteProducts = remote.fetchProductsForStores(ids).getOrElse { e ->
            _status.value = SyncStatus.Failed(e.message.orEmpty())
            return SyncResult.Error(e.message.orEmpty())
        }

        // ۳) ذخیره در یک تراکنش
        db.withTransaction {
            val marked = remoteStores.map {
                it.copy(isOwnedByMe = currentUserId.isNotBlank() && it.ownerId == currentUserId)
            }
            storeDao.upsertAll(marked)
            // محصولات فروشگاه‌های دریافتی جایگزین می‌شوند تا حذف‌شده‌ها باقی نمانند
            productDao.upsertAll(remoteProducts)
            ids.forEach { sid ->
                val keep = remoteProducts.filter { it.storeId == sid }.map { it.id }
                productDao.pruneMissing(sid, keep.ifEmpty { listOf(-1L) })
            }
        }

        lastKey = key
        lastSyncAt = System.currentTimeMillis()
        _status.value = SyncStatus.Done(remoteStores.size)
        return SyncResult.Success(remoteStores.size, remoteProducts.size)
    }

    /**
     * همگام‌سازی یک فروشگاه خاص هنگام باز کردن صفحه‌اش.
     *
     * **مهم:** علاوه بر محصولات، خودِ ردیف فروشگاه هم از سرور گرفته می‌شود
     * تا شمارنده دنبال‌کننده و میانگین امتیاز (که تریگرهای سرور به‌روز می‌کنند)
     * روی گوشی هم تازه شود — از جمله روی گوشی مالک فروشگاه.
     */
    suspend fun syncStore(storeId: Long, currentUserId: String = ""): SyncResult {
        if (!remote.isConfigured) return SyncResult.NotConfigured

        val store = remote.fetchStore(storeId).getOrElse { e ->
            return SyncResult.Error(e.message.orEmpty())
        }
        val products = remote.fetchProducts(storeId).getOrElse { e ->
            return SyncResult.Error(e.message.orEmpty())
        }

        db.withTransaction {
            store?.let {
                storeDao.upsert(
                    it.copy(isOwnedByMe = currentUserId.isNotBlank() && it.ownerId == currentUserId)
                )
            }
            productDao.upsertAll(products)
            // محصولاتی که روی سرور حذف شده‌اند، محلی هم پاک شوند
            productDao.pruneMissing(storeId, products.map { it.id }.ifEmpty { listOf(-1L) })
        }
        return SyncResult.Success(1, products.size)
    }

    /**
     * همگام‌سازی داده‌های شخصی کاربر: فروشگاه‌های خودش و فروشگاه‌هایی که دنبال کرده.
     *
     * این‌ها مستقل از شهر انتخابی گرفته می‌شوند، چون ممکن است فروشگاه کاربر
     * در شهر دیگری باشد و در همگام‌سازی منطقه‌ای دیده نشود.
     */
    suspend fun syncUserData(userId: String): SyncResult {
        if (!remote.isConfigured || userId.isBlank()) return SyncResult.NotConfigured

        val mine = remote.fetchStoresOfOwner(userId).getOrElse {
            return SyncResult.Error(it.message.orEmpty())
        }
        val followed = remote.fetchFollowedStores(userId).getOrElse {
            return SyncResult.Error(it.message.orEmpty())
        }
        val followedIds = remote.fetchFollowedIds(userId).getOrElse { emptyList() }

        db.withTransaction {
            if (mine.isNotEmpty()) storeDao.upsertAll(mine.map { it.copy(isOwnedByMe = true) })
            if (followed.isNotEmpty()) {
                storeDao.upsertAll(followed.map { it.copy(isOwnedByMe = it.ownerId == userId) })
            }
            // فهرست فالوها را با سرور یکسان می‌کنیم
            followDao.replaceAllFor(userId, followedIds)
        }

        // محصولات فروشگاه‌های خودِ کاربر هم بیایند
        val myIds = mine.map { it.id }
        if (myIds.isNotEmpty()) {
            remote.fetchProductsForStores(myIds).onSuccess { prods ->
                db.withTransaction {
                    productDao.upsertAll(prods)
                    myIds.forEach { sid ->
                        val keep = prods.filter { it.storeId == sid }.map { it.id }
                        productDao.pruneMissing(sid, keep.ifEmpty { listOf(-1L) })
                    }
                }
            }
        }
        return SyncResult.Success(mine.size + followed.size, myIds.size)
    }

    // ---------- نوشتن: اول سرور، بعد محلی ----------

    /**
     * ساخت یا به‌روزرسانی فروشگاه.
     * شناسه‌ای که سرور تولید می‌کند در Room هم استفاده می‌شود.
     */
    suspend fun pushStore(store: StoreEntity, ownerId: String): Result<StoreEntity> {
        if (!remote.isConfigured) {
            // حالت آفلاین: فقط محلی ذخیره کن و علامت بزن که همگام نیست
            val local = store.copy(ownerId = ownerId, isOwnedByMe = true, isSynced = false)
            val id = if (local.id == 0L) storeDao.insert(local) else { storeDao.update(local); local.id }
            return Result.success(local.copy(id = id))
        }

        return if (store.id == 0L) {
            remote.createStore(store, ownerId).mapCatching { created ->
                val local = created.copy(isOwnedByMe = true, isSynced = true)
                storeDao.insert(local)
                local
            }
        } else {
            remote.updateStore(store, ownerId).mapCatching {
                val local = store.copy(ownerId = ownerId, isOwnedByMe = true, isSynced = true)
                storeDao.update(local)
                local
            }
        }
    }

    suspend fun pushDeleteStore(store: StoreEntity): Result<Unit> {
        if (remote.isConfigured && store.isSynced) {
            remote.deleteStore(store.id).onFailure { return Result.failure(it) }
        }
        storeDao.delete(store)
        return Result.success(Unit)
    }

    /**
     * اگر تصویر محصول هنوز روی گوشی است، اول روی سرور بارگذاری می‌شود.
     *
     * مسیر محلی (مثل /data/.../product_123.jpg) برای کاربران دیگر بی‌معناست؛
     * باید به نشانی عمومی سرور تبدیل شود تا همه ببینند.
     */
    private suspend fun uploadImageIfLocal(product: ProductEntity, userId: String): ProductEntity {
        val path = product.imageUri
        if (path.isBlank() || path.startsWith("http")) return product   // از قبل روی سرور است
        if (!remote.isConfigured || userId.isBlank()) return product     // حالت آفلاین

        val f = java.io.File(path)
        if (!f.exists()) return product.copy(imageUri = "")

        return remote.uploadProductImage(f, userId)
            .map { url -> product.copy(imageUri = url) }
            .getOrElse { throw it }                                      // خطا به لایه بالا برود
    }

    suspend fun pushProduct(product: ProductEntity, userId: String = ""): Result<ProductEntity> {
        if (!remote.isConfigured) {
            val local = product.copy(isSynced = false)
            val id = if (local.id == 0L) productDao.insert(local)
            else { productDao.update(local); local.id }
            return Result.success(local.copy(id = id))
        }

        // تصویر پیش از ثبت محصول بارگذاری می‌شود
        val withImage = try {
            uploadImageIfLocal(product, userId)
        } catch (e: Exception) {
            return Result.failure(e)
        }

        return if (withImage.id == 0L) {
            remote.createProduct(withImage).mapCatching { created ->
                productDao.insert(created)
                created
            }
        } else {
            remote.updateProduct(withImage).mapCatching {
                val local = withImage.copy(isSynced = true)
                productDao.update(local)
                local
            }
        }
    }

    suspend fun pushDeleteProduct(product: ProductEntity): Result<Unit> {
        if (remote.isConfigured && product.isSynced) {
            remote.deleteProduct(product.id).onFailure { return Result.failure(it) }
        }
        productDao.delete(product)
        return Result.success(Unit)
    }

    /** فالو/آنفالو روی سرور (شمارنده با تریگر به‌روز می‌شود). */
    suspend fun pushFollow(storeId: Long, userId: String, follow: Boolean): Result<Unit> {
        if (!remote.isConfigured) return Result.success(Unit)
        return if (follow) remote.follow(storeId, userId) else remote.unfollow(storeId, userId)
    }

    suspend fun pushRating(storeId: Long, userId: String, score: Int): Result<Unit> {
        if (!remote.isConfigured) return Result.success(Unit)
        return remote.rateStore(storeId, userId, score)
    }

    fun clearStatus() { _status.value = SyncStatus.Idle }

    /** برای وقتی که کاربر عوض می‌شود و باید دوباره همه‌چیز گرفته شود. */
    fun invalidate() {
        lastKey = null
        lastSyncAt = 0
    }

    companion object {
        /** تا این مدت، منطقه‌ی تکراری دوباره از سرور گرفته نمی‌شود. */
        private const val CACHE_TTL_MS = 3 * 60 * 1000L
    }
}
