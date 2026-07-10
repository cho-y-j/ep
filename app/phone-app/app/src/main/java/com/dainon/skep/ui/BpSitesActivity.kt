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

/** BP 현장 목록 — 투입 보드를 현장(target_site_id) 단위로 묶어 표시. 탭 → 현장 상세 보드. */
class BpSitesActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = UiKit.page(this, "현장 보기") { finish() }
        val status = UiKit.hint(this, "불러오는 중…")
        content.addView(status)

        val token = Prefs.bpToken(this) ?: run { finish(); return }
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching { FieldApi(Prefs.serverUrl(this@BpSitesActivity)).listBpBoard(token) }
            }
            r.onSuccess { items ->
                content.removeAllViews()
                val bySite = items.filter { it.targetSiteId != null }.groupBy { it.targetSiteId!! }
                if (bySite.isEmpty()) {
                    content.addView(UiKit.hint(this@BpSitesActivity, "진행 중인 현장이 없습니다"))
                    return@onSuccess
                }
                for ((siteId, group) in bySite) {
                    val siteName = group.firstOrNull { !it.targetSiteName.isNullOrBlank() }?.targetSiteName
                        ?: "현장 #$siteId"
                    val eq = group.count { it.resourceType == "EQUIPMENT" }
                    val ppl = group.count { it.resourceType == "PERSON" }
                    val attended = group.count { it.todayAttended == true }
                    val sub = "장비 ${eq} · 인원 ${ppl} · 오늘 출근 ${attended}명"
                    content.addView(UiKit.card(this@BpSitesActivity, siteName, sub, "보기 ›") {
                        startActivity(Intent(this@BpSitesActivity, BpSiteBoardActivity::class.java)
                            .putExtra(BpSiteBoardActivity.EXTRA_SITE_ID, siteId)
                            .putExtra(BpSiteBoardActivity.EXTRA_SITE_NAME, siteName))
                    })
                }
            }.onFailure { status.text = "불러오기 실패: ${it.message}" }
        }
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
