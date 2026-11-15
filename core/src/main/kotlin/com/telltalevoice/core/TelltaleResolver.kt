package com.telltalevoice.core

/** Pleos TelltaleData 한 개를 안드로이드 없이 옮긴 값. type 은 TelltaleType 상수 이름. */
data class TelltaleReading(
    val type: String,
    val state: LampState,
    val color: LampColor = LampColor.UNKNOWN,
)

/** Pleos OilLevelState 이름과 같다. */
enum class FluidLevel { NORMAL, HIGH, LOW, CRITICALLY_LOW, ERROR, UNKNOWN }

/** 경고등이 켜지고 꺼진 변화. */
data class LampChange(val lamp: WarningLamp, val on: Boolean)

/**
 * 차 신호를 매뉴얼 경고등 목록으로 바꾼다.
 * - TPMS: 깜빡임을 한 번 본 후 켜짐으로 바뀌면 "TPMS 이상"으로 본다(매뉴얼: "깜빡인 후 켜집니다").
 * - 브레이크: 경고등 상수가 없어 브레이크액 수준 LOW, CRITICALLY_LOW 로 켜졌다고 본다.
 * - 비상등: Pleos 에 비상등 읽기 함수가 없어 TURN_SIGNAL_LEFT 와 TURN_SIGNAL_RIGHT 가 함께 켜지면 비상등으로 본다.
 * - EBD: 2027 그랜저 설명서 (9) "브레이크 경고등과 ABS 경고등이 동시에 켜진 경우, 제동력의 전후륜 배분 기능(EBD)도 정상적으로 작동하지 않습니다."
 *   Pleos 상수 짝이 없어 브레이크 경고(브레이크액 수준)와 ABS 가 함께 켜지면 EBD 도 켜졌다고 본다(2026. 11. 14. 원문 대조 후 더함).
 */
class TelltaleResolver {
    private var tpmsBlinkSeen = false
    private var active: Set<String> = emptySet()

    val activeLampIds: Set<String> get() = active

    /** 새 운전이 시작되면(시동) 깜빡임 기록을 지운다. */
    fun resetDriveCycle() {
        tpmsBlinkSeen = false
        active = emptySet()
    }

    fun resolve(readings: List<TelltaleReading>, brakeFluid: FluidLevel = FluidLevel.UNKNOWN): Set<WarningLamp> {
        val byType = readings.associateBy { it.type }
        val tpms = byType["TPMS"]
        if (tpms?.state == LampState.BLINK) tpmsBlinkSeen = true
        if (tpms == null || tpms.state == LampState.OFF) tpmsBlinkSeen = false

        val result = linkedSetOf<WarningLamp>()
        for (lamp in LampCatalog.all) {
            when (val s = lamp.signal) {
                LampSignal.None -> Unit
                LampSignal.BrakeFluidLow ->
                    if (brakeFluid == FluidLevel.LOW || brakeFluid == FluidLevel.CRITICALLY_LOW) result += lamp
                is LampSignal.Telltale -> {
                    if (lamp.id == LampCatalog.LOW_TIRE_PRESSURE.id || lamp.id == LampCatalog.TPMS_FAULT.id) {
                        tpmsCheck(lamp, result, tpms)
                        continue
                    }
                    val hit = s.types.any { t ->
                        val r = byType[t] ?: return@any false
                        r.state in s.states && (s.colors == null || r.color in s.colors)
                    }
                    if (hit) result += lamp
                }
            }
        }
        if (LampCatalog.BRAKE in result && LampCatalog.ABS in result) result += LampCatalog.EBD
        return result
    }

    private fun tpmsCheck(lamp: WarningLamp, result: MutableSet<WarningLamp>, tpms: TelltaleReading?) {
        if (tpms == null || tpms.state == LampState.OFF) return
        val fault = tpms.state == LampState.BLINK || tpmsBlinkSeen
        if (fault && lamp.id == LampCatalog.TPMS_FAULT.id) result += lamp
        if (!fault && lamp.id == LampCatalog.LOW_TIRE_PRESSURE.id) result += lamp
    }

    /** 이전 결과와 비교해 켜짐과 꺼짐을 돌려준다. 순서는 급한 갈래 먼저. */
    fun update(readings: List<TelltaleReading>, brakeFluid: FluidLevel = FluidLevel.UNKNOWN): List<LampChange> {
        val now = resolve(readings, brakeFluid)
        val nowIds = now.map { it.id }.toSet()
        val turnedOn = now.filter { it.id !in active }.map { LampChange(it, true) }
        val turnedOff = active.filter { it !in nowIds }.mapNotNull { LampCatalog.byId(it) }.map { LampChange(it, false) }
        active = nowIds
        return (turnedOn + turnedOff).sortedWith(compareBy({ !it.on }, { LampCatalog.urgency(it.lamp.tier) }))
    }

    companion object {
        fun hazardOn(readings: List<TelltaleReading>): Boolean {
            fun lit(t: String) = readings.any { it.type == t && it.state != LampState.OFF }
            return lit("TURN_SIGNAL_LEFT") && lit("TURN_SIGNAL_RIGHT")
        }

        fun trunkOpen(readings: List<TelltaleReading>): Boolean =
            readings.any { it.type == "TRUNK_OPEN" && it.state != LampState.OFF }

        fun doorOpen(readings: List<TelltaleReading>): Boolean =
            readings.any { it.type == "DOOR_OPEN" && it.state != LampState.OFF }
    }
}
