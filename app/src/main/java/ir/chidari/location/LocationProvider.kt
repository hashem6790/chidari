package ir.chidari.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** نتیجه درخواست موقعیت مکانی. */
sealed interface LocationResult {
    data class Success(val lat: Double, val lng: Double, val accuracyMeters: Float) : LocationResult
    data object PermissionDenied : LocationResult
    data object GpsDisabled : LocationResult
    data class Failure(val message: String) : LocationResult
}

/**
 * دریافت موقعیت فعلی دستگاه با استفاده از Fused Location Provider گوگل.
 * اگر سرویس گوگل در دسترس نباشد، به LocationManager سیستمی برمی‌گردد.
 */
class LocationProvider(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    fun isLocationEnabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    @SuppressLint("MissingPermission")
    suspend fun current(): LocationResult {
        if (!hasPermission()) return LocationResult.PermissionDenied
        if (!isLocationEnabled()) return LocationResult.GpsDisabled

        return try {
            val client = LocationServices.getFusedLocationProviderClient(context)

            // ابتدا آخرین موقعیت شناخته‌شده (سریع)، سپس در صورت نبود، موقعیت لحظه‌ای
            val last = suspendCancellableCoroutine { cont ->
                client.lastLocation
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resume(null) }
            }
            if (last != null && System.currentTimeMillis() - last.time < 2 * 60 * 1000) {
                return LocationResult.Success(last.latitude, last.longitude, last.accuracy)
            }

            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                .setDurationMillis(15_000)
                .setMaxUpdateAgeMillis(60_000)
                .build()

            val fresh = suspendCancellableCoroutine { cont ->
                client.getCurrentLocation(request, null)
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resume(null) }
            }

            when {
                fresh != null -> LocationResult.Success(fresh.latitude, fresh.longitude, fresh.accuracy)
                last != null -> LocationResult.Success(last.latitude, last.longitude, last.accuracy)
                else -> fallbackSystemLocation()
            }
        } catch (t: Throwable) {
            fallbackSystemLocation()
        }
    }

    /** پشتیبان: استفاده مستقیم از LocationManager وقتی سرویس‌های گوگل نصب نیست. */
    @SuppressLint("MissingPermission")
    private fun fallbackSystemLocation(): LocationResult {
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: return LocationResult.Failure("سرویس موقعیت مکانی در دسترس نیست")
            val providers = lm.getProviders(true)
            var best: android.location.Location? = null
            providers.forEach { p ->
                val loc = lm.getLastKnownLocation(p) ?: return@forEach
                if (best == null || loc.accuracy < best!!.accuracy) best = loc
            }
            best?.let { LocationResult.Success(it.latitude, it.longitude, it.accuracy) }
                ?: LocationResult.Failure("موقعیت مکانی پیدا نشد. لطفاً چند لحظه در فضای باز دوباره تلاش کنید.")
        } catch (t: Throwable) {
            LocationResult.Failure(t.message ?: "خطا در دریافت موقعیت مکانی")
        }
    }
}
