package com.telltalevoice.core

/**
 * 경고등 분류표.
 * 원문: 05. 설득리서치/V2_경고등별_판단기준과_갓길위험.md 1-3 표(매뉴얼 원문), 2-2 표(Pleos 상수 짝), 묶음 6(세 갈래).
 * 2026. 11. 11. 사용자 결정: 원인과 조치 방법 문장은 Pleos Connect 차종인 2027 그랜저(GN7) 설명서 원문(ManualGN7)을 경고등 정보와 같은 함수로 읽는다.
 * 2027 설명서 경고등 쪽에 없는 TPMS 이상, 예열, 이모빌라이저만 예전 원문을 둔다.
 * 즉시 정차 경고의 행동 문장은 4어절 이하(외부 자문: 급한 상황의 지시문은 핵심 어휘 위주로 3어절 정도).
 * 음성 문장(subject, predicate, action)도 2027 원문 낱말로 짧게 자른다. 원문에 없는 낱말은 넣지 않는다(VOICE_LINES_2027.md).
 * 도착 후 점검 문장 끝("도착하면 점검 방법을 알려 드리겠습니다")은 설명서가 아니라 앱이 하는 일을 알리는 문장이라 그대로 둔다.
 * 원문 문장은 고치지 않는다. 단위 시험(CatalogGoldenTest)이 글자 하나까지 고정한다.
 */
object LampCatalog {
    private const val GN7 = "현대자동차, 그랜저 GN7 2026 웹 사용설명서"
    private const val GN7_CH8 = "현대자동차, 그랜저 GN7 2026 웹 사용설명서 8장"
    private const val LX3 = "현대자동차, 팰리세이드 LX3 2026 웹 사용설명서"
    private const val CN7_EN = "Hyundai, Elantra CN7 2026 Owner's Manual"

    val ENGINE_OIL_PRESSURE = WarningLamp(
        id = "ENGINE_OIL_PRESSURE", manualNo = 1, nameKo = "엔진 오일 압력 경고등", tier = Tier.STOP_NOW,
        signal = LampSignal.Telltale(listOf("ENGINE_OIL_PRESSURE")),
        manualMeaning = ManualGN7.meaning(6),
        manualActions = ManualGN7.actions(6),
        manualSource = ManualGN7.sourceOf(6),
        subject = "엔진 오일 압력 경고등", predicate = "켜졌습니다", action = "도로 옆으로 정차하십시오",
        keywords = listOf("엔진 오일 압력", "도로 옆", "정차하십시오"),
        pairNote = "짝 있음",
    )

    val ENGINE_TEMPERATURE = WarningLamp(
        id = "ENGINE_TEMPERATURE", manualNo = 3, nameKo = "냉각수 수온 경고등", tier = Tier.STOP_NOW,
        signal = LampSignal.Telltale(listOf("ENGINE_TEMPERATURE")),
        manualMeaning = ManualGN7.meaning(7),
        manualActions = ManualGN7.actions(7),
        manualSource = ManualGN7.sourceOf(7),
        subject = "냉각수 수온 경고등", predicate = "켜졌습니다", action = "비상 경고등을 켜고 정차하십시오",
        keywords = listOf("냉각수 수온", "비상 경고등", "정차하십시오"),
        pairNote = "이름으로 짝지음. 뜻 설명은 문서에 없음",
    )

    val BRAKE = WarningLamp(
        id = "BRAKE", manualNo = 4, nameKo = "브레이크 경고등", tier = Tier.STOP_NOW,
        signal = LampSignal.BrakeFluidLow,
        manualMeaning = ManualGN7.meaning(8),
        manualActions = ManualGN7.actions(8),
        manualSource = ManualGN7.sourceOf(8),
        subject = "브레이크 경고등", predicate = "켜졌습니다", action = "서서히 감속 후 정차하십시오",
        keywords = listOf("브레이크", "감속", "정차하십시오"),
        pairNote = "짝 없음. 브레이크액은 별도 함수 getBrakeFluidLevelState로 읽음",
    )

    val CHARGING = WarningLamp(
        // 2026. 11. 11. 사용자 결정: 2027 원문에 정차 지시가 없고 배터리 충전 상태 확인과 점검 지시만 있어 STOP_NOW 에서 REST_AREA 로 옮김
        id = "CHARGING", manualNo = 5, nameKo = "충전 경고등", tier = Tier.REST_AREA,
        signal = LampSignal.Telltale(listOf("LOW_VOLT_BATTERY")),
        manualMeaning = ManualGN7.meaning(4),
        manualActions = ManualGN7.actions(4),
        manualSource = ManualGN7.sourceOf(4),
        subject = "충전 경고등", predicate = "켜졌습니다", action = "배터리 충전 상태를 확인하고 차량을 점검하십시오",
        keywords = listOf("충전", "배터리 충전 상태", "확인", "점검하십시오"),
        pairNote = "이름이 다름. 저전압 배터리와 충전 경고등이 같은 등인지 문서에 없음",
    )

