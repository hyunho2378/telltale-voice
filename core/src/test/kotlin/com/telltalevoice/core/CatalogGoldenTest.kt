package com.telltalevoice.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

/**
 * 기준 고정 시험. 분류, 음성 문장, 매뉴얼 원문을 글자 하나까지 고정한다.
 * 뜻과 행동 원문은 2026. 11. 11. 사용자 결정으로 2027 그랜저(GN7) 설명서(ManualGN7)로 바뀌었다(TPMS 이상, 예열, 이모빌라이저는 예전 원문).
 * 기준이 바뀌면 이 표를 함께 고쳐야 시험이 통과한다.
 */
class CatalogGoldenTest {

    data class Golden(
        val tier: Tier,
        val telltales: List<String>?,
        val speech: String,
        val meaning: String,
        val firstAction: String,
        val actionCount: Int,
    )

    private val arrival = "도착하면 점검 방법을 알려 드리겠습니다."

    private val golden: Map<String, Golden> = linkedMapOf(
        "ENGINE_OIL_PRESSURE" to Golden(Tier.STOP_NOW, listOf("ENGINE_OIL_PRESSURE"),
            "엔진 오일 압력 경고등이 켜졌습니다. 도로 옆으로 정차하십시오.",
            "오일이 부족하여 유압이 낮아지면 켜집니다.",
            "주행 중에 엔진 오일 압력 경고등이 켜지면 도로 옆으로 차를 안전하게 정차하고 엔진 오일양을 점검한 후 부족하면 보충하십시오.", 2),
        "ENGINE_TEMPERATURE" to Golden(Tier.STOP_NOW, listOf("ENGINE_TEMPERATURE"),
            "냉각수 수온 경고등이 켜졌습니다. 비상 경고등을 켜고 정차하십시오.",
            "냉각수 온도가 적정 범위를 넘을 경우 적색 표시등이 켜집니다.",
            "비상 경고등을 켜고 도로 가장자리에 안전하게 정차한 후 'P'(주차)로 변속하고 주 브레이크를 거십시오.", 2),
        "BRAKE" to Golden(Tier.STOP_NOW, null,
            "브레이크 경고등이 켜졌습니다. 서서히 감속 후 정차하십시오.",
            "브레이크액 부족",
            "브레이크액을 보충하십시오(브레이크액 점검하기 참고)", 8),
        "ABS" to Golden(Tier.STOP_NOW, listOf("ABS"),
            "ABS 경고등이 켜졌습니다. 안전한 장소에 정차하십시오.",
            "ABS(Anti-lock Brake System) 이상 발생 시 켜집니다.",
            "안전한 장소에 정차한 후 시동을 껐다가 다시 거십시오.", 2),
        "CHARGING" to Golden(Tier.REST_AREA, listOf("LOW_VOLT_BATTERY"),
            "충전 경고등이 켜졌습니다. 배터리 충전 상태를 확인하고 차량을 점검하십시오.",
            "구동 벨트가 끊어지거나 충전 장치에 이상이 있을 때 켜집니다.",
            "배터리 충전 상태를 확인하고 구동 벨트나 충전 계통을 점검한 후에도 경고등이 꺼지지 않으면 당사 직영 하이테크센터나 블루핸즈에서 차량을 점검하십시오.", 1),
        "LOW_TIRE_PRESSURE" to Golden(Tier.REST_AREA, listOf("TPMS"),
            "타이어 공기압이 낮습니다. 속도를 낮추고 급격한 조향을 피하십시오.",
            "타이어 공기압이 현저하게 낮거나 TPMS(저압 타이어 감시 시스템) 이상 시 경고등이 켜집니다.",
            "저압 타이어 경고등이 켜지면 속도를 낮추고 급격한 조향을 피하며 당사 직영 하이테크센터나 블루핸즈를 방문해 타이어 상태를 점검한 후 타이어 공기압을 조절하십시오.", 5),
        "MDPS" to Golden(Tier.REST_AREA, listOf("MDPS"),
            "전동 파워 스티어링 경고등이 켜졌습니다. 안전에 유의하며 블루핸즈에서 차량을 점검하십시오.",
            "전동 파워 스티어링 기능 이상 시 경고등이 켜집니다.",
            "시동 후 경고등이 켜지지 않거나, 경고등이 켜진 후 3초가 지나도 꺼지지 않으면 안전에 유의하며 당사 직영 하이테크센터나 블루핸즈에서 차량을 점검하십시오.", 1),
        "EBD" to Golden(Tier.REST_AREA, null,
            "EBD 경고등이 켜졌습니다. 고속 주행을 피하십시오.",
            "브레이크 경고등과 ABS 경고등이 동시에 켜진 경우, 제동력의 전후륜 배분 기능(EBD)도 정상적으로 작동하지 않습니다.",
            "차체가 불안정할 수 있으므로 고속 주행을 피하십시오.", 2),
        "LOW_FUEL" to Golden(Tier.REST_AREA, listOf("LOW_FUEL"),
            "연료가 부족합니다. 즉시 연료를 보충하십시오.",
            "남은 연료량이 적을 시 주황색으로 경고등이 켜집니다.",
            "연료 부족 경고등이 켜지면 즉시 연료를 보충하십시오.", 1),
        "LPG_OVERCHARGE" to Golden(Tier.REST_AREA, listOf("LPI"),
            "LPG 과충전 경고등이 켜졌습니다. 블루핸즈에서 차량을 점검하십시오.",
            "LPI 차량은 LPG가 과충전 되었을 경우 빨간색으로 경고등이 켜집니다.",
            "경고등이 켜지면 당사 직영 하이테크센터나 블루핸즈에서 차량을 점검하십시오.", 1),
        "ENGINE" to Golden(Tier.AFTER_ARRIVAL, listOf("ENGINE"),
            "엔진 경고등이 켜졌습니다. $arrival",
            "엔진 제어 장치, 연료 공급 장치 등에 이상이 있는 경우 켜집니다.",
            "3초가 지나도 경고등이 꺼지지 않으면 엔진 제어 장치, 연료 공급 장치 등에 이상이 있는 것이므로 가능한 한 빨리 당사 직영 하이테크센터나 블루핸즈에서 차량을 점검하십시오.", 4),
        "TPMS_FAULT" to Golden(Tier.AFTER_ARRIVAL, listOf("TPMS"),
            "TPMS 경고등이 깜빡인 후 켜졌습니다. $arrival",
            "타이어 공기압 경보 시스템에 이상이 있으면 클러스터 표시창에 TPMS 경고등이 깜빡인 후 켜집니다.",
            "즉시 당사 직영 하이테크센터나 블루핸즈에서 점검을 받으십시오.", 1),
        "AIRBAG" to Golden(Tier.AFTER_ARRIVAL, listOf("AIRBAG"),
            "에어백 경고등이 켜졌습니다. $arrival",
            "에어백 및 안전벨트 프리텐셔너 이상 발생 시 켜집니다.",
            "시동 시 경고등이 켜지지 않거나, 경고등이 켜진 후 3–6초가 지나도 꺼지지 않으면 장치에 이상이 발생한 것입니다. 당사 직영 하이테크센터나 블루핸즈에서 차량을 점검하십시오.", 1),
        "ELECTRONIC_PARKING_BRAKE" to Golden(Tier.AFTER_ARRIVAL, listOf("ELECTRONIC_PARKING_BRAKE"),
            "전자식 주차 브레이크 경고등이 켜졌습니다. $arrival",
            "전자식 주차 브레이크(Electric Parking Brake) 이상 시 경고등이 켜집니다.",
            "시동 시 켜진 경고등이 3초 후 꺼지지 않거나 주행 중 경고등이 켜지면 당사 직영 하이테크센터나 블루핸즈에서 차량을 점검하십시오.", 1),
        "FOUR_WHEEL_DRIVE" to Golden(Tier.AFTER_ARRIVAL, null,
            "4륜구동 경고등이 켜졌습니다. $arrival",
            "4륜구동에 이상 발생 시 경고등이 켜집니다.",
            "경고등이 꺼지지 않거나 빈번하게 켜질 경우, 당사 직영 하이테크센터나 블루핸즈에서 차량을 점검하십시오.", 1),
        "LED_HEADLAMP" to Golden(Tier.AFTER_ARRIVAL, listOf("LED_HEADLAMP"),
            "LED 전조등 고장 경고등이 켜졌습니다. $arrival",
            "LED 전조등 이상 발생 시 경고등이 켜지거나 LED 전조등 관련 부품 이상 발생 시 경고등이 깜빡입니다.",
            "당사 직영 하이테크센터나 블루핸즈에서 차량을 점검하십시오.", 1),
        "ESC" to Golden(Tier.AFTER_ARRIVAL, listOf("ESC"),
            "ESC 경고등이 켜졌습니다. $arrival",
            "ESC 제어 기능 작동 중 깜빡입니다.",
            "경고등이 켜진 상태로 유지되는 경우 당사 직영 하이테크센터나 블루핸즈에서 차량을 점검하십시오.", 1),
        "AUTO_HOLD" to Golden(Tier.AFTER_ARRIVAL, listOf("AUTO_HOLD"),
            "자동 정차 기능 표시등이 켜졌습니다. $arrival",
            "주황색: 자동 정차 기능 이상 발생",
            "주황색 표시등이 켜지는 경우 당사 직영 하이테크센터나 블루핸즈에서 차량을 점검하십시오.", 1),
        "DRIVER_ASSIST" to Golden(Tier.AFTER_ARRIVAL, listOf("FCA", "LKA", "LFA", "ELK"),
            "운전자 보조 경고등이 켜졌습니다. $arrival",
            "주황색: 전방 안전 기능 꺼짐/기능 고장 또는 이상 발생",
            "경고등이 켜진 상태로 유지되는 경우 전방 안전 기능이 설정되어 있는지 확인하십시오. 전방 안전 기능과 관련된 센서를 점검한 후 필요 시 이물질을 제거하십시오(인식 센서 및 제한 사항 참고). 센서의 이물질을 제거한 후에도 경고등이 켜지면 당사 직영 하이테크센터나 블루핸즈에서 차량을 점검하십시오.", 1),
        "MASTER_WARNING" to Golden(Tier.AFTER_ARRIVAL, listOf("MASTER_WARNING"),
            "통합 경고등이 켜졌습니다. $arrival",
            "운전자 보조의 각 기능 고장 또는 기능 이상이 발생 시 주황색으로 켜집니다.",
            "이상 센서의 이물질을 제거한 후에도 경고등이 켜지면 당사 직영 하이테크센터나 블루핸즈에서 차량을 점검하십시오.", 1),
        "PREHEAT" to Golden(Tier.AFTER_ARRIVAL, listOf("PREHEAT"),
            "예열 경고등이 켜졌습니다. $arrival",
            "예열 표시등이 깜빡이면 예열 시스템에 이상이 있는 것",
            "즉시 당사 직영 하이테크센터나 블루핸즈에서 점검을 받으십시오. 방치하면 화재가 날 수 있습니다.", 1),
        "IMMOBILIZER" to Golden(Tier.AFTER_ARRIVAL, listOf("IMMOBILIZER"),
            "이모빌라이저 경고등이 켜졌습니다. $arrival",
            "이모빌라이저 시스템의 고장이 예상되므로",
            "즉시 당사 직영 하이테크센터나 블루핸즈에서 차량을 점검 및 정비하십시오.", 1),
    )

