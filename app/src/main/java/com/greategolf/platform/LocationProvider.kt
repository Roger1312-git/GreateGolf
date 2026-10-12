package com.greategolf.platform

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.greategolf.domain.GeoFix
import com.greategolf.domain.GeoPoint
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class LocationProvider(context: Context) {
    private val client = LocationServices.getFusedLocationProviderClient(context.applicationContext)

    /** High-accuracy GPS fixes, ~1 per second. Caller must hold the location permission. */
    @SuppressLint("MissingPermission")
    fun fixes(intervalMs: Long = 1000): Flow<GeoFix> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (l in result.locations) {
                    trySend(
                        GeoFix(
                            point = GeoPoint(l.latitude, l.longitude),
                            timeMs = l.time,
                            speedMps = if (l.hasSpeed()) l.speed.toDouble() else null,
                            accuracyM = if (l.hasAccuracy()) l.accuracy.toDouble() else null,
                        )
                    )
                }
            }
        }
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose { client.removeLocationUpdates(callback) }
    }

    /** Last known position (may be stale or null); good enough for sorting "courses near you". */
    @SuppressLint("MissingPermission")
    suspend fun lastKnown(): GeoPoint? = try {
        client.lastLocation.await()?.let { GeoPoint(it.latitude, it.longitude) }
    } catch (e: Exception) { null }
}
