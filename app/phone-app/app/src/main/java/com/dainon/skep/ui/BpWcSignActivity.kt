package com.dainon.skep.ui

import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.dainon.skep.R
import com.dainon.skep.net.FieldApi
import com.dainon.skep.net.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 발주사(BP) 작업확인서 서명 — SignaturePadView → POST /api/work-confirmations/{id}/sign-bp. */
class BpWcSignActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_WC_ID = "wc_id"
        const val EXTRA_LABEL = "label"
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var sigPad: SignaturePadView
    private lateinit var btnSubmit: Button
    private var wcId: Long = -1L
    private var label: String = "-"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bp_wc_sign)
        wcId = intent.getLongExtra(EXTRA_WC_ID, -1L)
        label = intent.getStringExtra(EXTRA_LABEL) ?: "-"
        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tvLabel).text = label
        sigPad = findViewById(R.id.sigPad)
        findViewById<Button>(R.id.btnClear).setOnClickListener { sigPad.clear() }
        btnSubmit = findViewById(R.id.btnSubmit)
        btnSubmit.setOnClickListener { submit() }
    }

    private fun submit() {
        if (wcId <= 0) { Toast.makeText(this, "작업확인서 정보가 없습니다", Toast.LENGTH_SHORT).show(); return }
        if (sigPad.isEmpty()) { Toast.makeText(this, "서명을 그려주세요", Toast.LENGTH_SHORT).show(); return }
        val token = Prefs.bpToken(this) ?: return
        val base64 = Base64.encodeToString(sigPad.toPngBytes(), Base64.NO_WRAP)
        btnSubmit.isEnabled = false
        btnSubmit.text = "전송 중..."
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching { FieldApi(Prefs.serverUrl(this@BpWcSignActivity)).signBpWc(token, wcId, base64) }
            }
            r.onSuccess {
                Toast.makeText(this@BpWcSignActivity, "서명 완료", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this@BpWcSignActivity, PdfViewActivity::class.java)
                    .putExtra(PdfViewActivity.EXTRA_WC_ID, wcId)
                    .putExtra(PdfViewActivity.EXTRA_LABEL, "작업확인서 — $label"))
                finish()
            }.onFailure {
                btnSubmit.isEnabled = true
                btnSubmit.text = "서명 후 승인"
                Toast.makeText(this@BpWcSignActivity, "실패: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
