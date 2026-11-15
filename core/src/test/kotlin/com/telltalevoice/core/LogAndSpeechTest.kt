package com.telltalevoice.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class LogAndSpeechTest {
    /** 2026. 11. 12. 14:03:00 서울 = 2026-11-12T05:03:00Z */
    private val t0 = 1_794_459_780_000L
    private val road = RouteSnapshot(roadLabel = "경부고속도로", destinationName = "부산", onHighway = true)

    @Test
    fun `기록 켜짐과 꺼짐`() {
        val log = WarningLog()
        assertTrue(log.onLampOn("ENGINE", t0, road))
        assertEquals(1, log.active().size)
        assertTrue(log.onLampOff("ENGINE", t0 + 17 * 60_000, RouteSnapshot()))
        assertEquals(0, log.active().size)
        assertEquals(17 * 60_000L, log.all()[0].durationMs(0))
    }

    @Test
    fun `이미 켜진 경고는 새로 쓰지 않음`() {
        val log = WarningLog()
        log.onLampOn("ENGINE", t0, road)
        assertFalse(log.onLampOn("ENGINE", t0 + 1, road))
        assertEquals(1, log.all().size)
    }

    @Test
    fun `켜진 적 없는 꺼짐은 false`() = assertFalse(WarningLog().onLampOff("ENGINE", t0, road))

    @Test
    fun `꺼진 시각이 앞서면 오류`() {
        val log = WarningLog()
        log.onLampOn("ENGINE", t0, road)
        assertThrows(IllegalArgumentException::class.java) { log.onLampOff("ENGINE", t0 - 1, road) }
    }

    @Test
    fun `켜졌다 꺼졌다 두 번`() {
        val log = WarningLog()
        log.onLampOn("ENGINE", t0, road); log.onLampOff("ENGINE", t0 + 60_000, road)
        log.onLampOn("ENGINE", t0 + 120_000, road)
        assertEquals(2, log.onCount("ENGINE"))
        assertEquals(1, log.active().size)
        assertEquals(0, log.onCount("ABS"))
    }

    @Test
    fun `기록 한 줄 글 고정`() {
        val log = WarningLog()
        log.onLampOn("ENGINE", t0, road); log.onLampOff("ENGINE", t0 + 17 * 60_000, road)
        log.onLampOn("ABS", t0 + 30 * 60_000, RouteSnapshot())
        assertEquals("11. 12. 14:03 켜짐, 14:20 꺼짐(17분), 경부고속도로", log.describe(log.all()[0]))
        assertEquals("11. 12. 14:33 켜짐, 아직 켜져 있음", log.describe(log.all()[1]))
    }

    @Test
    fun `기록 저장과 복원`() {
        val log = WarningLog()
        log.onLampOn("ENGINE", t0, road.copy(latitude = 36.1, longitude = 127.2, roadLabel = "길|이름"))
        log.onLampOff("ENGINE", t0 + 1000, RouteSnapshot())
        log.onLampOn("ABS", t0 + 2000, road)
        val text = log.serialize()
        val back = WarningLog().apply { restore(text) }
        assertEquals(2, back.all().size)
        assertEquals("길/이름", back.all()[0].onRoute.roadLabel)
        assertEquals(36.1, back.all()[0].onRoute.latitude)
        assertEquals(log.all()[1], back.all()[1])
        assertEquals(text, back.serialize())
    }

    @Test
    fun `잘못된 저장 글은 오류`() {
        assertThrows(IllegalArgumentException::class.java) { WarningLog().restore("a\tb") }
    }

    @Test
    fun `clear`() {
        val log = WarningLog(); log.onLampOn("ENGINE", t0, road); log.clear()
        assertEquals(0, log.all().size)
    }

    @ParameterizedTest(name = "{0}m -> {1}")
    @CsvSource(
        "0, 100미터", "40, 100미터", "149, 100미터", "150, 200미터", "849, 800미터", "949, 900미터",
        "950, 1킬로미터", "1000, 1킬로미터", "1250, 1.3킬로미터", "9949, 9.9킬로미터", "9950, 10킬로미터",
        "11800, 12킬로미터", "12000, 12킬로미터", "25499, 25킬로미터",
    )
    fun `거리 읽기 고정`(m: Int, text: String) = assertEquals(text, SpeechBuilder.formatDistance(m))

    @Test
    fun `음수 거리는 오류`() {
        assertThrows(IllegalArgumentException::class.java) { SpeechBuilder.formatDistance(-1) }
    }

    @Test
    fun `휴게소 두 번째 말 고정`() =
        assertEquals("다음 휴게소까지 12킬로미터입니다. 경유지로 넣으시겠습니까?", SpeechBuilder.restAreaDistance(12_000).text)

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource("경고등, 경고등이", "경고, 경고가", "연료, 연료가", "공기압, 공기압이", "ABS, ABS가", "MDPS, MDPS가", "EPB, EPB가", "TPMS, TPMS가", "LDWL, LDWL이")
    fun `주격 조사`(noun: String, expected: String) = assertEquals(expected, SpeechBuilder.withSubjectParticle(noun))

    @Test
    fun `음성 엔진 글 바꾸기`() {
        assertEquals("에이비에스 경고등", SpeechBuilder.toTts("ABS 경고등"))
        assertEquals("엘이디 전조등", SpeechBuilder.toTts("LED 전조등"))
    }

    @Test
    fun `도착 말 고정`() = assertEquals("운전 중 켜진 경고등 2개를 화면에 띄웠습니다.", SpeechBuilder.arrival(2).text)

    @Test
    fun `위험 낱말은 STOP_NOW 밖에서 위반`() {
        val bad = LampCatalog.ENGINE.copy(action = "위험하니 도착하면 점검 방법을 알려 드리겠습니다")
        assertTrue(SpeechBuilder.violations(bad).contains("위험 낱말"))
    }

    @Test
    fun `핵심 낱말 5개는 위반`() {
        val bad = LampCatalog.ABS.copy(keywords = listOf("ABS", "경고등", "안전한", "장소", "세우십시오"))
        assertTrue(SpeechBuilder.violations(bad).any { it.startsWith("핵심 낱말 5") })
    }

    @Test
    fun `행동이 끝에 없으면 위반`() {
        val bad = LampCatalog.ABS.copy(action = "세우십시오 안전한 장소에")
        assertTrue(SpeechBuilder.violations(bad).contains("행동 낱말이 끝에 없음"))
    }

    @Test
    fun `물음은 위반`() {
        val bad = LampCatalog.ABS.copy(action = "안전한 장소에 차를 세우십시오?")
        assertTrue(SpeechBuilder.violations(bad).contains("물음"))
    }
}
