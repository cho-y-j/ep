package com.dainon.skep.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.dainon.skep.net.FieldApi
import com.dainon.skep.net.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * BP 현장 상세 보드 — 한 현장의 자원별 출근/누적/점검 상태 + 작업확인서(탭 시 PDF).
 * 점검 상태는 /api/resource-checks/bp-list 를 자원(owner)별로 매핑.
 */
class BpSiteBoardActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SITE_ID = "site_id"
        const val EXTRA_SITE_NAME = "site_name"
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val siteId = intent.getLongExtra(EXTRA_SITE_ID, -1L)
        val siteName = intent.getStringExtra(EXTRA_SITE_NAME) ?: "현장"
        val content = UiKit.page(this, siteName) { finish() }
        val status = UiKit.hint(this, "불러오는 중…")
        content.addView(status)

        val token = Prefs.bpToken(this) ?: run { finish(); return }
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val api = FieldApi(Prefs.serverUrl(this@BpSiteBoardActivity))
                    val board = api.listBpBoard(token).filter { it.targetSiteId == siteId }
                    val checks = runCatching { api.listBpResourceChecks(token) }.getOrDefault(emptyList())
                    board to checks
                }
            }
            r.onSuccess { (board, checks) ->
                content.removeAllViews()
                content.addView(UiKit.primaryButton(this@BpSiteBoardActivity, "현장 상황 보기") {
                    startActivity(Intent(this@BpSiteBoardActivity, SiteSituationActivity::class.java)
                        .putExtra(SiteSituationActivity.EXTRA_SITE_ID, siteId)
                        .putExtra(SiteSituationActivity.EXTRA_SITE_NAME, siteName))
                })
                if (board.isEmpty()) {
                    content.addView(UiKit.hint(this@BpSiteBoardActivity, "투입된 자원이 없습니다"))
                    return@onSuccess
                }
                // owner(자원)별 최신 점검 상태
                val latestCheck = checks
                    .filter { it.ownerType != null && it.ownerId != null }
                    .groupBy { "${it.ownerType}#${it.ownerId}" }
                    .mapValues { e -> e.value.maxByOrNull { it.id }?.status }

                for (b in board) {
                    val typeLabel = if (b.resourceType == "EQUIPMENT") "장비" else "인원"
                    val attend = if (b.todayAttended == true) "오늘 출근 ✓" else "오늘 미출근"
                    val checkLabel = checkLabel(latestCheck["${b.resourceType}#${b.resourceId}"])
                    val sub = "$typeLabel · ${b.supplierCompanyName ?: "-"}\n" +
                        "$attend · 누적 ${b.totalDays ?: 0}일 ${fmtH(b.totalHours)}h · $checkLabel"
                    content.addView(UiKit.card(this@BpSiteBoardActivity, b.resourceLabel ?: "-", sub, null, null))

                    b.recentConfirmations.orEmpty().forEach { rc ->
                        val sign = (if (rc.signedBySupplier == true) "공급사✓" else "공급사·") +
                            " " + (if (rc.signedByBp == true) "BP✓" else "BP·")
                        val rsub = "${rc.workDate ?: "-"} · ${fmtH(rc.totalHours)}h · $sign"
                        content.addView(UiKit.card(this@BpSiteBoardActivity, "  └ 작업확인서", rsub, "PDF ›") {
                            startActivity(Intent(this@BpSiteBoardActivity, PdfViewActivity::class.java)
                                .putExtra(PdfViewActivity.EXTRA_WC_ID, rc.id)
                                .putExtra(PdfViewActivity.EXTRA_LABEL, "작업확인서 ${rc.workDate ?: ""}"))
                        })
                    }
                }
            }.onFailure { status.text = "불러오기 실패: ${it.message}" }
        }
    }

    private fun checkLabel(status: String?): String = when (status) {
        "APPROVED" -> "점검 완료"
        "SUBMITTED" -> "점검 검토중"
        "REQUESTED" -> "점검 대기"
        "REJECTED" -> "점검 반려"
        null -> "점검 요청없음"
        else -> status
    }

    private fun fmtH(v: Double?): String = if (v == null) "0" else "%.1f".format(v)

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
