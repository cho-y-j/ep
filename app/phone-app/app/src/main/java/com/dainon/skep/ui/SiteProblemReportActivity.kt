package com.dainon.skep.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
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

/** 작업자 → BP사 현장 문제 신고 (인원/장비 + 내용). 발송 시 BP 웹 종 알림 + BP 폰 FCM. */
class SiteProblemReportActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_WORK_PLAN_ID = "work_plan_id"
        const val EXTRA_SITE_NAME = "site_name"
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var btnBack: Button
    private lateinit var tvSite: TextView
    private lateinit var rgCategory: RadioGroup
    private lateinit var etMessage: EditText
    private lateinit var btnSubmit: Button

    private var workPlanId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_site_problem_report)

        workPlanId = intent.getLongExtra(EXTRA_WORK_PLAN_ID, -1L)
        val siteName = intent.getStringExtra(EXTRA_SITE_NAME) ?: "-"

        btnBack = findViewById(R.id.btnBack)
        tvSite = findViewById(R.id.tvSite)
        rgCategory = findViewById(R.id.rgCategory)
        etMessage = findViewById(R.id.etMessage)
        btnSubmit = findViewById(R.id.btnSubmit)

        tvSite.text = siteName

        btnBack.setOnClickListener { finish() }
        btnSubmit.setOnClickListener { submit() }
    }

    private fun submit() {
        if (workPlanId <= 0) {
            Toast.makeText(this, "배정된 현장이 없습니다", Toast.LENGTH_SHORT).show()
            return
        }
        val category = when (rgCategory.checkedRadioButtonId) {
            R.id.rbPerson -> "PERSON"
            R.id.rbEquipment -> "EQUIPMENT"
            else -> "ETC"
        }
        val message = etMessage.text.toString().trim()
        if (message.isEmpty()) {
            Toast.makeText(this, "문제 내용을 입력해주세요", Toast.LENGTH_SHORT).show()
            return
        }
        val token = Prefs.token(this) ?: return
        btnSubmit.isEnabled = false
        btnSubmit.text = "전송 중..."
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    FieldApi(Prefs.serverUrl(this@SiteProblemReportActivity))
                        .reportIssue(token, workPlanId, category, message)
                }
            }
            r.onSuccess {
                Toast.makeText(this@SiteProblemReportActivity, "신고가 전송되었습니다", Toast.LENGTH_SHORT).show()
                finish()
            }.onFailure {
                btnSubmit.isEnabled = true
                btnSubmit.text = "신고 보내기"
                Toast.makeText(this@SiteProblemReportActivity, "실패: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