    val ABS = WarningLamp(
        id = "ABS", manualNo = 10, nameKo = "ABS 경고등", tier = Tier.STOP_NOW,
        signal = LampSignal.Telltale(listOf("ABS")),
        manualMeaning = ManualGN7.meaning(10),
        manualActions = ManualGN7.actions(10),
        manualSource = ManualGN7.sourceOf(10),
        subject = "ABS 경고등", predicate = "켜졌습니다", action = "안전한 장소에 정차하십시오",
        keywords = listOf("ABS", "경고등", "안전한 장소", "정차하십시오"),
        pairNote = "짝 있음",
    )

    val LOW_TIRE_PRESSURE = WarningLamp(
        id = "LOW_TIRE_PRESSURE", manualNo = 6, nameKo = "저압 타이어 경고등", tier = Tier.REST_AREA,
        signal = LampSignal.Telltale(listOf("TPMS"), states = setOf(LampState.ON)),
        manualMeaning = ManualGN7.meaning(13),
        manualActions = ManualGN7.actions(13),
        manualSource = ManualGN7.sourceOf(13),
        subject = "타이어 공기압", predicate = "낮습니다", action = "속도를 낮추고 급격한 조향을 피하십시오",
        keywords = listOf("타이어 공기압", "속도", "급격한 조향", "피하십시오"),
        pairNote = "짝 있음",
    )

    val EBD = WarningLamp(
        id = "EBD", manualNo = 11, nameKo = "EBD 경고(브레이크와 ABS 동시)", tier = Tier.REST_AREA,
        signal = LampSignal.None,
        manualMeaning = ManualGN7.meaning(9),
        manualActions = ManualGN7.actions(9),
        manualSource = ManualGN7.sourceOf(9),
        subject = "EBD 경고등", predicate = "켜졌습니다", action = "고속 주행을 피하십시오",
        keywords = listOf("EBD", "경고등", "고속 주행", "피하십시오"),
        pairNote = "짝 없음",
    )

    val LOW_FUEL = WarningLamp(
        id = "LOW_FUEL", manualNo = 13, nameKo = "연료 부족 경고등", tier = Tier.REST_AREA,
        signal = LampSignal.Telltale(listOf("LOW_FUEL")),
        manualMeaning = ManualGN7.meaning(12),
        manualActions = ManualGN7.actions(12),
        manualSource = ManualGN7.sourceOf(12),
        subject = "연료", predicate = "부족합니다", action = "즉시 연료를 보충하십시오",
        keywords = listOf("연료", "부족", "보충하십시오"),
        pairNote = "짝 있음",
    )

    val MDPS = WarningLamp(
        id = "MDPS", manualNo = 9, nameKo = "전동 파워 스티어링 경고등", tier = Tier.REST_AREA,
        signal = LampSignal.Telltale(listOf("MDPS")),
        manualMeaning = ManualGN7.meaning(3),
        manualActions = ManualGN7.actions(3),
        manualSource = ManualGN7.sourceOf(3),
        subject = "전동 파워 스티어링 경고등", predicate = "켜졌습니다", action = "안전에 유의하며 블루핸즈에서 차량을 점검하십시오",
        keywords = listOf("전동 파워 스티어링", "안전", "블루핸즈", "점검하십시오"),
        pairNote = "짝 있음",
    )

    val LPG_OVERCHARGE = WarningLamp(
        // 2026. 11. 15. 원문 대조로 더함. 설명서 (5) LPG 과충전 경고등은 빨간색이지만 정차 지시 없이 점검만 지시한다.
        // 감시 상수 LPI 는 95종에 있는데 분류표 짝이 없어 음성 안내가 나가지 않았다. 충전 경고등과 같은 휴게소까지(REST_AREA)로 두고
        // 화면 판정 라벨은 가까운 안전한 곳에서 점검으로 맞춘다.
        id = "LPG_OVERCHARGE", manualNo = 23, nameKo = "LPG 과충전 경고등", tier = Tier.REST_AREA,
        signal = LampSignal.Telltale(listOf("LPI")),
        manualMeaning = ManualGN7.meaning(5),
        manualActions = ManualGN7.actions(5),
        manualSource = ManualGN7.sourceOf(5),
        subject = "LPG 과충전 경고등", predicate = "켜졌습니다", action = "블루핸즈에서 차량을 점검하십시오",
        keywords = listOf("LPG", "과충전", "블루핸즈", "점검하십시오"),
        pairNote = "짝 있음(Pleos 상수 LPI)",
    )

