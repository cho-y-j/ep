package com.dainon.skep.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
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

class HistoryActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var listContainer: LinearLayout
    private lateinit var tvSummary: TextView
    private lateinit var tvTotalHours: TextView
    private lateinit var tvEmpty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
        listContainer = findViewById(R.id.listContainer)
        tvSummary = findViewById(R.id.tvSummary)
        tvTotalHours = findViewById(R.id.tvTotalHours)
        tvEmpty = findViewById(R.id.tvEmpty)

        load()
    }

    private fun load() {
        val token = Prefs.token(this) ?: return
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val api = FieldApi(Prefs.serverUrl(this@HistoryActivity))
                    val att = api.listMyAttendance(token)
                    val wc = runCatching { api.listMyWorkConfirmations(token) }.getOrElse { emptyList() }
                    att to wc
                }
            }
            r.onSuccess { (att, wc) -> render(att, wc) }
                .onFailure {
                    tvEmpty.text = "불러오기 실패: ${it.message}"
                    tvEmpty.visibility = View.VISIBLE
                }
        }
    }

    private fun render(list: List<FieldApi.AttendanceItem>, wcs: List<FieldApi.WorkConfirmationItem>) {
        if (list.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            return
        }
        tvEmpty.visibility = View.GONE
        val total = list.sumOf { it.hours ?: 0.0 }
        tvSummary.text = "총 근무 ${list.size}회"
        tvTotalHours.text = "%.1f시간".format(total)

        val wcByWp = wcs.associateBy { it.workPlanId }
        val inflater = LayoutInflater.from(this)
        listContainer.removeAllViews()
        for (a in list) {
            val card = inflater.inflate(R.layout.item_history, listContainer, false)
            card.findViewById<TextView>(R.id.tvDate).text = (a.checkInAt ?: "-").take(10).replace("-", ".")
            card.findViewById<TextView>(R.id.tvHours).text = a.hours?.let { "%.1f시간".format(it) } ?: "진행 중"
            card.findViewById<TextView>(R.id.tvSite).text =
                (a.siteName ?: "-") + (a.wpTitle?.let { " · $it" } ?: "")
            card.findViewById<TextView>(R.id.tvTime).text = buildString {
                append(a.checkInAt?.substring(11, 16) ?: "-")
                append(" ~ ")
                append(a.checkOutAt?.substring(11, 16) ?: "진행 중")
            }

            val wcRow = card.findViewById<View>(R.id.wcRow)
            val tvStatus = card.findViewById<TextView>(R.id.tvWcStatus)
            val tvAction = card.findViewById<TextView>(R.id.tvWcAction)
            val wc = a.workPlanId?.let { wcByWp[it] }
            when {
                a.checkOutAt == null -> {
                    wcRow.visibility = View.GONE
                }
                wc == null -> {
                    wcRow.visibility = View.VISIBLE
                    tvStatus.text = "작업확인서 생성 대기"
                    tvStatus.setTextColor(0xFF94A3B8.toInt())
                    tvAction.text = ""
                }
                wc.supplierSignedAt == null -> {
                    wcRow.visibility = View.VISIBLE
                    tvStatus.text = "사인 필요 · %.1f시간".format(wc.totalHours ?: 0.0)
                    tvStatus.setTextColor(0xFFC2410C.toInt())
                    tvAction.text = "사인하기 ›"
                    tvAction.setOnClickListener { openWcSign(wc) }
                    card.setOnClickListener { openWcSign(wc) }
                }
                wc.bpSignedAt == null -> {
                    wcRow.visibility = View.VISIBLE
                    tvStatus.text = "✓ 사인 완료 · BP 확인 대기"
                    tvStatus.setTextColor(0xFF15803D.toInt())
                    tvAction.text = ""
                }
                else -> {
                    wcRow.visibility = View.VISIBLE
                    tvStatus.text = "✓ 양측 사인 완료 · %.1f시간".format(wc.totalHours ?: 0.0)
                    tvStatus.setTextColor(0xFF15803D.toInt())
                    tvAction.text = ""
                }
            }
            listContainer.addView(card)
        }
    }

    private fun openWcSign(wc: FieldApi.WorkConfirmationItem) {
        val intent = Intent(this, WorkConfirmationActivity::class.java)
        intent.putExtra(WorkConfirmationActivity.EXTRA_WC_ID, wc.id)
        intent.putExtra(WorkConfirmationActivity.EXTRA_WC_DATE, wc.workDate)
        intent.putExtra(WorkConfirmationActivity.EXTRA_WC_HOURS, wc.totalHours ?: 0.0)
        startActivity(intent)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
