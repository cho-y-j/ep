package com.dainon.skep.service

import android.content.Intent
import android.util.Log
import com.dainon.skep.net.FieldApi
import com.dainon.skep.net.Prefs
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
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
 * Wearable Data Layer 수신 — 워치 상태(/skep/status) → 백엔드 중계.
 *
 * Path:
 *  - /skep/status → POST /api/field-auth/sensor (+ UI 브로드캐스트) + P5-W0 정책 회신(/skep/policy)
 *
 * 안전알림(/skep/safety)은 WatchSafetyListenerService 가 전담(중계 + 폰 로컬 경보).
 * 워치 측 (data/ServerClient) 가 우리 백엔드 wire 형식 (hr/spo2/body_temp/records[]/...) 을 사용하므로
 * 폰은 받은 JSON 을 그대로 forward 한다. P5-W0: 워치 하트비트(10~5분)마다 서버 정책을 받아 워치로 전달
 * (별도 폴링 없이 하트비트 응답 방식 — 저전력).
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
            // 구경로 /skep/sensor·/skep/alert 는 워치가 더 이상 보내지 않아 제거.
            // 안전알림 /skep/safety 는 WatchSafetyListenerService 가 전담(중계 + 폰 로컬 경보).
            "/skep/status" -> {
                postAuthed("/api/field-auth/sensor", data)
                broadcastUiUpdate(data)
                fetchAndPushPolicy()   // P5-W0: 하트비트 응답으로 현재 정책을 워치에 전달.
            }
        }
    }

    /** 서버 정책(폭염/강풍→YELLOW) 조회 후 원문 그대로 워치 /skep/policy 로 전달. */
    private fun fetchAndPushPolicy() {
        val token = Prefs.token(this) ?: return
        scope.launch {
            try {
                val json = FieldApi(Prefs.serverUrl(this@SkepWatchListenerService)).watchPolicyJson(token)
                val bytes = json.toByteArray(Charsets.UTF_8)
                Wearable.getNodeClient(this@SkepWatchListenerService).connectedNodes
                    .addOnSuccessListener { nodes ->
                        val mc = Wearable.getMessageClient(this@SkepWatchListenerService)
                        for (n in nodes) runCatching { mc.sendMessage(n.id, "/skep/policy", bytes) }
                    }
            } catch (e: Exception) {
                Log.w(TAG, "policy fetch/push failed: ${e.message}")
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

    private fun broadcastUiUpdate(json: String) {
        sendBroadcast(Intent(ACTION_WATCH_UPDATE).apply { putExtra("data", json) })
    }
}
