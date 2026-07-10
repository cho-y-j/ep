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

/** 현장 상황 진입용 현장 목록 — GET /api/sites(역할 스코프). 탭 → SiteSituationActivity. */
class SiteSituationListActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = UiKit.page(this, "현장 상황") { finish() }
        val status = UiKit.hint(this, "불러오는 중…")
        content.addView(status)

        val token = Prefs.bpToken(this) ?: run { finish(); return }
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching { FieldApi(Prefs.serverUrl(this@SiteSituationListActivity)).listSites(token) }
            }
            r.onSuccess { list ->
                content.removeAllViews()
                if (list.isEmpty()) {
                    content.addView(UiKit.hint(this@SiteSituationListActivity, "참여 중인 현장이 없습니다"))
                    return@onSuccess
                }
                for (site in list) {
                    val name = site.name ?: "현장 #${site.id}"
                    content.addView(UiKit.card(this@SiteSituationListActivity, name, site.address, "보기 ›") {
                        startActivity(Intent(this@SiteSituationListActivity, SiteSituationActivity::class.java)
                            .putExtra(SiteSituationActivity.EXTRA_SITE_ID, site.id)
                            .putExtra(SiteSituationActivity.EXTRA_SITE_NAME, name))
                    })
                }
            }.onFailure { status.text = "불러오기 실패: ${it.message}" }
        }
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
