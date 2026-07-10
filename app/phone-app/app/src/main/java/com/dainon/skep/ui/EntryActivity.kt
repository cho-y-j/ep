package com.dainon.skep.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.dainon.skep.R
import com.dainon.skep.net.Prefs

/** 런처 — 이미 로그인돼 있으면 해당 메인으로, 아니면 현장 작업자 / 발주사(BP) 선택. */
class EntryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (route()) return
        setContentView(R.layout.activity_entry)
        findViewById<Button>(R.id.btnWorker).setOnClickListener {
            startActivity(Intent(this, OnboardingActivity::class.java))
        }
        findViewById<Button>(R.id.btnBp).apply {
            text = "회사 로그인 (BP · 공급사)"
            setOnClickListener { startActivity(Intent(this@EntryActivity, BpLoginActivity::class.java)) }
        }
    }

    override fun onResume() {
        super.onResume()
        route()
    }

    /** 로그인 상태면 해당 메인으로 보내고 true. 회사로그인은 역할(공급사/BP)에 따라 분기. */
    private fun route(): Boolean {
        if (Prefs.isBpLoggedIn(this)) {
            val target = if (Prefs.isSupplierRole(this)) SupplierMainActivity::class.java
                         else BpMainActivity::class.java
            startActivity(Intent(this, target)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            finish(); return true
        }
        if (Prefs.isRegistered(this)) {
            startActivity(Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            finish(); return true
        }
        return false
    }
}
