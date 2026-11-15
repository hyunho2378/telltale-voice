package com.telltalevoice.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 정차 순간 전환: 엔진이 내보내는 화면 상태(UiState)가 기어 P 한 번의 신호로 주행 경고에서 정차 뒤 안내(또는 대피)로 바로 바뀌는지.
 * EngineHub 는 신호 하나마다 uiState 를 한 번 읽어 내보내므로, 신호 하나마다의 상태 순서를 기록해 중간 상태가 없는지 본다.
 */
class ParkTransitionTest {
    private fun kind(s: UiState) = when (s) {
        is UiState.Idle -> "Idle"; is UiState.Driving -> "Driving"; is UiState.Evacuation -> "Evacuation"; is UiState.ParkedDetail -> "Parked"
    }

    /** 시나리오 단계를 하나씩 넣고 그때마다의 상태 종류를 기록한다(같은 상태가 이어지면 한 번만). */
    private fun trace(s: Scenario): List<Pair<String, String>> {
        val runner = ScenarioRunner()
        val out = mutableListOf<Pair<String, String>>()
        for (step in s.steps.sortedBy { it.atMs }) {
            runner.apply(step.event, step.atMs)
            val k = kind(runner.engine.uiState())
            if (out.lastOrNull()?.second != k) out += step.note to k
        }
        return out
    }

    @Test
    fun `미리 듣기 다섯 장면 모두 정차 신호 한 번에 주행 경고에서 정차 뒤 화면으로`() {
        val want = mapOf(
            "engine_oil" to listOf("Idle", "Driving", "Parked"),
            "low_tire_city" to listOf("Idle", "Driving", "Parked"),
            "low_tire_highway" to listOf("Idle", "Driving", "Parked"),
            "engine_arrival" to listOf("Idle", "Driving", "Idle", "Driving", "Parked"),
            "brake_highway" to listOf("Idle", "Driving", "Evacuation"),
        )
        for (s in Scenarios.previews) {
            val t = trace(s)
            assertEquals(want[s.id], t.map { it.second }, "${s.id}: $t")
            // 정차 뒤 화면이나 대피 화면으로 바뀌는 단계는 기어 P 단계
            assertTrue(t.last().first.contains("P") || t.last().first.contains("정차"), "${s.id}: ${t.last()}")
        }
    }

    @Test
    fun `차속 0 이 먼저 오고 기어 P 가 오면 상태 변경은 P 한 번`() {
        val e = TelltaleVoiceEngine()
        e.onDriveStart(0); e.onBody(BodySignals(gear = Gear.DRIVE), 0); e.onSpeed(40.0)
        e.onTelltales(listOf(TelltaleReading("ENGINE_OIL_PRESSURE", LampState.ON)), FluidLevel.NORMAL, 0)
        assertTrue(e.uiState() is UiState.Driving)
        e.onSpeed(0.0)
        assertTrue(e.uiState() is UiState.Driving)
        e.onBody(BodySignals(gear = Gear.PARK), 1_000)
        assertTrue(e.uiState() is UiState.ParkedDetail)
    }

    @Test
    fun `기어 P 가 먼저 오면 늦게 올 차속 0 을 기다리지 않고 바로 열린다`() {
        // Pleos 실측 순서: 시속 40 마지막 값, 기어 P(즉시), 0.84초 뒤 차속 0
        val e = TelltaleVoiceEngine()
        e.onDriveStart(0); e.onBody(BodySignals(gear = Gear.DRIVE), 0); e.onSpeed(40.0)
        e.onTelltales(listOf(TelltaleReading("ENGINE_OIL_PRESSURE", LampState.ON)), FluidLevel.NORMAL, 0)
        assertTrue(e.uiState() is UiState.Driving)
        e.onBody(BodySignals(gear = Gear.PARK), 1_000)
        assertTrue(e.uiState() is UiState.ParkedDetail)
        e.onSpeed(0.0)
        assertTrue(e.uiState() is UiState.ParkedDetail)
    }

    @Test
    fun `P 에 들어온 뒤 새로 온 차속이 5km 를 넘으면 다시 막는다`() {
        val e = TelltaleVoiceEngine()
        e.onDriveStart(0); e.onBody(BodySignals(gear = Gear.DRIVE), 0); e.onSpeed(40.0)
        e.onTelltales(listOf(TelltaleReading("ENGINE_OIL_PRESSURE", LampState.ON)), FluidLevel.NORMAL, 0)
        e.onBody(BodySignals(gear = Gear.PARK), 1_000)
        e.onSpeed(10.0)
        assertTrue(e.uiState() is UiState.Driving)
    }

