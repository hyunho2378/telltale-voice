package com.telltalevoice.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 판정 근거 공개(DEV_V2 1번). 같은 경고등이 상황에 따라 다른 갈래와 문장, 장소가 된다. */
class DecisionBasisTest {
    private fun lines(out: List<SpokenAt>) = out.map { "${it.atMs} ${it.utterance.text}" }
    private val shop = Refuge(RefugeKind.REPAIR_SHOP, "블루핸즈 시험점", 1_200)
    private val shelter = Refuge(RefugeKind.DROWSY_SHELTER, "시험 졸음쉼터", 2_100)
    private val rest = Refuge(RefugeKind.REST_AREA, "시험 휴게소", 9_000)

    @Test
    fun `저압 타이어 시내 장면은 같은 문장과 휴게소 거리 없이 서비스센터까지 서행`() {
        val runner = ScenarioRunner()
        val out = runner.run(Scenarios.LOW_TIRE_CITY)
        assertEquals(listOf("1000 타이어 공기압이 낮습니다. 속도를 낮추고 급격한 조향을 피하십시오."), lines(out))
        val b = runner.engine.basisFor("LOW_TIRE_PRESSURE")!!
        assertEquals(Tier.REST_AREA, b.tier)
        assertEquals("서비스센터까지 서행", b.verdictLabel)
        assertEquals(RoadKind.GENERAL, b.road)
        assertEquals(30, b.speedKmh)
        assertEquals(DecisionRule.TIRE_GENERAL, b.rule)
    }

    @Test
    fun `저압 타이어 고속도로 시나리오는 속도와 휴게소로 말한다`() {
        val runner = ScenarioRunner()
        val out = runner.run(Scenarios.LOW_TIRE_HIGHWAY)
        assertEquals(listOf(
            "1000 타이어 공기압이 낮습니다. 속도를 낮추고 급격한 조향을 피하십시오.",
            "1500 다음 휴게소까지 12킬로미터입니다. 경유지로 넣으시겠습니까?",
        ), lines(out))
        val b = runner.engine.basisFor("LOW_TIRE_PRESSURE")!!
        assertEquals(Tier.REST_AREA, b.tier)
        assertEquals(RoadKind.HIGHWAY, b.road)
        assertEquals(120, b.speedKmh)
        assertEquals(DecisionRule.TIRE_HIGHWAY, b.rule)
        assertEquals("휴게소까지 서행", b.verdictLabel)
        assertEquals(RefugeKind.REST_AREA, b.refuge!!.kind)
        assertEquals(12_000, b.refugeMeters)
    }

    @Test
    fun `시내와 고속도로는 긴급도와 음성 문장이 같고 장소와 라벨, 한 문장, 근거 줄이 다르다`() {
        val city = Scenarios.basisFor("low_tire_city")!!
        val hw = Scenarios.basisFor("low_tire_highway")!!
        assertEquals(city.lampId, hw.lampId)
        assertEquals(Tier.REST_AREA, city.tier)
        assertEquals(city.tier, hw.tier)
        assertEquals(city.spokenText, hw.spokenText)
        assertNotEquals(city.verdictLabel, hw.verdictLabel)
        assertNotEquals(city.sentence, hw.sentence)
        assertNotEquals(city.refuge?.kind, hw.refuge?.kind)
        assertNotEquals(city.road, hw.road)
        assertNotEquals(city.speedKmh, hw.speedKmh)
    }

    @Test
    fun `일반도로 저압 타이어 장소는 정비소, 고속도로는 휴게소`() {
        val places = NearbyPlaces(shelter, rest, shop)
        val city = DecisionRules.decide(LampCatalog.LOW_TIRE_PRESSURE, RouteContext(listOf(TurnItem("TURN_LEFT", 500.0))), 30.0, places, 0).basis
        val hw = DecisionRules.decide(LampCatalog.LOW_TIRE_PRESSURE, RouteContext(listOf(TurnItem("TURN_TOLL", 9_000.0))), 120.0, places, 0).basis
        assertEquals(RefugeKind.REPAIR_SHOP, city.refuge!!.kind)
        assertEquals(RefugeKind.REST_AREA, hw.refuge!!.kind)
        assertEquals(Tier.REST_AREA, city.tier)
        assertEquals(Tier.REST_AREA, hw.tier)
    }

    @Test
    fun `기본 판정 규칙은 빈 상황의 decide 와 같다`() {
        for (l in LampCatalog.all) {
            assertEquals(DecisionRules.decide(l, RouteContext(), null, NearbyPlaces(), 0).basis.rule, DecisionRules.defaultRule(l.id), l.id)
        }
        assertNull(DecisionRules.defaultRule("NOPE"))
        assertNull(DecisionRules.defaultRule("TPMS"))
    }

