package com.telltalevoice.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 음성 문장(경고등별 1회차)은 2027 그랜저(GN7) 설명서 원문 낱말로만 짧게 자른다(2026. 11. 11. 사용자 결정, VOICE_LINES_2027.md). */
class VoiceLines2027Test {
    private val corpus: String = (ManualGN7.sections.flatMap { it.lines + it.name } + listOf(
        ManualGN7.TPMS_LOW_ACTION, ManualGN7.TPMS_NO_SHOP_ACTION, ManualGN7.OVERHEAT_STOP_ACTION, ManualGN7.OVERHEAT_INSPECT_ACTION,
    )).joinToString("\n")

    /** 2027 경고등 쪽에 없는 경고등(예전 판 그대로): 예열(디젤), 이모빌라이저 */
    private val no2027 = setOf("PREHEAT", "IMMOBILIZER")

    /** 술어는 원문 동사의 시제만 바꾼 꼴. 원문에 있는 줄기 */
    private val predicateStems = mapOf("켜졌습니다" to "켜지", "낮습니다" to "낮", "깜빡인 후 켜졌습니다" to "깜빡인 후 켜지", "부족합니다" to "부족하")

    private val lamps get() = LampCatalog.all.filter { it.id !in no2027 }

    @Test
    fun `주어와 행동 문장의 낱말은 모두 2027 원문에 있다`() {
        for (l in lamps) {
            l.subject.split(" ").forEach { w -> assertTrue(corpus.contains(w), "${l.id} 주어 낱말: $w") }
            if (l.action != LampCatalog.ARRIVAL_ACTION) {
                l.action.split(" ").forEach { w -> assertTrue(corpus.contains(w), "${l.id} 행동 낱말: $w") }
            }
        }
    }

    @Test
    fun `술어는 원문 동사 줄기의 시제만 바꾼다`() {
        for (l in lamps) {
            val stem = predicateStems[l.predicate]
            assertTrue(stem != null, "${l.id}: ${l.predicate}")
            assertTrue(corpus.contains(stem!!), "${l.id}: $stem")
        }
    }

    @Test
    fun `예전 판과 영문 판에서 온 낱말이 음성에 남지 않는다`() {
        val old = listOf("세우십시오", "급커브", "즉시 속도를", "엔진 과열", "비상등을", "전동 조향", "제동력 배분", "경보 장치", "파킹 브레이크", "사륜구동", "방문하십시오", "Drive")
        for (l in lamps) old.forEach { assertTrue(!SpeechBuilder.warning(l).text.contains(it), "${l.id}: $it") }
    }

    @Test
    fun `즉시 정차 경고의 행동 문장은 4어절 이하이고 2027 원문 낱말만 쓴다`() {
        val stop = LampCatalog.ofTier(Tier.STOP_NOW)
        assertEquals(listOf("ENGINE_OIL_PRESSURE", "ENGINE_TEMPERATURE", "BRAKE", "ABS"), stop.map { it.id })
        for (l in stop) {
            val words = l.action.split(" ")
            assertTrue(words.size <= 4, "${l.id}: ${l.action}")
            words.forEach { w -> assertTrue(corpus.contains(w), "${l.id} 낱말: $w") }
            assertTrue(l.action.endsWith("정차하십시오"), l.id)
            val second = SpeechBuilder.warning(l).text.substringAfter(". ").removeSuffix(".")
            assertEquals(l.action, second)
        }
        assertEquals(listOf("도로 옆으로 정차하십시오", "비상 경고등을 켜고 정차하십시오", "서서히 감속 후 정차하십시오", "안전한 장소에 정차하십시오"),
            stop.map { it.action })
    }

    @Test
    fun `즉시 정차 경고의 첫 문장은 그대로`() {
        assertEquals(listOf("엔진 오일 압력 경고등이 켜졌습니다", "냉각수 수온 경고등이 켜졌습니다", "브레이크 경고등이 켜졌습니다", "ABS 경고등이 켜졌습니다"),
            LampCatalog.ofTier(Tier.STOP_NOW).map { SpeechBuilder.warning(it).text.substringBefore(". ") })
    }

    @Test
    fun `저압 타이어 음성은 2027 TPMS 쪽 원문을 자른 문장`() {
        assertEquals("타이어 공기압이 낮습니다. 속도를 낮추고 급격한 조향을 피하십시오.", SpeechBuilder.warning(LampCatalog.LOW_TIRE_PRESSURE).text)
        assertTrue(ManualGN7.TPMS_LOW_ACTION.contains("속도를 낮추고 급격한 조향을 피하"))
    }

    @Test
    fun `충전 경고등은 휴게소까지 갈래와 점검 문장, 안전한 곳에서 점검 라벨`() {
        assertEquals(Tier.REST_AREA, LampCatalog.CHARGING.tier)
        assertEquals("충전 경고등이 켜졌습니다. 배터리 충전 상태를 확인하고 차량을 점검하십시오.", SpeechBuilder.warning(LampCatalog.CHARGING).text)
        val d = DecisionRules.decide(LampCatalog.CHARGING, RouteContext(), 60.0, NearbyPlaces(), 0)
        assertEquals(DecisionRule.CHARGING_CHECK, d.basis.rule)
        assertEquals("안전한 곳에서 점검", d.basis.verdictLabel)
        assertTrue(ManualGN7.section(4).lines.none { it.contains("정차") })
    }

    @Test
    fun `충전 경고등은 회전 직전이면 미루고 알림음이 없다`() {
        val u = SpeechBuilder.warning(LampCatalog.CHARGING)
        assertTrue(!u.chime)
        val req = SpeechRequest(u, LampCatalog.CHARGING.tier, 0)
        assertEquals(Decision.Defer(SpeechScheduler.MAX_DEFER_MS), SpeechScheduler().decide(req, AudioContext(nextTurnMeters = 100), 0))
    }

    @Test
    fun `EBD 는 음성 엔진에 우리말 읽기로 넘긴다`() {
        assertEquals("이비디 경고등이 켜졌습니다. 고속 주행을 피하십시오.", SpeechBuilder.warning(LampCatalog.EBD).ttsText)
    }
}
