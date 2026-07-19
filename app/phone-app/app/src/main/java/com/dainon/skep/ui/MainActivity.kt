package com.dainon.skep.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import okhttp3.OkHttpClient
import okhttp3.Request
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.dainon.skep.R
import com.dainon.skep.net.FieldApi
import com.dainon.skep.net.Prefs
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 메인 — 활성 작업계획서 1건 자동 표시(첫 번째). 출/퇴근 버튼은 CheckInActivity 로 분기.
 */
class MainActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var cardSite: View
    private lateinit var tvSiteName: TextView
    private lateinit var tvSiteAddress: TextView
    private lateinit var tvToday: TextView
    private lateinit var rowSupplier: View
    private lateinit var rowName: View
    private lateinit var rowJob: View
    private lateinit var rowHours: View
    private lateinit var rowStatus: View
    private lateinit var tvCheckTitle: TextView
    private lateinit var btnCheckAction: Button
    private lateinit var btnBreakAction: Button
    private lateinit var btnReportIssue: Button
    private lateinit var btnInspect: Button
    private lateinit var tvNotice: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var btnLogout: Button
    private lateinit var ivAvatar: ImageView
    private lateinit var cardPendingWc: View
    private lateinit var tvPendingWcDetail: TextView

    private lateinit var pageAttendance: View
    private lateinit var pageHistory: View
    private lateinit var pageProfile: View
    private lateinit var tabAttendance: TextView
    private lateinit var tabHistory: TextView
    private lateinit var tabProfile: TextView

    private lateinit var historyContainer: LinearLayout
    private lateinit var tvHistorySummary: TextView
    private lateinit var tvHistoryTotal: TextView
    private lateinit var tvHistoryEmpty: TextView

    private lateinit var ivProfileAvatar: ImageView
    private lateinit var tvProfileName: TextView
    private lateinit var tvProfileSupplier: TextView
    private lateinit var rowProfileJob: View
    private lateinit var rowProfileCode: View
    private lateinit var btnProfileLogout: Button

    private var currentWp: FieldApi.WorkPlanItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Prefs.isRegistered(this)) {
            startActivity(Intent(this, EntryActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        cardSite = findViewById(R.id.cardSite)
        tvSiteName = findViewById(R.id.tvSiteName)
        tvSiteAddress = findViewById(R.id.tvSiteAddress)
        tvToday = findViewById(R.id.tvToday)
        rowSupplier = findViewById(R.id.rowSupplier)
        rowName = findViewById(R.id.rowName)
        rowJob = findViewById(R.id.rowJob)
        rowHours = findViewById(R.id.rowHours)
        rowStatus = findViewById(R.id.rowStatus)
        tvCheckTitle = findViewById(R.id.tvCheckTitle)
        btnCheckAction = findViewById(R.id.btnCheckAction)
        btnBreakAction = findViewById(R.id.btnBreakAction)
        btnReportIssue = findViewById(R.id.btnReportIssue)
        btnInspect = findViewById(R.id.btnInspect)
        tvNotice = findViewById(R.id.tvNotice)
        tvEmpty = findViewById(R.id.tvEmpty)
        btnLogout = findViewById(R.id.btnLogout)
        ivAvatar = findViewById(R.id.ivAvatar)
        cardPendingWc = findViewById(R.id.cardPendingWc)
        tvPendingWcDetail = findViewById(R.id.tvPendingWcDetail)

        pageAttendance = findViewById(R.id.pageAttendance)
        pageHistory = findViewById(R.id.pageHistory)
        pageProfile = findViewById(R.id.pageProfile)
        tabAttendance = findViewById(R.id.tabAttendance)
        tabHistory = findViewById(R.id.tabHistory)
        tabProfile = findViewById(R.id.tabProfile)

        historyContainer = findViewById(R.id.historyContainer)
        tvHistorySummary = findViewById(R.id.tvHistorySummary)
        tvHistoryTotal = findViewById(R.id.tvHistoryTotal)
        tvHistoryEmpty = findViewById(R.id.tvHistoryEmpty)

        ivProfileAvatar = findViewById(R.id.ivProfileAvatar)
        tvProfileName = findViewById(R.id.tvProfileName)
        tvProfileSupplier = findViewById(R.id.tvProfileSupplier)
        rowProfileJob = findViewById(R.id.rowProfileJob)
        rowProfileCode = findViewById(R.id.rowProfileCode)
        btnProfileLogout = findViewById(R.id.btnProfileLogout)

        tvToday.text = SimpleDateFormat("yyyy.MM.dd (E)", Locale.KOREAN).format(Calendar.getInstance().time)

        btnLogout.setOnClickListener { logout() }
        btnProfileLogout.setOnClickListener { logout() }
        tabAttendance.setOnClickListener { switchTab(0) }
        tabHistory.setOnClickListener { switchTab(1) }
        tabProfile.setOnClickListener { switchTab(2) }
        cardSite.setOnClickListener { currentWp?.let { openCheckIn(it.workPlanId, it.openSessionId != null) } }
        btnCheckAction.setOnClickListener {
            currentWp?.let { openCheckIn(it.workPlanId, it.openSessionId != null) }
        }
        btnBreakAction.setOnClickListener { toggleBreak() }
        btnReportIssue.setOnClickListener {
            val wp = currentWp
            if (wp == null) {
                Toast.makeText(this, "배정된 현장이 없습니다", Toast.LENGTH_SHORT).show()
            } else {
                val intent = Intent(this, SiteProblemReportActivity::class.java)
                intent.putExtra(SiteProblemReportActivity.EXTRA_WORK_PLAN_ID, wp.workPlanId)
                intent.putExtra(SiteProblemReportActivity.EXTRA_SITE_NAME, wp.siteName ?: "-")
                startActivity(intent)
            }
        }
        btnInspect.setOnClickListener { startActivity(Intent(this, NfcScanActivity::class.java)) }

        ensureNotificationPermission()
        ensureBlePermissions()
        registerFcmToken()
        loadAvatar()
    }

    /** Android 13+(TIRAMISU) 알림 표시 런타임 권한 요청 — 공지·안전알림 알림용. */
    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 201)
        }
    }

    /**
     * Android 12+(S) BLE 권한 선요청 — P5-W2 파인드미 BLE 비콘(피재자 자가발동)·근접 게이지(응답자)용.
     * 파인드미는 사건 발생 시 자동 발동되므로 광고 권한을 평상시 홈에서 미리 확보한다.
     */
    private fun ensureBlePermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val perms = arrayOf(
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
        if (perms.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            ActivityCompat.requestPermissions(this, perms, 202)
        }
    }

    override fun onResume() {
        super.onResume()
        loadMe()
        loadPendingWorkConfirmations()
        com.dainon.skep.net.WatchLink.pushIdentity(this)
        // P5-W3 제3자 SOS 대리중계 — OS 위임 백그라운드 스캔 등록(멱등). onResume 라 BLE 권한 부여 직후에도 반영.
        com.dainon.skep.service.SosRelayScanner.register(this)
    }

    private fun toggleBreak() {
        val wp = currentWp ?: return
        val token = Prefs.token(this) ?: return
        val onBreak = wp.breakStartAt != null
        btnBreakAction.isEnabled = false
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    if (onBreak) api().endBreak(token, wp.workPlanId)
                    else api().startBreak(token, wp.workPlanId)
                }
            }
            btnBreakAction.isEnabled = true
            r.onSuccess {
                Toast.makeText(this@MainActivity, if (onBreak) "휴식 종료" else "휴식 시작", Toast.LENGTH_SHORT).show()
                loadMe()
            }.onFailure {
                Toast.makeText(this@MainActivity, "실패: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun switchTab(index: Int) {
        pageAttendance.visibility = if (index == 0) View.VISIBLE else View.GONE
        pageHistory.visibility = if (index == 1) View.VISIBLE else View.GONE
        pageProfile.visibility = if (index == 2) View.VISIBLE else View.GONE
        tabAttendance.setTextColor(if (index == 0) 0xFF2563EB.toInt() else 0xFF94A3B8.toInt())
        tabHistory.setTextColor(if (index == 1) 0xFF2563EB.toInt() else 0xFF94A3B8.toInt())
        tabProfile.setTextColor(if (index == 2) 0xFF2563EB.toInt() else 0xFF94A3B8.toInt())
        if (index == 1) loadHistory()
        if (index == 2) loadProfile()
    }

    private fun loadHistory() {
        val token = Prefs.token(this) ?: return
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val api = api()
                    val att = api.listMyAttendance(token)
                    val wc = runCatching { api.listMyWorkConfirmations(token) }.getOrElse { emptyList() }
                    att to wc
                }
            }
            r.onSuccess { (att, wc) -> renderHistory(att, wc) }
                .onFailure {
                    tvHistoryEmpty.text = "불러오기 실패: ${it.message}"
                    tvHistoryEmpty.visibility = View.VISIBLE
                }
        }
    }

    private fun renderHistory(list: List<FieldApi.AttendanceItem>, wcs: List<FieldApi.WorkConfirmationItem>) {
        if (list.isEmpty()) {
            tvHistoryEmpty.visibility = View.VISIBLE
            tvHistorySummary.text = "총 근무 0회"
            tvHistoryTotal.text = "0.0시간"
            historyContainer.removeAllViews()
            return
        }
        tvHistoryEmpty.visibility = View.GONE
        val total = list.sumOf { it.hours ?: 0.0 }
        tvHistorySummary.text = "총 근무 ${list.size}회"
        tvHistoryTotal.text = "%.1f시간".format(total)

        val wcByWp = wcs.associateBy { it.workPlanId }
        val inflater = LayoutInflater.from(this)
        historyContainer.removeAllViews()

        val groups = list.groupBy { it.checkInAt?.take(10) ?: "-" }
        val sortedDates = groups.keys.sortedDescending()
        for (date in sortedDates) {
            val items = groups[date].orEmpty()
            val daySum = items.sumOf { it.hours ?: 0.0 }
            val header = TextView(this).apply {
                text = "${date.replace("-", ".")} · ${items.size}건 · %.1f시간".format(daySum)
                textSize = 12f
                setTextColor(0xFF64748B.toInt())
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, 12, 0, 6)
            }
            historyContainer.addView(header)
            for (a in items) {
                val card = inflater.inflate(R.layout.item_history, historyContainer, false)
                card.findViewById<TextView>(R.id.tvDate).text = (a.checkInAt ?: "-").take(10).replace("-", ".")
                card.findViewById<TextView>(R.id.tvHours).text = a.hours?.let { "%.1f시간".format(it) } ?: "진행 중"
                card.findViewById<TextView>(R.id.tvSite).text =
                    (a.siteName ?: "-") + (a.wpTitle?.let { " · $it" } ?: "")
                card.findViewById<TextView>(R.id.tvTime).text = buildString {
                    append(a.checkInAt?.substring(11, 16) ?: "-")
                    append(" ~ ")
                    append(a.checkOutAt?.substring(11, 16) ?: "진행 중")
                }
                val wcRow = card.findViewById<View>(R.id.wcRow)
                val tvStatus = card.findViewById<TextView>(R.id.tvWcStatus)
                val tvAction = card.findViewById<TextView>(R.id.tvWcAction)
                val wc = a.workPlanId?.let { wcByWp[it] }
                when {
                    a.checkOutAt == null -> wcRow.visibility = View.GONE
                    wc == null -> {
                        wcRow.visibility = View.VISIBLE
                        tvStatus.text = "작업확인서 생성 대기"
                        tvStatus.setTextColor(0xFF94A3B8.toInt())
                        tvAction.text = ""
                    }
                    wc.supplierSignedAt == null -> {
                        wcRow.visibility = View.VISIBLE
                        tvStatus.text = "사인 필요 · %.1f시간".format(wc.totalHours ?: 0.0)
                        tvStatus.setTextColor(0xFFC2410C.toInt())
                        tvAction.text = "사인하기 ›"
                        val click = View.OnClickListener { openWcSign(wc) }
                        tvAction.setOnClickListener(click)
                        card.setOnClickListener(click)
                    }
                    wc.bpSignedAt == null -> {
                        wcRow.visibility = View.VISIBLE
                        tvStatus.text = "✓ 사인 완료 · BP 확인 대기"
                        tvStatus.setTextColor(0xFF15803D.toInt())
                        tvAction.text = ""
                    }
                    else -> {
                        wcRow.visibility = View.VISIBLE
                        tvStatus.text = "✓ 양측 사인 완료 · %.1f시간".format(wc.totalHours ?: 0.0)
                        tvStatus.setTextColor(0xFF15803D.toInt())
                        tvAction.text = ""
                    }
                }
                historyContainer.addView(card)
            }
        }
    }

    private fun openWcSign(wc: FieldApi.WorkConfirmationItem) {
        val intent = Intent(this, WorkConfirmationActivity::class.java)
        intent.putExtra(WorkConfirmationActivity.EXTRA_WC_ID, wc.id)
        intent.putExtra(WorkConfirmationActivity.EXTRA_WC_DATE, wc.workDate)
        intent.putExtra(WorkConfirmationActivity.EXTRA_WC_HOURS, wc.totalHours ?: 0.0)
        startActivity(intent)
    }

    private fun loadProfile() {
        val token = Prefs.token(this) ?: return
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { api().me(token) } }
            r.onSuccess { me ->
                tvProfileName.text = me.name
                tvProfileSupplier.text = me.supplierName ?: "-"
                setRow(rowProfileJob, "직종", me.jobTitle ?: "-")
                setRow(rowProfileCode, "출입 코드", Prefs.token(this@MainActivity) ?: "-")
            }
        }
        // 큰 아바타도 로드
        val url = "${Prefs.serverUrl(this)}/api/field-auth/my-photo"
        scope.launch(Dispatchers.IO) {
            runCatching {
                val client = OkHttpClient()
                val req = Request.Builder().url(url).header("X-Field-Token", token).get().build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val bytes = resp.body?.bytes() ?: return@use null
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
            }.onSuccess { bmp ->
                if (bmp != null) withContext(Dispatchers.Main) { ivProfileAvatar.setImageBitmap(bmp) }
            }
        }
    }

    private fun loadPendingWorkConfirmations() {
        val token = Prefs.token(this) ?: return
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { api().listMyWorkConfirmations(token) } }
            r.onSuccess { list ->
                val pending = list.firstOrNull { it.supplierSignedAt == null }
                if (pending == null) {
                    cardPendingWc.visibility = View.GONE
                } else {
                    cardPendingWc.visibility = View.VISIBLE
                    tvPendingWcDetail.text = "${pending.workDate ?: "-"} · ${"%.2f시간".format(pending.totalHours ?: 0.0)}"
                    cardPendingWc.setOnClickListener {
                        val intent = Intent(this@MainActivity, WorkConfirmationActivity::class.java)
                        intent.putExtra(WorkConfirmationActivity.EXTRA_WC_ID, pending.id)
                        intent.putExtra(WorkConfirmationActivity.EXTRA_WC_DATE, pending.workDate)
                        intent.putExtra(WorkConfirmationActivity.EXTRA_WC_HOURS, pending.totalHours ?: 0.0)
                        startActivity(intent)
                    }
                }
            }
        }
    }

    private fun loadAvatar() {
        val token = Prefs.token(this) ?: return
        val url = "${Prefs.serverUrl(this)}/api/field-auth/my-photo"
        scope.launch(Dispatchers.IO) {
            runCatching {
                val client = OkHttpClient()
                val req = Request.Builder().url(url).header("X-Field-Token", token).get().build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val bytes = resp.body?.bytes() ?: return@use null
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
            }.onSuccess { bmp ->
                if (bmp != null) withContext(Dispatchers.Main) { ivAvatar.setImageBitmap(bmp) }
            }
        }
    }

    private fun api() = FieldApi(Prefs.serverUrl(this))

    private fun logout() {
        com.dainon.skep.service.SosRelayScanner.unregister(this)   // P5-W3 로그아웃 시 대리중계 스캔 해제.
        Prefs.clearAuth(this)
        startActivity(Intent(this, EntryActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }

    private fun openCheckIn(workPlanId: Long, isCheckOut: Boolean) {
        val intent = Intent(this, CheckInActivity::class.java)
        intent.putExtra(CheckInActivity.EXTRA_WORK_PLAN_ID, workPlanId)
        intent.putExtra(CheckInActivity.EXTRA_IS_CHECK_OUT, isCheckOut)
        startActivity(intent)
    }

    private fun loadMe() {
        val token = Prefs.token(this) ?: return
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { api().me(token) } }
            result.onSuccess { renderMe(it) }
                .onFailure { Toast.makeText(this@MainActivity, "정보 불러오기 실패", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun renderMe(me: FieldApi.MeResponse) {
        setRow(rowSupplier, "소속", me.supplierName ?: "-")
        setRow(rowName, "이름", me.name)
        setRow(rowJob, "직종", me.jobTitle ?: "-")

        val wp = me.activeWorkPlans.firstOrNull()
        currentWp = wp
        if (wp == null) {
            tvSiteName.text = "배정 현장 없음"
            tvSiteAddress.text = "-"
            setRow(rowHours, "근무 시간", "-")
            setRow(rowStatus, "상태", "대기")
            btnCheckAction.isEnabled = false
            btnCheckAction.text = "배정 대기"
            btnBreakAction.visibility = View.GONE
            tvCheckTitle.text = "배정된 작업계획서가 없습니다"
            tvNotice.text = "· 공급사로부터 작업 배정을 기다리세요."
            return
        }

        tvSiteName.text = wp.siteName ?: "-"
        tvSiteAddress.text = wp.siteAddress ?: "-"
        val hours = "${wp.startTime?.substring(0, 5) ?: "-"} ~ ${wp.endTime?.substring(0, 5) ?: "-"}"
        setRow(rowHours, "근무 시간", hours)
        btnCheckAction.isEnabled = true
        when {
            wp.openSessionId == null && wp.todayClosedSession != null -> {
                val cs = wp.todayClosedSession
                val inT = cs.checkInAt?.substring(11, 16) ?: "-"
                val outT = cs.checkOutAt?.substring(11, 16) ?: "-"
                val h = cs.hours?.let { String.format("%.2f시간", it) } ?: ""
                setRow(rowStatus, "상태", "오늘 출근 완료 ($inT → $outT · $h)")
                tvCheckTitle.text = "오늘 근무가 완료되었습니다"
                btnCheckAction.text = "오늘 완료"
                btnCheckAction.isEnabled = false
                btnCheckAction.setTextColor(0xFF64748B.toInt())
                btnCheckAction.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFE2E8F0.toInt())
                btnBreakAction.visibility = View.GONE
            }
            wp.openSessionId == null -> {
                setRow(rowStatus, "상태", "출근 전")
                tvCheckTitle.text = "출근 체크를 진행해주세요"
                btnCheckAction.text = "출근 체크"
                btnCheckAction.setTextColor(0xFFFFFFFF.toInt())
                btnCheckAction.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF2563EB.toInt())
                btnBreakAction.visibility = View.GONE
            }
            wp.breakStartAt != null -> {
                setRow(rowStatus, "상태", "휴식 중 (${wp.breakStartAt.substring(11, 16)})")
                tvCheckTitle.text = "휴식 중입니다"
                btnCheckAction.text = "퇴근 (휴식 종료 필요)"
                btnCheckAction.isEnabled = false
                btnCheckAction.setTextColor(0xFF94A3B8.toInt())
                btnCheckAction.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFF1F5F9.toInt())
                btnBreakAction.visibility = View.VISIBLE
                btnBreakAction.text = "휴식 종료"
                btnBreakAction.setTextColor(0xFF15803D.toInt())
                btnBreakAction.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFDCFCE7.toInt())
            }
            else -> {
                val breakMin = wp.breakMinutes ?: 0
                val statusText = "출근 중 (${wp.checkInAt?.substring(11, 16) ?: "-"})" +
                        if (breakMin > 0) " · 휴식 ${breakMin}분" else ""
                setRow(rowStatus, "상태", statusText)
                tvCheckTitle.text = "퇴근 체크를 진행해주세요"
                btnCheckAction.text = "퇴근 체크"
                btnCheckAction.setTextColor(0xFFFFFFFF.toInt())
                btnCheckAction.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFE53935.toInt())
                btnBreakAction.visibility = View.VISIBLE
                btnBreakAction.text = "휴식 시작"
                btnBreakAction.setTextColor(0xFFF59E0B.toInt())
                btnBreakAction.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFFEF3C7.toInt())
            }
        }

        val radius = wp.siteRadiusM ?: 100
        tvNotice.text = "· 현장 반경 ${radius}m 이내에서만 출석체크가 가능합니다.\n· 부정 출석이 확인될 경우 근무 인정이 되지 않을 수 있습니다."
    }

    private fun setRow(row: View, label: String, value: String) {
        row.findViewById<TextView>(R.id.tvLabel).text = label
        row.findViewById<TextView>(R.id.tvValue).text = value
    }

    private fun registerFcmToken() {
        val token = Prefs.token(this) ?: return
        FirebaseMessaging.getInstance().token.addOnSuccessListener { fcmToken ->
            scope.launch(Dispatchers.IO) {
                runCatching { api().registerFieldFcmToken(token, fcmToken) }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
