package com.telltalevoice.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** 화면 작업자 요청(기록 상세, 설정, 출발 전 점검 상세)에 내주는 데이터. */
class ScreenDataV3Test {
    private val zone = ZoneId.of("Asia/Seoul")
    private fun ms(h: Int, m: Int, day: Int = 12) = ZonedDateTime.of(2026, 11, day, h, m, 0, 0, zone).toInstant().toEpochMilli()
    private fun driving() = TelltaleVoiceEngine().also { it.onDriveStart(0); it.onBody(BodySignals(gear = Gear.DRIVE), 0) }
    private fun drain(e: TelltaleVoiceEngine, to: Long) = generateSequence(0L) { it + 500 }.takeWhile { it <= to }.mapNotNull { e.tick(it) }.toList()

    @Test
    fun `기록 줄에 말한 문장과 켜진 시각, 꺼진 시각 글`() {
        val e = driving()
        e.onTelltales(listOf(TelltaleReading("ENGINE", LampState.ON)), FluidLevel.NORMAL, 0)
        drain(e, 1_000)
        e.onTelltales(emptyList(), FluidLevel.NORMAL, 17 * 60_000L)
        val row = e.logRows(18 * 60_000L).single()
        assertEquals(SpeechBuilder.warning(LampCatalog.ENGINE).text, row.spokenText)
        assertEquals("오전 9:00", LampTimeText.onTime(ms(9, 0), ms(10, 0), zone))
        assertEquals("11월 10일 오후 2:05", LampTimeText.onTime(ms(14, 5, 10), ms(10, 0), zone))
        assertEquals("오전 9:53", LampTimeText.offTime(ms(9, 53), zone))
        assertTrue(row.onTimeText.isNotEmpty())
        assertTrue(row.offTimeText != null)
    }

    @Test
    fun `말하지 않은 점등은 말한 문장이 없고 꺼지지 않으면 꺼진 시각이 없다`() {
        val e = driving()
        e.mute("ENGINE")
        e.onTelltales(listOf(TelltaleReading("ENGINE", LampState.ON)), FluidLevel.NORMAL, 0)
        drain(e, 2_000)
        val row = e.logRows(3_000).single()
        assertNull(row.spokenText)
        assertNull(row.offTimeText)
    }

    @Test
    fun `말한 문장은 저장과 복원을 거쳐도 남고 예전 6칸 기록도 읽는다`() {
        val log = WarningLog(zone)
        log.onLampOn("ENGINE", 0, RouteSnapshot(), Tier.AFTER_ARRIVAL)
        log.markSpoken("ENGINE", "엔진 경고등이 켜졌습니다. 도착하면 점검 방법을 알려 드리겠습니다.")
        log.markSpoken("ENGINE", "두 번째 문장")
        val back = WarningLog(zone).apply { restore(log.serialize()) }
        assertEquals("엔진 경고등이 켜졌습니다. 도착하면 점검 방법을 알려 드리겠습니다.", back.all().single().spokenText)
        val old = WarningLog(zone).apply { restore("ENGINE\t0\t\t||||0\t\tAFTER_ARRIVAL") }
        assertNull(old.all().single().spokenText)
    }

    @Test
    fun `안내 음성을 끄면 급하지 않은 경고는 말하지 않고 바로 정차 경고는 말한다`() {
        val e = driving()
        e.voiceEnabled = false
        e.onTelltales(listOf(TelltaleReading("ENGINE", LampState.ON)), FluidLevel.NORMAL, 0)
        assertTrue(drain(e, 2_000).isEmpty())
        e.onTelltales(listOf(TelltaleReading("ENGINE", LampState.ON), TelltaleReading("ABS", LampState.ON)), FluidLevel.NORMAL, 3_000)
        val out = generateSequence(3_000L) { it + 500 }.takeWhile { it <= 5_000 }.mapNotNull { e.tick(it) }.toList()
        assertEquals(listOf("ABS"), out.map { it.lampId })
        e.voiceEnabled = true
        e.onTelltales(listOf(TelltaleReading("ENGINE", LampState.ON), TelltaleReading("ABS", LampState.ON), TelltaleReading("LOW_FUEL", LampState.ON)), FluidLevel.NORMAL, 6_500)
        assertEquals("LOW_FUEL", generateSequence(6_500L) { it + 500 }.take(6).mapNotNull { e.tick(it) }.first().lampId)
    }

    @Test
    fun `안내 음성을 꺼도 직접 고른 다시 듣기는 말한다`() {
        val e = driving()
        e.onTelltales(listOf(TelltaleReading("ENGINE", LampState.ON)), FluidLevel.NORMAL, 0)
        drain(e, 500)
        e.voiceEnabled = false
        e.onSpeed(0.0); e.onBody(BodySignals(gear = Gear.PARK), 1_000)
        assertTrue(e.repeatLast(2_000))
        assertEquals("ENGINE", e.tick(2_000)?.lampId)
    }

    @Test
    fun `그만 해제와 전체 해제`() {
        val e = driving()
        e.mute("ENGINE"); e.mute("AIRBAG")
        assertEquals(setOf("ENGINE", "AIRBAG"), e.mutedLampIds())
        e.unmute("ENGINE")
        assertEquals(setOf("AIRBAG"), e.mutedLampIds())
        e.onTelltales(listOf(TelltaleReading("ENGINE", LampState.ON)), FluidLevel.NORMAL, 0)
        assertEquals("ENGINE", e.tick(0)?.lampId)
        e.unmuteAll()
        assertTrue(e.mutedLampIds().isEmpty())
        e.onDriveStart(1_000); e.mute("ESC"); e.onDriveStart(2_000)
        assertTrue(e.mutedLampIds().isEmpty())
    }

    @Test
    fun `출발 전 점검 항목별 설명서 문장`() {
        val pages = ManualGN7.sections.flatMap { it.lines } + ManualGN7.TPMS_LOW_ACTION + ManualGN7.TPMS_NO_SHOP_ACTION
        assertEquals(ManualGN7.meaning(13), PreTripKind.TIRE.manualMeaning)
        assertTrue(PreTripKind.TIRE.manualActions.all { it in pages })
        assertEquals(listOf("연료 부족 경고등이 켜지면 즉시 연료를 보충하십시오."), PreTripKind.FUEL.manualActions)
        assertTrue(PreTripKind.ENGINE_OIL.manualMeaning!!.startsWith("엔진 오일은"))
        assertEquals(3, PreTripKind.ENGINE_OIL.manualActions.size)
        assertEquals(3, PreTripKind.BRAKE_FLUID.manualActions.size)
        assertNull(PreTripKind.WASHER.manualMeaning)
        assertEquals(3, PreTripKind.WASHER.manualActions.size)
        PreTripKind.entries.forEach { k ->
            assertTrue(k.manualUrl.startsWith("https://ownersmanual.hyundai.com/full_webhelp/GN7/2027/ko_KR/"), k.name)
            k.manualActions.forEach { a -> assertFalse(a.contains("—") || a.contains("·"), a) }
        }
    }
}
