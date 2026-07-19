package com.dainon.skep.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.dainon.skep.net.Prefs
import com.dainon.skep.safety.Strobe
import com.dainon.skep.ui.FindMeActivity
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * P5-W2 파인드미(Find-Me) — 피재자 폰 자가 발동 foreground 서비스(특허 §5.6).
 * 동시에: ① 최대음량 사이렌(USAGE_ALARM·무음모드 우회) ② 카메라 토치 스트로브 ~4Hz
 *         ③ BLE 광고 송출(응답자 근접 게이지용) ④ 풀스크린 FindMeActivity.
 * 지속 조건: [해제] 버튼(FindMeActivity) 또는 find_me_stop FCM(FindMeAlarmService.stop) 뿐.
 * 배터리는 제한하지 않는다 — 사건은 분 단위(특허 §5.6 취지).
 */
class FindMeAlarmService : Service() {

    companion object {
        const val TAG = "FindMe"
        /** BLE SOS 광고 service UUID(고정 상수·서버/응답자와 공유 계약). */
        const val SERVICE_UUID = "8f7e0001-a2b3-4c5d-9e8f-102030405060"
        const val ACTION_STOP = "com.dainon.skep.action.FIND_ME_STOP"
        /** 서비스 종료를 FindMeActivity 에 알려 화면도 닫게 하는 앱-내부 브로드캐스트. */
        const val ACTION_FIND_ME_STOPPED = "com.dainon.skep.action.FIND_ME_STOPPED"

        private const val CHANNEL_ID = "skep_find_me"
        private const val NOTIF_ID = 996
        private const val EXTRA_ALERT_ID = "alert_id"
        private const val EXTRA_PERSON_ID = "person_id"

        @Volatile
        var isRunning = false
            private set

        /** 파인드미 시작(피재자 폰). alertId 미상이면 -1(로컬 자가발동), personId 미상이면 Prefs 폴백. */
        fun start(ctx: Context, alertId: Long, personId: Long) {
            val i = Intent(ctx, FindMeAlarmService::class.java)
                .putExtra(EXTRA_ALERT_ID, alertId)
                .putExtra(EXTRA_PERSON_ID, personId)
            ContextCompat.startForegroundService(ctx, i)
        }

        /** 파인드미 해제(find_me_stop FCM·[해제] 버튼). 실행 중이 아니면 무시(백그라운드 start 예외 회피). */
        fun stop(ctx: Context) {
            if (!isRunning) return
            ctx.startService(Intent(ctx, FindMeAlarmService::class.java).setAction(ACTION_STOP))
        }
    }

    private val strobeHandler = Handler(Looper.getMainLooper())
    private var cameraManager: CameraManager? = null
    private var torchCameraId: String? = null
    private var strobeTick = 0
    private var mediaPlayer: MediaPlayer? = null
    private val sirenLock = Any()
    /** sirenLock 가드. 정지 후 뒤늦게 깨어난 사이렌 스레드의 start 를 차단하는 생명주기 플래그. */
    private var stopped = false
    private var bleAdvertiser: BluetoothLeAdvertiser? = null

    private val strobeRunnable = object : Runnable {
        override fun run() {
            val on = Strobe.isOnAt(strobeTick++)
            runCatching { torchCameraId?.let { cameraManager?.setTorchMode(it, on) } }
            strobeHandler.postDelayed(this, Strobe.INTERVAL_MS)
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartFailure(errorCode: Int) { Log.e(TAG, "BLE advertise failed: $errorCode") }
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) { Log.i(TAG, "BLE advertising") }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (isRunning) return START_STICKY
        isRunning = true

        val alertId = intent?.getLongExtra(EXTRA_ALERT_ID, -1L) ?: -1L
        val personId = intent?.getLongExtra(EXTRA_PERSON_ID, -1L)?.takeIf { it > 0 }
            ?: Prefs.workerId(this)?.toLongOrNull() ?: -1L

        startForegroundNotification()
        startSiren()
        startStrobe()
        startBleAdvertising(alertId, personId)
        launchFindMeActivity()
        return START_STICKY
    }

    private fun startForegroundNotification() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "구조 요청(파인드미)", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "사이렌·플래시·비콘으로 구조를 요청 중"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
        val open = Intent(this, FindMeActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(
            this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("구조 요청 중")
            .setContentText("사이렌·플래시·비콘 작동 중 — 화면에서 해제할 수 있습니다")
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_ALARM)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .build()
        // connectedDevice 타입: 앱이 이미 보유한 NFC(normal 권한)로 전제조건 충족 → BLE 권한 미허가여도 시작 성공.
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        runCatching { ServiceCompat.startForeground(this, NOTIF_ID, notif, type) }
            .onFailure { Log.e(TAG, "startForeground failed: ${it.message}") }
    }