    @Test
    fun `P 에서 다른 신호가 바뀌어도 차속은 지우지 않는다`() {
        val e = TelltaleVoiceEngine()
        e.onDriveStart(0); e.onBody(BodySignals(gear = Gear.PARK), 0); e.onSpeed(10.0)
        e.onBody(BodySignals(gear = Gear.PARK, trunkOpen = true), 1_000)
        assertEquals(10.0, e.speedKmh)
    }

    @Test
    fun `기어 D 로 되돌아가면 다시 주행 중 화면`() {
        val e = TelltaleVoiceEngine()
        e.onDriveStart(0); e.onBody(BodySignals(gear = Gear.DRIVE), 0); e.onSpeed(40.0)
        e.onTelltales(listOf(TelltaleReading("ENGINE_OIL_PRESSURE", LampState.ON)), FluidLevel.NORMAL, 0)
        e.onBody(BodySignals(gear = Gear.PARK), 1_000)
        assertTrue(e.uiState() is UiState.ParkedDetail)
        assertEquals(null, e.speedKmh)
        e.onBody(BodySignals(gear = Gear.DRIVE), 2_000)
        assertTrue(e.uiState() is UiState.Driving, "P 에서 D 로 돌아오면 주행 중 화면")
        e.onSpeed(40.0)
        assertTrue(e.uiState() is UiState.Driving)
    }

    @Test
    fun `주차 브레이크 체결도 지난 차속을 믿지 않는다`() {
        val e = TelltaleVoiceEngine()
        e.onDriveStart(0); e.onBody(BodySignals(gear = Gear.DRIVE), 0); e.onSpeed(40.0)
        e.onTelltales(listOf(TelltaleReading("ENGINE_OIL_PRESSURE", LampState.ON)), FluidLevel.NORMAL, 0)
        assertTrue(e.uiState() is UiState.Driving)
        e.onParkingBrake(true)
        assertEquals(null, e.speedKmh)
        assertTrue(e.uiState() is UiState.ParkedDetail, "주차 브레이크 체결 한 번에 정차 뒤 화면")
        e.onSpeed(0.0)
        assertTrue(e.uiState() is UiState.ParkedDetail)
    }

    @Test
    fun `주차 브레이크를 풀면 다시 주행 중 화면`() {
        val e = TelltaleVoiceEngine()
        e.onDriveStart(0); e.onBody(BodySignals(gear = Gear.DRIVE), 0); e.onSpeed(40.0)
        e.onTelltales(listOf(TelltaleReading("ENGINE_OIL_PRESSURE", LampState.ON)), FluidLevel.NORMAL, 0)
        e.onParkingBrake(true)
        e.onSpeed(30.0)
        assertTrue(e.uiState() is UiState.Driving, "새 차속이 시속 5km 를 넘으면 다시 막음")
        e.onSpeed(0.0)
        assertTrue(e.uiState() is UiState.ParkedDetail)
        e.onParkingBrake(false)
        assertTrue(e.uiState() is UiState.Driving)
    }

    @Test
    fun `이미 체결된 주차 브레이크 신호가 다시 와도 차속은 지우지 않는다`() {
        val e = TelltaleVoiceEngine()
        e.onDriveStart(0); e.onBody(BodySignals(gear = Gear.DRIVE), 0)
        e.onParkingBrake(true)
        e.onSpeed(30.0)
        e.onParkingBrake(true)
        assertEquals(30.0, e.speedKmh)
    }

    @Test
    fun `기어 P 와 오래된 차속으로는 주행 중 음성 되풀이를 하지 않는다`() {
        // 바로 정차 경고는 차가 설 때까지 60초마다 되풀이한다. 기어 P 면 되풀이를 멈춘다.
        val e = TelltaleVoiceEngine()
        e.onDriveStart(0); e.onBody(BodySignals(gear = Gear.DRIVE), 0); e.onSpeed(40.0)
        e.onTelltales(listOf(TelltaleReading("ENGINE_OIL_PRESSURE", LampState.ON)), FluidLevel.NORMAL, 0)
        assertEquals(Utterance.Kind.WARNING, e.tick(0)?.kind)
        e.onBody(BodySignals(gear = Gear.PARK), 1_000)
        assertEquals(null, e.tick(1_000 + SpeechScheduler.STOP_NOW_REPEAT_MS))
    }
}
