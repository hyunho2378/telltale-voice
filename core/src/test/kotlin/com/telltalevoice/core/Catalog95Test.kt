package com.telltalevoice.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 경고등 도감(DEV_V2 3번, 2026. 11. 11. 사용자 결정: 설명서 이름과 실제 계기판 색, 못 찾은 상수는 숨김). */
class Catalog95Test {
    @Test
    fun `감시는 95종, 도감과 숨김을 합치면 95종`() {
        assertEquals(95, Catalog95.monitored.size)
        assertEquals(PleosTelltaleTypes.ALL, Catalog95.monitored)
        assertEquals(31, Catalog95.all.size)
        assertEquals(64, Catalog95.hiddenIds.size)
        assertEquals(Catalog95.monitored.toSet(), (Catalog95.all.map { it.id } + Catalog95.hiddenIds).toSet())
        assertEquals(Catalog95.all.map { it.id }, PleosTelltaleTypes.ALL.filter { id -> Catalog95.all.any { it.id == id } })
    }

    @Test
    fun `설명서 쪽 37개 항목 번호와 주소`() {
        assertEquals((1..37).toList(), ManualGN7.sections.map { it.no })
        assertTrue(ManualGN7.URL.startsWith("https://ownersmanual.hyundai.com/full_webhelp/GN7/2027/ko_KR/"))
        Catalog95.all.forEach { assertEquals(ManualGN7.URL, it.nameSource, it.id) }
    }

    @Test
    fun `이름과 문장은 설명서 항목 그대로`() {
        for (e in Catalog95.all) {
            val s = ManualGN7.section(e.manualNo)
            assertEquals(s.name, e.nameKo, e.id)
            assertEquals(s.lines, e.manualLines, e.id)
            assertEquals(ManualGN7.meaning(e.manualNo), e.manualMeaning, e.id)
            assertTrue(e.manualMeaning in s.lines, e.id)
            assertEquals(ManualGN7.actions(e.manualNo), e.actions, e.id)
            assertTrue(e.actions.all { it.contains("십시오") }, e.id)
        }
    }

    @Test
    fun `색 묶음은 설명서 표의 아이콘 색`() {
        val red = Catalog95.byColor(ColorGroup.RED).map { it.id }
        assertEquals(listOf("PARKING_BRAKE", "SEAT_BELT", "MDPS", "LOW_VOLT_BATTERY", "AIRBAG", "ENGINE_OIL_PRESSURE", "ENGINE_TEMPERATURE", "LPI"), red)
        assertEquals(ColorGroup.YELLOW, Catalog95.byId("ABS")!!.colorGroup)
        assertEquals(ColorGroup.YELLOW, Catalog95.byId("TPMS")!!.colorGroup)
        assertEquals(ColorGroup.GREEN_BLUE, Catalog95.byId("HEADLAMP_HIGH")!!.colorGroup)
        assertEquals(listOf(ManualColor.BLUE), Catalog95.byId("HEADLAMP_HIGH")!!.colors)
        // 표에 색이 없는 차로 유지 보조는 본문의 초록색
        assertEquals(ColorGroup.GREEN_BLUE, Catalog95.byId("LFA")!!.colorGroup)
        assertEquals(Catalog95.all.size, ColorGroup.entries.sumOf { Catalog95.byColor(it).size })
        assertEquals(listOf(8, 14, 9), ColorGroup.entries.map { Catalog95.byColor(it).size })
        assertEquals(listOf(ColorGroup.RED, ColorGroup.YELLOW, ColorGroup.GREEN_BLUE), Catalog95.grouped.keys.toList())
    }

    @Test
    fun `상태에 따라 색이 바뀌는 등은 본문 색을 모두 싣는다`() {
        assertEquals(listOf(ManualColor.ORANGE, ManualColor.GREEN, ManualColor.WHITE), Catalog95.byId("AUTO_HOLD")!!.colors)
    }

    @Test
    fun `색이 적혀 있지 않은 도로 결빙 경고등은 숨긴다`() {
        assertEquals(36, Catalog95.pairedNo["ICY_ROAD"])
        assertTrue(ManualGN7.section(36).symbolColors.isEmpty() && ManualGN7.section(36).textColors.isEmpty())
        assertTrue("ICY_ROAD" in Catalog95.hiddenIds)
    }

    @Test
    fun `분류표 짝이 있는 상수는 엔진 갈래를 싣는다`() {
        for (e in Catalog95.all.filter { it.lampId != null }) assertEquals(LampCatalog.byId(e.lampId!!)!!.tier, e.tier, e.id)
        assertEquals("LOW_TIRE_PRESSURE", Catalog95.byId("TPMS")!!.lampId)
        assertEquals("TIRE", Catalog95.byId("TPMS")!!.iconKind)
    }

    @Test
    fun `같은 이름은 같은 설명서 항목일 때만`() {
        Catalog95.all.groupBy { it.nameKo }.forEach { (name, list) -> assertEquals(1, list.map { it.manualNo }.toSet().size, name) }
        assertEquals(Catalog95.all.size - 2, Catalog95.all.map { it.nameKo }.toSet().size) // 방향지시등 좌우, 차로변경 보조 좌우
    }

    @Test
    fun `글자 깨짐 없이 한국어 이름과 문장`() {
        val glyph = Regex("[ʀ-ʯȀ-ɏƀ-ǿⱠ-Ɀ]")
        Catalog95.all.forEach { e -> assertFalse(glyph.containsMatchIn(e.nameKo + e.manualLines.joinToString()), e.id) }
        assertTrue(Catalog95.all.all { it.manualLines.isNotEmpty() && it.iconKind.isNotBlank() })
    }

    @Test
    fun `Pleos 상수 짝이 없는 설명서 경고등 세 개`() {
        assertEquals(listOf("BRAKE", "EBD", "FOUR_WHEEL_DRIVE"), Catalog95.manualOnly.map { it.id })
        assertEquals(listOf("브레이크 경고등", "EBD 경고등", "4륜구동 경고등"), Catalog95.manualOnly.map { it.nameKo })
        assertEquals(ColorGroup.RED, Catalog95.manualOnly.first().colorGroup)
        assertEquals(ColorGroup.YELLOW, Catalog95.manualOnly.last().colorGroup)
    }
}
