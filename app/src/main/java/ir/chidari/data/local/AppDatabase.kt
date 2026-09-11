package ir.chidari.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import ir.chidari.data.SeedData
import ir.chidari.data.auth.SupabaseConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Database(
    entities = [StoreEntity::class, ProductEntity::class, FavoriteEntity::class, FollowEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun storeDao(): StoreDao
    abstract fun productDao(): ProductDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun followDao(): FollowDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context.applicationContext).also { INSTANCE = it }
            }

        private fun build(context: Context): AppDatabase {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            return Room.databaseBuilder(context, AppDatabase::class.java, "bazar_nazdik.db")
                .fallbackToDestructiveMigration()
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // داده نمونه فقط وقتی ساخته می‌شود که سرور تنظیم نشده باشد؛
                        // در حالت آنلاین، داده‌های واقعی از Supabase می‌آیند.
                        if (!SupabaseConfig.isConfigured) {
                            scope.launch {
                                INSTANCE?.let { SeedData.populate(it) }
                            }
                        }
                    }
                })
                .build()
        }
    }
}
