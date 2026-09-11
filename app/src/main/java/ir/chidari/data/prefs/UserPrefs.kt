package ir.chidari.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "user_prefs")

/** موقعیت انتخاب‌شده کاربر: استان/شهر دستی یا مختصات GPS. */
data class UserLocation(
    val province: String = "",
    val city: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
    val useGps: Boolean = false,
    val onboarded: Boolean = false
) {
    val hasCoordinates: Boolean get() = lat != null && lng != null
    val label: String
        get() = when {
            city.isNotBlank() && province.isNotBlank() -> "$city، $province"
            province.isNotBlank() -> province
            hasCoordinates -> "موقعیت فعلی"
            else -> "همه ایران"
        }
}

class UserPrefs(private val context: Context) {

    private object Keys {
        val province = stringPreferencesKey("province")
        val city = stringPreferencesKey("city")
        val lat = doublePreferencesKey("lat")
        val lng = doublePreferencesKey("lng")
        val useGps = booleanPreferencesKey("use_gps")
        val onboarded = booleanPreferencesKey("onboarded")
        val sellerMode = booleanPreferencesKey("seller_mode")
    }

    val location: Flow<UserLocation> = context.dataStore.data.map { p ->
        UserLocation(
            province = p[Keys.province] ?: "",
            city = p[Keys.city] ?: "",
            lat = p[Keys.lat],
            lng = p[Keys.lng],
            useGps = p[Keys.useGps] ?: false,
            onboarded = p[Keys.onboarded] ?: false
        )
    }

    val sellerMode: Flow<Boolean> = context.dataStore.data.map { it[Keys.sellerMode] ?: false }

    suspend fun setManualLocation(province: String, city: String) {
        context.dataStore.edit { p ->
            p[Keys.province] = province
            p[Keys.city] = city
            p[Keys.useGps] = false
            p[Keys.onboarded] = true
            // مختصات مرکز شهر برای مرتب‌سازی بر اساس فاصله نگه داشته می‌شود
            ir.chidari.data.IranGeo.findCity(province, city)?.let {
                p[Keys.lat] = it.lat
                p[Keys.lng] = it.lng
            }
        }
    }

    suspend fun setGpsLocation(lat: Double, lng: Double, province: String, city: String) {
        context.dataStore.edit { p ->
            p[Keys.lat] = lat
            p[Keys.lng] = lng
            p[Keys.province] = province
            p[Keys.city] = city
            p[Keys.useGps] = true
            p[Keys.onboarded] = true
        }
    }

    suspend fun clearCityFilter() {
        context.dataStore.edit { p ->
            p[Keys.city] = ""
        }
    }

    suspend fun clearAllFilters() {
        context.dataStore.edit { p ->
            p[Keys.province] = ""
            p[Keys.city] = ""
            p[Keys.useGps] = false
            p[Keys.onboarded] = true
        }
    }

    suspend fun setSellerMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.sellerMode] = enabled }
    }

    suspend fun setOnboarded() {
        context.dataStore.edit { it[Keys.onboarded] = true }
    }
}
