package com.telltalevoice.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class SchedulerTest {
    private fun req(lamp: WarningLamp, at: Long = 0) = SpeechRequest(SpeechBuilder.warning(lamp), lamp.tier, at)
    private val near = AudioContext(nextTurnMeters = 150)
    private val far = AudioContext(nextTurnMeters = 2_000)

    @Test
    fun `기준 숫자 고정`() {
        assertEquals(300, SpeechScheduler.NEAR_TURN_METERS)
        assertEquals(10_000L, SpeechScheduler.MAX_DEFER_MS)
        assertEquals(3, SpeechScheduler.MAX_PER_DRIVE)
        assertEquals(60_000L, SpeechScheduler.STOP_NOW_REPEAT_MS)
    }

    @ParameterizedTest(name = "{0} 회전 직전에도 즉시")
    @CsvSource("ENGINE_OIL_PRESSURE", "ENGINE_TEMPERATURE", "BRAKE", "ABS")
    fun `STOP_NOW 는 미루지 않음`(id: String) {
        val lamp = LampCatalog.byId(id)!!
        assertEquals(Decision.SpeakNow, SpeechScheduler().decide(req(lamp), near, 0))
    }

    @ParameterizedTest(name = "{0} 회전 직전이면 10초 미룸")
    @CsvSource("CHARGING", "LOW_TIRE_PRESSURE", "MDPS", "EBD", "LOW_FUEL", "ENGINE", "AIRBAG", "TPMS_FAULT", "ESC")
    fun `급하지 않은 경고는 회전 직전에 미룸`(id: String) {
        val lamp = LampCatalog.byId(id)!!
        assertEquals(Decision.Defer(10_000), SpeechScheduler().decide(req(lamp), near, 0))
    }

    @ParameterizedTest(name = "회전까지 {0}미터 -> 미룸 {1}")
    @CsvSource("0, true", "1, true", "150, true", "299, true", "300, false", "301, false", "1000, false")
    fun `회전 거리 경계`(meters: Int, deferred: Boolean) {
        val d = SpeechScheduler().decide(req(LampCatalog.ENGINE), AudioContext(nextTurnMeters = meters), 0)
        assertEquals(deferred, d is Decision.Defer)
    }

    @ParameterizedTest(name = "만든 뒤 {0}ms -> 말함 {1}")
    @CsvSource("0, false", "5000, false", "9999, false", "10000, true", "15000, true")
    fun `미루기는 최대 10초`(elapsed: Long, speak: Boolean) {
        val d = SpeechScheduler().decide(req(LampCatalog.LOW_TIRE_PRESSURE, 1_000), near, 1_000 + elapsed)
        assertEquals(speak, d == Decision.SpeakNow)
    }

    @Test
    fun `회전 정보가 없으면 바로 말함`() {
        assertEquals(Decision.SpeakNow, SpeechScheduler().decide(req(LampCatalog.ENGINE), AudioContext(), 0))
    }

    @Test
    fun `회전이 멀면 바로 말함`() {
        assertEquals(Decision.SpeakNow, SpeechScheduler().decide(req(LampCatalog.LOW_FUEL), far, 0))
    }

    @Test
    fun `Gleo 가 말해도 STOP_NOW 는 바로 말함`() {
        assertEquals(Decision.SpeakNow, SpeechScheduler().decide(req(LampCatalog.BRAKE), AudioContext(gleoSpeaking = true), 0))
    }

    @Test
    fun `Gleo 가 말하면 급하지 않은 경고는 기다림`() {
        assertEquals(Decision.WaitAudio, SpeechScheduler().decide(req(LampCatalog.ENGINE), AudioContext(gleoSpeaking = true), 0))
    }

    @Test
    fun `브레이크를 밟는 중이면 급하지 않은 경고는 미룸`() {
        assertEquals(Decision.Defer(10_000), SpeechScheduler().decide(req(LampCatalog.LOW_FUEL), AudioContext(braking = true), 0))
        assertEquals(Decision.SpeakNow, SpeechScheduler().decide(req(LampCatalog.ABS), AudioContext(braking = true), 0))
    }

    @Test
    fun `후진 중에는 화면에만`() {
        assertEquals(Decision.ScreenOnly, SpeechScheduler().decide(req(LampCatalog.BRAKE), AudioContext(reversing = true), 0))
        assertEquals(Decision.ScreenOnly, SpeechScheduler().decide(req(LampCatalog.ENGINE), AudioContext(reversing = true), 0))
    }

    @Test
    fun `그만 한 경고는 다시 먼저 말하지 않음`() {
        val s = SpeechScheduler()
        s.submit(req(LampCatalog.ENGINE))
        s.mute("ENGINE")
        assertEquals(0, s.pendingCount)
        assertEquals(Decision.Drop(SpeechScheduler.DROP_MUTED), s.decide(req(LampCatalog.ENGINE), far, 0))
        s.onDriveStart()
        assertFalse(s.isMuted("ENGINE"))
    }

    @Test
    fun `알림음 숫자 고정과 STOP_NOW 만 알림음`() {
        assertEquals(220, SpeechScheduler.CHIME_MS)
        assertEquals(1_100, SpeechScheduler.CHIME_HZ)
        assertEquals(3_000L, SpeechScheduler.SCREEN_ONLY_MS)
        LampCatalog.all.forEach { assertEquals(it.tier == Tier.STOP_NOW, SpeechBuilder.warning(it).chime, it.id) }
    }

    @Test
    fun `다른 오디오가 말하면 기다림`() {
        assertEquals(Decision.WaitAudio, SpeechScheduler().decide(req(LampCatalog.ENGINE), AudioContext(otherAudioBusy = true), 0))
    }

    @Test
    fun `바로 정차가 아닌 경고는 한 운전에 3번까지`() {
        val s = SpeechScheduler()
        repeat(3) { i ->
            assertTrue(s.submit(req(LampCatalog.ENGINE, i * 1000L)))
            assertEquals("ENGINE", s.tick(i * 1000L, far)?.utterance?.lampId)
        }
        assertEquals(3, s.spokenCountForLamp("ENGINE"))
        assertFalse(s.submit(req(LampCatalog.ENGINE, 9_000)))
        assertEquals(Decision.Drop(SpeechScheduler.DROP_LIMIT), s.decide(req(LampCatalog.ENGINE), far, 0))
    }

    @Test
    fun `바로 정차 경고는 한 운전 상한이 없다`() {
        val s = SpeechScheduler()
        repeat(6) { i ->
            assertTrue(s.submit(req(LampCatalog.BRAKE, i * 1000L)))
            assertEquals("BRAKE", s.tick(i * 1000L, far)?.utterance?.lampId)
        }
        assertEquals(6, s.spokenCountForLamp("BRAKE"))
        assertEquals(Decision.SpeakNow, s.decide(req(LampCatalog.BRAKE), far, 0))
        LampCatalog.all.forEach { assertEquals(it.tier != Tier.STOP_NOW, req(it).capped, it.id) }
    }

    @Test
    fun `대피 문장은 상한 규칙 그대로`() {
        val s = SpeechScheduler()
        val evac = SpeechRequest(SpeechBuilder.evacuation(EvacuationStep.ASK_HAZARD)!!, null, 0)
        assertTrue(evac.capped)
        repeat(3) { i -> assertTrue(s.submit(evac.copy(createdAtMs = i * 1000L))); assertTrue(s.tick(i * 1000L, far) != null) }
        assertFalse(s.submit(evac.copy(createdAtMs = 9_000)))
        assertEquals(Decision.Drop(SpeechScheduler.DROP_LIMIT), s.decide(evac, far, 0))
    }

    @Test
    fun `새 운전이면 횟수 다시 셈`() {
        val s = SpeechScheduler()
        repeat(3) { s.submit(req(LampCatalog.BRAKE)); s.tick(0, far) }
        s.onDriveStart()
        assertEquals(0, s.spokenCountForLamp("BRAKE"))
        assertTrue(s.submit(req(LampCatalog.BRAKE)))
    }

    @Test
    fun `같은 문장은 줄에 한 번만`() {
        val s = SpeechScheduler()
        assertTrue(s.submit(req(LampCatalog.ENGINE)))
        assertFalse(s.submit(req(LampCatalog.ENGINE, 10)))
        assertEquals(1, s.pendingCount)
    }

    @Test
    fun `급한 경고 먼저 말함`() {
        val s = SpeechScheduler()
        s.submit(req(LampCatalog.ENGINE, 0))
        s.submit(req(LampCatalog.LOW_FUEL, 1))
        s.submit(req(LampCatalog.ABS, 2))
        assertEquals(listOf("ABS", "LOW_FUEL", "ENGINE"), List(3) { s.tick(10, far)!!.utterance.lampId })
        assertNull(s.tick(10, far))
    }

    @Test
    fun `대피 문장은 급한 요청`() {
        val e = SpeechRequest(SpeechBuilder.evacuation(EvacuationStep.ASK_HAZARD)!!, null, 0)
        assertTrue(e.urgent)
        assertEquals(Decision.SpeakNow, SpeechScheduler().decide(e, near, 0))
    }

    @Test
    fun `회전 직전에 미룬 경고는 10초 뒤 tick 에서 나옴`() {
        val s = SpeechScheduler()
        s.submit(req(LampCatalog.ENGINE, 0))
        assertNull(s.tick(9_500, near))
        assertEquals("ENGINE", s.tick(10_000, near)?.utterance?.lampId)
    }

    @Test
    fun `회전을 지나면 10초 전에도 말함`() {
        val s = SpeechScheduler()
        s.submit(req(LampCatalog.ENGINE, 0))
        assertNull(s.tick(2_000, near))
        assertEquals("ENGINE", s.tick(3_000, far)?.utterance?.lampId)
    }

    @Test
    fun `Gleo 가 끝나면 말함`() {
        val s = SpeechScheduler()
        s.submit(req(LampCatalog.ENGINE, 0))
        assertNull(s.tick(0, AudioContext(gleoSpeaking = true)))
        assertEquals(1, s.pendingCount)
        assertEquals("ENGINE", s.tick(500, AudioContext())?.utterance?.lampId)
    }

    @Test
    fun `경고가 꺼지면 줄에서 지움`() {
        val s = SpeechScheduler()
        s.submit(req(LampCatalog.ENGINE))
        s.cancelLamp("ENGINE")
        assertEquals(0, s.pendingCount)
    }

    @Test
    fun `종류로 지우기`() {
        val s = SpeechScheduler()
        s.submit(SpeechRequest(SpeechBuilder.restAreaDistance(12_000), Tier.REST_AREA, 0))
        s.submit(req(LampCatalog.LOW_FUEL))
        s.cancelKind(Utterance.Kind.REST_AREA_DISTANCE)
        assertEquals(listOf("LOW_FUEL"), s.pendingRequests().map { it.utterance.lampId })
    }

    @Test
    fun `횟수 열쇠는 종류와 경고 이름`() {
        assertEquals("WARNING:BRAKE", req(LampCatalog.BRAKE).capKey)
    }
}
