package com.telltalevoice.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

/** 시연 시나리오 세 개를 끝까지 돌린 결과(말한 시각과 문장)를 고정한다. */
class EngineScenarioTest {
    private fun run(s: Scenario) = ScenarioRunner().let { it to it.run(s) }
    private fun lines(out: List<SpokenAt>) = out.map { "${it.atMs} ${it.utterance.text}" }

    @Test
    fun `시나리오 세 개 고정`() {
        assertEquals(listOf("engine_oil", "low_tire", "engine_arrival", "brake_highway"), Scenarios.all.map { it.id })
        assertEquals("브레이크 경고, 고속도로", Scenarios.byId("brake_highway")!!.title)
        assertNull(Scenarios.byId("x"))
    }

    @Test
    fun `브레이크 고속도로 대피 순서`() {
        val (runner, out) = run(Scenarios.BRAKE_HIGHWAY)
        assertEquals(listOf(
            "2000 브레이크 경고등이 켜졌습니다. 서서히 감속 후 정차하십시오.",
            "20000 비상등을 켜십시오.",
            "26000 트렁크를 여십시오.",
            "32000 차에서 내려 가드레일 밖으로 피하십시오. 함께 탄 분도 가드레일 밖으로 피하십시오.",
            "38000 가드레일 밖에서 긴급견인을 부르십시오.",
        ), lines(out))
        assertTrue(out[0].utterance.chime)
        assertEquals(EvacuationStep.OUTSIDE, runner.engine.evacuation.step)
        val ui = runner.engine.uiState() as UiState.Evacuation
        assertEquals(listOf(ButtonAction.CALL_EX_TOW, ButtonAction.CALL_INSURANCE), ui.buttons)
        assertEquals("긴급견인 1588-2504", ui.lines.last())
    }

    @Test
    fun `저압 타이어는 회전 뒤에 말하고 휴게소 거리를 묻는다`() {
        val (runner, out) = run(Scenarios.LOW_TIRE)
        assertEquals(listOf(
            "6000 타이어 공기압이 낮습니다. 속도를 낮추고 급격한 조향을 피하십시오.",
            "6500 다음 휴게소까지 12킬로미터입니다. 경유지로 넣으시겠습니까?",
        ), lines(out))
        assertTrue(out[0].atMs - 1000 <= SpeechScheduler.MAX_DEFER_MS)
        val ui = runner.engine.uiState() as UiState.ParkedDetail
        assertEquals(listOf("LOW_TIRE_PRESSURE"), ui.items.map { it.lamp.id })
    }

    @Test
    fun `엔진 경고는 Gleo 뒤에 한 번만 말하고 도착 뒤 기록 두 줄`() {
        val (runner, out) = run(Scenarios.ENGINE_ARRIVAL)
        assertEquals(listOf(
            "9000 엔진 경고등이 켜졌습니다. 도착하면 점검 방법을 알려 드리겠습니다.",
            "46000 운전 중 켜진 경고등 1개를 화면에 띄웠습니다.",
        ), lines(out))
        val ui = runner.engine.uiState() as UiState.ParkedDetail
        assertTrue(ui.arrived)
        assertEquals(2, ui.items.single().logLines.size)
        assertEquals(2, runner.engine.warningLog.onCount("ENGINE"))
    }

    @Test
    fun `엔진 오일 압력은 회전 직전에도 바로 말하고 정차 뒤 설명서 문장`() {
        val (runner, out) = run(Scenarios.ENGINE_OIL)
        assertEquals(listOf("2000 엔진 오일 압력 경고등이 켜졌습니다. 도로 옆으로 정차하십시오."), lines(out))
        assertTrue(out[0].utterance.chime)
        val ui = runner.engine.uiState() as UiState.ParkedDetail
        assertEquals(LampCatalog.ENGINE_OIL_PRESSURE.manualActions, ui.items.single().lamp.manualActions)
        assertEquals(EvacuationStep.IDLE, runner.engine.evacuation.step)
    }

    @Test
    fun `일반 도로 STOP_NOW 는 대피 안내 없이 상한 없이 60초마다 되풀이`() {
        val e = TelltaleVoiceEngine()
        e.onDriveStart(0)
        e.onBody(BodySignals(gear = Gear.DRIVE), 0)
        e.onRoute(RouteContext(listOf(TurnItem("TURN_RIGHT", 2_000.0))))
        e.onTelltales(listOf(TelltaleReading("ENGINE_OIL_PRESSURE", LampState.ON)), FluidLevel.NORMAL, 0)
        val spoken = (0..400_000L step 500).mapNotNull { t -> e.tick(t)?.let { t } }
        assertEquals((0L..360_000L step 60_000L).toList(), spoken)
        assertEquals(EvacuationStep.IDLE, e.evacuation.step)
    }

    @Test
    fun `STOP_NOW 경고가 꺼지면 되풀이 멈춤`() {
        val e = TelltaleVoiceEngine()
        e.onDriveStart(0)
        e.onBody(BodySignals(gear = Gear.DRIVE), 0)
        e.onTelltales(listOf(TelltaleReading("ENGINE_OIL_PRESSURE", LampState.ON)), FluidLevel.NORMAL, 0)
        val before = (0..250_000L step 500).mapNotNull { t -> e.tick(t)?.let { t } }
        assertEquals(listOf(0L, 60_000L, 120_000L, 180_000L, 240_000L), before)
        e.onTelltales(emptyList(), FluidLevel.NORMAL, 250_500)
        assertNull((250_500..600_000L step 500).firstNotNullOfOrNull { e.tick(it) })
    }

