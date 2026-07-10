package com.dainon.skep.service

import android.util.Log
import com.dainon.skep.net.FieldApi
import com.dainon.skep.net.Prefs
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 워치 안전알림 중계 — Wearable Data Layer path "/skep/safety" 수신
 * body {worker_id, kind, hr, spo2, lat?, lng?} → POST /api/field-auth/emergency (X-Field-Token).
 */
class WatchSafetyListenerService : WearableListenerService() {

    companion object {
        const val TAG = "WatchSafety"
        const val PATH_SAFETY = "/skep/safety"
    }

    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private data class SafetyMsg(
        @SerializedName("worker_id") val workerId: String?,
        val kind: String?,
        val hr: Int?,
        val spo2: Int?,
        val lat: Double?,
        val lng: Double?,
    )

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != PATH_SAFETY) return
        val json = String(event.data, Charsets.UTF_8)
        Log.d(TAG, "watch safety: $json")

        val msg = runCatching { gson.fromJson(json, SafetyMsg::class.java) }.getOrNull() ?: return
        // person 식별은 X-Field-Token 으로 — worker_id 별도 불필요.
        val authToken = Prefs.token(this) ?: run {
            Log.w(TAG, "no field token — skip relay")
            return
        }
        val kind = msg.kind ?: "unknown"

        scope.launch {
            runCatching {
                FieldApi(Prefs.serverUrl(this@WatchSafetyListenerService))
                    .emergencyAlert(authToken, kind, msg.hr, msg.spo2, msg.lat, msg.lng)
            }.onFailure { Log.e(TAG, "emergency relay failed: ${it.message}") }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