    private fun each(name: String, check: (WarningLamp, Golden) -> Unit): List<DynamicTest> =
        LampCatalog.all.map { lamp ->
            dynamicTest("$name: ${lamp.id}") { check(lamp, golden.getValue(lamp.id)) }
        }

    @Test
    fun `분류표는 22개 경고등이고 기준 표와 같은 순서`() {
        assertEquals(22, LampCatalog.all.size)
        assertEquals(golden.keys.toList(), LampCatalog.all.map { it.id })
    }

    @Test
    fun `갈래별 개수 고정 STOP_NOW 4 REST_AREA 6 AFTER_ARRIVAL 12`() {
        assertEquals(4, LampCatalog.ofTier(Tier.STOP_NOW).size)
        assertEquals(6, LampCatalog.ofTier(Tier.REST_AREA).size)
        assertEquals(12, LampCatalog.ofTier(Tier.AFTER_ARRIVAL).size)
    }

    @Test
    fun `경고등 id 는 겹치지 않음`() {
        assertEquals(LampCatalog.all.size, LampCatalog.all.map { it.id }.toSet().size)
    }

    @TestFactory
    fun `갈래 고정`() = each("갈래") { lamp, g -> assertEquals(g.tier, lamp.tier) }

    @TestFactory
    fun `Pleos 상수 짝 고정`() = each("상수") { lamp, g ->
        val s = lamp.signal
        if (g.telltales == null) assertTrue(s !is LampSignal.Telltale, "짝 없는 경고등인데 상수가 있음")
        else assertEquals(g.telltales, (s as LampSignal.Telltale).types)
    }

