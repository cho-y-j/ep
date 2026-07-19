package com.dainon.skep.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import com.dainon.skep.net.FieldApi
import com.dainon.skep.net.Prefs
import com.dainon.skep.safety.SosPayloadParser
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Tasks
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * P5-W3 제3자 SOS 대리중계 수신 — SosRelayScanner 가 등록한 PendingIntent 스캔의 매칭 브로드캐스트 타깃(특허 §5.7).
 * 결과 serviceData(SosPayloadParser) → 마지막 위치 첨부 → POST /api/field-auth/sos-relay(X-Field-Token=중계자).
 * 동일 victim 5분 스로틀(SharedPreferences)로 폭풍 중계 방지. 서버가 중복 dedupe 담당. 성공/실패 로그만.
 * 미구현(404) 등엔 사용자에게 표출하지 않는다(중계자는 대응 의무자가 아님).
 */
class SosRelayReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getIntExtra(BluetoothLeScanner.EXTRA_ERROR_CODE, 0) != 0) {
            Log.e(TAG, "relay scan error: ${intent.getIntExtra(BluetoothLeScanner.EXTRA_ERROR_CODE, 0)}")
            return
        }
        @Suppress("DEPRECATION")
        val results = intent.getParcelableArrayListExtra<ScanResult>(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT)
            ?: return

        // 여러 결과 중 파싱되고 personId 유효한 것 중 신호 가장 강한 하나만 중계(서버가 dedupe).
        val best = results
            .mapNotNull { r -> SosPayloadParser.parse(serviceData(r))?.takeIf { it.personId > 0 }?.let { it to r.rssi } }
            .maxByOrNull { it.second } ?: return
        val (payload, rssi) = best

        val token = Prefs.token(context) ?: return          // 미로그인 → 중계 안 함.
        if (isThrottled(context, payload.personId)) return   // 5분 내 동일 victim 재중계 방지.
        markRelayed(context, payload.personId)               // 실패해도 폭풍 방지 위해 시도 시점에 기록.

        val pending = goAsync()
        Thread {
            try {
                val loc = lastLocation(context)
                val ok = FieldApi(Prefs.serverUrl(context))
                    .sosRelay(token, payload.personId, payload.alertId, rssi, loc?.first, loc?.second)
                if (ok) {
                    Log.i(TAG, "relayed SOS victim=${payload.personId} alert=${payload.alertId} rssi=$rssi")
                    notifyQuiet(context)
                } else {
                    Log.w(TAG, "relay POST not accepted (server may not implement yet) victim=${payload.personId}")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "relay failed: ${t.message}")
            } finally {
                pending.finish()
            }
        }.start()
    }

    private fun serviceData(r: ScanResult): ByteArray? =
        r.scanRecord?.getServiceData(ParcelUuid(UUID.fromString(FindMeAlarmService.SERVICE_UUID)))

    /** 마지막 위치(있으면). 권한 없거나 조회 실패면 null → 서버엔 relay_lat/lng=null. */
    @SuppressLint("MissingPermission")
    private fun lastLocation(ctx: Context): Pair<Double, Double>? {
        if (!hasLocationPermission(ctx)) return null
        return runCatching {
            val loc = Tasks.await(
                LocationServices.getFusedLocationProviderClient(ctx).lastLocation, 3, TimeUnit.SECONDS
            ) ?: return null
            loc.latitude to loc.longitude
        }.getOrNull()
    }

    private fun hasLocationPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun isThrottled(ctx: Context, personId: Int): Boolean {
        val last = prefs(ctx).getLong(key(personId), 0L)
        return System.currentTimeMillis() - last < THROTTLE_MS
    }

    private fun markRelayed(ctx: Context, personId: Int) {
        prefs(ctx).edit().putLong(key(personId), System.currentTimeMillis()).apply()
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
    private fun key(personId: Int) = "v_$personId"

    /** 중계 성공 알림(조용히) — 소리·진동 없음. 중계자는 대응 의무자가 아니므로 불안 조성 금지. */
    private fun notifyQuiet(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "SOS 대리중계", NotificationManager.IMPORTANCE_LOW).apply {
                description = "근처 SOS 를 관제로 중계했을 때의 조용한 알림"
                setSound(null, null)
                enableVibration(false)
            }
        )
        val notif = Notification.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("SOS 대리중계")
            .setContentText("근처 SOS를 관제로 중계했습니다")
            .setCategory(Notification.CATEGORY_STATUS)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(NOTIF_ID, notif) }
    }

    companion object {
        const val TAG = "SosRelay"
        private const val PREFS_FILE = "skep_sos_relay"
        private const val THROTTLE_MS = 5 * 60 * 1000L
        private const val CHANNEL_ID = "skep_sos_relay"
        private const val NOTIF_ID = 995
    }
}
