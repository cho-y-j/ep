package com.dainon.skep.net

import android.content.Context
import com.dainon.skep.BuildConfig

/**
 * SharedPreferences 래퍼.
 * 신규 흐름(코드 인증) — workerId = personId 문자열, token = attendance_code, personName 추가.
 * 레거시 site 필드는 그대로 두되 새 흐름에서 미사용.
 * 온보딩 완료 = workerId & token 존재.
 */
object Prefs {
    private const val FILE = "skep_field"

    private fun sp(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun serverUrl(ctx: Context): String =
        sp(ctx).getString("server_url", BuildConfig.DEFAULT_SERVER_URL) ?: BuildConfig.DEFAULT_SERVER_URL

    fun workerId(ctx: Context): String? = sp(ctx).getString("worker_id", null)
    fun token(ctx: Context): String? = sp(ctx).getString("token", null)
    fun personName(ctx: Context): String? = sp(ctx).getString("person_name", null)

    fun siteId(ctx: Context): Long = sp(ctx).getLong("site_id", -1L)
    fun siteName(ctx: Context): String? = sp(ctx).getString("site_name", null)
    fun siteCenterLat(ctx: Context): Double =
        java.lang.Double.longBitsToDouble(sp(ctx).getLong("site_lat", 0L))
    fun siteCenterLng(ctx: Context): Double =
        java.lang.Double.longBitsToDouble(sp(ctx).getLong("site_lng", 0L))
    fun siteRadiusM(ctx: Context): Int = sp(ctx).getInt("site_radius_m", 300)

    fun isRegistered(ctx: Context): Boolean = !workerId(ctx).isNullOrBlank() && !token(ctx).isNullOrBlank()

    /** 신규 — 코드 인증 결과 저장. */
    fun saveAuth(ctx: Context, personId: Long, token: String, personName: String) {
        sp(ctx).edit()
            .putString("worker_id", personId.toString())
            .putString("token", token)
            .putString("person_name", personName)
            .apply()
    }

    /** 로그아웃 — 인증 정보 제거. */
    fun clearAuth(ctx: Context) {
        sp(ctx).edit()
            .remove("worker_id")
            .remove("token")
            .remove("person_name")
            .apply()
    }

    // ───── 회사 세션 (BP·공급사) — 웹 아이디/비번 JWT ─────
    fun bpToken(ctx: Context): String? = sp(ctx).getString("bp_token", null)
    fun bpName(ctx: Context): String? = sp(ctx).getString("bp_name", null)
    /** 로그인 사용자 역할 — BP / EQUIPMENT_SUPPLIER / MANPOWER_SUPPLIER / ADMIN. */
    fun bpRole(ctx: Context): String? = sp(ctx).getString("bp_role", null)
    fun isBpLoggedIn(ctx: Context): Boolean = !bpToken(ctx).isNullOrBlank()
    fun isSupplierRole(ctx: Context): Boolean =
        bpRole(ctx) == "EQUIPMENT_SUPPLIER" || bpRole(ctx) == "MANPOWER_SUPPLIER"

    fun saveBp(ctx: Context, token: String, name: String?, role: String?) {
        sp(ctx).edit().putString("bp_token", token).putString("bp_name", name)
            .putString("bp_role", role).apply()
    }

    fun clearBp(ctx: Context) {
        sp(ctx).edit().remove("bp_token").remove("bp_name").remove("bp_role").apply()
    }
}
