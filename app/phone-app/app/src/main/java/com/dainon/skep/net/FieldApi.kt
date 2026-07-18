package com.dainon.skep.net

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * skep-v2 백엔드 클라이언트.
 * - JSON snake_case
 * - 신규 인증 흐름 field-auth (코드 기반)
 * - 레거시 field (워치/FCM 호환 유지 — 새 흐름에서는 호출 안 함)
 * - 모든 호출은 동기. 호출부에서 Dispatchers.IO 코루틴/스레드로 감쌀 것.
 */
class FieldApi(private val baseUrl: String) {

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json".toMediaType()

    // ───── 신규 흐름 DTO ─────

    /** POST /api/field-auth/auth 응답. */
    data class AuthResponse(
        val token: String,
        @SerializedName("person_id") val personId: Long,
        val name: String,
        @SerializedName("job_title") val jobTitle: String?,
        @SerializedName("supplier_id") val supplierId: Long?,
        @SerializedName("supplier_name") val supplierName: String?,
        @SerializedName("has_photo") val hasPhoto: Boolean,
    )

    /** GET /api/field-auth/me — AuthResponse 필드 + active_work_plans. */
    data class MeResponse(
        val token: String,
        @SerializedName("person_id") val personId: Long,
        val name: String,
        @SerializedName("job_title") val jobTitle: String?,
        @SerializedName("supplier_id") val supplierId: Long?,
        @SerializedName("supplier_name") val supplierName: String?,
        @SerializedName("active_work_plans") val activeWorkPlans: List<WorkPlanItem>,
    )

    /** 활성 작업계획서 한 건. */
    data class WorkPlanItem(
        @SerializedName("work_plan_id") val workPlanId: Long,
        val title: String?,
        @SerializedName("work_date") val workDate: String?,
        @SerializedName("start_time") val startTime: String?,
        @SerializedName("end_time") val endTime: String?,
        @SerializedName("site_name") val siteName: String?,
        @SerializedName("site_address") val siteAddress: String?,
        @SerializedName("site_lat") val siteLat: Double?,
        @SerializedName("site_lng") val siteLng: Double?,
        @SerializedName("site_radius_m") val siteRadiusM: Int?,
        @SerializedName("open_session_id") val openSessionId: Long?,
        @SerializedName("check_in_at") val checkInAt: String?,
        @SerializedName("break_start_at") val breakStartAt: String?,
        @SerializedName("break_minutes") val breakMinutes: Int?,
        @SerializedName("today_closed_session") val todayClosedSession: ClosedSession?,
    )

    /** 오늘 이미 완료된(퇴근까지 찍은) 세션. */
    data class ClosedSession(
        val id: Long,
        @SerializedName("check_in_at") val checkInAt: String?,
        @SerializedName("check_out_at") val checkOutAt: String?,
        val hours: Double?,
    )

    /** 출/퇴근 응답. */
    data class SessionResponse(
        val id: Long,
        @SerializedName("person_id") val personId: Long,
        @SerializedName("work_plan_id") val workPlanId: Long,
        @SerializedName("check_in_at") val checkInAt: String?,
        @SerializedName("check_out_at") val checkOutAt: String?,
        val hours: Double?,
    )

    // ───── 신규 흐름 API ─────

