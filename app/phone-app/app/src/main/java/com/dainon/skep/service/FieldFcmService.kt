package com.dainon.skep.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.util.Log
import com.dainon.skep.net.FieldApi
import com.dainon.skep.net.Prefs
import com.dainon.skep.ui.AnnouncementActivity
import com.dainon.skep.ui.SafetyAlertActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * FCM 수신 (현장 앱) — 공지(announcement) 풀스크린 + 시스템 알림.
 * onNewToken → 작업자면 /api/field-auth/register-token, 회사로그인이면 /api/auth/register-fcm-token.
 */
class FieldFcmService : FirebaseMessagingService() {

    companion object {
        const val TAG = "FieldFCM"
        const val CHANNEL_ID = "skep_announcement"
        // S5' 안전 3등급 채널.
        const val EMERGENCY_CHANNEL_ID = "skep_safety_emergency"  // 무음모드 우회(알람 사운드).
        const val CAUTION_CHANNEL_ID = "skep_safety_caution"
    }

    override fun onNewToken(token: String) {
        val api = FieldApi(Prefs.serverUrl(this))
        val fieldToken = Prefs.token(this)
        val bpToken = Prefs.bpToken(this)
        Thread {
            runCatching {
                when {
                    fieldToken != null -> api.registerFieldFcmToken(fieldToken, token)
                    bpToken != null -> api.registerBpFcmToken(bpToken, token)
                    else -> {}
                }
            }.onFailure { Log.e(TAG, "register fcm token failed: ${it.message}") }
        }.start()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val type = data["type"] ?: message.notification?.let { "announcement" } ?: return
        val title = data["title"] ?: message.notification?.title ?: "공지"
        val body = data["body"] ?: message.notification?.body ?: ""
        val severity = data["severity"]   // EMERGENCY | CAUTION | NORMAL | null(레거시).
        Log.d(TAG, "push [$type] sev=$severity $title")

        when (severity) {
            "EMERGENCY", "CAUTION" -> showSafety(
                severity, title, body,
                ttsText = data["tts_text"] ?: body,
                alertId = data["alert_id"]?.toLongOrNull() ?: -1L,
            )
            // severity 없는 기존 페이로드 = 기존 경로(하위호환). NORMAL 도 동일.
            else -> if (type == "announcement") showAnnouncement(title, body)
        }
    }

    /**
     * S5' 안전 3등급 표시 — SafetyAlertActivity 로 TTS 읽어주기·진동·[확인] 처리.
     * EMERGENCY = 알람 사운드 채널(무음모드 우회) + 반복 진동 + 풀스크린. CAUTION = TTS 1회 + 진동.
     * 둘 다 풀스크린 인텐트로 확실히 [확인] 화면을 띄운다(백그라운드/잠금에서도).
     */
    private fun showSafety(severity: String, title: String, body: String,
                           ttsText: String, alertId: Long) {
        val emergency = severity == "EMERGENCY"
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channelId = if (emergency) EMERGENCY_CHANNEL_ID else CAUTION_CHANNEL_ID

        if (emergency) {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)                       // 알람 스트림 → 무음/DND 우회.
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            nm.createNotificationChannel(
                NotificationChannel(channelId, "긴급 안전알림", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "작업중지·응급 등 긴급 안전알림(무음모드 우회)"
                    setSound(alarmUri, attrs)
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 600, 400, 600, 400, 600)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    setBypassDnd(true)                                        // 정책 접근 권한 있으면 DND 우회.
                }
            )
        } else {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "안전 주의알림", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "휴식·수분 등 안전 주의알림"
                    enableVibration(true)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
            )
        }

        val fullScreen = Intent(this, SafetyAlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("title", title)
            putExtra("body", body)
            putExtra("tts_text", ttsText)
            putExtra("severity", severity)
            putExtra("alert_id", alertId)
        }
        val reqCode = if (alertId > 0) alertId.toInt() else title.hashCode()
        val pi = PendingIntent.getActivity(
            this, reqCode, fullScreen,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setPriority(Notification.PRIORITY_MAX)
            .setCategory(if (emergency) Notification.CATEGORY_ALARM else Notification.CATEGORY_REMINDER)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .setAutoCancel(true)
            .build()
        nm.notify(reqCode, notification)

        // 앱이 포그라운드일 때 즉시 띄우기.
        runCatching { startActivity(fullScreen) }
    }

    private fun showAnnouncement(title: String, body: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "현장 공지", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )

        val fullScreen = Intent(this, AnnouncementActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("title", title)
            putExtra("body", body)
        }
        val pi = PendingIntent.getActivity(
            this, title.hashCode(), fullScreen,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 시스템 알림 (풀스크린 인텐트 포함 — 잠금/백그라운드에서도 크게 뜸).
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setPriority(Notification.PRIORITY_HIGH)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .setAutoCancel(true)
            .build()
        nm.notify(System.currentTimeMillis().toInt(), notification)

        // 앱이 포그라운드일 때 즉시 풀스크린 띄우기.
        runCatching { startActivity(fullScreen) }
    }
}
