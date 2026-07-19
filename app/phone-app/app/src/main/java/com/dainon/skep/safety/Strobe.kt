package com.dainon.skep.safety

/**
 * P5-W2 파인드미 카메라 플래시 스트로브 토글 스케줄(순수).
 * 125ms 마다 on/off 를 뒤집어 한 주기 250ms = 약 4Hz 점멸(특허 §5.6 플래시 스트로브).
 * 서비스가 Handler.postDelayed(INTERVAL_MS) 로 tick 을 올리며 isOnAt(tick) 로 토치를 제어한다.
 */
object Strobe {
    const val INTERVAL_MS = 125L
    fun isOnAt(tick: Int): Boolean = tick % 2 == 0
}