    val ENGINE = afterArrival(
        id = "ENGINE", no = 2, name = "엔진 경고등", types = listOf("ENGINE"),
        meaning = ManualGN7.meaning(17),
        actions = ManualGN7.actions(17),
        subject = "엔진 경고등", keyword = "엔진",
        source = ManualGN7.sourceOf(17),
    )

    val TPMS_FAULT = WarningLamp(
        id = "TPMS_FAULT", manualNo = 7, nameKo = "TPMS 이상 경고등", tier = Tier.AFTER_ARRIVAL,
        signal = LampSignal.Telltale(listOf("TPMS"), states = setOf(LampState.BLINK)),
        manualMeaning = "타이어 공기압 경보 시스템에 이상이 있으면 클러스터 표시창에 TPMS 경고등이 깜빡인 후 켜집니다.",
        manualActions = listOf("즉시 당사 직영 하이테크센터나 블루핸즈에서 점검을 받으십시오."),
        manualSource = GN7,
        subject = "TPMS 경고등", predicate = "깜빡인 후 켜졌습니다", action = ARRIVAL_ACTION,
        keywords = listOf("TPMS", "경고등", "도착", "알려 드리겠습니다"),
        pairNote = "같은 상수의 깜빡임 값으로 구분될 가능성. 문서에 설명 없음",
    )

    val AIRBAG = afterArrival(
        id = "AIRBAG", no = 8, name = "에어백 경고등", types = listOf("AIRBAG"),
        meaning = ManualGN7.meaning(2),
        actions = ManualGN7.actions(2),
        subject = "에어백 경고등", keyword = "에어백",
        source = ManualGN7.sourceOf(2),
    )

    val ELECTRONIC_PARKING_BRAKE = afterArrival(
        id = "ELECTRONIC_PARKING_BRAKE", no = 12, name = "전자식 파킹 브레이크 경고등", types = listOf("ELECTRONIC_PARKING_BRAKE"),
        meaning = ManualGN7.meaning(14),
        actions = ManualGN7.actions(14),
        subject = "전자식 주차 브레이크 경고등", keyword = "주차 브레이크",
        source = ManualGN7.sourceOf(14),
    )

    val ESC = afterArrival(
        id = "ESC", no = 17, name = "ESC 작동 표시등", types = listOf("ESC"),
        // 깜빡임은 ESC 가 작동하는 정상 상태라 켜짐(ON)만 이상으로 본다.
        states = setOf(LampState.ON),
        meaning = ManualGN7.meaning(20),
        actions = ManualGN7.actions(20),
        subject = "ESC 경고등", keyword = "ESC",
        source = ManualGN7.sourceOf(20),
    )

    val LED_HEADLAMP = afterArrival(
        id = "LED_HEADLAMP", no = 16, name = "LED 전조등 고장 경고등", types = listOf("LED_HEADLAMP"),
        meaning = ManualGN7.meaning(19),
        actions = ManualGN7.actions(19),
        subject = "LED 전조등 고장 경고등", keyword = "LED 전조등",
        source = ManualGN7.sourceOf(19),
    )

    val AUTO_HOLD = afterArrival(
        id = "AUTO_HOLD", no = 18, name = "자동 정차(AUTO HOLD) 이상", types = listOf("AUTO_HOLD"),
        // 흰색은 준비, 초록색은 작동, 노란색만 이상(V2 1-2 원문).
        colors = setOf(LampColor.YELLOW),
        meaning = ManualGN7.meaning(16),
        actions = ManualGN7.actions(16),
        subject = "자동 정차 기능 표시등", keyword = "자동 정차", lampWord = "표시등",
        source = ManualGN7.sourceOf(16),
    )

    val DRIVER_ASSIST = afterArrival(
        id = "DRIVER_ASSIST", no = 19, name = "전방 안전, 조향 안전, 차로 안전 경고등", types = listOf("FCA", "LKA", "LFA", "ELK"),
        // 빨간색 점멸은 기능 작동, 주황색(노란색 값)이 이상.
        colors = setOf(LampColor.YELLOW),
        meaning = ManualGN7.meaning(22),
        actions = ManualGN7.actions(22),
        subject = "운전자 보조 경고등", keyword = "운전자 보조",
        source = ManualGN7.sourceOf(22),
    )

    val MASTER_WARNING = afterArrival(
        id = "MASTER_WARNING", no = 20, name = "통합 경고등", types = listOf("MASTER_WARNING"),
        meaning = ManualGN7.meaning(15),
        actions = ManualGN7.actions(15),
        subject = "통합 경고등", keyword = "통합", source = ManualGN7.sourceOf(15),
    )

