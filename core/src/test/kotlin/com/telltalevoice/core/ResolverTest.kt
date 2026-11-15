package com.telltalevoice.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource

class ResolverTest {
    private fun r(type: String, state: LampState = LampState.ON, color: LampColor = LampColor.UNKNOWN) =
        TelltaleReading(type, state, color)

    private fun ids(vararg readings: TelltaleReading, brake: FluidLevel = FluidLevel.NORMAL) =
        TelltaleResolver().resolve(readings.toList(), brake).map { it.id }

    @ParameterizedTest(name = "{0} {1} -> {2}")
    @CsvSource(
        "ENGINE_OIL_PRESSURE, ON, ENGINE_OIL_PRESSURE",
        "ENGINE_TEMPERATURE, ON, ENGINE_TEMPERATURE",
        "LOW_VOLT_BATTERY, ON, CHARGING",
        "ABS, ON, ABS",
        "TPMS, ON, LOW_TIRE_PRESSURE",
        "TPMS, BLINK, TPMS_FAULT",
        "MDPS, ON, MDPS",
        "LOW_FUEL, ON, LOW_FUEL",
        "ENGINE, ON, ENGINE",
        "ENGINE, BLINK, ENGINE",
        "AIRBAG, ON, AIRBAG",
        "ELECTRONIC_PARKING_BRAKE, ON, ELECTRONIC_PARKING_BRAKE",
        "LED_HEADLAMP, BLINK, LED_HEADLAMP",
        "ESC, ON, ESC",
        "MASTER_WARNING, ON, MASTER_WARNING",
        "PREHEAT, BLINK, PREHEAT",
        "IMMOBILIZER, ON, IMMOBILIZER",
    )
    fun `경고등 상수는 매뉴얼 경고등으로`(type: String, state: LampState, expected: String) {
        assertEquals(listOf(expected), ids(r(type, state)))
    }

    @ParameterizedTest(name = "{0} {1} 은 경고 아님")
    @CsvSource(
        "ESC, BLINK", "PREHEAT, ON", "ENGINE, OFF", "ABS, OFF",
        "PARKING_BRAKE, ON", "HEADLAMP_LOW, ON", "DRIVE_MODE_SPORT, ON", "TURN_SIGNAL_LEFT, BLINK", "SEAT_BELT, BLINK",
    )
    fun `정상 작동 표시나 짝 없는 상수는 무시`(type: String, state: LampState) {
        assertEquals(emptyList<String>(), ids(r(type, state)))
    }

    @ParameterizedTest(name = "AUTO_HOLD {0} -> {1}")
    @CsvSource("WHITE, false", "GREEN, false", "YELLOW, true", "RED, false", "UNKNOWN, false")
    fun `자동 정차는 노란색만 이상`(color: LampColor, fault: Boolean) {
        assertEquals(fault, "AUTO_HOLD" in ids(r("AUTO_HOLD", LampState.ON, color)))
    }

    @ParameterizedTest(name = "{0} {1} -> {2}")
    @CsvSource("FCA, RED, false", "FCA, YELLOW, true", "LKA, YELLOW, true", "LFA, GREEN, false", "ELK, YELLOW, true")
    fun `운전자 보조는 주황 이상만, 빨강 점멸은 작동`(type: String, color: LampColor, fault: Boolean) {
        assertEquals(fault, "DRIVER_ASSIST" in ids(r(type, LampState.BLINK, color)))
    }

    @ParameterizedTest(name = "브레이크액 {0} -> {1}")
    @CsvSource("NORMAL, false", "HIGH, false", "LOW, true", "CRITICALLY_LOW, true", "ERROR, false", "UNKNOWN, false")
    fun `브레이크 경고는 브레이크액 수준으로`(level: FluidLevel, on: Boolean) {
        assertEquals(on, "BRAKE" in ids(brake = level))
    }