    /** 최대음량 알람 사운드 반복(무음모드 우회). prepare 블로킹 회피 위해 별도 스레드. */
    private fun startSiren() {
        Thread {
            runCatching {
                val am = getSystemService(AUDIO_SERVICE) as AudioManager
                am.setStreamVolume(
                    AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0
                )
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                val mp = MediaPlayer()
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                mp.setDataSource(this, uri)
                mp.isLooping = true
                mp.prepare()  // 블로킹(수백 ms) — 락 밖에서 수행
                // prepare 중 stopSiren 이 왔을 수 있다. 대입·start·정지판정을 원자화해 레이스 창을 닫는다.
                synchronized(sirenLock) {
                    if (stopped) {
                        mp.release()          // 이미 정지됨 → 재생하지 않고 폐기
                    } else {
                        mediaPlayer = mp      // start 전에 대입 — 정지 경로가 항상 이 인스턴스를 참조
                        mp.start()
                    }
                }
            }.onFailure { Log.e(TAG, "siren failed: ${it.message}") }
        }.start()
    }

    private fun stopSiren() {
        synchronized(sirenLock) {
            stopped = true                       // 먼저 세팅 — 아직 대입 전인 스레드도 start 를 포기
            runCatching { mediaPlayer?.stop() }
            runCatching { mediaPlayer?.release() }
            mediaPlayer = null
        }
    }

    /** 토치 스트로브 — setTorchMode 는 CAMERA 권한 불요. 플래시 있는 카메라 하나 선택. */
    private fun startStrobe() {
        runCatching {
            val cm = getSystemService(CAMERA_SERVICE) as CameraManager
            val id = cm.cameraIdList.firstOrNull {
                cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: run { Log.w(TAG, "no flash camera — strobe skip"); return }
            cameraManager = cm
            torchCameraId = id
            strobeTick = 0
            strobeHandler.post(strobeRunnable)
        }.onFailure { Log.e(TAG, "strobe start failed: ${it.message}") }
    }

    private fun stopStrobe() {
        strobeHandler.removeCallbacks(strobeRunnable)
        runCatching { torchCameraId?.let { cameraManager?.setTorchMode(it, false) } }
    }

    /** BLE 광고 — service UUID(메인) + 스캔응답에 payload(alertId int32 BE + personId int32 BE). */
    @SuppressLint("MissingPermission")
    private fun startBleAdvertising(alertId: Long, personId: Long) {
        if (!hasAdvertisePermission()) { Log.w(TAG, "no BLUETOOTH_ADVERTISE — beacon skip"); return }
        val adapter = (getSystemService(BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        if (adapter == null || !adapter.isEnabled) { Log.w(TAG, "BT off — beacon skip"); return }
        val advertiser = adapter.bluetoothLeAdvertiser
            ?: run { Log.w(TAG, "no LE advertiser (peripheral unsupported) — beacon skip"); return }

        val uuid = ParcelUuid(UUID.fromString(SERVICE_UUID))
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .build()
        // 메인 광고엔 UUID 만(ScanFilter=UUID 로 매칭). payload 는 31B 초과 회피 위해 스캔응답에.
        val advData = AdvertiseData.Builder().addServiceUuid(uuid).build()
        val payload = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
            .putInt(alertId.toInt()).putInt(personId.toInt()).array()
        val scanResp = AdvertiseData.Builder().addServiceData(uuid, payload).build()
        runCatching {
            advertiser.startAdvertising(settings, advData, scanResp, advertiseCallback)
            bleAdvertiser = advertiser
        }.onFailure { Log.e(TAG, "advertise start failed: ${it.message}") }
    }

    @SuppressLint("MissingPermission")
    private fun stopBleAdvertising() {
        runCatching { bleAdvertiser?.stopAdvertising(advertiseCallback) }
        bleAdvertiser = null
    }

    private fun hasAdvertisePermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) ==
            PackageManager.PERMISSION_GRANTED

    private fun launchFindMeActivity() {
        val i = Intent(this, FindMeActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        runCatching { startActivity(i) }
    }

    override fun onDestroy() {
        stopStrobe()
        stopSiren()
        stopBleAdvertising()
        isRunning = false
        runCatching { sendBroadcast(Intent(ACTION_FIND_ME_STOPPED).setPackage(packageName)) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
