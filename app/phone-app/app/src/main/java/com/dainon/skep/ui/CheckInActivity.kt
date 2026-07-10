package com.dainon.skep.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.dainon.skep.R
import com.dainon.skep.net.FieldApi
import com.dainon.skep.net.Prefs
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 출/퇴근 체크 화면 — GPS로 현장과의 거리 확인 후 서버에 전송.
 * 진입: MainActivity 가 work_plan_id + is_check_out 인텐트로 전달.
 */
class CheckInActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_WORK_PLAN_ID = "work_plan_id"
        const val EXTRA_IS_CHECK_OUT = "is_check_out"
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var fused: FusedLocationProviderClient

    private lateinit var btnBack: Button
    private lateinit var tvSiteName: TextView
    private lateinit var tvSiteAddress: TextView
    private lateinit var tvRadius: TextView
    private lateinit var mapWebView: WebView
    private lateinit var tvResultIcon: TextView
    private lateinit var tvResultTitle: TextView
    private lateinit var tvResultDetail: TextView
    private lateinit var tvActionTitle: TextView
    private lateinit var btnAction: Button
    private lateinit var rowDistance: View
    private lateinit var rowTime: View
    private lateinit var rowAddress: View
    private lateinit var ivPhoto: ImageView
    private lateinit var tvPhotoEmpty: TextView
    private lateinit var btnTakePhoto: Button

    private var workPlan: FieldApi.WorkPlanItem? = null
    private var isCheckOut: Boolean = false
    private var insideRange: Boolean = false
    private var lastLocation: Location? = null
    private var photoFile: File? = null
    private var photoUri: Uri? = null
    private var photoKey: String? = null

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = photoFile
        if (success && file != null && file.exists()) {
            ivPhoto.setImageURI(null)
            ivPhoto.setImageURI(photoUri)
            tvPhotoEmpty.visibility = View.GONE
            btnTakePhoto.text = "다시 촬영"
            uploadPhoto(file)
        }
    }

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera()
        else Toast.makeText(this, "카메라 권한이 필요합니다", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_check_in)
        fused = LocationServices.getFusedLocationProviderClient(this)

        isCheckOut = intent.getBooleanExtra(EXTRA_IS_CHECK_OUT, false)
        val wpId = intent.getLongExtra(EXTRA_WORK_PLAN_ID, -1L)

        btnBack = findViewById(R.id.btnBack)
        tvSiteName = findViewById(R.id.tvSiteName)
        tvSiteAddress = findViewById(R.id.tvSiteAddress)
        tvRadius = findViewById(R.id.tvRadius)
        mapWebView = findViewById(R.id.mapWebView)
        @SuppressLint("SetJavaScriptEnabled")
        mapWebView.settings.javaScriptEnabled = true
        mapWebView.settings.domStorageEnabled = true
        mapWebView.settings.setSupportZoom(true)
        mapWebView.settings.builtInZoomControls = false
        mapWebView.settings.displayZoomControls = false
        // 부모 ScrollView 가 가로채면 카카오맵 핀치 줌이 안 먹힘.
        mapWebView.setOnTouchListener { v, _ ->
            v.parent.requestDisallowInterceptTouchEvent(true)
            false
        }
        tvResultIcon = findViewById(R.id.tvResultIcon)
        tvResultTitle = findViewById(R.id.tvResultTitle)
        tvResultDetail = findViewById(R.id.tvResultDetail)
        tvActionTitle = findViewById(R.id.tvActionTitle)
        btnAction = findViewById(R.id.btnAction)
        rowDistance = findViewById(R.id.rowDistance)
        rowTime = findViewById(R.id.rowTime)
        rowAddress = findViewById(R.id.rowAddress)
        ivPhoto = findViewById(R.id.ivPhoto)
        tvPhotoEmpty = findViewById(R.id.tvPhotoEmpty)
        btnTakePhoto = findViewById(R.id.btnTakePhoto)
        btnTakePhoto.setOnClickListener { onTakePhotoClicked() }

        setRow(rowDistance, "현장과의 거리", "확인 중...")
        setRow(rowTime, "확인 시간", "-")
        setRow(rowAddress, "위치", "-")

        if (isCheckOut) {
            tvActionTitle.text = "퇴근 체크"
            btnAction.text = "퇴근 체크하기"
            btnAction.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFE53935.toInt())
        }

        btnBack.setOnClickListener { finish() }
        btnAction.setOnClickListener { performAction() }
        btnAction.isEnabled = false

        loadWorkPlan(wpId)
    }

    private fun api() = FieldApi(Prefs.serverUrl(this))

    private fun loadWorkPlan(wpId: Long) {
        val token = Prefs.token(this) ?: return
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { api().me(token) } }
            result.onSuccess { me ->
                val wp = me.activeWorkPlans.firstOrNull { it.workPlanId == wpId }
                if (wp == null) {
                    Toast.makeText(this@CheckInActivity, "작업계획서를 찾을 수 없습니다", Toast.LENGTH_SHORT).show()
                    finish()
                    return@onSuccess
                }
                workPlan = wp
                tvSiteName.text = wp.siteName ?: "-"
                tvSiteAddress.text = wp.siteAddress ?: "-"
                setRow(rowAddress, "위치", wp.siteAddress ?: "-")
                checkLocation()
            }.onFailure {
                Toast.makeText(this@CheckInActivity, "정보 불러오기 실패", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkLocation() {
        if (!hasLocationPermission()) {
            ActivityCompat.requestPermissions(this, arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ), 100)
            return
        }
        fetchLocation { loc ->
            lastLocation = loc
            if (loc == null) {
                tvResultIcon.text = "!"
                tvResultIcon.setTextColor(0xFFE53935.toInt())
                tvResultTitle.text = "위치 확인 실패"
                tvResultDetail.text = "GPS 신호를 받지 못했습니다."
                return@fetchLocation
            }
            val wp = workPlan ?: return@fetchLocation
            val now = SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.KOREAN).format(Date())
            setRow(rowTime, "확인 시간", now)
            if (wp.siteLat == null || wp.siteLng == null) {
                setRow(rowDistance, "현장과의 거리", "현장 좌표 없음")
                tvResultIcon.text = "!"
                tvResultIcon.setTextColor(0xFFFFB74D.toInt())
                tvResultTitle.text = "현장 좌표 미설정"
                tvResultDetail.text = "관리자에게 현장 좌표 설정을 요청하세요."
                insideRange = true // 좌표 없으면 일단 허용
                btnAction.isEnabled = true
                return@fetchLocation
            }
            val results = FloatArray(1)
            Location.distanceBetween(loc.latitude, loc.longitude, wp.siteLat, wp.siteLng, results)
            val distance = results[0].toInt()
            val radius = wp.siteRadiusM ?: 300
            setRow(rowDistance, "현장과의 거리", "${distance}m")
            loadMap(wp, loc)
            if (distance <= radius) {
                insideRange = true
                tvResultIcon.text = "✓"
                tvResultIcon.setTextColor(0xFF43A047.toInt())
                tvResultTitle.text = "현장 범위 내에 있습니다"
                tvResultDetail.text = "출/퇴근 체크를 진행할 수 있습니다."
                btnAction.isEnabled = true
            } else {
                insideRange = false
                tvResultIcon.text = "✗"
                tvResultIcon.setTextColor(0xFFE53935.toInt())
                tvResultTitle.text = "현장 범위 밖입니다"
                tvResultDetail.text = "현장 ${radius}m 이내로 이동해주세요. (현재 ${distance}m)"
                btnAction.isEnabled = false
            }
        }
    }

    private fun performAction() {
        val wp = workPlan ?: return
        val token = Prefs.token(this) ?: return
        val loc = lastLocation
        btnAction.isEnabled = false
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    if (isCheckOut) api().checkOutV2(token, wp.workPlanId, photoKey, loc?.latitude, loc?.longitude)
                    else api().checkInV2(token, wp.workPlanId, photoKey, loc?.latitude, loc?.longitude)
                }
            }
            r.onSuccess { s ->
                val msg = if (isCheckOut) {
                    val hours = s.hours?.let { String.format("%.1f시간", it) } ?: ""
                    "퇴근 완료 $hours"
                } else "출근 완료"
                Toast.makeText(this@CheckInActivity, msg, Toast.LENGTH_SHORT).show()
                finish()
            }.onFailure {
                btnAction.isEnabled = true
                val msg = if (it is FieldApi.OutOfSiteException) {
                    it.distanceM?.let { d -> "현장 밖입니다 (거리 ${d}m)" } ?: "현장 밖입니다"
                } else "실패: ${it.message}"
                Toast.makeText(this@CheckInActivity, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun onTakePhotoClicked() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            cameraPermission.launch(android.Manifest.permission.CAMERA)
            return
        }
        launchCamera()
    }

    private fun launchCamera() {
        val file = File(cacheDir, "att_${System.currentTimeMillis()}.jpg")
        photoFile = file
        photoUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        takePicture.launch(photoUri)
    }

    private fun uploadPhoto(file: File) {
        val token = Prefs.token(this) ?: return
        photoKey = null
        btnTakePhoto.isEnabled = false
        btnTakePhoto.text = "업로드 중..."
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { api().uploadAttendancePhoto(token, file) } }
            btnTakePhoto.isEnabled = true
            r.onSuccess { key ->
                photoKey = key
                btnTakePhoto.text = "다시 촬영"
                Toast.makeText(this@CheckInActivity, "사진 첨부 완료", Toast.LENGTH_SHORT).show()
            }.onFailure {
                btnTakePhoto.text = "다시 촬영"
                Toast.makeText(this@CheckInActivity, "사진 업로드 실패: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadMap(wp: FieldApi.WorkPlanItem, loc: Location?) {
        if (wp.siteLat == null || wp.siteLng == null) return
        val base = Prefs.serverUrl(this)
        val radius = wp.siteRadiusM ?: 300
        val userPart = if (loc != null) {
            val acc = if (loc.hasAccuracy()) "&accuracy=${loc.accuracy.toInt()}" else ""
            "&user_lat=${loc.latitude}&user_lng=${loc.longitude}$acc"
        } else ""
        val url = "$base/api/field-auth/map?lat=${wp.siteLat}&lng=${wp.siteLng}&radius=$radius$userPart"
        mapWebView.loadUrl(url)
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun fetchLocation(onResult: (Location?) -> Unit) {
        val req = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .build()
        fused.getCurrentLocation(req, null)
            .addOnSuccessListener { loc ->
                if (loc != null) onResult(loc)
                else fused.lastLocation
                    .addOnSuccessListener { onResult(it) }
                    .addOnFailureListener { onResult(null) }
            }
            .addOnFailureListener { onResult(null) }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
            checkLocation()
        }
    }

    private fun setRow(row: View, label: String, value: String) {
        row.findViewById<TextView>(R.id.tvLabel).text = label
        row.findViewById<TextView>(R.id.tvValue).text = value
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
