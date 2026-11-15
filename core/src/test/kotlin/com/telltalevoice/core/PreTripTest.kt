package com.telltalevoice.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 출발 전 점검(DEV_V2 2번). 가짜 신호로 시험한다. */
class PreTripTest {
    private val allOk = PreTripSignals(FluidLevel.NORMAL, FluidLevel.NORMAL, false, 64, listOf(TireReading("LEFT_FRONT", 35f, 30f)))
    private val tpmsOff = listOf(TelltaleReading("TPMS", LampState.OFF), TelltaleReading("LOW_FUEL", LampState.OFF))

    private fun status(items: List<PreTripItem>) = items.associate { it.kind to it.status }

    @Test
    fun `다섯 줄 순서와 모두 정상`() {
        val items = PreTrip.items(allOk, tpmsOff)
        assertEquals(PreTripKind.entries.toList(), items.map { it.kind })
        assertTrue(items.all { it.status == PreTripStatus.OK })
        assertEquals("64%", items.last().valueText)
        assertNull(PreTrip.sentence(items))
    }

    @Test
    fun `신호가 없으면 모두 알 수 없음`() {
        val items = PreTrip.items(PreTripSignals(), emptyList())
        assertTrue(items.all { it.status == PreTripStatus.UNKNOWN && it.valueText == null })
        assertNull(PreTrip.utterance(items))
    }

    @Test
    fun `타이어는 경고등 또는 바퀴 공기압이 저압 기준보다 낮으면 확인 필요`() {
        assertEquals(PreTripStatus.CHECK, status(PreTrip.items(allOk, listOf(TelltaleReading("TPMS", LampState.ON))))[PreTripKind.TIRE])
        val low = allOk.copy(tires = listOf(TireReading("LEFT_FRONT", 35f, 30f), TireReading("RIGHT_REAR", 28f, 30f)))
        val item = PreTrip.items(low, emptyList()).first()
        assertEquals(PreTripStatus.CHECK, item.status)
        assertEquals("LOW_TIRE_PRESSURE", item.lampId)
        val blink = PreTrip.items(allOk, listOf(TelltaleReading("TPMS", LampState.BLINK))).first()
        assertEquals("TPMS_FAULT", blink.lampId)
    }

    @Test
    fun `오일 수준 값별 상태`() {
        fun oil(l: FluidLevel) = PreTrip.items(allOk.copy(engineOil = l), tpmsOff)[1]
        assertEquals(PreTripStatus.OK, oil(FluidLevel.NORMAL).status)
        assertEquals("낮음", oil(FluidLevel.LOW).valueText)
        assertEquals("매우 낮음", oil(FluidLevel.CRITICALLY_LOW).valueText)
        assertEquals(PreTripStatus.CHECK, oil(FluidLevel.HIGH).status)
        assertEquals(PreTripStatus.UNKNOWN, oil(FluidLevel.ERROR).status)
    }

    @Test
    fun `와셔액과 엔진 오일 수준은 설명서 문장이 없어 말하지 않는다`() {
        val items = PreTrip.items(allOk.copy(washerLow = true, engineOil = FluidLevel.LOW), tpmsOff)
        assertEquals(PreTripStatus.CHECK, status(items)[PreTripKind.WASHER])
        assertEquals("부족", items[3].valueText)
        assertNull(PreTrip.sentence(items))
    }

    @Test
    fun `말하는 순서는 브레이크액 타이어 연료`() {
        val all = PreTrip.items(allOk.copy(brakeFluid = FluidLevel.LOW), listOf(TelltaleReading("TPMS", LampState.ON), TelltaleReading("LOW_FUEL", LampState.ON)))
        assertEquals(PreTrip.BRAKE_FLUID_SENTENCE, PreTrip.sentence(all))
        val tireFuel = PreTrip.items(allOk, listOf(TelltaleReading("TPMS", LampState.ON), TelltaleReading("LOW_FUEL", LampState.ON)))
        assertEquals(PreTrip.TIRE_SENTENCE, PreTrip.sentence(tireFuel))
        val fuel = PreTrip.items(allOk, listOf(TelltaleReading("LOW_FUEL", LampState.ON)))
        assertEquals(PreTrip.FUEL_SENTENCE, PreTrip.sentence(fuel))
        assertEquals("64%", fuel.last().valueText)
    }

    @Test
    fun `말하는 문장은 설명서 원문을 그대로 자른 문장`() {
        assertTrue(LampCatalog.BRAKE.manualActions.any { it.contains(PreTrip.BRAKE_FLUID_SENTENCE) })
        assertTrue(LampCatalog.LOW_TIRE_PRESSURE.manualActions.contains(PreTrip.TIRE_SENTENCE))
        assertTrue(LampCatalog.LOW_FUEL.manualActions.any { it.contains(PreTrip.FUEL_SENTENCE) })
    }

