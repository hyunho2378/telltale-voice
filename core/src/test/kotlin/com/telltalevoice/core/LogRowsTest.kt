package com.telltalevoice.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** 기록 목록(DEV_V2 6번). 시각 글은 화면 LampTime 규칙과 같다. */
class LogRowsTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private fun ms(h: Int, m: Int, day: Int = 12) = ZonedDateTime.of(2026, 11, day, h, m, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun `시각 글 규칙`() {
        val now = ms(18, 0)
        assertEquals("오전 9:36 켜짐", LampTimeText.describe(ms(9, 36), null, now, zone))
        assertEquals("오전 9:36 켜짐, 9:53 꺼짐", LampTimeText.describe(ms(9, 36), ms(9, 53), now, zone))
        assertEquals("오전 11:50 켜짐, 오후 12:10 꺼짐", LampTimeText.describe(ms(11, 50), ms(12, 10), now, zone))
        assertEquals("오후 2:03 켜짐, 꺼짐", LampTimeText.describe(ms(14, 3), ms(14, 3), now, zone))
        assertEquals("11월 10일 오전 12:05 켜짐", LampTimeText.describe(ms(0, 5, 10), null, now, zone))
    }

    @Test
    fun `최근 순 목록과 이름, 벽시계 시각, 갈래`() {
        val log = WarningLog(zone)
        log.wallOffsetMs = ms(9, 0)
        log.onLampOn("ENGINE", 0, RouteSnapshot(roadLabel = "국도 7호선"), Tier.AFTER_ARRIVAL)
        log.onLampOff("ENGINE", 17 * 60_000L, RouteSnapshot())
        log.onLampOn("LOW_TIRE_PRESSURE", 36 * 60_000L, RouteSnapshot(), Tier.REST_AREA)
        val rows = log.rows(40 * 60_000L)
        assertEquals(listOf("LOW_TIRE_PRESSURE", "ENGINE"), rows.map { it.lampId })
        assertEquals("저압 타이어 경고등", rows[0].nameKo)
        assertEquals(ms(9, 36), rows[0].onAtWallMs)
        assertNull(rows[0].offAtWallMs)
        assertEquals("오전 9:36 켜짐", rows[0].timeText)
        assertEquals("오전 9:00 켜짐, 9:17 꺼짐", rows[1].timeText)
        assertEquals(Tier.AFTER_ARRIVAL, rows[1].tier)
        assertEquals("국도 7호선", rows[1].roadLabel)
    }

    @Test
    fun `판정 갈래가 없으면 분류표 갈래`() {
        val log = WarningLog(zone)
        log.onLampOn("ABS", 0, RouteSnapshot())
        assertEquals(Tier.STOP_NOW, log.rows(0).single().tier)
    }

    @Test
    fun `갈래 칸이 저장과 복원을 거쳐도 남고 예전 5칸 기록도 읽는다`() {
        val log = WarningLog(zone)
        log.onLampOn("LOW_TIRE_PRESSURE", 0, RouteSnapshot(), Tier.AFTER_ARRIVAL)
        val back = WarningLog(zone).apply { restore(log.serialize()) }
        assertEquals(Tier.AFTER_ARRIVAL, back.all().single().tier)
        val old = WarningLog(zone).apply { restore("ENGINE\t0\t\t||||0\t") }
        assertNull(old.all().single().tier)
        assertEquals(Tier.AFTER_ARRIVAL, old.rows(0).single().tier)
    }

    @Test
    fun `엔진 기록 목록은 판정 갈래를 싣는다`() {
        val runner = ScenarioRunner()
        runner.run(Scenarios.LOW_TIRE_HIGHWAY)
        assertEquals(Tier.REST_AREA, runner.engine.logRows(40_000).single().tier)
    }
}
