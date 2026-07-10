package com.dainon.skep.ui

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.dainon.skep.net.FieldApi
import com.dainon.skep.net.Prefs
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 장비공급사 메인 — 작업확인서(서명결과, 탭→PDF) + 내 장비(탭→일상점검 이력). FCM 토큰 등록.
 */
class SupplierMainActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var wcBox: LinearLayout
    private lateinit var eqBox: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Prefs.isBpLoggedIn(this)) { goEntry(); return }
        val content = UiKit.page(this, (Prefs.bpName(this) ?: "공급사") + " 님", null)
        content.addView(UiKit.primaryButton(this, "현장 상황") {
            startActivity(Intent(this, SiteSituationListActivity::class.java))
        })
        content.addView(UiKit.primaryButton(this, "받은 요청 — 점검 · 이행지시") {
            startActivity(Intent(this, SupplierInboxActivity::class.java))
        })
        content.addView(UiKit.primaryButton(this, "로그아웃") { logout() })

        content.addView(UiKit.sectionTitle(this, "작업확인서 (서명 결과)"))
        wcBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(wcBox)

        content.addView(UiKit.sectionTitle(this, "내 장비 — 일상점검"))
        eqBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(eqBox)

        registerFcm()
    }

    override fun onResume() {
        super.onResume()
        loadWc()
        loadEquipment()
    }

    private fun api() = FieldApi(Prefs.serverUrl(this))

    private fun loadWc() {
        val token = Prefs.bpToken(this) ?: return
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { api().listSupplierWc(token) } }
            r.onSuccess { list ->
                wcBox.removeAllViews()
                if (list.isEmpty()) { wcBox.addView(UiKit.hint(this@SupplierMainActivity, "발급한 작업확인서가 없습니다")); return@onSuccess }
                for (wc in list) {
                    val title = "${wc.workDate ?: "-"} · ${wc.personName ?: "-"}"
                    val sign = (if (wc.supplierSignedAt != null) "공급사✓" else "공급사·") +
                        " " + (if (wc.bpSignedAt != null) "BP✓" else "BP·")
                    val sub = buildString {
                        wc.totalHours?.let { append("%.1f시간 · ".format(it)) }
                        append(sign)
                        wc.wpTitle?.let { append(" · "); append(it) }
                    }
                    wcBox.addView(UiKit.card(this@SupplierMainActivity, title, sub, "PDF ›") {
                        startActivity(Intent(this@SupplierMainActivity, PdfViewActivity::class.java)
                            .putExtra(PdfViewActivity.EXTRA_WC_ID, wc.id)
                            .putExtra(PdfViewActivity.EXTRA_LABEL, "작업확인서 ${wc.workDate ?: ""}"))
                    })
                }
            }.onFailure { wcBox.removeAllViews(); wcBox.addView(UiKit.hint(this@SupplierMainActivity, "불러오기 실패: ${it.message}")) }
        }
    }

    private fun loadEquipment() {
        val token = Prefs.bpToken(this) ?: return
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { api().listMyEquipment(token) } }
            r.onSuccess { list ->
                eqBox.removeAllViews()
                if (list.isEmpty()) { eqBox.addView(UiKit.hint(this@SupplierMainActivity, "등록된 장비가 없습니다")); return@onSuccess }
                for (eq in list) {
                    val title = listOfNotNull(eq.vehicleNo, eq.model).joinToString(" · ").ifBlank { "장비 #${eq.id}" }
                    val sub = listOfNotNull(eq.category, eq.currentSiteName?.let { "현장: $it" }).joinToString(" · ")
                    eqBox.addView(UiKit.card(this@SupplierMainActivity, title, sub, "점검 ›") {
                        startActivity(Intent(this@SupplierMainActivity, SupplierEquipmentInspectionsActivity::class.java)
                            .putExtra(SupplierEquipmentInspectionsActivity.EXTRA_EQUIPMENT_ID, eq.id)
                            .putExtra(SupplierEquipmentInspectionsActivity.EXTRA_LABEL, title))
                    })
                }
            }.onFailure { eqBox.removeAllViews(); eqBox.addView(UiKit.hint(this@SupplierMainActivity, "불러오기 실패: ${it.message}")) }
        }
    }

    private fun registerFcm() {
        val token = Prefs.bpToken(this) ?: return
        FirebaseMessaging.getInstance().token.addOnSuccessListener { fcm ->
            scope.launch(Dispatchers.IO) { runCatching { api().registerBpFcmToken(token, fcm) } }
        }
    }

    private fun logout() { Prefs.clearBp(this); goEntry() }

    private fun goEntry() {
        startActivity(Intent(this, EntryActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