    @Test
    fun `시동 ON 과 기어 P 에서 확인 필요가 있을 때 한 번만 말한다`() {
        val e = TelltaleVoiceEngine()
        e.onIgnition(true, 0)
        e.onTelltales(listOf(TelltaleReading("LOW_FUEL", LampState.ON)), FluidLevel.NORMAL, 0)
        assertNull(e.scheduler.pendingRequests().firstOrNull { it.utterance.kind == Utterance.Kind.PRE_TRIP })
        e.onBody(BodySignals(gear = Gear.PARK), 100)
        val spoken = generateSequence(0L) { it + 500 }.take(10).mapNotNull { e.tick(it) }.toList()
        assertEquals(1, spoken.count { it.kind == Utterance.Kind.PRE_TRIP })
        assertEquals(PreTrip.FUEL_SENTENCE, spoken.first { it.kind == Utterance.Kind.PRE_TRIP }.text)
        e.onBody(BodySignals(gear = Gear.DRIVE), 6_000)
        e.onBody(BodySignals(gear = Gear.PARK), 7_000)
        assertTrue(e.scheduler.pendingRequests().none { it.utterance.kind == Utterance.Kind.PRE_TRIP })
    }

    @Test
    fun `시동이 꺼져 있으면 기어 P 여도 말하지 않는다`() {
        val e = TelltaleVoiceEngine()
        e.onDriveStart(0)
        e.onTelltales(emptyList(), FluidLevel.LOW, 0)
        e.onBody(BodySignals(gear = Gear.PARK), 0)
        assertTrue(e.scheduler.pendingRequests().none { it.utterance.kind == Utterance.Kind.PRE_TRIP })
        assertEquals(PreTripStatus.CHECK, e.preTrip()[2].status)
    }

    @Test
    fun `엔진 신호 넣기로 다섯 줄이 바뀐다`() {
        val e = TelltaleVoiceEngine()
        e.onPreTripSignals(allOk.copy(washerLow = true), 0)
        e.onTelltales(tpmsOff, FluidLevel.NORMAL, 0)
        assertEquals(listOf(PreTripStatus.OK, PreTripStatus.OK, PreTripStatus.OK, PreTripStatus.CHECK, PreTripStatus.OK), e.preTrip().map { it.status })
    }
    @Test
    fun `값 글은 현대차 화면 낱말`() {
        // docs/UX_WRITING.md 3절: 타이어 저압, 점검 필요. 화면은 값이 없을 때 확인 중과 확인 불가를 보인다
        val low = allOk.copy(tires = listOf(TireReading("LEFT_FRONT", 28f, 30f)))
        assertEquals("저압", PreTrip.items(low, emptyList()).first().valueText)
        assertEquals("저압", PreTrip.items(allOk, listOf(TelltaleReading("TPMS", LampState.ON))).first().valueText)
        assertEquals("점검 필요", PreTrip.items(allOk, listOf(TelltaleReading("TPMS", LampState.BLINK))).first().valueText)
        assertEquals("정상", PreTrip.items(allOk, tpmsOff).first().valueText)
    }

    @Test
    fun `상태 값 이름 고정`() {
        // 화면이 쓰는 글("신호 없음")의 상태 값 이름. core 가 돌려주는 이름이 바뀌면 화면 글도 함께 바꿔야 한다.
        assertEquals(listOf("OK", "CHECK", "UNKNOWN"), PreTripStatus.entries.map { it.name })
        assertEquals(listOf("RECEIVED", "WAITING", "NO_SIGNAL"), PreTripSignal.entries.map { it.name })
        assertEquals(listOf("TIRE", "ENGINE_OIL", "BRAKE_FLUID", "WASHER", "FUEL"), PreTripKind.entries.map { it.name })
    }

    @Test
    fun `신호가 없는 줄은 값 글이 없고 상태는 UNKNOWN 하나로만`() {
        val items = PreTrip.items(PreTripSignals(), emptyList())
        items.forEach {
            assertEquals(PreTripStatus.UNKNOWN, it.status, it.kind.name)
            assertNull(it.valueText, it.kind.name)
        }
        // 신호를 내주지 않는 차량과 아직 신호가 오지 않은 때를 상태 값으로 가른다
        val no = PreTrip.items(PreTripSignals(unsupported = PreTripKind.entries.toSet()), emptyList())
        assertEquals(List(5) { PreTripSignal.NO_SIGNAL }, no.map { it.signal })
        assertEquals(List(5) { PreTripStatus.UNKNOWN }, no.map { it.status })
    }

    @Test
    fun `상태가 정해진 줄의 신호 값은 늘 RECEIVED`() {
        PreTrip.items(allOk, tpmsOff).forEach {
            assertEquals(PreTripStatus.OK, it.status, it.kind.name)
            assertEquals(PreTripSignal.RECEIVED, it.signal, it.kind.name)
        }
    }
}

