package com.dainon.skep.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.dainon.skep.net.FieldApi
import com.dainon.skep.net.Prefs
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 한 장비의 일상점검 이력 — 점검일/종합판정 + 체크리스트 항목별 결과. 조회 전용. */
class SupplierEquipmentInspectionsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_EQUIPMENT_ID = "equipment_id"
        const val EXTRA_LABEL = "label"
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val gson = Gson()

    private data class ChecklistItem(val key: String?, val label: String?, val result: String?, val note: String?)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val equipmentId = intent.getLongExtra(EXTRA_EQUIPMENT_ID, -1L)
        val label = intent.getStringExtra(EXTRA_LABEL) ?: "장비 점검"
        val content = UiKit.page(this, "$label — 일상점검") { finish() }
        val status = UiKit.hint(this, "불러오는 중…")
        content.addView(status)

        val token = Prefs.bpToken(this) ?: run { finish(); return }
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching { FieldApi(Prefs.serverUrl(this@SupplierEquipmentInspectionsActivity))
                    .listEquipmentInspections(token, equipmentId) }
            }
            r.onSuccess { list ->
                content.removeAllViews()
                if (list.isEmpty()) {
                    content.addView(UiKit.hint(this@SupplierEquipmentInspectionsActivity, "점검 이력이 없습니다"))
                    return@onSuccess
                }
                for (insp in list) {
                    val title = "${insp.inspectDate ?: "-"} · ${overallLabel(insp.overall)}"
                    val sub = buildString {
                        insp.inspectorName?.let { append("점검자: $it\n") }
                        append(checklistSummary(insp.items))
                        insp.notes?.takeIf { it.isNotBlank() }?.let { append("\n비고: $it") }
                    }
                    content.addView(UiKit.card(this@SupplierEquipmentInspectionsActivity, title, sub, null, null))
                }
            }.onFailure { status.text = "불러오기 실패: ${it.message}" }
        }
    }

    private fun overallLabel(o: String?): String = when (o) {
        "PASS" -> "정상"
        "ATTENTION" -> "주의"
        "FAIL" -> "이상"
        else -> o ?: "-"
    }

    /** items JSON → "항목: 결과" 줄 목록. 파싱 실패 시 빈 문자열. */
    private fun checklistSummary(itemsJson: String?): String {
        if (itemsJson.isNullOrBlank()) return ""
        val items = runCatching { gson.fromJson(itemsJson, Array<ChecklistItem>::class.java) }.getOrNull() ?: return ""
        return items.joinToString("\n") { "· ${it.label ?: it.key ?: "-"}: ${resultLabel(it.result)}" }
    }

    private fun resultLabel(r: String?): String = when (r) {
        "OK" -> "정상"
        "CHECK" -> "주의"
        "FAIL" -> "이상"
        else -> r ?: "-"
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
