package com.dainon.skep.ui

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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
 * 공급사 받은 요청 — 점검요청(증빙 제출) + 이행지시(증빙 제출). 파일 선택 → multipart 업로드.
 * (web: SupplierInboxPage + ComplianceOrdersPage)
 */
class SupplierInboxActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var checkBox: LinearLayout
    private lateinit var orderBox: LinearLayout

    /** 파일 선택 완료 시 실행할 업로드 동작 (fileName, mime, bytes). */
    private var onPicked: ((String, String, ByteArray) -> Unit)? = null

    private val picker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val cb = onPicked ?: return@registerForActivityResult
        onPicked = null
        if (uri == null) return@registerForActivityResult
        val read = runCatching {
            val mime = contentResolver.getType(uri) ?: "application/octet-stream"
            val name = displayName(uri) ?: "proof"
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("읽기 실패")
            Triple(name, mime, bytes)
        }
        read.onSuccess { (n, m, b) -> cb(n, m, b) }
            .onFailure { Toast.makeText(this, "파일 읽기 실패: ${it.message}", Toast.LENGTH_LONG).show() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = UiKit.page(this, "받은 요청") { finish() }
        content.addView(UiKit.sectionTitle(this, "점검 요청 (증빙 제출)"))
        checkBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(checkBox)
        content.addView(UiKit.sectionTitle(this, "이행지시 (증빙 제출)"))
        orderBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(orderBox)
    }

    override fun onResume() {
        super.onResume()
        loadChecks()
        loadOrders()
    }

    private fun api() = FieldApi(Prefs.serverUrl(this))

    private fun statusKo(s: String?): String = when (s) {
        "REQUESTED" -> "제출 대기"; "SUBMITTED" -> "검토중"; "APPROVED" -> "승인"; "REJECTED" -> "반려"; else -> s ?: "-"
    }

    private fun loadChecks() {
        val token = Prefs.bpToken(this) ?: return
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { api().listSupplierChecks(token) } }
            r.onSuccess { all ->
                checkBox.removeAllViews()
                if (all.isEmpty()) { checkBox.addView(UiKit.hint(this@SupplierInboxActivity, "받은 점검요청이 없습니다")); return@onSuccess }
                for (c in all) {
                    val sub = "${c.checkType ?: "-"} · ${statusKo(c.status)}" +
                        (c.dueDate?.let { " · 기한 $it" } ?: "") +
                        (c.notes?.takeIf { it.isNotBlank() }?.let { "\n$it" } ?: "")
                    val actionable = c.status == "REQUESTED" || c.status == "REJECTED"
                    if (actionable) {
                        checkBox.addView(UiKit.actionCard(this@SupplierInboxActivity, c.ownerLabel ?: "-", sub,
                            listOf("증빙 제출" to { pickAndUpload { n, m, b -> uploadCheck(c.id, n, m, b) } })))
                    } else {
                        checkBox.addView(UiKit.card(this@SupplierInboxActivity, c.ownerLabel ?: "-", sub, null, null))
                    }
                }
            }.onFailure { checkBox.removeAllViews(); checkBox.addView(UiKit.hint(this@SupplierInboxActivity, "불러오기 실패: ${it.message}")) }
        }
    }

    private fun loadOrders() {
        val token = Prefs.bpToken(this) ?: return
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { api().listSupplierComplianceOrders(token) } }
            r.onSuccess { all ->
                orderBox.removeAllViews()
                if (all.isEmpty()) { orderBox.addView(UiKit.hint(this@SupplierInboxActivity, "받은 이행지시가 없습니다")); return@onSuccess }
                for (o in all) {
                    val title = listOfNotNull(o.orderType, o.orderSubtype).joinToString(" · ").ifBlank { "이행지시 #${o.id}" }
                    val sub = "${o.targetLabel ?: "-"} · ${statusKo(o.status)}" +
                        (o.dueDate?.let { " · 기한 $it" } ?: "") +
                        (o.requestNotes?.takeIf { it.isNotBlank() }?.let { "\n$it" } ?: "")
                    val actionable = o.status == "REQUESTED" || o.status == "REJECTED"
                    if (actionable) {
                        orderBox.addView(UiKit.actionCard(this@SupplierInboxActivity, title, sub,
                            listOf("증빙 제출" to { pickAndUpload { n, m, b -> uploadOrder(o.id, n, m, b) } })))
                    } else {
                        orderBox.addView(UiKit.card(this@SupplierInboxActivity, title, sub, null, null))
                    }
                }
            }.onFailure { orderBox.removeAllViews(); orderBox.addView(UiKit.hint(this@SupplierInboxActivity, "불러오기 실패: ${it.message}")) }
        }
    }

    private fun pickAndUpload(upload: (String, String, ByteArray) -> Unit) {
        onPicked = upload
        picker.launch("*/*")
    }

    private fun uploadCheck(id: Long, name: String, mime: String, bytes: ByteArray) {
        val token = Prefs.bpToken(this) ?: return
        Toast.makeText(this, "제출 중…", Toast.LENGTH_SHORT).show()
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { api().submitCheckFile(token, id, name, mime, bytes) } }
            r.onSuccess { Toast.makeText(this@SupplierInboxActivity, "제출 완료", Toast.LENGTH_SHORT).show(); loadChecks() }
                .onFailure { Toast.makeText(this@SupplierInboxActivity, "실패: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    private fun uploadOrder(id: Long, name: String, mime: String, bytes: ByteArray) {
        val token = Prefs.bpToken(this) ?: return
        Toast.makeText(this, "제출 중…", Toast.LENGTH_SHORT).show()
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { api().submitComplianceProof(token, id, name, mime, bytes) } }
            r.onSuccess { Toast.makeText(this@SupplierInboxActivity, "제출 완료", Toast.LENGTH_SHORT).show(); loadOrders() }
                .onFailure { Toast.makeText(this@SupplierInboxActivity, "실패: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    private fun displayName(uri: Uri): String? =
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