    @TestFactory
    fun `음성 문장 고정`() = each("문장") { lamp, g -> assertEquals(g.speech, SpeechBuilder.warning(lamp).text) }

    @TestFactory
    fun `음성 규칙 위반 없음`() = each("규칙") { lamp, _ ->
        assertEquals(emptyList<String>(), SpeechBuilder.violations(lamp))
    }

    @TestFactory
    fun `핵심 낱말 4개 이하`() = each("낱말") { lamp, _ -> assertTrue(lamp.keywords.size <= 4) }

    @TestFactory
    fun `매뉴얼 뜻 원문 고정`() = each("뜻") { lamp, g -> assertEquals(g.meaning, lamp.manualMeaning) }

    @TestFactory
    fun `매뉴얼 첫 행동 원문 고정`() = each("행동") { lamp, g -> assertEquals(g.firstAction, lamp.manualActions.first()) }

    @TestFactory
    fun `매뉴얼 행동 문장 수 고정`() = each("행동수") { lamp, g -> assertEquals(g.actionCount, lamp.manualActions.size) }

    @TestFactory
    fun `음성 엔진 글에 영문 약어 없음`() = each("TTS") { lamp, _ ->
        val tts = SpeechBuilder.warning(lamp).ttsText
        assertTrue(tts.none { it in 'A'..'Z' }, tts)
    }

