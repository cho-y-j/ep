package com.dainon.skep.ui

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
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

/** NFC 태그 스캔 → 자원 식별. 장비 태그면 점검 화면으로, 작업자 카드면 안내. */
class NfcScanActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var nfc: NfcAdapter? = null
    private lateinit var tvStatus: TextView
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nfc_scan)
        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
        tvStatus = findViewById(R.id.tvStatus)
        nfc = NfcAdapter.getDefaultAdapter(this)
        if (nfc == null) {
            tvStatus.text = "이 기기는 NFC를 지원하지 않습니다"
        } else if (!nfc!!.isEnabled) {
            tvStatus.text = "설정에서 NFC를 켜주세요"
        }
    }

    override fun onResume() {
        super.onResume()
        val adapter = nfc ?: return
        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        val pi = PendingIntent.getActivity(this, 0, intent, flags)
        runCatching { adapter.enableForegroundDispatch(this, pi, null, null) }
    }

    override fun onPause() {
        super.onPause()
        runCatching { nfc?.disableForegroundDispatch(this) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val tag: Tag? = if (Build.VERSION.SDK_INT >= 33)
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        else @Suppress("DEPRECATION") intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        val id = tag?.id ?: return
        val tagId = id.joinToString("") { "%02x".format(it) }
        if (busy) return
        resolve(tagId)
    }

    private fun resolve(tagId: String) {
        val token = Prefs.token(this) ?: return
        busy = true
        tvStatus.text = "식별 중..."
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching { FieldApi(Prefs.serverUrl(this@NfcScanActivity)).resolveNfc(token, tagId) }
            }
            busy = false
            r.onSuccess { res ->
                when (res.type) {
                    "EQUIPMENT" -> {
                        val i = Intent(this@NfcScanActivity, EquipmentInspectionActivity::class.java)
                        i.putExtra(EquipmentInspectionActivity.EXTRA_EQUIPMENT_ID, res.id)
                        i.putExtra(EquipmentInspectionActivity.EXTRA_EQUIPMENT_LABEL, res.label ?: "-")
                        startActivity(i)
                        finish()
                    }
                    "PERSON" -> {
                        tvStatus.text = "작업자 카드: ${res.label ?: "-"}"
                        Toast.makeText(this@NfcScanActivity, "작업자 NFC 출근은 준비 중입니다", Toast.LENGTH_SHORT).show()
                    }
                    else -> tvStatus.text = "알 수 없는 태그입니다"
                }
            }.onFailure {
                tvStatus.text = "등록되지 않았거나 식별 실패\n태그를 다시 대주세요"
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
