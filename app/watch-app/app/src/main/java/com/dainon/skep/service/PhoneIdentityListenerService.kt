package com.dainon.skep.service

import android.content.Context
import android.util.Log
import com.dainon.skep.data.ServerClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 폰 → 워치 페어링 수신. path "/skep/worker_id"
 * body: {worker_id, token}
 *
 * 워치는 받은 token 을 SharedPreferences("skep")["fieldToken"] 에 저장하고
 * ServerClient.fieldToken 을 즉시 갱신해 이후 HTTP 호출에 X-Field-Token 으로 사용.
 */
class PhoneIdentityListenerService : WearableListenerService() {

    companion object {
        const val TAG = "PhoneIdentity"
        const val PATH_WORKER_ID = "/skep/worker_id"
    }

    private val gson = Gson()

    private data class IdentityMsg(
        @SerializedName("worker_id") val workerId: String?,
        val token: String?,
        @SerializedName("server_url") val serverUrl: String?,
        val lat: Double?,
        val lng: Double?,
    )

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != PATH_WORKER_ID) return
        val json = String(event.data, Charsets.UTF_8)
        Log.d(TAG, "phone → identity: $json")
        val msg = runCatching { gson.fromJson(json, IdentityMsg::class.java) }.getOrNull() ?: return

        val sharedPrefs = getSharedPreferences("skep", Context.MODE_PRIVATE)
        val edit = sharedPrefs.edit()
        msg.workerId?.takeIf { it.isNotBlank() }?.let { edit.putString("workerId", it) }
        msg.token?.takeIf { it.isNotBlank() }?.let {
            edit.putString("fieldToken", it)
            ServerClient.updateFieldToken(it)
        }
        // 폰이 실제 사용하는 베이스 URL — 워치 ServerClient 가 BuildConfig 기본보다 우선 사용.
        msg.serverUrl?.takeIf { it.isNotBlank() }?.let {
            edit.putString("serverUrl", it)
            ServerClient.updateUrl(it)
        }
        // 폰 GPS 좌표 백업 — 워치 자체 GPS 없을 때 SensorService 가 fallback 으로 사용.
        msg.lat?.let { edit.putString("phoneLat", it.toString()) }
        msg.lng?.let { edit.putString("phoneLng", it.toString()) }
        edit.apply()

        // 페어링 직후 — 이미 받아둔 FCM 토큰 있으면 백엔드에 워치 토큰 등록.
        val pendingFcm = sharedPrefs.getString("watchFcmToken", null)?.takeIf { it.isNotBlank() }
        if (pendingFcm != null && msg.token?.isNotBlank() == true) {
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { ServerClient.registerWatchFcmToken(pendingFcm) }
            }
        }
    }
}
