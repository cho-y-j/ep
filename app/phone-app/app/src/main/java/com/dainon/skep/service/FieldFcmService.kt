package com.dainon.skep.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import com.dainon.skep.net.FieldApi
import com.dainon.skep.net.Prefs
import com.dainon.skep.ui.AnnouncementActivity
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
        val type = message.data["type"] ?: message.notification?.let { "announcement" } ?: return
        val title = message.data["title"] ?: message.notification?.title ?: "공지"
        val body = message.data["body"] ?: message.notification?.body ?: ""
        Log.d(TAG, "push [$type] $title")

        if (type == "announcement") showAnnouncement(title, body)
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