    @TestFactory
    fun `출처 표기 있음`() = each("출처") { lamp, _ ->
        assertTrue(lamp.manualSource.startsWith("현대자동차"), lamp.manualSource)
    }

    @Test
    fun `STOP_NOW 문장은 모두 정차하십시오로 끝남`() {
        LampCatalog.ofTier(Tier.STOP_NOW).forEach { assertTrue(SpeechBuilder.warning(it).text.endsWith("정차하십시오."), it.id) }
    }

    @Test
    fun `AFTER_ARRIVAL 문장은 계속 주행 가능이라고 말하지 않음`() {
        LampCatalog.all.forEach { assertTrue(!SpeechBuilder.warning(it).text.contains("계속 주행"), it.id) }
    }

    @Test
    fun `짝 있는 Pleos 상수 21개 고정`() {
        assertEquals(
            setOf("ENGINE_OIL_PRESSURE", "ENGINE_TEMPERATURE", "LOW_VOLT_BATTERY", "ABS", "TPMS", "MDPS", "LOW_FUEL",
                "ENGINE", "AIRBAG", "ELECTRONIC_PARKING_BRAKE", "LED_HEADLAMP", "ESC", "AUTO_HOLD",
                "FCA", "LKA", "LFA", "ELK", "MASTER_WARNING", "PREHEAT", "IMMOBILIZER", "LPI"),
            LampCatalog.mappedTelltaleTypes,
        )
    }

    @Test
    fun `짝 상수는 모두 Pleos TelltaleType 95종 안에 있음`() {
        assertEquals(95, PleosTelltaleTypes.ALL.size)
        assertEquals(95, PleosTelltaleTypes.ALL.toSet().size)
        assertTrue(PleosTelltaleTypes.ALL.containsAll(LampCatalog.mappedTelltaleTypes))
        assertTrue(PleosTelltaleTypes.ALL.containsAll(listOf("TURN_SIGNAL_LEFT", "TURN_SIGNAL_RIGHT", "TRUNK_OPEN", "DOOR_OPEN")))
    }

    @Test
    fun `TPMS 한 상수에 경고등 두 개`() {
        assertEquals(listOf("LOW_TIRE_PRESSURE", "TPMS_FAULT"), LampCatalog.forTelltaleType("TPMS").map { it.id })
    }

    @Test
    fun `byId 없는 이름은 null`() {
        assertEquals(null, LampCatalog.byId("NOPE"))
        assertEquals("BRAKE", LampCatalog.byId("BRAKE")?.id)
    }

    @Test
    fun `갈래 우선순위 숫자 고정`() {
        assertEquals(listOf(0, 1, 2), Tier.entries.map(LampCatalog::urgency))
    }
}
