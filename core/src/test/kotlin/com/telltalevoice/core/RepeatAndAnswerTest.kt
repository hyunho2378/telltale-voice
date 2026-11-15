package com.telltalevoice.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 다시 듣기(DEV_V2 4번)와 Gleo 질문 답(5번) */
class RepeatAndAnswerTest {
    private fun driving(): TelltaleVoiceEngine = TelltaleVoiceEngine().also {
        it.onDriveStart(0)
        it.onBody(BodySignals(gear = Gear.DRIVE), 0)
        it.onSpeed(60.0)
    }

    private fun drain(e: TelltaleVoiceEngine, from: Long, to: Long) =
        generateSequence(from) { it + 500 }.takeWhile { it <= to }.mapNotNull { t -> e.tick(t)?.let { t to it } }.toList()

    @Test
    fun `마지막 안내가 없으면 말하지 않는다`() {
        val e = driving()
        assertFalse(e.repeatLast(1_000))
        assertNull(e.tick(1_000))
    }

    @Test
    fun `바로 정차 경고는 횟수 상한을 넘어 바로 다시 말한다`() {
        val e = driving()
        e.onTelltales(listOf(TelltaleReading("ENGINE_OIL_PRESSURE", LampState.ON)), FluidLevel.NORMAL, 0)
        drain(e, 0, 200_000)
        assertEquals(4, e.scheduler.spokenCountForLamp("ENGINE_OIL_PRESSURE")) // 0, 60, 120, 180초(바로 정차는 상한 없음)
        assertTrue(e.repeatLast(300_000))
        val u = e.tick(300_000)!!
        assertEquals(SpeechBuilder.warning(LampCatalog.ENGINE_OIL_PRESSURE).text, u.text)
        assertEquals(5, e.scheduler.spokenCountForLamp("ENGINE_OIL_PRESSURE"))
    }

    @Test
    fun `나머지 경고는 달리는 중이면 정차 뒤에 한 번 더 말한다`() {
        val e = driving()
        e.onTelltales(listOf(TelltaleReading("ENGINE", LampState.ON)), FluidLevel.NORMAL, 0)
        assertEquals("ENGINE", e.tick(0)!!.lampId)
        assertTrue(e.repeatLast(5_000))
        assertTrue(e.repeatWaiting)
        assertNull(e.tick(5_000))
        e.onSpeed(0.0)
        e.onBody(BodySignals(gear = Gear.PARK), 9_000)
        val u = e.tick(9_000)!!
        assertEquals(SpeechBuilder.warning(LampCatalog.ENGINE).text, u.text)
        assertFalse(e.repeatWaiting)
        assertNull(e.tick(9_500))
    }

    @Test
    fun `정차 중이면 바로 말하고 그만 한 경고도 다시 듣기는 말한다`() {
        val e = driving()
        e.onTelltales(listOf(TelltaleReading("ENGINE", LampState.ON)), FluidLevel.NORMAL, 0)
        e.tick(0)
        e.mute("ENGINE")
        e.onSpeed(0.0)
        e.onBody(BodySignals(gear = Gear.PARK), 1_000)
        e.tick(1_000) // 도착 안내는 없고(도착 전) 줄이 비어 있음
        assertTrue(e.repeatLast(2_000))
        assertEquals("ENGINE", e.tick(2_000)!!.lampId)
    }

    @Test
    fun `대피 문장도 다시 듣기 대상`() {
        val runner = ScenarioRunner()
        runner.run(Scenarios.BRAKE_HIGHWAY)
        val last = runner.engine.lastSpoken!!.utterance
        assertEquals(Utterance.Kind.EVACUATION, last.kind)
        assertTrue(runner.engine.repeatLast(60_000))
        assertEquals(last.text, runner.engine.tick(60_000)!!.text)
    }

    @Test
    fun `켜진 경고등 묻기는 가장 급한 경고 문장, 없으면 없다고 답한다`() {
        val e = driving()
        assertEquals(TelltaleVoiceEngine.ANSWER_NO_LAMP, e.answerActiveLamps(0).text)
        assertEquals(TelltaleVoiceEngine.ANSWER_NO_LAMP, e.tick(0)!!.text)
        e.onTelltales(listOf(TelltaleReading("ENGINE", LampState.ON), TelltaleReading("ABS", LampState.ON)), FluidLevel.NORMAL, 1_000)
        drain(e, 1_000, 3_000)
        val a = e.answerActiveLamps(4_000)
        assertEquals(SpeechBuilder.warning(LampCatalog.ABS).text, a.text)
        assertEquals(Utterance.Kind.ANSWER, e.tick(4_000)!!.kind)
    }

    @Test
    fun `답은 다시 듣기의 마지막 안내를 바꾸지 않는다`() {
        val e = driving()
        e.onTelltales(listOf(TelltaleReading("ENGINE", LampState.ON)), FluidLevel.NORMAL, 0)
        e.tick(0)
        e.answerActiveLamps(1_000); e.tick(1_000)
        assertEquals(Utterance.Kind.WARNING, e.lastSpoken!!.utterance.kind)
    }

    @Test
    fun `가까운 서비스센터 답은 이름과 거리, 모르면 말하지 않는다`() {
        val e = driving()
        assertNull(e.answerRepairShop(0))
        e.onNearby(NearbyPlaces(repairShop = Refuge(RefugeKind.REPAIR_SHOP, "블루핸즈 시험점", 3_600)))
        assertEquals("가까운 서비스센터는 블루핸즈 시험점, 3.6킬로미터입니다.", e.answerRepairShop(10)!!.text)
    }
}