    /** POST /api/field-auth/auth { code } — 코드로 person 인증. */
    fun authByCode(code: String): AuthResponse {
        val payload = gson.toJson(mapOf("code" to code))
        val req = Request.Builder()
            .url("$baseUrl/api/field-auth/auth")
            .post(payload.toRequestBody(jsonType))
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("auth HTTP ${resp.code}: $body")
            return gson.fromJson(body, AuthResponse::class.java)
        }
    }

    /** GET /api/field-auth/me (X-Field-Token) — 본인 정보 + 활성 작업계획서. */
    fun me(token: String): MeResponse {
        val req = Request.Builder()
            .url("$baseUrl/api/field-auth/me")
            .header("X-Field-Token", token)
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("me HTTP ${resp.code}: $body")
            return gson.fromJson(body, MeResponse::class.java)
        }
    }

    /** POST /api/field-auth/register-token (X-Field-Token) — FCM 토큰 등록. */
    fun registerFieldFcmToken(token: String, fcmToken: String): Boolean {
        val payload = gson.toJson(mapOf("fcm_token" to fcmToken))
        val req = Request.Builder()
            .url("$baseUrl/api/field-auth/register-token")
            .header("X-Field-Token", token)
            .post(payload.toRequestBody(jsonType))
            .build()
        client.newCall(req).execute().use { resp -> return resp.isSuccessful }
    }

    /** POST /api/field-auth/safety-alerts/{id}/ack (X-Field-Token) — S5' 안전알림 확인응답([확인] 버튼). */
    fun ackSafetyAlert(token: String, alertId: Long): Boolean {
        val req = Request.Builder()
            .url("$baseUrl/api/field-auth/safety-alerts/$alertId/ack")
            .header("X-Field-Token", token)
            .post("".toRequestBody(jsonType))
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("ack HTTP ${resp.code}: $body")
            return true
        }
    }

    /** GET /api/field-auth/watch-policy (X-Field-Token) — P5-W0 워치 전송 정책 JSON(원문 그대로 워치에 전달). */
    fun watchPolicyJson(token: String): String {
        val req = Request.Builder()
            .url("$baseUrl/api/field-auth/watch-policy")
            .header("X-Field-Token", token).get().build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("watch-policy HTTP ${resp.code}: $body")
            return body
        }
    }

    /** 현장 밖 체크인(서버 403 + code=OUT_OF_SITE) 예외. distance_m = 현장 중심까지 거리(m). */
    class OutOfSiteException(val distanceM: Int?) : RuntimeException("OUT_OF_SITE")

    /** POST /api/field-auth/check-in (X-Field-Token). */
    fun checkInV2(token: String, workPlanId: Long, photoKey: String?, lat: Double?, lng: Double?): SessionResponse {
        val payload = HashMap<String, Any?>()
        payload["work_plan_id"] = workPlanId
        payload["photo_key"] = photoKey
        payload["lat"] = lat
        payload["lng"] = lng
        val req = Request.Builder()
            .url("$baseUrl/api/field-auth/check-in")
            .header("X-Field-Token", token)
            .post(gson.toJson(payload).toRequestBody(jsonType))
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (resp.code == 403) {
                val ob = runCatching { gson.fromJson(body, OutOfSiteBody::class.java) }.getOrNull()
                if (ob?.code == "OUT_OF_SITE") throw OutOfSiteException(ob.distanceM)
            }
            if (!resp.isSuccessful) error("check-in HTTP ${resp.code}: $body")
            return gson.fromJson(body, SessionResponse::class.java)
        }
    }

    /** POST /api/field-auth/check-out (X-Field-Token). */
    fun checkOutV2(token: String, workPlanId: Long, photoKey: String?, lat: Double?, lng: Double?): SessionResponse {
        val payload = HashMap<String, Any?>()
        payload["work_plan_id"] = workPlanId
        payload["photo_key"] = photoKey
        payload["lat"] = lat
        payload["lng"] = lng
        val req = Request.Builder()
            .url("$baseUrl/api/field-auth/check-out")
            .header("X-Field-Token", token)
            .post(gson.toJson(payload).toRequestBody(jsonType))
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("check-out HTTP ${resp.code}: $body")
            return gson.fromJson(body, SessionResponse::class.java)
        }
    }

    /** 출/퇴근 기록 한 건. */
    data class AttendanceItem(
        val id: Long,
        @SerializedName("work_plan_id") val workPlanId: Long?,
        @SerializedName("wp_title") val wpTitle: String?,
        @SerializedName("site_name") val siteName: String?,
        @SerializedName("check_in_at") val checkInAt: String?,
        @SerializedName("check_out_at") val checkOutAt: String?,
        val hours: Double?,
    )

    /** GET /api/field-auth/my-attendance — 본인 출/퇴근 기록 (최근순). */
    fun listMyAttendance(token: String): List<AttendanceItem> {
        val req = Request.Builder()
            .url("$baseUrl/api/field-auth/my-attendance")
            .header("X-Field-Token", token).get().build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("att HTTP ${resp.code}: $body")
            return gson.fromJson(body, Array<AttendanceItem>::class.java).toList()
        }
    }

    /** 작업확인서 목록 (본인). */
    data class WorkConfirmationItem(
        val id: Long,
        @SerializedName("work_plan_id") val workPlanId: Long,
        @SerializedName("work_date") val workDate: String?,
        @SerializedName("total_hours") val totalHours: Double?,
        val status: String?,
        @SerializedName("supplier_signed_at") val supplierSignedAt: String?,
        @SerializedName("bp_signed_at") val bpSignedAt: String?,
        val remarks: String?,
    )

    /** GET /api/field-auth/work-confirmations — 본인 작업확인서 리스트. */
    fun listMyWorkConfirmations(token: String): List<WorkConfirmationItem> {
        val req = Request.Builder()
            .url("$baseUrl/api/field-auth/work-confirmations")
            .header("X-Field-Token", token).get().build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("wc list HTTP ${resp.code}: $body")
            return gson.fromJson(body, Array<WorkConfirmationItem>::class.java).toList()
        }
    }

    /** POST /api/field-auth/work-confirmations/{id}/sign — 사인 제출. */
    fun signWorkConfirmation(token: String, wcId: Long, totalHours: Double, remarks: String?, signaturePngBase64: String): WorkConfirmationItem {
        val payload = HashMap<String, Any?>()
        payload["total_hours"] = totalHours
        payload["remarks"] = remarks
        payload["signature_png_base64"] = signaturePngBase64
        val req = Request.Builder()
            .url("$baseUrl/api/field-auth/work-confirmations/$wcId/sign")
            .header("X-Field-Token", token)
            .post(gson.toJson(payload).toRequestBody(jsonType))
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("sign HTTP ${resp.code}: $body")
            return gson.fromJson(body, WorkConfirmationItem::class.java)
        }
    }

    /** POST /api/field-auth/issue-report (X-Field-Token) — 현장 문제 신고 (인원/장비) → BP사 알림. */
    fun reportIssue(token: String, workPlanId: Long, category: String, message: String): Boolean {
        val payload = HashMap<String, Any?>()
        payload["work_plan_id"] = workPlanId
        payload["category"] = category
        payload["message"] = message
        val req = Request.Builder()
            .url("$baseUrl/api/field-auth/issue-report")
            .header("X-Field-Token", token)
            .post(gson.toJson(payload).toRequestBody(jsonType))
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("issue HTTP ${resp.code}: $body")
            return true
        }
    }

    /** NFC 태그 식별 결과. category 는 EQUIPMENT 일 때만. */
    data class NfcResult(
        val type: String,
        val id: Long,
        val label: String?,
        val category: String?,
    )

    /** GET /api/field-auth/nfc/{tagId} (X-Field-Token) — 태그 → PERSON/EQUIPMENT 식별. */
    fun resolveNfc(token: String, tagId: String): NfcResult {
        val req = Request.Builder()
            .url("$baseUrl/api/field-auth/nfc/$tagId")
            .header("X-Field-Token", token).get().build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("nfc HTTP ${resp.code}: $body")
            return gson.fromJson(body, NfcResult::class.java)
        }
    }

    /** POST /api/field-auth/equipment-inspection (X-Field-Token) — 장비 일상점검 제출. items 는 체크리스트 JSON 문자열. */
    fun submitEquipmentInspection(token: String, equipmentId: Long, inspectDate: String?,
                                  items: String, photoKey: String?, notes: String?, overall: String): Boolean {
        val payload = HashMap<String, Any?>()
        payload["equipment_id"] = equipmentId
        payload["inspect_date"] = inspectDate
        payload["items"] = items
        payload["photo_key"] = photoKey
        payload["notes"] = notes
        payload["overall"] = overall
        val req = Request.Builder()
            .url("$baseUrl/api/field-auth/equipment-inspection")
            .header("X-Field-Token", token)
            .post(gson.toJson(payload).toRequestBody(jsonType))
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("inspection HTTP ${resp.code}: $body")
            return true
        }
    }

    /** POST /api/field-auth/break/start — 휴식 시작. */
    fun startBreak(token: String, workPlanId: Long): Boolean {
        val payload = gson.toJson(mapOf("work_plan_id" to workPlanId))
        val req = Request.Builder()
            .url("$baseUrl/api/field-auth/break/start")
            .header("X-Field-Token", token)
            .post(payload.toRequestBody(jsonType))
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("break start HTTP ${resp.code}: $body")
            return true
        }
    }

    /** POST /api/field-auth/break/end — 휴식 종료. */
    fun endBreak(token: String, workPlanId: Long): Boolean {
        val payload = gson.toJson(mapOf("work_plan_id" to workPlanId))
        val req = Request.Builder()
            .url("$baseUrl/api/field-auth/break/end")
            .header("X-Field-Token", token)
            .post(payload.toRequestBody(jsonType))
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("break end HTTP ${resp.code}: $body")
            return true
        }
    }

    /** POST /api/field-auth/upload-photo (multipart) — 사진 업로드 후 photo_key 반환. */
    fun uploadAttendancePhoto(token: String, file: File): String {
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, file.asRequestBody("image/jpeg".toMediaType()))
            .build()
        val req = Request.Builder()
            .url("$baseUrl/api/field-auth/upload-photo")
            .header("X-Field-Token", token)
            .post(multipart)
            .build()
        client.newCall(req).execute().use { resp ->
            val txt = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("upload HTTP ${resp.code}: $txt")
            val obj = gson.fromJson(txt, Map::class.java) as Map<*, *>
            return obj["photo_key"]?.toString() ?: error("no photo_key")
        }
    }

    // ───── 작업자/BP 로그인 (아이디·비번) ─────

    /** POST /api/field-auth/login {username,password} — 작업자 아이디/비번 로그인. AuthResponse(token=출근코드) 반환. */
    fun workerLogin(username: String, password: String): AuthResponse {
        val payload = gson.toJson(mapOf("username" to username, "password" to password))
        val req = Request.Builder().url("$baseUrl/api/field-auth/login")
            .post(payload.toRequestBody(jsonType)).build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("login HTTP ${resp.code}: $body")
            return gson.fromJson(body, AuthResponse::class.java)
        }
    }

    data class BpToken(@SerializedName("access_token") val accessToken: String?)
    data class BpMeResponse(val user: BpUser?, val company: BpCompany?)
    data class BpUser(val name: String?, val role: String?)
    data class BpCompany(val name: String?)

    /** POST /api/auth/login {email,password} — BP 로그인. access_token 반환. */
    fun bpLogin(email: String, password: String): String {
        val payload = gson.toJson(mapOf("email" to email, "password" to password))
        val req = Request.Builder().url("$baseUrl/api/auth/login")
            .post(payload.toRequestBody(jsonType)).build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("bp login HTTP ${resp.code}: $body")
            return gson.fromJson(body, BpToken::class.java).accessToken ?: error("no access_token")
        }
    }

    /** GET /api/auth/me (Bearer). */
    fun bpMe(token: String): BpMeResponse {
        val req = Request.Builder().url("$baseUrl/api/auth/me")
            .header("Authorization", "Bearer $token").get().build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("me HTTP ${resp.code}: $body")
            return gson.fromJson(body, BpMeResponse::class.java)
        }
    }

    /** POST /api/auth/register-fcm-token (Bearer) — BP FCM 토큰 등록. */
    fun registerBpFcmToken(token: String, fcmToken: String): Boolean {
        val payload = gson.toJson(mapOf("fcm_token" to fcmToken))
        val req = Request.Builder().url("$baseUrl/api/auth/register-fcm-token")
            .header("Authorization", "Bearer $token")
            .post(payload.toRequestBody(jsonType)).build()
        client.newCall(req).execute().use { resp -> return resp.isSuccessful }
    }

    data class NotificationItem(
        val id: Long,
        val type: String?,
        val title: String?,
        val message: String?,
        @SerializedName("created_at") val createdAt: String?,
    )
    private data class NotificationPage(val content: List<NotificationItem>?)

    /** GET /api/notifications (Bearer) — BP 받은 알림. */
    fun listBpNotifications(token: String): List<NotificationItem> {
        val req = Request.Builder().url("$baseUrl/api/notifications?size=50")
            .header("Authorization", "Bearer $token").get().build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("noti HTTP ${resp.code}: $body")
            return gson.fromJson(body, NotificationPage::class.java).content ?: emptyList()
        }
    }

    data class BpWcItem(
        val id: Long,
        @SerializedName("work_date") val workDate: String?,
        @SerializedName("total_hours") val totalHours: Double?,
        @SerializedName("person_name") val personName: String?,
        @SerializedName("wp_title") val wpTitle: String?,
    )

    /** GET /api/work-confirmations/bp-pending (Bearer) — BP 서명 대기 목록. */
    fun listBpPendingWc(token: String): List<BpWcItem> {
        val req = Request.Builder().url("$baseUrl/api/work-confirmations/bp-pending")
            .header("Authorization", "Bearer $token").get().build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("bp-pending HTTP ${resp.code}: $body")
            return gson.fromJson(body, Array<BpWcItem>::class.java).toList()
        }
    }

    /** POST /api/work-confirmations/{id}/sign-bp (Bearer) {pngBase64} — BP 서명. */
    fun signBpWc(token: String, wcId: Long, pngBase64: String): Boolean {
        val payload = gson.toJson(mapOf("pngBase64" to pngBase64))
        val req = Request.Builder().url("$baseUrl/api/work-confirmations/$wcId/sign-bp")
            .header("Authorization", "Bearer $token")
            .post(payload.toRequestBody(jsonType)).build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("sign-bp HTTP ${resp.code}: $body")
            return true
        }
    }

    // ───── BP 현장 보드 (Bearer) ─────

    data class BoardConfirmation(
        val id: Long,
        @SerializedName("work_date") val workDate: String?,
        @SerializedName("total_hours") val totalHours: Double?,
        @SerializedName("signed_by_supplier") val signedBySupplier: Boolean?,
        @SerializedName("signed_by_bp") val signedByBp: Boolean?,
    )

    data class BoardItem(
        @SerializedName("resource_type") val resourceType: String?,   // PERSON | EQUIPMENT
        @SerializedName("resource_id") val resourceId: Long?,
        @SerializedName("resource_label") val resourceLabel: String?,
        @SerializedName("supplier_company_name") val supplierCompanyName: String?,
        @SerializedName("target_site_id") val targetSiteId: Long?,
        @SerializedName("target_site_name") val targetSiteName: String?,
        @SerializedName("total_days") val totalDays: Int?,
        @SerializedName("total_hours") val totalHours: Double?,
        @SerializedName("today_attended") val todayAttended: Boolean?,
        @SerializedName("recent_confirmations") val recentConfirmations: List<BoardConfirmation>?,
    )

    /** GET /api/field-deployments/bp/board (Bearer) — BP 투입 현황(자원별). */
    fun listBpBoard(token: String): List<BoardItem> {
        val req = Request.Builder().url("$baseUrl/api/field-deployments/bp/board")
            .header("Authorization", "Bearer $token").get().build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("board HTTP ${resp.code}: $body")
            return gson.fromJson(body, Array<BoardItem>::class.java).toList()
        }
    }

    data class ResourceCheckItem(
        val id: Long,
        @SerializedName("owner_type") val ownerType: String?,   // PERSON | EQUIPMENT
        @SerializedName("owner_id") val ownerId: Long?,
        @SerializedName("check_type") val checkType: String?,
        val status: String?,    // REQUESTED | SUBMITTED | APPROVED | REJECTED | CANCELLED
    )

    /** GET /api/resource-checks/bp-list (Bearer) — BP가 발행한 점검 요청 목록. */
    fun listBpResourceChecks(token: String): List<ResourceCheckItem> {
        val req = Request.Builder().url("$baseUrl/api/resource-checks/bp-list")
            .header("Authorization", "Bearer $token").get().build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("checks HTTP ${resp.code}: $body")
            return gson.fromJson(body, Array<ResourceCheckItem>::class.java).toList()
        }
    }

    // ───── 공급사 (Bearer) ─────

    data class SupplierWcItem(
        val id: Long,
        @SerializedName("work_date") val workDate: String?,
        @SerializedName("total_hours") val totalHours: Double?,
        @SerializedName("person_name") val personName: String?,
        @SerializedName("wp_title") val wpTitle: String?,
        @SerializedName("supplier_signed_at") val supplierSignedAt: String?,
        @SerializedName("bp_signed_at") val bpSignedAt: String?,
        val status: String?,
    )

    /** GET /api/work-confirmations/supplier-list (Bearer) — 공급사 발급 작업확인서 + 서명상태. */
    fun listSupplierWc(token: String): List<SupplierWcItem> {
        val req = Request.Builder().url("$baseUrl/api/work-confirmations/supplier-list")
            .header("Authorization", "Bearer $token").get().build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("supplier-wc HTTP ${resp.code}: $body")
            return gson.fromJson(body, Array<SupplierWcItem>::class.java).toList()
        }
    }

    data class EquipmentItem(
        val id: Long,
        @SerializedName("vehicle_no") val vehicleNo: String?,
        val category: String?,
        val model: String?,
        @SerializedName("current_site_name") val currentSiteName: String?,
    )

    /** GET /api/equipment (Bearer) — 내 회사 장비 목록. */
    fun listMyEquipment(token: String): List<EquipmentItem> {
        val req = Request.Builder().url("$baseUrl/api/equipment")
            .header("Authorization", "Bearer $token").get().build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("equipment HTTP ${resp.code}: $body")
            return gson.fromJson(body, Array<EquipmentItem>::class.java).toList()
        }
    }

    data class InspectionItem(
        val id: Long,
        @SerializedName("inspect_date") val inspectDate: String?,
        val items: String?,     // 체크리스트 JSON 문자열 [{key,label,result,note}]
        val notes: String?,
        val overall: String?,   // PASS | ATTENTION | FAIL
        @SerializedName("inspector_name") val inspectorName: String?,
    )

    /** GET /api/equipment/{id}/daily-inspections (Bearer) — 장비 일상점검 이력. */
    fun listEquipmentInspections(token: String, equipmentId: Long): List<InspectionItem> {
        val req = Request.Builder().url("$baseUrl/api/equipment/$equipmentId/daily-inspections")
            .header("Authorization", "Bearer $token").get().build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("inspections HTTP ${resp.code}: $body")
            return gson.fromJson(body, Array<InspectionItem>::class.java).toList()
        }
    }

    /** GET /api/work-confirmations/{id}/pdf (Bearer) — 작업확인서 PDF 바이트. */
    fun downloadWcPdf(token: String, wcId: Long): ByteArray {
        val req = Request.Builder().url("$baseUrl/api/work-confirmations/$wcId/pdf")
            .header("Authorization", "Bearer $token").get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("pdf HTTP ${resp.code}")
            return resp.body?.bytes() ?: error("빈 PDF")
        }
    }

    // ───── 받은 요청 처리 (공급사 점검요청·이행지시 / BP 투입요청) ─────

    data class ResourceCheckInbox(
        val id: Long,
        @SerializedName("owner_label") val ownerLabel: String?,
        @SerializedName("check_type") val checkType: String?,
        @SerializedName("due_date") val dueDate: String?,
        val notes: String?,
        val status: String?,    // REQUESTED | SUBMITTED | APPROVED | REJECTED | CANCELLED
    )

    /** GET /api/resource-checks/supplier-list (Bearer) — 공급사가 받은 점검요청. */
    fun listSupplierChecks(token: String): List<ResourceCheckInbox> {
        val req = Request.Builder().url("$baseUrl/api/resource-checks/supplier-list")
            .header("Authorization", "Bearer $token").get().build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("checks HTTP ${resp.code}: $body")
            return gson.fromJson(body, Array<ResourceCheckInbox>::class.java).toList()
        }
    }

    /** POST /api/resource-checks/{id}/submit-file (multipart file) — 점검 증빙 제출. */
    fun submitCheckFile(token: String, checkId: Long, fileName: String, mime: String, bytes: ByteArray): Boolean {
        val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", fileName, bytes.toRequestBody(mime.toMediaType()))
            .build()
        val req = Request.Builder().url("$baseUrl/api/resource-checks/$checkId/submit-file")
            .header("Authorization", "Bearer $token").post(multipart).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("check submit HTTP ${resp.code}: ${resp.body?.string().orEmpty()}")
            return true
        }
    }

    data class ComplianceInbox(
        val id: Long,
        @SerializedName("target_label") val targetLabel: String?,
        @SerializedName("order_type") val orderType: String?,
        @SerializedName("order_subtype") val orderSubtype: String?,
        @SerializedName("due_date") val dueDate: String?,
        @SerializedName("request_notes") val requestNotes: String?,
        val status: String?,    // REQUESTED | SUBMITTED | APPROVED | REJECTED
    )

    /** GET /api/compliance-orders?scope=supplier (Bearer) — 공급사가 받은 이행지시. */
    fun listSupplierComplianceOrders(token: String): List<ComplianceInbox> {
        val req = Request.Builder().url("$baseUrl/api/compliance-orders?scope=supplier")
            .header("Authorization", "Bearer $token").get().build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("orders HTTP ${resp.code}: $body")
            return gson.fromJson(body, Array<ComplianceInbox>::class.java).toList()
        }
    }

    /** POST /{id}/proof (multipart file) → POST /{id}/submit — 이행지시 증빙 업로드 후 제출. */
    fun submitComplianceProof(token: String, orderId: Long, fileName: String, mime: String, bytes: ByteArray): Boolean {
        val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", fileName, bytes.toRequestBody(mime.toMediaType()))
            .build()
        val up = Request.Builder().url("$baseUrl/api/compliance-orders/$orderId/proof")
            .header("Authorization", "Bearer $token").post(multipart).build()
        client.newCall(up).execute().use { resp ->
            if (!resp.isSuccessful) error("proof HTTP ${resp.code}: ${resp.body?.string().orEmpty()}")
        }
        val submit = Request.Builder().url("$baseUrl/api/compliance-orders/$orderId/submit")
            .header("Authorization", "Bearer $token")
            .post(gson.toJson(mapOf("submission_notes" to "")).toRequestBody(jsonType)).build()
        client.newCall(submit).execute().use { resp ->
            if (!resp.isSuccessful) error("submit HTTP ${resp.code}: ${resp.body?.string().orEmpty()}")
            return true
        }
    }

    data class DeploymentInbox(
        val id: Long,
        @SerializedName("resource_type") val resourceType: String?,
        @SerializedName("resource_label") val resourceLabel: String?,
        @SerializedName("supplier_company_name") val supplierCompanyName: String?,
        @SerializedName("target_site_name") val targetSiteName: String?,
        @SerializedName("start_date") val startDate: String?,
        val note: String?,
        val status: String?,    // REQUESTED = BP 처리 대기
    )

    /** GET /api/field-deployments/bp (Bearer) — BP가 받은 투입요청. */
    fun listBpDeployments(token: String): List<DeploymentInbox> {
        val req = Request.Builder().url("$baseUrl/api/field-deployments/bp")
            .header("Authorization", "Bearer $token").get().build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("deployments HTTP ${resp.code}: $body")
            return gson.fromJson(body, Array<DeploymentInbox>::class.java).toList()
        }
    }

    /** POST /api/field-deployments/{id}/accept|reject (Bearer). */
    fun reviewDeployment(token: String, id: Long, accept: Boolean, note: String?): Boolean {
        val action = if (accept) "accept" else "reject"
        val req = Request.Builder().url("$baseUrl/api/field-deployments/$id/$action")
            .header("Authorization", "Bearer $token")
            .post(gson.toJson(mapOf("note" to note)).toRequestBody(jsonType)).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("$action HTTP ${resp.code}: ${resp.body?.string().orEmpty()}")
            return true
        }
    }

    // ───── 현장 상황 (BP·공급사 공용, Bearer) ─────

    data class SiteBrief(
        val id: Long,
        val name: String?,
        val address: String?,
    )

    /** GET /api/sites (Bearer) — 역할 스코프 현장 목록(BP=자사, 공급사=참여). */
    fun listSites(token: String): List<SiteBrief> {
        val req = Request.Builder().url("$baseUrl/api/sites")
            .header("Authorization", "Bearer $token").get().build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("sites HTTP ${resp.code}: $body")
            return gson.fromJson(body, Array<SiteBrief>::class.java).toList()
        }
    }

    data class SiteSituation(
        val site: SituationSite?,
        val attendance: SituationAttendance?,
        val safety: SituationSafety?,
        val weather: SituationWeather?,
        @SerializedName("work_progress") val workProgress: SituationWorkProgress?,
        val resources: SituationResources?,
    )

    data class SituationSite(
        val name: String?,
        val address: String?,
        @SerializedName("detail_address") val detailAddress: String?,
        @SerializedName("map_url") val mapUrl: String?,   // null이면 좌표 미설정
    )

    data class SituationAttendance(
        @SerializedName("checked_in_count") val checkedInCount: Int?,
        val workers: List<SituationWorker>?,
    )

    data class SituationWorker(
        val name: String?,
        @SerializedName("supplier_name") val supplierName: String?,
        @SerializedName("check_in_at") val checkInAt: String?,
        @SerializedName("on_break") val onBreak: Boolean?,
    )

    data class SituationSafety(
        @SerializedName("unresolved_count") val unresolvedCount: Int?,
        val alerts: List<SituationAlert>?,
    )

    data class SituationAlert(
        @SerializedName("person_name") val personName: String?,
        val kind: String?,
        val level: String?,
        val resolved: Boolean?,
        @SerializedName("created_at") val createdAt: String?,
    )

    data class SituationWeather(
        val available: Boolean?,
        @SerializedName("temp_c") val tempC: Double?,
        val humidity: Double?,
        @SerializedName("feels_like") val feelsLike: Double?,
        @SerializedName("stage_label") val stageLabel: String?,
    )

    data class SituationWorkProgress(
        @SerializedName("today_total") val todayTotal: Int?,
        val plans: List<SituationPlan>?,
    )

    data class SituationPlan(
        val title: String?,
        val status: String?,
        @SerializedName("start_time") val startTime: String?,
        @SerializedName("end_time") val endTime: String?,
    )

    data class SituationResources(
        @SerializedName("equipment_count") val equipmentCount: Int?,
        @SerializedName("person_count") val personCount: Int?,
        val items: List<SituationResourceItem>?,
    )

    data class SituationResourceItem(
        @SerializedName("resource_type") val resourceType: String?,   // EQUIPMENT | PERSON
        val label: String?,
        @SerializedName("supplier_company_name") val supplierCompanyName: String?,
        @SerializedName("today_attended") val todayAttended: Boolean?,
    )

    /** GET /api/field/site-situation/{siteId} (Bearer) — 현장 하나의 6요소 통합. */
    fun getSiteSituation(token: String, siteId: Long): SiteSituation {
        val req = Request.Builder().url("$baseUrl/api/field/site-situation/$siteId")
            .header("Authorization", "Bearer $token").get().build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("site-situation HTTP ${resp.code}: $body")
            return gson.fromJson(body, SiteSituation::class.java)
        }
    }

    // ───── 레거시 흐름 (워치/FCM 호환) ─────

    data class Site(
        val id: Long,
        val name: String,
        @SerializedName("center_lat") val centerLat: Double,
        @SerializedName("center_lng") val centerLng: Double,
        @SerializedName("radius_m") val radiusM: Int,
    )

    data class RegisterResponse(
        @SerializedName("worker_id") val workerId: String,
        val token: String,
        val site: Site,
    )

    sealed class CheckInResult {
        data class Inside(val checkedInAt: String) : CheckInResult()
        data class OutOfSite(val distanceM: Int) : CheckInResult()
        data class Error(val code: Int, val message: String) : CheckInResult()
    }

    private data class CheckInOk(@SerializedName("checked_in_at") val checkedInAt: String?)
    private data class OutOfSiteBody(val code: String?, @SerializedName("distance_m") val distanceM: Int?)

    /** POST /api/field-auth/emergency (X-Field-Token) — 워치 안전알림 중계. 토큰이 person 식별. */
    fun emergencyAlert(
        authToken: String,
        kind: String,
        hr: Int?,
        spo2: Int?,
        lat: Double?,
        lng: Double?,
    ): Boolean {
        val payload = HashMap<String, Any?>()
        payload["kind"] = kind
        payload["level"] = "danger"
        payload["hr"] = hr
        payload["spo2"] = spo2
        payload["lat"] = lat
        payload["lng"] = lng
        val req = Request.Builder()
            .url("$baseUrl/api/field-auth/emergency")
            .header("X-Field-Token", authToken)
            .post(gson.toJson(payload).toRequestBody(jsonType))
            .build()
        client.newCall(req).execute().use { resp -> return resp.isSuccessful }
    }
}