    @Test
    fun `기본 판정 규칙은 갈래별로 정해진 규칙`() {
        LampCatalog.ofTier(Tier.STOP_NOW).forEach { assertEquals(DecisionRule.STOP_NOW_ROAD, DecisionRules.defaultRule(it.id), it.id) }
        assertEquals(DecisionRule.CHARGING_CHECK, DecisionRules.defaultRule("CHARGING"))
        assertEquals(DecisionRule.RED_CHECK, DecisionRules.defaultRule("LPG_OVERCHARGE"))
        LampCatalog.ofTier(Tier.REST_AREA).filter { it.id != "CHARGING" && it.id != "LPG_OVERCHARGE" }
            .forEach { assertEquals(DecisionRule.REST_AREA_DEFAULT, DecisionRules.defaultRule(it.id), it.id) }
        LampCatalog.ofTier(Tier.AFTER_ARRIVAL).forEach { assertEquals(DecisionRule.AFTER_ARRIVAL_DEFAULT, DecisionRules.defaultRule(it.id), it.id) }
    }

    @Test
    fun `판정 라벨은 규칙마다 정해진 글`() {
        assertEquals(listOf("즉시 정차", "즉시 정차", "휴게소까지 서행", "서비스센터까지 서행", "안전한 곳에서 점검",
            "휴게소까지 주행 가능", "안전한 곳에서 점검", "도착 후 점검"),
            DecisionRule.entries.map { it.label })
    }

    @Test
    fun `일반도로 저압 타이어는 가까운 정비소를 고른다`() {
        val d = DecisionRules.decide(
            LampCatalog.LOW_TIRE_PRESSURE, RouteContext(listOf(TurnItem("TURN_LEFT", 500.0))), 25.0,
            NearbyPlaces(shelter, rest, shop), 0,
        )
        assertEquals(shop, d.basis.refuge)
        assertEquals("블루핸즈 시험점", d.basis.refugeName)
        assertEquals(1_200, d.basis.refugeMeters)
    }

    @Test
    fun `고속도로 즉시 정차 경고는 졸음쉼터와 휴게소 중 가까운 곳`() {
        val hwRoute = RouteContext(listOf(TurnItem("TURN_TOLL", 20_000.0)))
        val d = DecisionRules.decide(LampCatalog.ENGINE_OIL_PRESSURE, hwRoute, 112.4, NearbyPlaces(shelter, rest, shop), 5)
        assertEquals(DecisionRule.STOP_NOW_HIGHWAY, d.basis.rule)
        assertEquals(shelter, d.basis.refuge)
        assertEquals(112, d.basis.speedKmh)
        assertEquals(Tier.STOP_NOW, d.basis.tier)
        assertEquals(5, d.basis.atMs)
    }

    @Test
    fun `일반도로 즉시 정차 경고는 장소 없이 도로 옆 정차`() {
        val d = DecisionRules.decide(LampCatalog.ENGINE_OIL_PRESSURE, RouteContext(listOf(TurnItem("TURN_LEFT", 100.0))), 40.0, NearbyPlaces(shelter, rest, shop), 0)
        assertEquals(DecisionRule.STOP_NOW_ROAD, d.basis.rule)
        assertNull(d.basis.refuge)
        assertEquals(RoadKind.GENERAL, d.basis.road)
    }

    @Test
    fun `도로를 모르면 저압 타이어는 휴게소까지 그대로`() {
        val d = DecisionRules.decide(LampCatalog.LOW_TIRE_PRESSURE, RouteContext(), null, NearbyPlaces(), 0)
        assertEquals(DecisionRule.REST_AREA_DEFAULT, d.basis.rule)
        assertEquals(Tier.REST_AREA, d.basis.tier)
        assertEquals(RoadKind.UNKNOWN, d.basis.road)
        assertNull(d.basis.speedKmh)
        assertEquals(SpeechBuilder.warning(LampCatalog.LOW_TIRE_PRESSURE).text, d.basis.spokenText)
    }

    @Test
    fun `점검만 적은 경고등은 도착 후 점검 규칙과 서비스센터`() {
        val d = DecisionRules.decide(LampCatalog.ENGINE, RouteContext(), 60.0, NearbyPlaces(repairShop = shop), 0)
        assertEquals(DecisionRule.AFTER_ARRIVAL_DEFAULT, d.basis.rule)
        assertEquals(shop, d.basis.refuge)
    }

    @Test
    fun `일반도로 저압 타이어는 분류표 경고등과 문장을 그대로 쓴다`() {
        val d = DecisionRules.decide(LampCatalog.LOW_TIRE_PRESSURE, RouteContext(roadLabel = "시내 도로"), 20.0, NearbyPlaces(), 0)
        assertEquals(LampCatalog.LOW_TIRE_PRESSURE, d.lamp)
        assertEquals(DecisionRule.TIRE_GENERAL, d.basis.rule)
        assertEquals(SpeechBuilder.warning(LampCatalog.LOW_TIRE_PRESSURE).text, d.basis.spokenText)
        assertEquals(emptyList<String>(), SpeechBuilder.violations(d.lamp))
    }

