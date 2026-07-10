package com.dainon.skep.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import android.widget.LinearLayout
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
 * 현장 상황 — 한 현장의 지도·출근·안전·날씨·작업진행·투입자원을 한 화면에.
 * 진입: site_id(Long) 인텐트 extra. Bearer(bp_token)로 GET /api/field/site-situation/{siteId}.
 * 공급사 스코프는 백엔드가 처리하므로 앱은 응답을 그대로 표시.
 */
class SiteSituationActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SITE_ID = "site_id"
        const val EXTRA_SITE_NAME = "site_name"
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var siteId: Long = -1L
    private lateinit var body: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        siteId = intent.getLongExtra(EXTRA_SITE_ID, -1L)
        val siteName = intent.getStringExtra(EXTRA_SITE_NAME) ?: "현장 상황"
        val content = UiKit.page(this, siteName) { finish() }
        content.addView(UiKit.primaryButton(this, "새로고침") { load() })
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(body)
        load()
    }

    private fun load() {
        val token = Prefs.bpToken(this) ?: run { finish(); return }
        if (siteId <= 0L) {
            body.removeAllViews(); body.addView(UiKit.hint(this, "현장 정보가 없습니다")); return
        }
        body.removeAllViews()
        body.addView(UiKit.hint(this, "불러오는 중…"))
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching { FieldApi(Prefs.serverUrl(this@SiteSituationActivity)).getSiteSituation(token, siteId) }
            }
            r.onSuccess { render(it) }
                .onFailure {
                    body.removeAllViews()
                    body.addView(UiKit.hint(this@SiteSituationActivity, "불러오기 실패: ${it.message}"))
                }
        }
    }

    private fun render(s: FieldApi.SiteSituation) {
        body.removeAllViews()

        // 지도
        body.addView(UiKit.sectionTitle(this, "현장 지도"))
        val mapUrl = s.site?.mapUrl
        if (!mapUrl.isNullOrBlank()) body.addView(buildMap(Prefs.serverUrl(this) + mapUrl))
        else body.addView(UiKit.hint(this, "좌표 미설정"))
        val addr = listOfNotNull(s.site?.address, s.site?.detailAddress).joinToString(" ").trim()
        if (addr.isNotBlank()) body.addView(UiKit.card(this, s.site?.name ?: "-", addr, null, null))

        // 출근
        val att = s.attendance
        body.addView(UiKit.sectionTitle(this, "출근 현황 · ${att?.checkedInCount ?: 0}명"))
        val workers = att?.workers.orEmpty()
        if (workers.isEmpty()) body.addView(UiKit.hint(this, "출근한 인원이 없습니다"))
        else workers.forEach { w ->
            val sub = listOfNotNull(
                w.supplierName,
                fmtTime(w.checkInAt)?.let { "체크인 $it" },
                if (w.onBreak == true) "휴식중" else null
            ).joinToString(" · ")
            body.addView(UiKit.card(this, w.name ?: "-", sub, null, null))
        }

        // 안전
        val safety = s.safety
        val unresolved = safety?.unresolvedCount ?: 0
        body.addView(UiKit.sectionTitle(this, if (unresolved > 0) "안전 · 미해결 ${unresolved}건" else "안전 · 이상 없음"))
        val alerts = safety?.alerts.orEmpty()
        if (alerts.isEmpty()) body.addView(UiKit.hint(this, "안전 알림이 없습니다"))
        else alerts.forEach { a ->
            val title = "${a.personName ?: "-"} · ${a.kind ?: "-"}"
            val sub = listOfNotNull(
                a.level,
                if (a.resolved == true) "해결됨" else "미해결",
                fmtTime(a.createdAt)
            ).joinToString(" · ")
            body.addView(UiKit.card(this, title, sub, null, null))
        }

        // 날씨
        val w = s.weather
        body.addView(UiKit.sectionTitle(this, "날씨"))
        if (w?.available == true) {
            val title = listOfNotNull(
                w.tempC?.let { "${it}℃" },
                w.feelsLike?.let { "체감 ${it}℃" }
            ).joinToString(" · ").ifBlank { "-" }
            val sub = listOfNotNull(w.stageLabel, w.humidity?.let { "습도 ${it}%" }).joinToString(" · ")
            body.addView(UiKit.card(this, title, sub, null, null))
        } else {
            body.addView(UiKit.hint(this, "날씨 정보 없음"))
        }

        // 작업진행
        val wp = s.workProgress
        body.addView(UiKit.sectionTitle(this, "작업 진행 · ${wp?.todayTotal ?: 0}건"))
        val plans = wp?.plans.orEmpty()
        if (plans.isEmpty()) body.addView(UiKit.hint(this, "오늘 작업이 없습니다"))
        else plans.forEach { p ->
            val time = listOfNotNull(p.startTime, p.endTime).joinToString(" ~ ")
            val sub = listOfNotNull(p.status, time.ifBlank { null }).joinToString(" · ")
            body.addView(UiKit.card(this, p.title ?: "-", sub, null, null))
        }

        // 투입자원
        val res = s.resources
        body.addView(UiKit.sectionTitle(this, "투입 자원 · 장비 ${res?.equipmentCount ?: 0} · 인원 ${res?.personCount ?: 0}"))
        val items = res?.items.orEmpty()
        if (items.isEmpty()) body.addView(UiKit.hint(this, "투입된 자원이 없습니다"))
        else items.forEach { r ->
            val typeLabel = if (r.resourceType == "EQUIPMENT") "장비" else "인원"
            val attend = if (r.todayAttended == true) "오늘 출근" else "오늘 미출근"
            val sub = listOfNotNull(typeLabel, r.supplierCompanyName, attend).joinToString(" · ")
            body.addView(UiKit.card(this, r.label ?: "-", sub, null, null))
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun buildMap(url: String): WebView {
        val web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.setSupportZoom(true)
        web.settings.builtInZoomControls = false
        web.settings.displayZoomControls = false
        web.setOnTouchListener { v, _ ->
            v.parent.requestDisallowInterceptTouchEvent(true)
            false
        }
        web.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, UiKit.dp(this, 220)
        )
        web.loadUrl(url)
        return web
    }

    /** ISO "2026-07-10T08:30:00" → "07-10 08:30". */
    private fun fmtTime(iso: String?): String? {
        if (iso.isNullOrBlank()) return null
        val s = iso.take(16).replace("T", " ")
        return if (s.length >= 16) s.substring(5) else s
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
