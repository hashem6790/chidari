package ir.chidari

import android.app.Application
import ir.chidari.data.auth.AuthClient
import ir.chidari.data.local.AppDatabase
import ir.chidari.data.remote.RemoteDataSource
import ir.chidari.data.remote.SyncManager
import ir.chidari.data.prefs.SettingsRepository
import ir.chidari.data.prefs.UserPrefs
import ir.chidari.data.repo.AppRepository
import ir.chidari.location.LocationProvider

/** نگهدارنده وابستگی‌های سراسری برنامه (تزریق وابستگی ساده و دستی). */
class ChiDariApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.get(this) }
    val repository: AppRepository by lazy { AppRepository(database) }
    val prefs: UserPrefs by lazy { UserPrefs(this) }
    val settings: SettingsRepository by lazy { SettingsRepository(this) }
    val locationProvider: LocationProvider by lazy { LocationProvider(this) }
    val authClient: AuthClient by lazy { AuthClient(this) }
    val remote: RemoteDataSource by lazy { RemoteDataSource(authClient) }
    val syncManager: SyncManager by lazy { SyncManager(database, remote) }
}
