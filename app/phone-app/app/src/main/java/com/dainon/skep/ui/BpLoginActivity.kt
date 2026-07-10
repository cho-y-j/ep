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

/** 발주사(BP) 로그인 — 웹 아이디/비번 → POST /api/auth/login → BpMainActivity. */
class BpLoginActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bp_login)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        tvStatus = findViewById(R.id.tvStatus)
        btnLogin.setOnClickListener { login() }
        // 테스트 빠른 로그인 (비번 test1234) — 디버그 빌드에서만 노출.
        val btnQuickBp = findViewById<Button>(R.id.btnQuickBp)
        val btnQuickEq = findViewById<Button>(R.id.btnQuickEq)
        val btnQuickMp = findViewById<Button>(R.id.btnQuickMp)
        if (BuildConfig.DEBUG) {
            fun quick(email: String) { etEmail.setText(email); etPassword.setText("test1234"); login() }
            btnQuickBp.setOnClickListener { quick("bp1@example.com") }
            btnQuickEq.setOnClickListener { quick("equipment1@example.com") }
            btnQuickMp.setOnClickListener { quick("manpower1@example.com") }
        } else {
            btnQuickBp.visibility = View.GONE
            btnQuickEq.visibility = View.GONE
            btnQuickMp.visibility = View.GONE
        }
    }

    private fun login() {
        val email = etEmail.text.toString().trim()
        val pw = etPassword.text.toString()
        if (email.isEmpty() || pw.isEmpty()) {
            Toast.makeText(this, "아이디/비밀번호를 입력하세요", Toast.LENGTH_SHORT).show(); return
        }
        btnLogin.isEnabled = false
        tvStatus.text = "로그인 중..."
        tvStatus.setTextColor(0xFF64748B.toInt())
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val api = FieldApi(Prefs.serverUrl(this@BpLoginActivity))
                    val token = api.bpLogin(email, pw)
                    val me = runCatching { api.bpMe(token).user }.getOrNull()
                    Triple(token, me?.name, me?.role)
                }
            }
            r.onSuccess { (token, name, role) ->
                Prefs.saveBp(this@BpLoginActivity, token, name, role)
                val target = if (role == "EQUIPMENT_SUPPLIER" || role == "MANPOWER_SUPPLIER")
                    SupplierMainActivity::class.java else BpMainActivity::class.java
                startActivity(Intent(this@BpLoginActivity, target)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                finish()
            }.onFailure {
                tvStatus.text = "로그인 실패 — 아이디/비밀번호를 확인하세요"
                tvStatus.setTextColor(0xFFE53935.toInt())
                btnLogin.isEnabled = true
            }
        }
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
