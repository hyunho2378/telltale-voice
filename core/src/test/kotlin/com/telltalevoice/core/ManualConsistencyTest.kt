package com.telltalevoice.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 도감(Catalog95)과 정차 뒤 화면(LampCatalog)의 뜻 문장과 해야 할 일 문장이 같은 2027 그랜저(GN7) 설명서 원문인지 대조한다(2026. 11. 11. 사용자 지시).
 */
class ManualConsistencyTest {
    /** 분류표 경고등과 그 경고등의 설명서 항목 번호 */
    private val lampSection = mapOf(
        "ENGINE_OIL_PRESSURE" to 6, "ENGINE_TEMPERATURE" to 7, "BRAKE" to 8, "CHARGING" to 4, "ABS" to 10,
        "LOW_TIRE_PRESSURE" to 13, "MDPS" to 3, "EBD" to 9, "LOW_FUEL" to 12, "ENGINE" to 17, "AIRBAG" to 2,
        "ELECTRONIC_PARKING_BRAKE" to 14, "FOUR_WHEEL_DRIVE" to 11, "LED_HEADLAMP" to 19, "ESC" to 20,
        "AUTO_HOLD" to 16, "DRIVER_ASSIST" to 22, "MASTER_WARNING" to 15, "LPG_OVERCHARGE" to 5,
    )
    private val entries get() = Catalog95.all + Catalog95.manualOnly

    @Test
    fun `엔진 오일 압력 뜻은 2027 설명서 원문 하나`() {
        val want = "오일이 부족하여 유압이 낮아지면 켜집니다."
        assertEquals(want, LampCatalog.ENGINE_OIL_PRESSURE.manualMeaning)
        assertEquals(want, Catalog95.byId("ENGINE_OIL_PRESSURE")!!.manualMeaning)
        assertTrue(want in ManualGN7.section(6).lines)
    }

    @Test
    fun `같은 설명서 항목이면 도감과 정차 뒤 화면의 뜻이 같다`() {
        val pairs = entries.filter { it.lampId != null && lampSection[it.lampId] == it.manualNo }
        assertEquals(19, pairs.map { it.lampId }.toSet().size)
        pairs.forEach { e -> assertEquals(e.manualMeaning, LampCatalog.byId(e.lampId!!)!!.manualMeaning, e.id) }
    }

    @Test
    fun `같은 설명서 항목이면 도감과 정차 뒤 화면의 해야 할 일 문장이 같다`() {
        val pairs = entries.filter { it.lampId != null && lampSection[it.lampId] == it.manualNo }
        pairs.forEach { e -> assertEquals(e.actions, LampCatalog.byId(e.lampId!!)!!.manualActions, e.id) }
    }

    @Test
    fun `정차 뒤 화면 19개 경고등의 문장은 모두 2027 설명서 원문이고 출처도 2027`() {
        val pages = ManualGN7.sections.flatMap { it.lines } + listOf(
            ManualGN7.TPMS_LOW_ACTION, ManualGN7.TPMS_NO_SHOP_ACTION, ManualGN7.OVERHEAT_STOP_ACTION, ManualGN7.OVERHEAT_INSPECT_ACTION,
        )
        for ((id, no) in lampSection) {
            val l = LampCatalog.byId(id)!!
            assertTrue(l.manualMeaning in pages, id)
            l.manualActions.forEach { assertTrue(it in pages, "$id: $it") }
            assertTrue(l.manualActions.isNotEmpty(), id)
            assertTrue(l.manualSource.startsWith(ManualGN7.SOURCE), id)
            assertEquals(ManualGN7.sourceOf(no), l.manualSource, id)
        }
    }

    @Test
    fun `예전 판 문장과 영문 문장이 정차 뒤 화면에 남지 않는다`() {
        val old = listOf("점검을 받으십시오", "Drive carefully", "드라이브 벨트", "엔진 오일이 부족하여", "반드시 브레이크액을 보충한 후 주행하십시오")
        for (id in lampSection.keys) {
            val l = LampCatalog.byId(id)!!
            val all = (listOf(l.manualMeaning) + l.manualActions).joinToString(" ")
            old.forEach { assertTrue(!all.contains(it), "$id: $it") }
        }
    }

    @Test
    fun `행동 문장이 없는 경고등 쪽은 같은 설명서 다른 쪽 원문으로 채운다`() {
        assertEquals(listOf(ManualGN7.OVERHEAT_STOP_ACTION, ManualGN7.OVERHEAT_INSPECT_ACTION), LampCatalog.ENGINE_TEMPERATURE.manualActions)
        assertEquals(ManualGN7.TPMS_LOW_ACTION, LampCatalog.LOW_TIRE_PRESSURE.manualActions.first())
        assertEquals(ManualGN7.TPMS_NO_SHOP_ACTION, LampCatalog.LOW_TIRE_PRESSURE.manualActions.last())
    }

    @Test
    fun `출발 전 점검 문장도 2027 원문`() {
        assertEquals("즉시 브레이크액을 보충하십시오.", PreTrip.BRAKE_FLUID_SENTENCE)
        assertEquals("타이어 공기압을 확인한 후 필요 시 공기를 보충하십시오.", PreTrip.TIRE_SENTENCE)
        assertTrue(ManualGN7.section(8).lines.any { it.contains(PreTrip.BRAKE_FLUID_SENTENCE) })
        assertTrue(PreTrip.TIRE_SENTENCE in ManualGN7.section(13).lines)
        assertTrue(PreTrip.FUEL_SENTENCE in ManualGN7.section(12).lines)
    }
}
