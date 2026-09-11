package ir.chidari.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface StoreDao {

    /**
     * درج بدون بازنویسی.
     *
     * عمداً از REPLACE استفاده نمی‌شود: REPLACE در SQLite یعنی DELETE سپس INSERT،
     * که دو عارضه دارد — جریان Room یک لحظه ردیف را «ناموجود» می‌بیند و عدد
     * روی صفحه می‌پرد، و بدتر اینکه محصولات وابسته با CASCADE پاک می‌شوند.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(store: StoreEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(stores: List<StoreEntity>): List<Long>

    @Update
    suspend fun updateStore(store: StoreEntity)

    /** درج یا به‌روزرسانی بدون حذف ردیف (بدون پرش در رابط کاربری). */
    @Transaction
    suspend fun upsert(store: StoreEntity): Long {
        val id = insertIgnore(store)
        if (id == -1L) {
            updateStore(store)
            return store.id
        }
        return id
    }

    @Transaction
    suspend fun upsertAll(stores: List<StoreEntity>) {
        val ids = insertAllIgnore(stores)
        ids.forEachIndexed { i, id -> if (id == -1L) updateStore(stores[i]) }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(store: StoreEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stores: List<StoreEntity>): List<Long>

    @Update
    suspend fun update(store: StoreEntity)

    @Delete
    suspend fun delete(store: StoreEntity)

    @Query("SELECT * FROM stores ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<StoreEntity>>

    @Query("SELECT * FROM stores WHERE id = :id")
    fun observeById(id: Long): Flow<StoreEntity?>

    @Query("SELECT * FROM stores WHERE id = :id")
    suspend fun getById(id: Long): StoreEntity?

    @Query("SELECT * FROM stores WHERE ownerId = :ownerId ORDER BY createdAt DESC")
    fun observeMine(ownerId: String): Flow<List<StoreEntity>>

    @Query("SELECT COUNT(*) FROM stores")
    suspend fun count(): Int

    @Query("SELECT DISTINCT province FROM stores ORDER BY province")
    fun observeProvinces(): Flow<List<String>>

    /**
     * جست‌وجوی فروشگاه‌ها با فیلترهای اختیاری.
     * پارامتر خالی («») یعنی «همه».
     */
    @Query(
        """
        SELECT * FROM stores
        WHERE (:province = '' OR province = :province)
          AND (:city = '' OR city = :city)
          AND (:category = '' OR category = :category)
          AND (:query = '' OR name LIKE '%' || :query || '%'
                           OR bio LIKE '%' || :query || '%'
                           OR category LIKE '%' || :query || '%')
        ORDER BY rating DESC, createdAt DESC
        LIMIT :limit
        """
    )
    fun search(
        province: String,
        city: String,
        category: String,
        query: String,
        limit: Int = 300
    ): Flow<List<StoreEntity>>

    @Query("SELECT COUNT(*) FROM products WHERE storeId = :storeId")
    suspend fun productCount(storeId: Long): Int

    @Query("SELECT MIN(price) FROM products WHERE storeId = :storeId")
    suspend fun minPrice(storeId: Long): Long?

    @Query("UPDATE stores SET rating = :rating, ratingCount = ratingCount + 1 WHERE id = :id")
    suspend fun updateRating(id: Long, rating: Double)

    /** پاک کردن فروشگاه‌های کش‌شده (فروشگاه‌های خود کاربر نگه داشته می‌شوند). */
    @Query("DELETE FROM stores WHERE isOwnedByMe = 0")
    suspend fun deleteAllNotOwned()

    /** تغییر فوری شمارنده دنبال‌کننده (برای به‌روزرسانی خوش‌بینانه رابط کاربری). */
    @Query("UPDATE stores SET followerCount = MAX(followerCount + :delta, 0) WHERE id = :id")
    suspend fun bumpFollowerCount(id: Long, delta: Int)

    /** شمارنده دنبال‌کننده به‌صورت جریان زنده. */
    @Query("SELECT followerCount FROM stores WHERE id = :id")
    fun observeStoreFollowerCount(id: Long): Flow<Int?>

    /** فروشگاه‌هایی که هنوز روی سرور ثبت نشده‌اند. */
    @Query("SELECT * FROM stores WHERE isSynced = 0")
    suspend fun pendingSync(): List<StoreEntity>

    /** پس از ورود کاربر، فروشگاه‌های او علامت‌گذاری می‌شوند. */
    @Query("UPDATE stores SET isOwnedByMe = (ownerId = :userId AND :userId != '')")
    suspend fun refreshOwnership(userId: String)
}

@Dao
interface ProductDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(product: ProductEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(products: List<ProductEntity>): List<Long>

    @Update
    suspend fun updateProduct(product: ProductEntity)

    /** درج یا به‌روزرسانی بدون حذف ردیف. */
    @Transaction
    suspend fun upsertAll(products: List<ProductEntity>) {
        val ids = insertAllIgnore(products)
        ids.forEachIndexed { i, id -> if (id == -1L) updateProduct(products[i]) }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(product: ProductEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(products: List<ProductEntity>)

    @Update
    suspend fun update(product: ProductEntity)

    @Delete
    suspend fun delete(product: ProductEntity)

    @Query("SELECT * FROM products WHERE storeId = :storeId ORDER BY updatedAt DESC")
    fun observeByStore(storeId: Long): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE id = :id")
    fun observeById(id: Long): Flow<ProductEntity?>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getById(id: Long): ProductEntity?

    @Query("SELECT DISTINCT category FROM products ORDER BY category")
    fun observeCategories(): Flow<List<String>>

    /** جست‌وجوی سراسری محصولات همراه با اطلاعات فروشگاه (برای مقایسه). */
    @Query(
        """
        SELECT p.id AS id, p.storeId AS storeId, p.title AS title, p.category AS category,
               p.price AS price, p.unit AS unit, p.description AS description, p.specs AS specs,
               p.available AS available, p.discountPercent AS discountPercent, p.emoji AS emoji,
               p.imageUri AS imageUri,
               s.name AS storeName, s.category AS storeCategory, s.province AS province,
               s.city AS city, s.lat AS lat, s.lng AS lng, s.rating AS rating,
               s.phone AS phone, s.address AS address
        FROM products p INNER JOIN stores s ON s.id = p.storeId
        WHERE (:province = '' OR s.province = :province)
          AND (:city = '' OR s.city = :city)
          AND (:category = '' OR p.category = :category)
          AND (:query = '' OR p.title LIKE '%' || :query || '%'
                           OR p.description LIKE '%' || :query || '%'
                           OR p.specs LIKE '%' || :query || '%'
                           OR s.name LIKE '%' || :query || '%')
          AND (:onlyAvailable = 0 OR p.available = 1)
        LIMIT :limit
        """
    )
    fun search(
        province: String,
        city: String,
        category: String,
        query: String,
        onlyAvailable: Int,
        limit: Int = 400
    ): Flow<List<ProductWithStore>>

    /** همه ارائه‌های یک عنوان محصول در فروشگاه‌های مختلف — قلب صفحه مقایسه. */
    @Query(
        """
        SELECT p.id AS id, p.storeId AS storeId, p.title AS title, p.category AS category,
               p.price AS price, p.unit AS unit, p.description AS description, p.specs AS specs,
               p.available AS available, p.discountPercent AS discountPercent, p.emoji AS emoji,
               p.imageUri AS imageUri,
               s.name AS storeName, s.category AS storeCategory, s.province AS province,
               s.city AS city, s.lat AS lat, s.lng AS lng, s.rating AS rating,
               s.phone AS phone, s.address AS address
        FROM products p INNER JOIN stores s ON s.id = p.storeId
        WHERE p.title LIKE '%' || :title || '%'
        ORDER BY p.price ASC
        """
    )
    fun observeOffersFor(title: String): Flow<List<ProductWithStore>>

    /**
     * تعداد فروشگاه‌های ارائه‌دهنده و کمترین قیمت، برای هر عنوان کالا.
     * جایگزین حلقه پرهزینه‌ای که قبلاً در رابط کاربری اجرا می‌شد.
     */
    @Query(
        """
        SELECT p.title AS title, COUNT(*) AS offerCount, MIN(
            CASE WHEN p.discountPercent > 0
                 THEN p.price * (100 - p.discountPercent) / 100
                 ELSE p.price END
        ) AS minPrice
        FROM products p INNER JOIN stores s ON s.id = p.storeId
        WHERE (:province = '' OR s.province = :province)
          AND (:city = '' OR s.city = :city)
        GROUP BY p.title
        HAVING COUNT(*) > 1
        """
    )
    fun observeOfferStats(province: String, city: String): Flow<List<TitleStats>>

    @Query("SELECT COUNT(*) FROM products")
    suspend fun count(): Int

    /** حذف محصولات چند فروشگاه (هنگام جایگزینی داده‌های سرور). */
    @Query("DELETE FROM products WHERE storeId IN (:storeIds)")
    suspend fun deleteByStoreIds(storeIds: List<Long>)

    /** یافتن محصول با بارکد (برای پرکردن خودکار فرم). */
    @Query("SELECT * FROM products WHERE barcode = :barcode AND barcode != '' LIMIT 1")
    suspend fun findByBarcode(barcode: String): ProductEntity?

    /** حذف محصولاتی از یک فروشگاه که دیگر روی سرور نیستند. */
    @Query("DELETE FROM products WHERE storeId = :storeId AND id NOT IN (:keepIds)")
    suspend fun pruneMissing(storeId: Long, keepIds: List<Long>)

    /** ردیف‌هایی که هنوز به سرور فرستاده نشده‌اند. */
    @Query("SELECT * FROM products WHERE isSynced = 0")
    suspend fun pendingSync(): List<ProductEntity>
}

@Dao
interface FavoriteDao {

    /** فروشگاه‌های علاقه‌مندی، مستقیم از پایگاه داده (نه فیلتر روی فهرست جاری). */
    @Query(
        """
        SELECT s.* FROM stores s
        INNER JOIN favorites f ON f.`key` = 'store:' || s.id
        WHERE f.userId = :userId
        ORDER BY f.addedAt DESC
        """
    )
    fun observeFavoriteStores(userId: String): Flow<List<StoreEntity>>

    /** محصولات علاقه‌مندی به همراه اطلاعات فروشگاه. */
    @Query(
        """
        SELECT p.id AS id, p.storeId AS storeId, p.title AS title, p.category AS category,
               p.price AS price, p.unit AS unit, p.description AS description, p.specs AS specs,
               p.available AS available, p.discountPercent AS discountPercent, p.emoji AS emoji,
               p.imageUri AS imageUri,
               s.name AS storeName, s.category AS storeCategory, s.province AS province,
               s.city AS city, s.lat AS lat, s.lng AS lng, s.rating AS rating,
               s.phone AS phone, s.address AS address
        FROM products p
        INNER JOIN stores s ON s.id = p.storeId
        INNER JOIN favorites f ON f.`key` = 'product:' || p.id
        WHERE f.userId = :userId
        ORDER BY f.addedAt DESC
        """
    )
    fun observeFavoriteProducts(userId: String): Flow<List<ProductWithStore>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(fav: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE userId = :userId AND `key` = :key")
    suspend fun remove(userId: String, key: String)

    @Query("SELECT `key` FROM favorites WHERE userId = :userId")
    fun observeKeys(userId: String): Flow<List<String>>
}

@Dao
interface FollowDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun follow(follow: FollowEntity)

    @Query("DELETE FROM follows WHERE userId = :userId AND storeId = :storeId")
    suspend fun unfollow(userId: String, storeId: Long)

    @Query("SELECT storeId FROM follows WHERE userId = :userId")
    fun observeFollowedIds(userId: String): Flow<List<Long>>

    @Query("SELECT COUNT(*) FROM follows WHERE storeId = :storeId")
    fun observeFollowerCount(storeId: Long): Flow<Int>

    @Query("DELETE FROM follows WHERE userId = :userId")
    suspend fun clearFor(userId: String)

    /** جایگزینی کامل فهرست فالوهای کاربر با آنچه روی سرور است. */
    @Transaction
    suspend fun replaceAllFor(userId: String, storeIds: List<Long>) {
        clearFor(userId)
        storeIds.forEach { follow(FollowEntity(userId = userId, storeId = it)) }
    }

    /** فروشگاه‌هایی که کاربر فالو کرده است. */
    @Query(
        """
        SELECT s.* FROM stores s
        INNER JOIN follows f ON f.storeId = s.id
        WHERE f.userId = :userId
        ORDER BY f.followedAt DESC
        """
    )
    fun observeFollowedStores(userId: String): Flow<List<StoreEntity>>
}