    val PREHEAT = afterArrival(
        id = "PREHEAT", no = 21, name = "예열 표시등(디젤)", types = listOf("PREHEAT"),
        // 깜빡이면 예열 시스템 이상(V2 원문). 켜짐은 정상 예열.
        states = setOf(LampState.BLINK),
        meaning = "예열 표시등이 깜빡이면 예열 시스템에 이상이 있는 것",
        actions = listOf("즉시 당사 직영 하이테크센터나 블루핸즈에서 점검을 받으십시오. 방치하면 화재가 날 수 있습니다."),
        subject = "예열 경고등", keyword = "예열", source = "현대자동차, 투싼 NX4 2026 웹 사용설명서",
    )

    val IMMOBILIZER = afterArrival(
        id = "IMMOBILIZER", no = 22, name = "이모빌라이저 경고등", types = listOf("IMMOBILIZER"),
        meaning = "이모빌라이저 시스템의 고장이 예상되므로",
        actions = listOf("즉시 당사 직영 하이테크센터나 블루핸즈에서 차량을 점검 및 정비하십시오."),
        subject = "이모빌라이저 경고등", keyword = "이모빌라이저", source = LX3,
    )

    val FOUR_WHEEL_DRIVE = WarningLamp(
        id = "FOUR_WHEEL_DRIVE", manualNo = 15, nameKo = "4WD 경고등", tier = Tier.AFTER_ARRIVAL,
        signal = LampSignal.None,
        manualMeaning = ManualGN7.meaning(11),
        manualActions = ManualGN7.actions(11),
        manualSource = ManualGN7.sourceOf(11),
        subject = "4륜구동 경고등", predicate = "켜졌습니다", action = ARRIVAL_ACTION,
        keywords = listOf("4륜구동", "경고등", "도착", "알려 드리겠습니다"),
        pairNote = "모드 표시만 있음. 4WD 고장 경고 상수는 없음",
    )

    /** 정차 지시가 없는 경고의 운전 중 문장 끝(X1 4-3 예문). */
    const val ARRIVAL_ACTION = "도착하면 점검 방법을 알려 드리겠습니다"

    private fun afterArrival(
        id: String, no: Int, name: String, types: List<String>,
        meaning: String, actions: List<String>, subject: String, keyword: String,
        states: Set<LampState> = setOf(LampState.ON, LampState.BLINK),
        colors: Set<LampColor>? = null,
        source: String = GN7,
        lampWord: String = "경고등",
    ) = WarningLamp(
        id = id, manualNo = no, nameKo = name, tier = Tier.AFTER_ARRIVAL,
        signal = LampSignal.Telltale(types, states, colors),
        manualMeaning = meaning, manualActions = actions, manualSource = source,
        subject = subject, predicate = "켜졌습니다", action = ARRIVAL_ACTION,
        keywords = listOf(keyword, lampWord, "도착", "알려 드리겠습니다"),
        pairNote = "짝 있음",
    )

    /** 분류표 전체. 순서는 갈래 순, 같은 갈래 안에서는 매뉴얼 번호 순. */
    val all: List<WarningLamp> = listOf(
        ENGINE_OIL_PRESSURE, ENGINE_TEMPERATURE, BRAKE, ABS,
        CHARGING, LOW_TIRE_PRESSURE, MDPS, EBD, LOW_FUEL, LPG_OVERCHARGE,
        ENGINE, TPMS_FAULT, AIRBAG, ELECTRONIC_PARKING_BRAKE, FOUR_WHEEL_DRIVE, LED_HEADLAMP, ESC,
        AUTO_HOLD, DRIVER_ASSIST, MASTER_WARNING, PREHEAT, IMMOBILIZER,
    )

    fun byId(id: String): WarningLamp? = all.firstOrNull { it.id == id }

    fun ofTier(tier: Tier): List<WarningLamp> = all.filter { it.tier == tier }

    /** Pleos TelltaleType 이름으로 찾기. 한 상수에 둘 이상(TPMS)이면 모두 돌려준다. */
    fun forTelltaleType(type: String): List<WarningLamp> =
        all.filter { val s = it.signal; s is LampSignal.Telltale && type in s.types }

    /** 경고등 짝이 있는 Pleos TelltaleType 이름 전체. */
    val mappedTelltaleTypes: Set<String> =
        all.mapNotNull { it.signal as? LampSignal.Telltale }.flatMap { it.types }.toSet()

    /** 갈래 우선순위. 숫자가 작을수록 급하다. */
    fun urgency(tier: Tier): Int = when (tier) {
        Tier.STOP_NOW -> 0
        Tier.REST_AREA -> 1
        Tier.AFTER_ARRIVAL -> 2
    }
}
