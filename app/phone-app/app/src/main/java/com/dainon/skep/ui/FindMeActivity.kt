package com.dainon.skep.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.dainon.skep.service.FindMeAlarmService

/**
 * P5-W2 파인드미 피재자 화면 — 검정/흰 배경 교차 점멸 + "구조 요청 중" + [구조자 도착·해제].
 * 화면 밝기 최대·화면 켜짐·잠금화면 위. 사이렌·토치·BLE 는 FindMeAlarmService 가 담당한다.
 * [해제] 또는 find_me_stop(서비스 종료 브로드캐스트) 시 화면도 닫는다.
 */
class FindMeActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var white = false
    private var root: LinearLayout? = null
    private var label: TextView? = null

    private val flash = object : Runnable {
        override fun run() {
            white = !white
            root?.setBackgroundColor(if (white) WHITE else BLACK)
            label?.setTextColor(if (white) BLACK else WHITE)
            handler.postDelayed(this, FLASH_MS)
        }
    }

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { finish() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true); setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply { screenBrightness = 1f }  // 최대 밝기.

        setContentView(buildView())
        ContextCompat.registerReceiver(
            this, stopReceiver,
            IntentFilter(FindMeAlarmService.ACTION_FIND_ME_STOPPED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        handler.post(flash)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun buildView(): LinearLayout {
        fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
        val ctx = this
        val lbl = TextView(ctx).apply {
            text = "구조 요청 중"
            textSize = 40f
            gravity = Gravity.CENTER
            setTextColor(WHITE)
        }
        label = lbl
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(BLACK)
            setPadding(dp(24), dp(24), dp(24), dp(24))
            root = this
            addView(lbl)
            addView(TextView(ctx).apply {
                text = "사이렌·플래시·비콘으로 구조를 요청하고 있습니다"
                textSize = 18f
                gravity = Gravity.CENTER
                setTextColor(WHITE)
                setPadding(0, dp(16), 0, dp(24))
            })
            addView(Button(ctx).apply {
                text = "구조자 도착 · 해제"
                textSize = 22f
                setOnClickListener { stopAll() }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(88)
                )
            })
        }
    }

    private fun stopAll() {
        FindMeAlarmService.stop(this)
        finish()
    }

    /** 파인드미는 [해제] 버튼으로만 종료 — 실수로 닫혀 사이렌만 남는 것을 방지. */
    override fun onBackPressed() { /* no-op */ }

    override fun onDestroy() {
        handler.removeCallbacks(flash)
        runCatching { unregisterReceiver(stopReceiver) }
        super.onDestroy()
    }

    companion object {
        private const val WHITE = 0xFFFFFFFF.toInt()
        private const val BLACK = 0xFF000000.toInt()
        private const val FLASH_MS = 450L
    }
}
