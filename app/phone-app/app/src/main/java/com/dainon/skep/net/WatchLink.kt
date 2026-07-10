package com.dainon.skep.net

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.google.android.gms.wearable.Wearable
import com.google.gson.Gson

/**
 * 폰 - 워치 Data Layer 연동.
 * 워치 PhoneIdentityListenerService 가 path "/skep/worker_id" 로 수신 후
 * worker_id/token/server_url 을 저장하고, lat/lng 가 있으면 SharedPreferences 의 phoneLat/Lng 백업으로 저장한다.
 * server_url 은 폰이 실제 사용하는 베이스 URL — 워치가 직접 서버 호출 시 이 값을 우선 사용.
 * lat/lng 백업은 워치 자체 GPS 가 못 잡혔을 때 알람 좌표 fallback 으로 사용됨.
 */
object WatchLink {
    private const val PATH_WORKER_ID = "/skep/worker_id"
    private val gson = Gson()

    fun pushIdentity(ctx: Context) {
        val workerId = Prefs.workerId(ctx) ?: return
        val token = Prefs.token(ctx)
        val loc = lastKnownLocation(ctx)
        val payload = gson.toJson(buildMap<String, Any> {
            put("worker_id", workerId)
            if (token != null) put("token", token)
            put("server_url", Prefs.serverUrl(ctx))
            if (loc != null) {
                put("lat", loc.first)
                put("lng", loc.second)
            }
        }).toByteArray(Charsets.UTF_8)
        try {
            Wearable.getNodeClient(ctx).connectedNodes
                .addOnSuccessListener { nodes ->
                    val mc = Wearable.getMessageClient(ctx)
                    for (n in nodes) {
                        runCatching { mc.sendMessage(n.id, PATH_WORKER_ID, payload) }
                    }
                }
        } catch (_: Exception) {
            // 워치 미연결 등 — 무시
        }
    }

    private fun lastKnownLocation(ctx: Context): Pair<Double, Double>? {
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        if (!granted) return null
        return try {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            for (p in providers) {
                @Suppress("MissingPermission")
                val l = lm.getLastKnownLocation(p) ?: continue
                return l.latitude to l.longitude
            }
            null
        } catch (_: SecurityException) {
            null
        }
    }
}
