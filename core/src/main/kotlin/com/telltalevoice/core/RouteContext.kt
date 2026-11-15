package com.telltalevoice.core

/** NaviHelper TBTInfo 한 개(type 은 TurnType 상수 이름, distance 는 미터). */
data class TurnItem(val type: String, val distanceMeters: Double, val description: String = "")

/**
 * 길 안내 상태. NaviHelper 다음 회전 목록(onTBTInfo)에서 만든다.
 * 고속도로 판단: 목록에 요금소(TURN_TOLL)나 나들목(TURN_ENTER_IC_*, TURN_EXIT_IC_*), 휴게소(TURN_ENTER_SERVICEAREA, TURN_RESTAREA)가 있으면 고속도로 위로 본다(R7).
 */
data class RouteContext(
    val turns: List<TurnItem> = emptyList(),
    val guiding: Boolean = false,
    val destinationName: String? = null,
    val roadLabel: String? = null,
) {
    val nextTurnMeters: Int?
        get() = turns.filter { it.type !in NON_MANEUVER }.minOfOrNull { it.distanceMeters }?.toInt()

    val onHighway: Boolean
        get() = turns.any { it.type in HIGHWAY_TYPES || it.type.startsWith("TURN_ENTER_IC_") || it.type.startsWith("TURN_EXIT_IC_") }

    val nextRestArea: TurnItem?
        get() = turns.filter { it.type in REST_TYPES }.minByOrNull { it.distanceMeters }

    companion object {
        val REST_TYPES = setOf("TURN_ENTER_SERVICEAREA", "TURN_RESTAREA")
        val HIGHWAY_TYPES = setOf("TURN_TOLL") + REST_TYPES
        /** 운전자가 핸들을 돌리지 않는 항목. 다음 회전 거리에서 뺀다. */
        val NON_MANEUVER = setOf("TURN_STRAIGHT", "TURN_UNKNOWN", "TURN_TUNNEL", "TURN_ARRIVE", "TURN_TOLL") + REST_TYPES
    }
}
