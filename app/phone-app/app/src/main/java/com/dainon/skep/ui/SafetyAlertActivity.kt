package com.dainon.skep.ui

import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.dainon.skep.net.FieldApi
import com.dainon.skep.net.Prefs
import java.util.Locale

/**
 * S5' 안전알림 표시 — TTS 읽어주기 + 진동 + [확인] 대형 버튼(확인응답 루프).
 * EMERGENCY = 잠금화면 위 + 화면 깨움 + 반복 진동 + 알람볼륨 최대(무음모드 우회는 채널 알람 사운드).
 * CAUTION   = 1회 진동 + TTS 1회.
 * [확인] → POST /api/field-auth/safety-alerts/{id}/ack. TTS 재생 시각은 로컬 로그(증거 보조).
 */
class SafetyAlertActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var vibrator: Vibrator? = null
    private var ttsText: String = ""
    private var emergency: Boolean = false
    private var alertId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        emergency = intent.getStringExtra("severity") == "EMERGENCY"
        ttsText = intent.getStringExtra("tts_text") ?: (intent.getStringExtra("body") ?: "")
        alertId = intent.getLongExtra("alert_id", -1L)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true); setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        // 긴급은 알람 볼륨 최대(무음모드 우회 보조 — 채널 사운드가 알람 스트림).
        if (emergency) runCatching {
            val am = getSystemService(AUDIO_SERVICE) as AudioManager
            am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        }

        setContentView(buildView(
            intent.getStringExtra("title") ?: "안전 알림",
            intent.getStringExtra("body") ?: ttsText,
        ))

        vibrator = resolveVibrator()
        startVibration()
        tts = TextToSpeech(this, this)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.KOREAN
            tts?.speak(ttsText, TextToSpeech.QUEUE_FLUSH, null, "safety-$alertId")
            Log.i(TAG, "TTS played at ${System.currentTimeMillis()} alert=$alertId text=$ttsText")
        } else {
            Log.w(TAG, "TTS init failed status=$status")
        }
    }

    /** [확인] → 서버 ack. 성공/실패와 무관하게 화면은 닫는다(로컬 로그 보존). */
    private fun ack() {
        stopAll()
        val token = Prefs.token(this)
        if (token == null || alertId <= 0) { finish(); return }
        Thread {
            runCatching { FieldApi(Prefs.serverUrl(this)).ackSafetyAlert(token, alertId) }
                .onSuccess { Log.i(TAG, "ack ok alert=$alertId") }
                .onFailure { Log.e(TAG, "ack failed alert=$alertId: ${it.message}") }
            runOnUiThread { finish() }
        }.start()
    }

    private fun startVibration() {
        val v = vibrator ?: return
        if (emergency) {
            val pattern = longArrayOf(0, 600, 400, 600, 400, 600)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                v.vibrate(VibrationEffect.createWaveform(pattern, 0))  // 0=반복.
            else @Suppress("DEPRECATION") v.vibrate(pattern, 0)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                v.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            else @Suppress("DEPRECATION") v.vibrate(500)
        }
    }

    private fun stopAll() {
        runCatching { vibrator?.cancel() }
        runCatching { tts?.stop() }
    }

    private fun resolveVibrator(): Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        else @Suppress("DEPRECATION") getSystemService(VIBRATOR_SERVICE) as Vibrator

    private fun buildView(title: String, body: String): LinearLayout {
        val bg = if (emergency) 0xFFB91C1C.toInt() else 0xFFB45309.toInt()  // rose-700 / amber-700.
        fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
        val ctx = this
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(bg)
            setPadding(dp(28), dp(28), dp(28), dp(28))
            addView(TextView(ctx).apply {
                text = if (emergency) "긴급" else "주의"
                textSize = 18f; setTextColor(0xFFFFFFFF.toInt()); gravity = Gravity.CENTER
            })
            addView(TextView(ctx).apply {
                text = title
                textSize = 30f; setTextColor(0xFFFFFFFF.toInt()); gravity = Gravity.CENTER
                setPadding(0, dp(16), 0, dp(12))
            })
            addView(TextView(ctx).apply {
                text = body
                textSize = 20f; setTextColor(0xFFFFFFFF.toInt()); gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            })
            addView(Button(ctx).apply {
                text = "확인"
                textSize = 22f
                setOnClickListener { ack() }
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(88)).apply {
                    topMargin = dp(20)
                }
            })
        }
    }

    override fun onDestroy() {
        stopAll()
        tts?.shutdown()
        super.onDestroy()
    }

    companion object { const val TAG = "SafetyAlert" }
}
