package com.dainon.skep.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.dainon.skep.net.FieldApi
import com.dainon.skep.net.Prefs
import com.dainon.skep.ui.SafetyAlertActivity
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
 * 워치 안전알림 수신 — Wearable Data Layer path "/skep/safety".
 * body {worker_id, kind, hr, spo2, lat?, lng?} 를 받아 두 가지를 한다:
 *  ① 피재자 폰 로컬 경보 — vibrateAlarm + 풀스크린 SafetyAlertActivity(서버 왕복 전에 즉시).
 *  ② 서버 중계 — POST /api/field-auth/emergency (X-Field-Token 이 person 식별).
 * /skep/safety 로 오는 kind(emergency/manual …)는 모두 긴급이므로 kind 구분 없이 경보한다.
 */
class WatchSafetyListenerService : WearableListenerService() {

    companion object {
        const val TAG = "WatchSafety"
        const val PATH_SAFETY = "/skep/safety"
        private const val LOCAL_ALARM_CHANNEL_ID = "skep_watch_safety_local"
        private const val LOCAL_ALARM_NOTIF_ID = 997
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
        val kind = msg.kind ?: "unknown"

        // ① 피재자 폰 로컬 경보 — 서버 중계 성공 여부와 무관하게 항상 발동.
        fireLocalAlarm(kind)

        // ② 서버 중계 — person 식별은 X-Field-Token 으로, worker_id 별도 불필요.
        val authToken = Prefs.token(this) ?: run {
            Log.w(TAG, "no field token — skip relay")
            return
        }
        scope.launch {
            runCatching {
                FieldApi(Prefs.serverUrl(this@WatchSafetyListenerService))
                    .emergencyAlert(authToken, kind, msg.hr, msg.spo2, msg.lat, msg.lng)
            }.onFailure { Log.e(TAG, "emergency relay failed: ${it.message}") }
        }
    }

    /** 피재자 폰 로컬 경보 — 강한 진동 + 풀스크린 안전알림(TTS·[확인]). */
    private fun fireLocalAlarm(kind: String) {
        vibrateAlarm()

        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(LOCAL_ALARM_CHANNEL_ID, "내 안전경보", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "내 워치가 감지한 긴급상황 로컬 경보"
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )

        val title = "긴급 안전경보"
        val body = "내 워치가 위급상황을 감지했습니다. 주변에 알리세요."
        val fullScreen = Intent(this, SafetyAlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("title", title)
            putExtra("body", body)
            putExtra("tts_text", body)
            putExtra("severity", "EMERGENCY")
            putExtra("alert_id", -1L)
        }
        val pi = PendingIntent.getActivity(
            this, kind.hashCode(), fullScreen,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, LOCAL_ALARM_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(Notification.PRIORITY_MAX)
            .setCategory(Notification.CATEGORY_ALARM)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .setAutoCancel(true)
            .build()
        nm.notify(LOCAL_ALARM_NOTIF_ID, notification)

        // 앱이 포그라운드면 즉시 띄우기(백그라운드/잠금은 풀스크린 인텐트가 처리).
        runCatching { startActivity(fullScreen) }

        // P5-W2 자가발동 — /skep/safety 는 모두 긴급이므로(위 클래스 주석) 서버 왕복 없이 파인드미
        // (사이렌·플래시 스트로브·BLE 비콘) 동시 시작(특허 §5.6). alertId 는 서버 미할당이라 -1.
        val myPersonId = Prefs.workerId(this)?.toLongOrNull() ?: -1L
        FindMeAlarmService.start(this, -1L, myPersonId)
    }

    private fun vibrateAlarm() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION") getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500, 200, 1000), -1))
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
