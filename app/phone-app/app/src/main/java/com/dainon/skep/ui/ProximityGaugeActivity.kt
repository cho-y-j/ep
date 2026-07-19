package com.dainon.skep.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.dainon.skep.safety.RssiMapper
import com.dainon.skep.service.FindMeAlarmService
import java.util.UUID

/**
 * P5-W2 근접 게이지(응답자) — 파인드미 BLE 광고(service UUID)를 스캔해 RSSI→게이지·구간 진동으로
 * 위성측위 무력한 실내·야간 최종 접근을 안내(특허 §5.6). RSSI→게이지 매핑은 RssiMapper(순수·단위테스트).
 * 실기기 전 실광고/실스캔 검증 불가 — 매핑·상태 로직만 코드 수준으로 확정.
 */
class ProximityGaugeActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var scanner: BluetoothLeScanner? = null
    private var vibrator: Vibrator? = null

    private var gauge: ProgressBar? = null
    private var percentView: TextView? = null
    private var statusView: TextView? = null

    @Volatile private var lastSignalAt = 0L
    private var lastVibeAt = 0L

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) { onRssi(result.rssi) }
        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.maxByOrNull { it.rssi }?.let { onRssi(it.rssi) }
        }
        override fun onScanFailed(errorCode: Int) { Log.e(TAG, "scan failed: $errorCode") }
    }

    /** NO_SIGNAL_MS 이상 수신 없으면 "신호 없음". */
    private val watchdog = object : Runnable {
        override fun run() {
            if (System.currentTimeMillis() - lastSignalAt > NO_SIGNAL_MS) {
                gauge?.progress = 0
                percentView?.text = "-"
                statusView?.text = "신호 없음 — 피재자 방향으로 이동하세요"
            }
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildView(intent.getStringExtra("victim_name") ?: "동료"))
        vibrator = resolveVibrator()
        if (hasScanPermission()) startScan()
        else ActivityCompat.requestPermissions(this, scanPermissions(), REQ_SCAN)
    }

    private fun onRssi(rssi: Int) {
        lastSignalAt = System.currentTimeMillis()
        val pct = RssiMapper.gaugePercent(rssi)
        runOnUiThread {
            gauge?.progress = pct
            percentView?.text = "$pct%"
            statusView?.text = "신호 감지 ($rssi dBm)"
        }
        maybeVibrate(pct)
    }

    /** 구간별 진동 — 가까울수록 강하고 자주(단, throttle 로 연속 진동 방지). */
    private fun maybeVibrate(pct: Int) {
        val level = RssiMapper.vibrationLevel(pct)
        if (level == 0) return
        val now = System.currentTimeMillis()
        val interval = when (level) { 3 -> 400L; 2 -> 800L; else -> 1500L }
        if (now - lastVibeAt < interval) return
        lastVibeAt = now
        val ms = when (level) { 3 -> 250L; 2 -> 150L; else -> 80L }
        val v = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        else @Suppress("DEPRECATION") v.vibrate(ms)
    }

    @SuppressLint("MissingPermission")
    private fun startScan() {
        val adapter = (getSystemService(BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        if (adapter == null || !adapter.isEnabled) {
            statusView?.text = "블루투스를 켜주세요"
            return
        }
        scanner = adapter.bluetoothLeScanner
        if (scanner == null) { statusView?.text = "BLE 스캔 미지원 단말"; return }
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(UUID.fromString(FindMeAlarmService.SERVICE_UUID)))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        runCatching {
            scanner?.startScan(listOf(filter), settings, scanCallback)
            lastSignalAt = System.currentTimeMillis()
            handler.postDelayed(watchdog, 1000L)
        }.onFailure { Log.e(TAG, "startScan failed: ${it.message}"); statusView?.text = "스캔 시작 실패" }
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        handler.removeCallbacks(watchdog)
        runCatching { scanner?.stopScan(scanCallback) }
        scanner = null
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_SCAN) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) startScan()
            else statusView?.text = "블루투스 스캔 권한이 필요합니다"
        }
    }

    private fun buildView(victimName: String): LinearLayout {
        fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
        val ctx = this
        val bar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progress = 0
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(28))
                .apply { topMargin = dp(20) }
        }
        gauge = bar
        val pct = TextView(ctx).apply {
            text = "-"; textSize = 44f; gravity = Gravity.CENTER; setTextColor(0xFFFFFFFF.toInt())
        }
        percentView = pct
        val status = TextView(ctx).apply {
            text = "신호 탐색 중..."; textSize = 18f; gravity = Gravity.CENTER; setTextColor(0xFFE5E7EB.toInt())
            setPadding(0, dp(12), 0, 0)
        }
        statusView = status
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF111827.toInt())  // slate-900.
            setPadding(dp(24), dp(24), dp(24), dp(24))
            addView(TextView(ctx).apply {
                text = "${victimName}님에게 접근 중"
                textSize = 20f; gravity = Gravity.CENTER; setTextColor(0xFFFFFFFF.toInt())
            })
            addView(pct)
            addView(bar)
            addView(status)
            addView(Button(ctx).apply {
                text = "종료"
                textSize = 18f
                setOnClickListener { finish() }
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64))
                    .apply { topMargin = dp(32) }
            })
        }
    }

    private fun hasScanPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED
        else
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    private fun scanPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun resolveVibrator(): Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        else @Suppress("DEPRECATION") getSystemService(VIBRATOR_SERVICE) as Vibrator

    override fun onDestroy() {
        stopScan()
        super.onDestroy()
    }

    companion object {
        const val TAG = "ProximityGauge"
        private const val REQ_SCAN = 210
        private const val NO_SIGNAL_MS = 4000L
    }
}