    @Test
    fun `정차하면 STOP_NOW 되풀이 멈춤`() {
        val e = TelltaleVoiceEngine()
        e.onDriveStart(0)
        e.onBody(BodySignals(gear = Gear.DRIVE), 0)
        e.onTelltales(listOf(TelltaleReading("ABS", LampState.ON)), FluidLevel.NORMAL, 0)
        assertEquals("ABS", e.tick(0)?.lampId)
        e.onBody(BodySignals(gear = Gear.PARK), 1_000)
        assertNull((1_000..200_000L step 500).firstNotNullOfOrNull { e.tick(it) })
    }

    @Test
    fun `경고가 꺼지면 말 줄에서 빠짐`() {
        val e = TelltaleVoiceEngine()
        e.onDriveStart(0)
        e.onRoute(RouteContext(listOf(TurnItem("TURN_LEFT", 100.0))))
        e.onTelltales(listOf(TelltaleReading("ENGINE", LampState.ON)), FluidLevel.NORMAL, 0)
        e.onTelltales(emptyList(), FluidLevel.NORMAL, 2_000)
        assertNull((0..20_000L step 500).firstNotNullOfOrNull { e.tick(it) })
        assertEquals(1, e.warningLog.all().size)
    }

    @Test
    fun `후진 중에는 말하지 않고 기어가 바뀌면 말함`() {
        val e = TelltaleVoiceEngine()
        e.onDriveStart(0)
        e.onBody(BodySignals(gear = Gear.REVERSE), 0)
        e.onTelltales(listOf(TelltaleReading("LOW_FUEL", LampState.ON)), FluidLevel.NORMAL, 0)
        assertNull(e.tick(500))
        e.onBody(BodySignals(gear = Gear.DRIVE), 1_000)
        assertEquals("LOW_FUEL", e.tick(1_000)?.lampId)
    }

    @Test
    fun `그만 하면 다시 듣기도 막힘`() {
        val e = TelltaleVoiceEngine()
        e.onDriveStart(0)
        e.onTelltales(listOf(TelltaleReading("ENGINE", LampState.ON)), FluidLevel.NORMAL, 0)
        e.mute("ENGINE")
        assertNull(e.tick(0))
        e.repeatCurrent(100)
        assertNull(e.tick(200))
    }

    @Test
    fun `휴게소 들르기는 다음 휴게소를 돌려줌`() {
        val e = TelltaleVoiceEngine()
        e.onRoute(RouteContext(listOf(TurnItem("TURN_RESTAREA", 9_000.0, "시험"), TurnItem("TURN_ENTER_SERVICEAREA", 4_000.0, "시험2"))))
        assertEquals(4_000.0, e.restAreaForWaypoint()?.distanceMeters)
    }

    @Test
    fun `경고가 없으면 Idle`() = assertEquals(UiState.Idle, TelltaleVoiceEngine().uiState())

    @TestFactory
    fun `주행 중 화면은 3줄 이하 버튼 1개에서 2개`() = LampCatalog.all.filter { it.signal != LampSignal.None }.map { lamp ->
        dynamicTest(lamp.id) {
            val e = TelltaleVoiceEngine()
            e.onDriveStart(0)
            e.onBody(BodySignals(gear = Gear.DRIVE), 0)
            e.onRoute(RouteContext(listOf(TurnItem("TURN_ENTER_SERVICEAREA", 12_000.0))))
            val s = lamp.signal
            val readings = if (s is LampSignal.Telltale) listOf(TelltaleReading(s.types.first(), s.states.first(), s.colors?.first() ?: LampColor.UNKNOWN)) else emptyList()
            val brake = if (s == LampSignal.BrakeFluidLow) FluidLevel.LOW else FluidLevel.NORMAL
            e.onTelltales(readings, brake, 0)
            val ui = e.uiState()
            val (lines, buttons) = when (ui) {
                is UiState.Driving -> ui.lines to ui.buttons
                is UiState.Evacuation -> ui.lines to ui.buttons
                else -> error("주행 중 화면 아님: $ui")
            }
            assertTrue(lines.size <= TelltaleVoiceEngine.MAX_DRIVING_LINES, "$lines")
            assertTrue(buttons.size in 1..TelltaleVoiceEngine.MAX_DRIVING_BUTTONS, "$buttons")
        }
    }

    @Test
    fun `고속도로 판단은 요금소 나들목 휴게소`() {
        assertTrue(RouteContext(listOf(TurnItem("TURN_TOLL", 1.0))).onHighway)
        assertTrue(RouteContext(listOf(TurnItem("TURN_EXIT_IC_R", 1.0))).onHighway)
        assertTrue(RouteContext(listOf(TurnItem("TURN_RESTAREA", 1.0))).onHighway)
        assertTrue(!RouteContext(listOf(TurnItem("TURN_LEFT", 1.0))).onHighway)
    }

    @Test
    fun `다음 회전 거리는 직진과 요금소를 뺌`() {
        val r = RouteContext(listOf(TurnItem("TURN_STRAIGHT", 50.0), TurnItem("TURN_TOLL", 80.0), TurnItem("TURN_RIGHT", 700.0)))
        assertEquals(700, r.nextTurnMeters)
        assertNull(RouteContext().nextTurnMeters)
    }
}
