package com.dainon.skep.service

import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.dainon.skep.net.Prefs
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Wearable Data Layer 수신 — 워치 → 폰 메시지 처리 + 백엔드 중계.
 *
 * Paths:
 *  - /skep/sensor, /skep/status → POST /api/field-auth/sensor
 *  - /skep/alert                → POST /api/field-auth/emergency (+ 폰 강한 진동)
 *
 * 워치 측 (data/ServerClient) 가 우리 백엔드 wire 형식 (hr/spo2/bodyTemp/...) 을 사용하므로
 * 폰은 받은 JSON 을 그대로 forward 한다.
 */
class SkepWatchListenerService : WearableListenerService() {

    companion object {
        const val TAG = "SkepWatchListener"
        const val ACTION_WATCH_UPDATE = "com.dainon.skep.WATCH_UPDATE"
        private val sharedClient = OkHttpClient()
        private val jsonType = "application/json".toMediaType()
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onMessageReceived(event: MessageEvent) {
        val data = String(event.data, Charsets.UTF_8)
        Log.d(TAG, "watch → ${event.path}: $data")
        when (event.path) {
            "/skep/sensor", "/skep/status" -> {
                postAuthed("/api/field-auth/sensor", data)
                broadcastUiUpdate(data)
            }
            "/skep/alert" -> {
                Log.w(TAG, "EMERGENCY from watch")
                vibrateAlarm()
                postAuthed("/api/field-auth/emergency", data)
                broadcastUiUpdate(data)
            }
        }
    }

    private fun postAuthed(path: String, body: String) {
        val token = Prefs.token(this) ?: run {
            Log.w(TAG, "no token — skip relay")
            return
        }
        val url = "${Prefs.serverUrl(this)}$path"
        scope.launch {
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("X-Field-Token", token)
                    .post(body.toRequestBody(jsonType))
                    .build()
                sharedClient.newCall(req).execute().use { resp ->
                    Log.d(TAG, "relay $path → ${resp.code}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "relay failed: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun vibrateAlarm() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION") getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500, 200, 1000), -1))
    }

    private fun broadcastUiUpdate(json: String) {
        sendBroadcast(Intent(ACTION_WATCH_UPDATE).apply { putExtra("data", json) })
    }
}