    @Test
    fun `엔진 판정 근거는 가장 급한 켜진 경고등을 따르고 정차 뒤 화면에도 실린다`() {
        val e = TelltaleVoiceEngine()
        e.onDriveStart(0)
        e.onBody(BodySignals(gear = Gear.DRIVE), 0)
        e.onSpeed(50.0)
        e.onNearby(NearbyPlaces(repairShop = shop))
        e.onTelltales(listOf(TelltaleReading("ENGINE", LampState.ON)), FluidLevel.NORMAL, 1_000)
        assertEquals("ENGINE", e.basis()!!.lampId)
        e.onTelltales(listOf(TelltaleReading("ENGINE", LampState.ON), TelltaleReading("ENGINE_OIL_PRESSURE", LampState.ON)), FluidLevel.NORMAL, 2_000)
        assertEquals("ENGINE_OIL_PRESSURE", e.basis()!!.lampId)
        e.onSpeed(0.0)
        e.onBody(BodySignals(gear = Gear.PARK), 3_000)
        val ui = e.uiState() as UiState.ParkedDetail
        assertEquals(listOf("ENGINE_OIL_PRESSURE", "ENGINE"), ui.items.map { it.basis!!.lampId })
    }

    @Test
    fun `꺼진 뒤에도 마지막 판정이 남고 새 운전에서 지워진다`() {
        val e = TelltaleVoiceEngine()
        e.onDriveStart(0)
        e.onTelltales(listOf(TelltaleReading("ENGINE", LampState.ON)), FluidLevel.NORMAL, 1_000)
        e.onTelltales(emptyList(), FluidLevel.NORMAL, 2_000)
        assertEquals("ENGINE", e.basis()!!.lampId)
        e.onDriveStart(3_000)
        assertNull(e.basis())
    }

    @Test
    fun `시내 저압 타이어는 기록에도 휴게소까지 갈래로 남는다`() {
        val runner = ScenarioRunner()
        runner.run(Scenarios.LOW_TIRE_CITY)
        assertEquals(Tier.REST_AREA, runner.engine.logRows(25_000).single().tier)
    }

    @Test
    fun `미리 듣기 장면 다섯 개 모두 판정 근거에 시속이 있다`() {
        assertEquals(mapOf("engine_oil" to 50, "low_tire_city" to 30, "low_tire_highway" to 120, "engine_arrival" to 70, "brake_highway" to 110),
            Scenarios.previews.associate { it.id to Scenarios.basisFor(it.id)!!.speedKmh })
        assertEquals(DecisionRule.STOP_NOW_ROAD, Scenarios.basisFor("engine_oil")!!.rule)
        assertEquals(DecisionRule.STOP_NOW_HIGHWAY, Scenarios.basisFor("brake_highway")!!.rule)
        assertEquals(DecisionRule.AFTER_ARRIVAL_DEFAULT, Scenarios.basisFor("engine_arrival")!!.rule)
    }

    @Test
    fun `엔진 점검 장면은 60초 안에 도착 뒤 화면이 열린다`() {
        val r = ScenarioRunner()
        r.run(Scenarios.ENGINE_ARRIVAL)
        assertTrue(Scenarios.ENGINE_ARRIVAL.durationMs <= 60_000)
        assertTrue(Scenarios.ENGINE_ARRIVAL.steps.maxOf { it.atMs } < 60_000)
        val ui = r.engine.uiState() as UiState.ParkedDetail
        assertTrue(ui.arrived)
    }

    @Test
    fun `장면 끝에는 시속 0이라 정차 뒤 화면이 열린다`() {
        for (s in listOf(Scenarios.ENGINE_OIL, Scenarios.ENGINE_ARRIVAL, Scenarios.LOW_TIRE_CITY, Scenarios.LOW_TIRE_HIGHWAY)) {
            val r = ScenarioRunner(); r.run(s)
            assertEquals(0.0, r.engine.speedKmh, s.id)
            assertTrue(r.engine.parkedForScreen(), s.id)
        }
    }

    @Test
    fun `v2 미리 듣기 장면 다섯 개와 기존 목록 유지`() {
        assertEquals(listOf("engine_oil", "low_tire_city", "low_tire_highway", "engine_arrival", "brake_highway"), Scenarios.previews.map { it.id })
        assertEquals("저압 타이어 경고(시내)", Scenarios.byId("low_tire_city")!!.title)
        assertEquals(4, Scenarios.all.size)
        Scenarios.previews.forEach { assertTrue(Scenarios.basisFor(it.id) != null, it.id) }
    }
}
