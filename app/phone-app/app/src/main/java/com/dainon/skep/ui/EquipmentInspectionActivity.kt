package com.dainon.skep.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.dainon.skep.R
import com.dainon.skep.net.FieldApi
import com.dainon.skep.net.Prefs
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 장비 일상점검 체크리스트 작성 + 제출. NFC 스캔(EQUIPMENT)으로 진입. 제출 결과는 BP 가 웹에서 조회. */
class EquipmentInspectionActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_EQUIPMENT_ID = "equipment_id"
        const val EXTRA_EQUIPMENT_LABEL = "equipment_label"

        // 건설장비 일상점검 기본 항목 (key, 표시 라벨).
        private val ITEMS = listOf(
            "engine" to "엔진/시동 상태",
            "hydraulic" to "유압 계통(호스·실린더 누유)",
            "track" to "타이어/궤도 상태",
            "brake" to "제동장치(브레이크)",
            "lights" to "등화/경광등/후진경보",
            "gauges" to "계기판/게이지",
            "fluids" to "연료/엔진오일/냉각수",
            "safety" to "안전벨트/안전장치",
            "attach" to "작업장치(버킷·붐 등)",
            "leak" to "누유/누수 여부",
        )
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val rows = ArrayList<View>()
    private var equipmentId: Long = -1L

    private lateinit var btnSubmit: Button
    private lateinit var etNotes: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_equipment_inspection)

        equipmentId = intent.getLongExtra(EXTRA_EQUIPMENT_ID, -1L)
        val label = intent.getStringExtra(EXTRA_EQUIPMENT_LABEL) ?: "-"

        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tvEquip).text = label
        etNotes = findViewById(R.id.etNotes)
        btnSubmit = findViewById(R.id.btnSubmit)

        val container = findViewById<LinearLayout>(R.id.itemsContainer)
        val inflater = LayoutInflater.from(this)
        for ((_, itemLabel) in ITEMS) {
            val row = inflater.inflate(R.layout.item_inspection_row, container, false)
            row.findViewById<TextView>(R.id.tvItemLabel).text = itemLabel
            container.addView(row)
            rows.add(row)
        }

        btnSubmit.setOnClickListener { submit() }
    }

    private fun submit() {
        if (equipmentId <= 0) {
            Toast.makeText(this, "장비 정보가 없습니다", Toast.LENGTH_SHORT).show()
            return
        }
        val arr = rows.mapIndexed { i, row ->
            val (key, label) = ITEMS[i]
            val result = when (row.findViewById<RadioGroup>(R.id.rgResult).checkedRadioButtonId) {
                R.id.rbCheck -> "CHECK"
                R.id.rbFail -> "FAIL"
                else -> "OK"
            }
            val note = row.findViewById<EditText>(R.id.etItemNote).text.toString().trim()
            mapOf("key" to key, "label" to label, "result" to result, "note" to note)
        }
        val overall = when {
            arr.any { it["result"] == "FAIL" } -> "FAIL"
            arr.any { it["result"] == "CHECK" } -> "ATTENTION"
            else -> "PASS"
        }
        val itemsJson = Gson().toJson(arr)
        val notes = etNotes.text.toString().trim().ifBlank { null }
        val token = Prefs.token(this) ?: return
        btnSubmit.isEnabled = false
        btnSubmit.text = "제출 중..."
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    FieldApi(Prefs.serverUrl(this@EquipmentInspectionActivity))
                        .submitEquipmentInspection(token, equipmentId, null, itemsJson, null, notes, overall)
                }
            }
            r.onSuccess {
                Toast.makeText(this@EquipmentInspectionActivity, "점검 제출 완료", Toast.LENGTH_SHORT).show()
                finish()
            }.onFailure {
                btnSubmit.isEnabled = true
                btnSubmit.text = "점검 제출"
                Toast.makeText(this@EquipmentInspectionActivity, "실패: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
