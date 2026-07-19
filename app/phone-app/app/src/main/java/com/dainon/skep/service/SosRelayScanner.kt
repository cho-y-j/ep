package com.dainon.skep.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.UUID

/**
 * P5-W3 제3자 SOS 대리중계 — OS 위임 백그라운드 BLE 스캔 등록/해제(특허 §5.7).
 * startScan(filters, settings, PendingIntent) 방식이라 상시 포그라운드 서비스 없이 OS 가 매칭 시에만
 * SosRelayReceiver 를 깨운다(저전력). 등록/해제는 동일 PendingIntent 로 멱등하다.
 */
object SosRelayScanner {
    const val TAG = "SosRelay"
    const val ACTION_MATCH = "com.dainon.skep.action.SOS_RELAY_MATCH"
    private const val REQ = 771

    /** 로그인 상태에서 스캔 등록. 권한·BT 미비면 조용히 skip(다음 진입/부팅에 재시도). 멱등. */
    @SuppressLint("MissingPermission")
    fun register(ctx: Context) {
        if (!hasScanPermission(ctx)) { Log.w(TAG, "no BLE scan permission — relay scan skip"); return }
        val scanner = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
            ?.adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner
            ?: run { Log.w(TAG, "BT off / no scanner — relay scan skip"); return }

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(UUID.fromString(FindMeAlarmService.SERVICE_UUID)))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .build()
        runCatching { scanner.startScan(listOf(filter), settings, pendingIntent(ctx)) }
            .onSuccess { Log.i(TAG, "relay scan registered") }
            .onFailure { Log.e(TAG, "relay scan register failed: ${it.message}") }
    }

    /** 로그아웃 시 해제. */
    @SuppressLint("MissingPermission")
    fun unregister(ctx: Context) {
        val scanner = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
            ?.adapter?.bluetoothLeScanner ?: return
        runCatching { scanner.stopScan(pendingIntent(ctx)) }
            .onFailure { Log.e(TAG, "relay scan unregister failed: ${it.message}") }
    }

    /** 스캔 결과를 SosRelayReceiver 로 전달. OS 가 결과 extra 를 채우므로 S+ 에선 MUTABLE 필수. */
    private fun pendingIntent(ctx: Context): PendingIntent {
        val intent = Intent(ctx, SosRelayReceiver::class.java).setAction(ACTION_MATCH)
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags = flags or PendingIntent.FLAG_MUTABLE
        return PendingIntent.getBroadcast(ctx, REQ, intent, flags)
    }

    private fun hasScanPermission(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED
        else
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
}
