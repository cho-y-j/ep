package com.dainon.skep.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.dainon.skep.net.FieldApi
import com.dainon.skep.net.Prefs
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * P5-W2 동료 긴급 호출(응답자) — FCM kind=peer_emergency 풀스크린(SafetyAlertActivity 패턴 재사용).
 * 피재자 이름·거리 표시 + [제가 갑니다](→respond API) + [지도 열기](geo:). [제가 갑니다] 후 근접 게이지로 전환.
 */
class PeerEmergencyActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var tts: TextToSpeech? = null
    private var vibrator: Vibrator? = null
    private lateinit var fused: FusedLocationProviderClient

    private var alertId = -1L
    private var victimName = "동료"
    private var lat: Double? = null
    private var lng: Double? = null
    private var distanceView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true); setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        alertId = intent.getLongExtra("alert_id", -1L)
        victimName = intent.getStringExtra("victim_name") ?: "동료"
        lat = intent.getDoubleExtra("lat", Double.NaN).takeIf { !it.isNaN() }
        lng = intent.getDoubleExtra("lng", Double.NaN).takeIf { !it.isNaN() }

        runCatching {
            val am = getSystemService(AUDIO_SERVICE) as AudioManager
            am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        }

        setContentView(buildView())
        vibrator = resolveVibrator()
        startVibration()
        tts = TextToSpeech(this, this)

        fused = LocationServices.getFusedLocationProviderClient(this)
        loadDistance()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.KOREAN
            tts?.speak("동료가 위급합니다. $victimName. 제가 갑니다를 눌러 응답하세요.",
                TextToSpeech.QUEUE_FLUSH, null, "peer-$alertId")
        }
    }

    private fun buildView(): LinearLayout {
        fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
        val ctx = this
        val dist = TextView(ctx).apply {
            text = "거리 계산 중..."
            textSize = 20f; gravity = Gravity.CENTER; setTextColor(0xFFFFFFFF.toInt())
        }
        distanceView = dist
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xFFB91C1C.toInt())  // rose-700.
            setPadding(dp(24), dp(24), dp(24), dp(24))
            addView(TextView(ctx).apply {
                text = "동료 긴급 호출"
                textSize = 18f; gravity = Gravity.CENTER; setTextColor(0xFFFFFFFF.toInt())
            })
            addView(TextView(ctx).apply {
                text = "${victimName}님 위급"
                textSize = 32f; gravity = Gravity.CENTER; setTextColor(0xFFFFFFFF.toInt())
                setPadding(0, dp(12), 0, dp(8))
            })
            addView(dist)
            addView(Button(ctx).apply {
                text = "제가 갑니다"
                textSize = 22f
                setOnClickListener { respondGoing() }
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(84))
                    .apply { topMargin = dp(28) }
            })
            addView(Button(ctx).apply {
                text = "지도 열기"
                textSize = 18f
                setOnClickListener { openMap() }
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64))
                    .apply { topMargin = dp(12) }
            })
        }
    }

    @SuppressLint("MissingPermission")
    private fun loadDistance() {
        val la = lat; val ln = lng
        if (la == null || ln == null) { distanceView?.text = "피재자 위치 정보 없음"; return }
        if (!hasLocationPermission()) { distanceView?.text = "내 위치 권한 없음 (지도로 이동)"; return }
        fused.lastLocation
            .addOnSuccessListener { loc ->
                if (loc == null) { distanceView?.text = "내 위치 확인 불가 (지도로 이동)"; return@addOnSuccessListener }
                val res = FloatArray(1)
                Location.distanceBetween(loc.latitude, loc.longitude, la, ln, res)
                distanceView?.text = "약 ${res[0].toInt()}m 거리"
            }
            .addOnFailureListener { distanceView?.text = "내 위치 확인 불가 (지도로 이동)" }
    }

    /** [제가 갑니다] → respond API(GOING). 서버 미구현/오류여도 graceful(재시도 1회+토스트+로그) 후 근접 게이지로. */
    private fun respondGoing() {
        stopAll()
        val token = Prefs.token(this)
        if (token == null || alertId <= 0) { goToGauge(); return }
        scope.launch {
            val ok = postGoing(token) || postGoing(token)  // 1회 재시도.
            if (ok) Log.i(TAG, "respond GOING ok alert=$alertId")
            else {
                Log.w(TAG, "respond GOING failed (server may not implement yet) alert=$alertId")
                Toast.makeText(this@PeerEmergencyActivity, "응답 전송 실패 — 계속 진행합니다", Toast.LENGTH_SHORT).show()
            }
            goToGauge()
        }
    }

    private suspend fun postGoing(token: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { FieldApi(Prefs.serverUrl(this@PeerEmergencyActivity)).respondSafetyAlert(token, alertId, "GOING") }
            .getOrDefault(false)
    }

    private fun goToGauge() {
        startActivity(
            android.content.Intent(this, ProximityGaugeActivity::class.java)
                .putExtra("alert_id", alertId)
                .putExtra("victim_name", victimName)
        )
        finish()
    }

    private fun openMap() {
        val la = lat; val ln = lng
        if (la == null || ln == null) { Toast.makeText(this, "피재자 위치 정보가 없습니다", Toast.LENGTH_SHORT).show(); return }
        val uri = Uri.parse("geo:$la,$ln?q=$la,$ln(${Uri.encode(victimName)})")
        runCatching { startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri)) }
            .onFailure { Toast.makeText(this, "지도 앱이 없습니다", Toast.LENGTH_SHORT).show() }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun startVibration() {
        val v = vibrator ?: return
        val pattern = longArrayOf(0, 600, 400, 600, 400, 600)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            v.vibrate(VibrationEffect.createWaveform(pattern, 0))
        else @Suppress("DEPRECATION") v.vibrate(pattern, 0)
    }

    private fun resolveVibrator(): Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        else @Suppress("DEPRECATION") getSystemService(VIBRATOR_SERVICE) as Vibrator

    private fun stopAll() {
        runCatching { vibrator?.cancel() }
        runCatching { tts?.stop() }
    }

    override fun onDestroy() {
        stopAll()
        tts?.shutdown()
        scope.cancel()
        super.onDestroy()
    }

    companion object { const val TAG = "PeerEmergency" }
}
