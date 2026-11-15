package com.telltalevoice.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 판정 정확성 점검(2026. 11. 14.): 도감과 판정 규칙을 2027 그랜저 설명서 원문과 다시 대조한 결과를 고정한다. */
class DecisionAudit2027Test {
    /** 분류표 경고등과 2027 설명서 항목 번호(ManualConsistencyTest 와 같은 표) */
    private val lampSection = mapOf(
        "ENGINE_OIL_PRESSURE" to 6, "ENGINE_TEMPERATURE" to 7, "BRAKE" to 8, "CHARGING" to 4, "ABS" to 10,
        "LOW_TIRE_PRESSURE" to 13, "MDPS" to 3, "EBD" to 9, "LOW_FUEL" to 12, "ENGINE" to 17, "AIRBAG" to 2,
        "ELECTRONIC_PARKING_BRAKE" to 14, "FOUR_WHEEL_DRIVE" to 11, "LED_HEADLAMP" to 19, "ESC" to 20,
        "AUTO_HOLD" to 16, "DRIVER_ASSIST" to 22, "MASTER_WARNING" to 15, "LPG_OVERCHARGE" to 5,
    )

    @Test
    fun `바로 정차 갈래는 2027 행동 문장에 정차 지시가 있는 경고등과 정확히 같다`() {
        for ((id, _) in lampSection) {
            val l = LampCatalog.byId(id)!!
            val stop = l.manualActions.any { it.contains("정차") }
            assertEquals(stop, l.tier == Tier.STOP_NOW, "$id: ${l.manualActions}")
        }
    }

    @Test
    fun `휴게소까지 기본 규칙 문장은 정차 지시가 없다는 사실만 말한다`() {
        val s = DecisionRule.REST_AREA_DEFAULT.sentence
        assertFalse(s.contains("속도를 낮추"), s)
        for (l in LampCatalog.ofTier(Tier.REST_AREA).filter { it.id in lampSection }) {
            assertTrue(l.manualActions.none { it.contains("정차") }, l.id)
        }
    }

    @Test
    fun `저압 타이어 고속도로와 일반도로 문장의 속도 낮추기는 원문에 있다`() {
        assertTrue(ManualGN7.TPMS_LOW_ACTION.contains("속도를 낮추고"))
        assertTrue(DecisionRule.TIRE_HIGHWAY.sentence.contains("속도를 낮춰"))
        assertTrue(DecisionRule.TIRE_GENERAL.sentence.contains("속도를 낮춰"))
    }

    @Test
    fun `브레이크 경고와 ABS 가 함께 켜지면 EBD 도 켜진다`() {
        val r = TelltaleResolver()
        val ids = r.resolve(listOf(TelltaleReading("ABS", LampState.ON)), FluidLevel.LOW).map { it.id }
        assertTrue("EBD" in ids)
        assertTrue(ManualGN7.section(9).lines.first().startsWith("브레이크 경고등과 ABS 경고등이 동시에 켜진 경우"))
        assertFalse("EBD" in TelltaleResolver().resolve(listOf(TelltaleReading("ABS", LampState.ON)), FluidLevel.NORMAL).map { it.id })
        assertFalse("EBD" in TelltaleResolver().resolve(emptyList(), FluidLevel.LOW).map { it.id })
    }

    @Test
    fun `해야 할 일이 없는 상태 표시등은 도감에서 갈래를 싣지 않는다`() {
        val lfa = Catalog95.byId("LFA")!!
        assertNull(lfa.tier); assertNull(lfa.lampId)
        assertTrue(ManualGN7.actions(29).isEmpty())
        Catalog95.all.filter { it.tier != null }.forEach { assertTrue(it.actions.isNotEmpty(), it.id) }
    }

    @Test
    fun `깜빡임이 정상 작동인 표시등은 켜짐만 이상으로 본다`() {
        // (20) "ESC 제어 기능 작동 중 깜빡입니다", "경고등이 켜진 상태로 유지되는 경우 ... 점검"
        assertEquals(setOf(LampState.ON), (LampCatalog.ESC.signal as LampSignal.Telltale).states)
        assertTrue(TelltaleResolver().resolve(listOf(TelltaleReading("ESC", LampState.BLINK))).isEmpty())
        // (16) 자동 정차는 주황색(Pleos 색 값 YELLOW)만 이상
        assertEquals(setOf(LampColor.YELLOW), (LampCatalog.AUTO_HOLD.signal as LampSignal.Telltale).colors)
        assertTrue(ManualGN7.section(16).lines.any { it.startsWith("주황색: 자동 정차 기능 이상") })
    }

    @Test
    fun `TPMS 는 깜빡인 뒤 켜지면 이상, 켜지기만 하면 저압`() {
        assertTrue(ManualGN7.section(13).lines.any { it.contains("약 1분간 깜빡인 후 켜지면") })
        val r = TelltaleResolver()
        r.resolve(listOf(TelltaleReading("TPMS", LampState.BLINK)))
        assertEquals(listOf("TPMS_FAULT"), r.resolve(listOf(TelltaleReading("TPMS", LampState.ON))).map { it.id })
        assertEquals(listOf("LOW_TIRE_PRESSURE"), TelltaleResolver().resolve(listOf(TelltaleReading("TPMS", LampState.ON))).map { it.id })
    }
}
