package com.dainon.skep.ui

import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.dainon.skep.R

/**
 * 공지 풀스크린 — FCM data.type=announcement 수신 시 표시.
 * 잠금화면 위 + 화면 깨움(manifest: showWhenLocked/turnScreenOn).
 */
class AnnouncementActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // API 26 이하 호환: 코드로도 잠금화면 위/화면 켜기 지정.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        setContentView(R.layout.activity_announcement)
        bind(intent)

        findViewById<Button>(R.id.btnAnnConfirm).setOnClickListener { finish() }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        bind(intent)
    }

    private fun bind(intent: android.content.Intent) {
        findViewById<TextView>(R.id.tvAnnTitle).text = intent.getStringExtra("title") ?: "공지"
        findViewById<TextView>(R.id.tvAnnBody).text = intent.getStringExtra("body") ?: ""
    }
}
