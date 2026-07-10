package com.dainon.skep.data

import com.dainon.skep.BuildConfig
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * 서버 HTTP 통신 — skep-v2 백엔드 /api/field-auth 직결.
 * 인증: 출퇴근 코드(attendance_code) = fieldToken → X-Field-Token 헤더.
 * getAIInsight/getWorkerRegistry 는 skep-v2 에 대응 endpoint 없음 — 호출자 정리 필요(SensorService, MainActivity).
 */
object ServerClient {

    private val gson = Gson()
    private val JSON_TYPE = "application/json".toMediaType()
    var baseUrl = BuildConfig.SERVER_URL
    var fieldToken: String = ""

    fun updateUrl(url: String) { if (url.isNotBlank()) baseUrl = url }
    fun updateFieldToken(token: String) { if (token.isNotBlank()) fieldToken = token }

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /** X-Field-Token 인증된 Request.Builder. 토큰 없으면 null 반환. */
    private fun authedBuilder(path: String): Request.Builder? {
        if (fieldToken.isBlank()) return null
        return Request.Builder()
            .url("$baseUrl$path")
            .header("X-Field-Token", fieldToken)
    }

    /** 긴급 안전알림 전송. POST /api/field-auth/emergency */
    suspend fun sendSafetyAlert(
        workerId: String, kind: String, hr: Int, spo2: Int, lat: Double?, lng: Double?
    ): Boolean = withContext(Dispatchers.IO) {
        // lat/lng 가 null 이면 키 자체를 제외 — 백엔드는 좌표 없는 행으로 저장하고 지도에 안 찍힘.
        val body = gson.toJson(buildMap<String, Any> {
            put("kind", kind); put("level", "danger")
            put("hr", hr); put("spo2", spo2)
            if (lat != null) put("lat", lat); if (lng != null) put("lng", lng)
        }).toRequestBody(JSON_TYPE)
        val req = authedBuilder("/api/field-auth/emergency")?.post(body)?.build()
            ?: return@withContext false
        runCatching { client.newCall(req).execute().use { it.isSuccessful } }.getOrDefault(false)
    }

    /** 5분 주기 센서 데이터 전송. POST /api/field-auth/sensor */
    suspend fun sendSensorData(data: SensorPayload): Boolean = withContext(Dispatchers.IO) {
        val body = gson.toJson(buildMap<String, Any> {
            put("hr", data.heartRate); put("spo2", data.spo2); put("bodyTemp", data.bodyTemp)
            put("stress", data.stress); put("state", data.status)
            if (data.latitude != null) put("lat", data.latitude)
            if (data.longitude != null) put("lng", data.longitude)
        }).toRequestBody(JSON_TYPE)
        val req = authedBuilder("/api/field-auth/sensor")?.post(body)?.build()
            ?: return@withContext false
        runCatching { client.newCall(req).execute().use { it.isSuccessful } }.getOrDefault(false)
    }

    /** 최근 알림 가져오기. GET /api/field-auth/alerts/recent */
    suspend fun getAlerts(limit: Int = 20): List<AlertMessage> = withContext(Dispatchers.IO) {
        val req = authedBuilder("/api/field-auth/alerts/recent?limit=$limit")?.get()?.build()
            ?: return@withContext emptyList()
        runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val type = object : TypeToken<List<AlertMessage>>() {}.type
                gson.fromJson<List<AlertMessage>>(resp.body?.string() ?: "[]", type)
            }
        }.getOrDefault(emptyList())
    }

    /** 워치 FCM 토큰 등록. POST /api/field-auth/register-watch-token */
    suspend fun registerWatchFcmToken(fcmToken: String): Boolean = withContext(Dispatchers.IO) {
        if (fcmToken.isBlank()) return@withContext false
        val body = gson.toJson(mapOf("fcm_token" to fcmToken)).toRequestBody(JSON_TYPE)
        val req = authedBuilder("/api/field-auth/register-watch-token")?.post(body)?.build()
            ?: return@withContext false
        runCatching { client.newCall(req).execute().use { it.isSuccessful } }.getOrDefault(false)
    }

    /** 베이스라인 서버 동기화. POST /api/field-auth/baseline/sync */
    suspend fun syncBaseline(data: Map<String, Any>): Boolean = withContext(Dispatchers.IO) {
        val body = gson.toJson(data).toRequestBody(JSON_TYPE)
        val req = authedBuilder("/api/field-auth/baseline/sync")?.post(body)?.build()
            ?: return@withContext false
        runCatching { client.newCall(req).execute().use { it.isSuccessful } }.getOrDefault(false)
    }

    /** 베이스라인 서버에서 복원. GET /api/field-auth/baseline/restore */
    @Suppress("UNCHECKED_CAST")
    suspend fun restoreBaseline(workerId: String): Map<String, Any>? = withContext(Dispatchers.IO) {
        val req = authedBuilder("/api/field-auth/baseline/restore")?.get()?.build()
            ?: return@withContext null
        runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                gson.fromJson(resp.body?.string() ?: return@use null, Map::class.java) as Map<String, Any>
            }
        }.getOrNull()
    }

    /** AI 인사이트 가져오기. 대응 endpoint 없음 — 호출자 정리 전 임시 stub. */
    suspend fun getAIInsight(): RiskStatus? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("$baseUrl/api/public-data/ai-insight").get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                gson.fromJson(resp.body?.string() ?: return@use null, RiskStatus::class.java)
            }
        }.getOrNull()
    }

    /** 작업자 레지스트리 (P2P 이름 표시). 대응 endpoint 없음 — 호출자 정리 전 임시 stub. */
    suspend fun getWorkerRegistry(): String = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("$baseUrl/api/workers").get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use ""
                val type = object : TypeToken<List<Map<String, Any>>>() {}.type
                val workers: List<Map<String, Any>> = gson.fromJson(resp.body?.string() ?: "[]", type)
                workers.joinToString(",") { "${it["id"]}:${it["name"]}" }
            }
        }.getOrDefault("")
    }
}
