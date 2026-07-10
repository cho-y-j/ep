package com.dainon.skep.ui

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.dainon.skep.R
import com.dainon.skep.net.FieldApi
import com.dainon.skep.net.Prefs
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 발주사(BP) 메인 — 서명 대기 작업확인서 + 받은 알림. FCM 토큰 등록. */
class BpMainActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var tvBpName: TextView
    private lateinit var wcContainer: LinearLayout
    private lateinit var tvWcEmpty: TextView
    private lateinit var notiContainer: LinearLayout
    private lateinit var tvNotiEmpty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Prefs.isBpLoggedIn(this)) { goEntry(); return }
        setContentView(R.layout.activity_bp_main)
        tvBpName = findViewById(R.id.tvBpName)
        wcContainer = findViewById(R.id.wcContainer)
        tvWcEmpty = findViewById(R.id.tvWcEmpty)
        notiContainer = findViewById(R.id.notiContainer)
        tvNotiEmpty = findViewById(R.id.tvNotiEmpty)
        tvBpName.text = (Prefs.bpName(this) ?: "발주사") + " 님"
        findViewById<Button>(R.id.btnLogout).setOnClickListener { logout() }
        findViewById<Button>(R.id.btnSites).setOnClickListener {
            startActivity(Intent(this, BpSitesActivity::class.java))
        }
        findViewById<Button>(R.id.btnInbox).setOnClickListener {
            startActivity(Intent(this, BpInboxActivity::class.java))
        }
        registerFcm()
    }

    override fun onResume() {
        super.onResume()
        loadWc()
        loadNoti()
    }

    private fun api() = FieldApi(Prefs.serverUrl(this))

    private fun loadWc() {
        val token = Prefs.bpToken(this) ?: return
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { api().listBpPendingWc(token) } }
            r.onSuccess { list ->
                wcContainer.removeAllViews()
                tvWcEmpty.visibility = if (list.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
                for (wc in list) {
                    val title = "${wc.workDate ?: "-"} · ${wc.personName ?: "-"}"
                    val sub = buildString {
                        wc.totalHours?.let { append("%.1f시간".format(it)) }
                        wc.wpTitle?.let { if (isNotEmpty()) append(" · "); append(it) }
                    }
                    addRow(wcContainer, title, sub, "서명하기 ›") { openSign(wc) }
                }
            }.onFailure {
                tvWcEmpty.text = "불러오기 실패: ${it.message}"
                tvWcEmpty.visibility = android.view.View.VISIBLE
            }
        }
    }

    private fun openSign(wc: FieldApi.BpWcItem) {
        val i = Intent(this, BpWcSignActivity::class.java)
        i.putExtra(BpWcSignActivity.EXTRA_WC_ID, wc.id)
        i.putExtra(BpWcSignActivity.EXTRA_LABEL, "${wc.workDate ?: "-"} · ${wc.personName ?: "-"}")
        startActivity(i)
    }

    private fun loadNoti() {
        val token = Prefs.bpToken(this) ?: return
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { api().listBpNotifications(token) } }
            r.onSuccess { list ->
                notiContainer.removeAllViews()
                tvNotiEmpty.visibility = if (list.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
                for (n in list) {
                    val sub = buildString {
                        n.message?.let { append(it) }
                        n.createdAt?.let { if (isNotEmpty()) append("\n"); append(it.take(16).replace("T", " ")) }
                    }
                    addRow(notiContainer, n.title ?: "알림", sub, null, null)
                }
            }
        }
    }

    private fun addRow(container: LinearLayout, title: String, subtitle: String?, action: String?, onClick: (() -> Unit)?) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xFFFFFFFF.toInt())
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
            if (onClick != null) setOnClickListener { onClick() }
        }
        val left = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        left.addView(TextView(this).apply {
            text = title; textSize = 14f; setTextColor(0xFF0F172A.toInt())
            setTypeface(typeface, Typeface.BOLD)
        })
        if (!subtitle.isNullOrBlank()) left.addView(TextView(this).apply {
            text = subtitle; textSize = 12f; setTextColor(0xFF64748B.toInt())
            setPadding(0, dp(2), 0, 0)
        })
        card.addView(left)
        if (action != null) card.addView(TextView(this).apply {
            text = action; textSize = 12f; setTextColor(0xFF2563EB.toInt())
            setTypeface(typeface, Typeface.BOLD)
        })
        container.addView(card)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun registerFcm() {
        val token = Prefs.bpToken(this) ?: return
        FirebaseMessaging.getInstance().token.addOnSuccessListener { fcm ->
            scope.launch(Dispatchers.IO) { runCatching { api().registerBpFcmToken(token, fcm) } }
        }
    }

    private fun logout() {
        Prefs.clearBp(this)
        goEntry()
    }

    private fun goEntry() {
        startActivity(Intent(this, EntryActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
