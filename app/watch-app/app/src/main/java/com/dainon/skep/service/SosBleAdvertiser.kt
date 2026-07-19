package com.dainon.skep.service

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import java.util.UUID

/**
 * P5-W3 통신불능 폴백 — 워치 BLE SOS 광고(특허 §5.7).
 * 폰 전달이 실패한 EMERGENCY 에서만 송출: 제3자 폰(SosRelayScanner)이 UUID 로 매칭 →
 * serviceData(alertId=-1, personId)를 읽어 서버로 대리 중계. 폰이 붙어 있으면 폰이 광고를
 * 담당하므로(FindMeAlarmService) 워치는 송출하지 않는다.
 *
 * 광고 배치는 폰 FindMeAlarmService 와 동일: 메인=UUID(제3자 폰 ScanFilter 매칭),
 * 스캔응답=serviceData(payload). 31B 초과 회피 위해 payload 는 스캔응답에 싣는다.
 * 파라미터 LOW_LATENCY+HIGH power — 구조 상황이라 배터리를 아끼지 않는다(특허 §5.6·§5.7 취지).
 */
object SosBleAdvertiser {

    private const val TAG = "SosBle"

    private var advertiser: BluetoothLeAdvertiser? = null

    @Volatile
    var isRunning = false
        private set

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.i(TAG, "SOS advertising")
        }
        override fun onStartFailure(errorCode: Int) {
            isRunning = false
            Log.e(TAG, "SOS advertise failed: $errorCode")
        }
    }

    /** 통신불능 EMERGENCY 시 SOS 광고 시작(멱등). personId 미상이면 -1. */
    @SuppressLint("MissingPermission")
    fun start(context: Context, personId: Int) {
        if (isRunning) return
        if (!hasAdvertisePermission(context)) { Log.w(TAG, "no BLUETOOTH_ADVERTISE — SOS skip"); return }
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        if (adapter == null || !adapter.isEnabled) { Log.w(TAG, "BT off — SOS skip"); return }
        val adv = adapter.bluetoothLeAdvertiser
            ?: run { Log.w(TAG, "no LE advertiser (peripheral 미지원) — SOS skip"); return }

        val uuid = ParcelUuid(UUID.fromString(SosPayload.SERVICE_UUID))
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .setTimeout(0)
            .build()
        val advData = AdvertiseData.Builder().addServiceUuid(uuid).build()
        val payload = SosPayload.encode(SosPayload.WATCH_ALERT_ID, personId)
        val scanResp = AdvertiseData.Builder().addServiceData(uuid, payload).build()
        runCatching {
            adv.startAdvertising(settings, advData, scanResp, advertiseCallback)
            advertiser = adv
            isRunning = true
            Log.w(TAG, "🆘 SOS advertise started (person=$personId)")
        }.onFailure { Log.e(TAG, "SOS advertise start failed: ${it.message}") }
    }

    /** SOS 광고 중지(멱등) — EMERGENCY 해제·폰 재연결 시. */
    @SuppressLint("MissingPermission")
    fun stop() {
        if (!isRunning) return
        runCatching { advertiser?.stopAdvertising(advertiseCallback) }
        advertiser = null
        isRunning = false
        Log.d(TAG, "SOS advertise stopped")
    }

    private fun hasAdvertisePermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) ==
            PackageManager.PERMISSION_GRANTED
}