    @Test
    fun `TPMS 깜빡인 뒤 켜지면 이상으로 유지`() {
        val res = TelltaleResolver()
        assertEquals(listOf("TPMS_FAULT"), res.resolve(listOf(r("TPMS", LampState.BLINK))).map { it.id })
        assertEquals(listOf("TPMS_FAULT"), res.resolve(listOf(r("TPMS", LampState.ON))).map { it.id })
    }

    @Test
    fun `TPMS 꺼지면 깜빡임 기억 지움`() {
        val res = TelltaleResolver()
        res.resolve(listOf(r("TPMS", LampState.BLINK)))
        res.resolve(listOf(r("TPMS", LampState.OFF)))
        assertEquals(listOf("LOW_TIRE_PRESSURE"), res.resolve(listOf(r("TPMS", LampState.ON))).map { it.id })
    }

    @Test
    fun `새 운전이면 깜빡임 기억 지움`() {
        val res = TelltaleResolver()
        res.resolve(listOf(r("TPMS", LampState.BLINK)))
        res.resetDriveCycle()
        assertEquals(listOf("LOW_TIRE_PRESSURE"), res.resolve(listOf(r("TPMS", LampState.ON))).map { it.id })
    }

    @Test
    fun `update 는 켜짐과 꺼짐을 돌려줌`() {
        val res = TelltaleResolver()
        val on = res.update(listOf(r("ENGINE")))
        assertEquals(listOf(LampChange(LampCatalog.ENGINE, true)), on)
        assertEquals(emptyList<LampChange>(), res.update(listOf(r("ENGINE"))))
        assertEquals(listOf(LampChange(LampCatalog.ENGINE, false)), res.update(emptyList()))
        assertEquals(emptySet<String>(), res.activeLampIds)
    }

    @Test
    fun `update 켜짐은 급한 갈래 먼저`() {
        val res = TelltaleResolver()
        val out = res.update(listOf(r("ENGINE"), r("LOW_FUEL"), r("ABS")))
        assertEquals(listOf("ABS", "LOW_FUEL", "ENGINE"), out.map { it.lamp.id })
    }

    @Test
    fun `여러 경고 동시에`() {
        assertEquals(setOf("ABS", "BRAKE", "ENGINE", "EBD"), ids(r("ABS"), r("ENGINE"), brake = FluidLevel.LOW).toSet()) // 브레이크와 ABS 가 함께면 EBD(2027 설명서 (9))
    }

    @Test
    fun `비상등은 좌우 방향지시등이 함께 켜질 때`() {
        assertTrue(TelltaleResolver.hazardOn(listOf(r("TURN_SIGNAL_LEFT", LampState.BLINK), r("TURN_SIGNAL_RIGHT", LampState.BLINK))))
        assertFalse(TelltaleResolver.hazardOn(listOf(r("TURN_SIGNAL_LEFT", LampState.BLINK))))
        assertFalse(TelltaleResolver.hazardOn(listOf(r("TURN_SIGNAL_LEFT", LampState.OFF), r("TURN_SIGNAL_RIGHT", LampState.BLINK))))
    }

    @Test
    fun `트렁크와 문 열림 표시등`() {
        assertTrue(TelltaleResolver.trunkOpen(listOf(r("TRUNK_OPEN"))))
        assertFalse(TelltaleResolver.trunkOpen(listOf(r("TRUNK_OPEN", LampState.OFF))))
        assertTrue(TelltaleResolver.doorOpen(listOf(r("DOOR_OPEN"))))
        assertFalse(TelltaleResolver.doorOpen(emptyList()))
    }

    @ParameterizedTest
    @EnumSource(LampState::class)
    fun `95종 상수 전부를 넣어도 예외 없음`(state: LampState) {
        val all = PleosTelltaleTypes.ALL.map { r(it, state, LampColor.YELLOW) }
        val out = TelltaleResolver().resolve(all, FluidLevel.NORMAL)
        if (state == LampState.OFF) assertTrue(out.isEmpty()) else assertTrue(out.isNotEmpty())
    }
}
