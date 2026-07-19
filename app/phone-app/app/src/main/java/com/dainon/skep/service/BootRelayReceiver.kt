package com.dainon.skep.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dainon.skep.net.Prefs

/**
 * P5-W3 부팅 후 SOS 대리중계 스캔 재등록 — PendingIntent 스캔은 재부팅 시 사라지므로 로그인 상태면 다시 건다(특허 §5.7).
 */
class BootRelayReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (Prefs.isRegistered(context)) SosRelayScanner.register(context)
    }
}
