package com.dainon.skep.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.dainon.skep.net.FieldApi
import com.dainon.skep.net.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 작업확인서 PDF 뷰어 — GET /api/work-confirmations/{id}/pdf 다운로드 → PdfRenderer 렌더 + 공유/저장. */
class PdfViewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_WC_ID = "wc_id"
        const val EXTRA_LABEL = "label"
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val wcId = intent.getLongExtra(EXTRA_WC_ID, -1L)
        val label = intent.getStringExtra(EXTRA_LABEL) ?: "작업확인서"
        val content = UiKit.page(this, label) { finish() }
        val status = UiKit.hint(this, "PDF 불러오는 중…")
        content.addView(status)

        val token = Prefs.bpToken(this)
        if (token == null || wcId <= 0) { status.text = "정보가 없습니다"; return }

        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = FieldApi(Prefs.serverUrl(this@PdfViewActivity)).downloadWcPdf(token, wcId)
                    val f = File(cacheDir, "wc_$wcId.pdf")
                    f.writeBytes(bytes)
                    f
                }
            }
            r.onSuccess { f ->
                content.removeView(status)
                content.addView(UiKit.primaryButton(this@PdfViewActivity, "공유 / 저장") { share(f, label) })
                runCatching { renderPdf(f, content) }
                    .onFailure { content.addView(UiKit.hint(this@PdfViewActivity, "미리보기 실패 — 공유/저장으로 열어보세요")) }
            }.onFailure {
                status.text = "불러오기 실패: ${it.message}"
            }
        }
    }

    private fun renderPdf(f: File, content: LinearLayout) {
        val pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        val width = resources.displayMetrics.widthPixels - UiKit.dp(this, 28)
        for (i in 0 until renderer.pageCount) {
            val page = renderer.openPage(i)
            val scale = width.toFloat() / page.width
            val height = (page.height * scale).toInt()
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(0xFFFFFFFF.toInt())
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            content.addView(ImageView(this).apply {
                setImageBitmap(bmp)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = UiKit.dp(this@PdfViewActivity, 10) }
            })
        }
        renderer.close()
        pfd.close()
    }

    private fun share(f: File, label: String) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "$label 공유"))
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
