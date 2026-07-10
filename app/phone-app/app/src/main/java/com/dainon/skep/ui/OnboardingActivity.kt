package com.dainon.skep.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.dainon.skep.BuildConfig
import com.dainon.skep.R
import com.dainon.skep.net.FieldApi
import com.dainon.skep.net.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 현장 작업자 로그인 — 공급사가 발급한 아이디/비번 → POST /api/field-auth/login → MainActivity. */
class OnboardingActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Prefs.isRegistered(this)) {
            startActivity(Intent(this, MainActivity::class.java)); finish(); return
        }
        setContentView(R.layout.activity_onboarding)
        etUsername = findViewById(R.id.etUsername)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        tvStatus = findViewById(R.id.tvStatus)
        btnLogin.setOnClickListener { login() }
        // 테스트 자동입력 — 디버그 빌드에서만 노출.
        val btnQuick = findViewById<Button>(R.id.btnQuickWorker)
        if (BuildConfig.DEBUG) {
            btnQuick.setOnClickListener {
                etUsername.setText("worker"); etPassword.setText("test1234"); login()
            }
        } else {
            btnQuick.visibility = View.GONE
        }
    }

    private fun login() {
        val u = etUsername.text.toString().trim()
        val p = etPassword.text.toString()
        if (u.isEmpty() || p.isEmpty()) {
            Toast.makeText(this, "아이디/비밀번호를 입력하세요", Toast.LENGTH_SHORT).show(); return
        }
        btnLogin.isEnabled = false
        tvStatus.text = "로그인 중..."
        tvStatus.setTextColor(0xFF64748B.toInt())
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching { FieldApi(Prefs.serverUrl(this@OnboardingActivity)).workerLogin(u, p) }
            }
            r.onSuccess { resp ->
                Prefs.saveAuth(this@OnboardingActivity, resp.personId, resp.token, resp.name)
                com.dainon.skep.net.WatchLink.pushIdentity(this@OnboardingActivity)
                startActivity(Intent(this@OnboardingActivity, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                finish()
            }.onFailure {
                tvStatus.text = "아이디 또는 비밀번호가 올바르지 않습니다"
                tvStatus.setTextColor(0xFFE53935.toInt())
                btnLogin.isEnabled = true
            }
        }
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
