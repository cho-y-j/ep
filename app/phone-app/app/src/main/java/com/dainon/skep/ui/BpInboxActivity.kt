package com.dainon.skep.ui

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.dainon.skep.net.FieldApi
import com.dainon.skep.net.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** BP 받은 투입요청 — 공급사가 보낸 현장 투입요청 수락/거절. (web: BpInboxPage) */
class BpInboxActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var listBox: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = UiKit.page(this, "받은 투입요청") { finish() }
        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(listBox)
        load()
    }

    private fun api() = FieldApi(Prefs.serverUrl(this))

    private fun load() {
        val token = Prefs.bpToken(this) ?: run { finish(); return }
        listBox.removeAllViews()
        listBox.addView(UiKit.hint(this, "불러오는 중…"))
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { api().listBpDeployments(token) } }
            r.onSuccess { all ->
                val pending = all.filter { it.status == "REQUESTED" }
                listBox.removeAllViews()
                if (pending.isEmpty()) { listBox.addView(UiKit.hint(this@BpInboxActivity, "처리할 투입요청이 없습니다")); return@onSuccess }
                for (d in pending) {
                    val type = if (d.resourceType == "EQUIPMENT") "장비" else "인원"
                    val sub = buildString {
                        append("$type · ${d.supplierCompanyName ?: "-"}\n")
                        append("현장: ${d.targetSiteName ?: "-"}")
                        d.startDate?.let { append(" · 시작 $it") }
                        d.note?.takeIf { it.isNotBlank() }?.let { append("\n메모: $it") }
                    }
                    listBox.addView(UiKit.actionCard(this@BpInboxActivity, d.resourceLabel ?: "-", sub, listOf(
                        "수락" to { review(d.id, true) },
                        "거절" to { review(d.id, false) },
                    )))
                }
            }.onFailure { listBox.removeAllViews(); listBox.addView(UiKit.hint(this@BpInboxActivity, "불러오기 실패: ${it.message}")) }
        }
    }

    private fun review(id: Long, accept: Boolean) {
        val token = Prefs.bpToken(this) ?: return
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { api().reviewDeployment(token, id, accept, null) } }
            r.onSuccess {
                Toast.makeText(this@BpInboxActivity, if (accept) "수락했습니다" else "거절했습니다", Toast.LENGTH_SHORT).show()
                load()
            }.onFailure { Toast.makeText(this@BpInboxActivity, "실패: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
