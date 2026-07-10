package com.dainon.skep.service

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.*
import com.google.gson.Gson

/**
 * Wearable Data Layer API — 워치↔폰 통신
 * Tasks.await() 대신 완전 비동기 처리
 */
object WearableCommService {

    private const val TAG = "WearComm"
    private const val PATH_STATUS = "/skep/status"
    private const val PATH_SAFETY = "/skep/safety"   // 안전알림: 폰이 POST /api/field-auth/emergency 로 중계
    private const val PATH_ACK = "/skep/ack"

    private val gson = Gson()

    /** 상태 업데이트를 폰에 전송 */
    fun sendStatus(context: Context, status: Map<String, Any>) {
        sendToPhone(context, PATH_STATUS, gson.toJson(status).toByteArray())
    }

    /**
     * 안전알림을 폰에 전송 → 폰이 POST /api/field-auth/emergency 로 중계.
     * body (snake_case): {worker_id, kind, hr, spo2, lat?, lng?}
     * kind: emergency / fall / abnormal_vital / manual ...
     * lat/lng 는 옵션 — 좌표 못 잡았으면 키 자체를 제외한다.
     */
    fun sendSafetyAlert(
        context: Context,
        workerId: String,
        kind: String,
        hr: Int,
        spo2: Int,
        lat: Double?,
        lng: Double?
    ) {
        val data = buildMap<String, Any> {
            put("worker_id", workerId)
            put("kind", kind)
            put("hr", hr)
            put("spo2", spo2)
            if (lat != null) put("lat", lat)
            if (lng != null) put("lng", lng)
        }
        sendToPhone(context, PATH_SAFETY, gson.toJson(data).toByteArray())
    }

    /** 확인(ACK)을 폰에 전송 */
    fun sendAck(context: Context, workerId: String) {
        sendToPhone(context, PATH_ACK, gson.toJson(mapOf("workerId" to workerId, "action" to "ack")).toByteArray())
    }

    /** 비동기로 폰에 메시지 전송 (Tasks.await 사용 안 함) */
    private fun sendToPhone(context: Context, path: String, data: ByteArray) {
        try {
            Wearable.getNodeClient(context).connectedNodes
                .addOnSuccessListener { nodes ->
                    val phoneNode = nodes.firstOrNull()
                    if (phoneNode != null) {
                        Wearable.getMessageClient(context).sendMessage(phoneNode.id, path, data)
                            .addOnSuccessListener {
                                Log.d(TAG, "📱 Sent to phone: $path (${data.size}bytes)")
                            }
                            .addOnFailureListener { e ->
                                Log.w(TAG, "Send failed: $path → ${e.message}")
                            }
                    } else {
                        Log.w(TAG, "No phone connected")
                    }
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "getNodes failed: ${e.message}")
                }
        } catch (e: Exception) {
            Log.e(TAG, "WearComm error: ${e.message}")
        }
    }
}