/** 출발 전 점검 문장과 경고 안내가 같은 항목이면 이미 경고가 나간 항목의 점검 문장은 말하지 않는다(2026. 11. 11. 사용자 결정 5번). */
class PreTripDedupTest {
    private fun spoken(e: TelltaleVoiceEngine, from: Long = 0, to: Long = 10_000) =
        generateSequence(from) { it + 500 }.takeWhile { it <= to }.mapNotNull { e.tick(it) }.toList()

    @Test
    fun `저압 타이어 경고가 나간 운전에서는 타이어 점검 문장을 말하지 않는다`() {
        val e = TelltaleVoiceEngine()
        e.onIgnition(true, 0)
        e.onBody(BodySignals(gear = Gear.PARK), 0)
        e.onTelltales(listOf(TelltaleReading("TPMS", LampState.ON)), FluidLevel.NORMAL, 100)
        val out = spoken(e)
        assertEquals(1, out.count { it.kind == Utterance.Kind.WARNING && it.lampId == "LOW_TIRE_PRESSURE" })
        assertTrue(out.none { it.text == PreTrip.TIRE_SENTENCE })
        assertEquals(PreTripStatus.CHECK, e.preTrip().first().status)
    }

    @Test
    fun `브레이크액 경고가 나간 운전에서는 브레이크액 점검 문장을 말하지 않는다`() {
        val e = TelltaleVoiceEngine()
        e.onIgnition(true, 0)
        e.onBody(BodySignals(gear = Gear.PARK), 0)
        e.onTelltales(emptyList(), FluidLevel.LOW, 100)
        val out = spoken(e)
        assertTrue(out.any { it.kind == Utterance.Kind.WARNING && it.lampId == "BRAKE" })
        assertTrue(out.none { it.text == PreTrip.BRAKE_FLUID_SENTENCE })
    }

    @Test
    fun `점검 문장이 먼저 줄에 들어가도 그 경고가 켜지면 점검 문장을 지운다`() {
        val e = TelltaleVoiceEngine()
        e.onIgnition(true, 0)
        // 바퀴 공기압만으로 타이어 확인 필요(경고등은 아직 꺼짐)
        e.onPreTripSignals(PreTripSignals(tires = listOf(TireReading("LEFT_FRONT", 20f, 30f))), 0)
        e.onBody(BodySignals(gear = Gear.PARK), 0)
        assertTrue(e.scheduler.pendingRequests().any { it.utterance.text == PreTrip.TIRE_SENTENCE })
        e.onTelltales(listOf(TelltaleReading("TPMS", LampState.ON)), FluidLevel.NORMAL, 0)
        val out = spoken(e)
        assertTrue(out.none { it.text == PreTrip.TIRE_SENTENCE })
        assertTrue(out.any { it.lampId == "LOW_TIRE_PRESSURE" })
    }

    @Test
    fun `경고가 나간 항목을 빼고 남은 항목의 문장은 말한다`() {
        val e = TelltaleVoiceEngine()
        e.onIgnition(true, 0)
        e.onBody(BodySignals(gear = Gear.PARK), 0)
        e.onTelltales(listOf(TelltaleReading("TPMS", LampState.ON)), FluidLevel.NORMAL, 100)
        spoken(e, 0, 2_000)
        assertTrue("LOW_TIRE_PRESSURE" in e.warnedLamps())
        val items = e.preTrip()
        assertEquals(null, PreTrip.sentence(items, e.warnedLamps()))
        val withFuel = PreTrip.items(PreTripSignals(), listOf(TelltaleReading("TPMS", LampState.ON), TelltaleReading("LOW_FUEL", LampState.ON)))
        assertEquals(PreTrip.FUEL_SENTENCE, PreTrip.sentence(withFuel, setOf("LOW_TIRE_PRESSURE")))
    }

    @Test
    fun `연료는 경고 안내가 나가도 점검 문장을 그대로 말한다`() {
        val items = PreTrip.items(PreTripSignals(), listOf(TelltaleReading("LOW_FUEL", LampState.ON)))
        assertEquals(PreTrip.FUEL_SENTENCE, PreTrip.sentence(items, setOf("LOW_FUEL")))
        assertEquals(setOf("BRAKE", "LOW_TIRE_PRESSURE", "TPMS_FAULT"), PreTrip.DEDUP_LAMP_IDS)
    }

    @Test
    fun `새 운전에서는 경고 기록이 지워진다`() {
        val e = TelltaleVoiceEngine()
        e.onIgnition(true, 0)
        e.onBody(BodySignals(gear = Gear.PARK), 0)
        e.onTelltales(listOf(TelltaleReading("TPMS", LampState.ON)), FluidLevel.NORMAL, 100)
        spoken(e, 0, 2_000)
        e.onIgnition(false, 3_000)
        e.onIgnition(true, 4_000)
        assertTrue(e.warnedLamps().isEmpty())
    }

}
