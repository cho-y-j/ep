package com.dainon.skep.ui

import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
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

/** 작업확인서 본인 사인 + 시간 보정 + 제출. */
class WorkConfirmationActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_WC_ID = "wc_id"
        const val EXTRA_WC_DATE = "wc_date"
        const val EXTRA_WC_HOURS = "wc_hours"
        const val TAG = "WorkConfirm"
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var btnBack: Button
    private lateinit var rowDate: View
    private lateinit var rowAuto: View
    private lateinit var rowBreak: View
    private lateinit var etHours: EditText
    private lateinit var etRemarks: EditText
    private lateinit var sigPad: SignaturePadView
    private lateinit var btnClear: Button
    private lateinit var btnSubmit: Button

    private var wcId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_work_confirmation)

        wcId = intent.getLongExtra(EXTRA_WC_ID, -1L)
        val date = intent.getStringExtra(EXTRA_WC_DATE) ?: "-"
        val initialHours = intent.getDoubleExtra(EXTRA_WC_HOURS, 0.0)

        btnBack = findViewById(R.id.btnBack)
        rowDate = findViewById(R.id.rowDate)
        rowAuto = findViewById(R.id.rowAuto)
        rowBreak = findViewById(R.id.rowBreak)
        etHours = findViewById(R.id.etHours)
        etRemarks = findViewById(R.id.etRemarks)
        sigPad = findViewById(R.id.sigPad)
        btnClear = findViewById(R.id.btnClear)
        btnSubmit = findViewById(R.id.btnSubmit)

        setRow(rowDate, "근무일", date)
        setRow(rowAuto, "자동 계산", String.format("%.2f시간", initialHours + 1.0))
        setRow(rowBreak, "휴식 차감", "1.00시간")
        etHours.setText(String.format("%.2f", initialHours))

        btnBack.setOnClickListener { finish() }
        btnClear.setOnClickListener { sigPad.clear() }
        btnSubmit.setOnClickListener { submit() }
    }

    private fun submit() {
        if (sigPad.isEmpty()) {
            Toast.makeText(this, "사인을 그려주세요", Toast.LENGTH_SHORT).show()
            return
        }
        val hours = etHours.text.toString().trim().toDoubleOrNull()
        if (hours == null || hours < 0) {
            Toast.makeText(this, "총 근무시간이 올바르지 않습니다", Toast.LENGTH_SHORT).show()
            return
        }
        val token = Prefs.token(this) ?: return
        val sigBase64 = Base64.encodeToString(sigPad.toPngBytes(), Base64.NO_WRAP)
        val remarks = etRemarks.text.toString().trim().ifBlank { null }
        btnSubmit.isEnabled = false
        btnSubmit.text = "전송 중..."
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    FieldApi(Prefs.serverUrl(this@WorkConfirmationActivity))
                        .signWorkConfirmation(token, wcId, hours, remarks, sigBase64)
                }
            }
            r.onSuccess {
                Toast.makeText(this@WorkConfirmationActivity, "사인 완료", Toast.LENGTH_SHORT).show()
                finish()
            }.onFailure {
                btnSubmit.isEnabled = true
                btnSubmit.text = "사인 후 제출"
                Log.w(TAG, "work confirmation sign failed", it)
                Toast.makeText(this@WorkConfirmationActivity, "제출에 실패했습니다 — 잠시 후 다시 시도해주세요", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setRow(row: View, label: String, value: String) {
        row.findViewById<TextView>(R.id.tvLabel).text = label
        row.findViewById<TextView>(R.id.tvValue).text = value
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
