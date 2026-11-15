package com.telltalevoice.core

/**
 * 경고등 정보 화면의 색 묶음. 화면 탭 세 개와 같다. 설명서에 적힌 실제 계기판 색으로 묶는다.
 * RED 는 설명서 빨간색, YELLOW 는 설명서 주황색, GREEN_BLUE 는 설명서 초록색과 파란색.
 */
enum class ColorGroup { RED, YELLOW, GREEN_BLUE }

/**
 * 경고등 정보 한 줄. Pleos TelltaleType 상수 한 개에 한 줄.
 * 이름과 색, 문장은 모두 현대자동차 2027 그랜저(GN7) 웹 취급설명서 "경고등 및 표시등 확인하기"(Pleos Connect 차종) 원문이다.
 * - nameSource: 이름을 가져온 설명서 쪽 주소
 * - manualNo: 그 쪽의 항목 번호 (1)부터 (37)
 * - colorGroup: 설명서 표의 아이콘 색. 표에 색이 없으면 본문에 적힌 초록색이나 파란색
 * - colors: 표와 본문에 적힌 색 모두(상태에 따라 색이 바뀌는 등)
 * - tier: 엔진이 이 상수로 판정하는 갈래(분류표 LampCatalog 짝이 있을 때만)
 * - manualMeaning: ManualGN7.meaning(항목), actions: ManualGN7.actions(항목). 정차 후 화면(LampCatalog)과 같은 함수라 두 곳 문장이 늘 같다
 * - manualLines: 경고등 쪽 항목 문장 전부
 */
data class CatalogEntry(
    val id: String,
    val nameKo: String,
    val nameSource: String,
    val manualNo: Int,
    val colorGroup: ColorGroup,
    val colors: List<ManualColor>,
    val tier: Tier?,
    val manualMeaning: String?,
    val actions: List<String>,
    val manualLines: List<String>,
    val manualSource: String,
    val iconKind: String,
    /** 짝지은 분류표 경고등 id(LampCatalog). 없으면 null */
    val lampId: String?,
)

/**
 * 경고등 정보. 감시는 Pleos 상수 95종 그대로 하고(TelltaleResolver), 목록에는 설명서에서 이름과 색을 찾은 상수만 싣는다.
 * 설명서에서 찾지 못한 상수는 hiddenIds 에 있다.
 */
object Catalog95 {
    /** 감시하는 상수 95종(SDK 순서) */
    val monitored: List<String> get() = PleosTelltaleTypes.ALL

    /** 상수와 설명서 항목 번호, 아이콘 종류. 설명서 이름과 뜻이 상수와 맞는 항목만 짝지었다. */
    private val pairs: Map<String, Pair<Int, String>> = linkedMapOf(
        "SEAT_BELT" to (1 to "SEAT_BELT"),
        "AIRBAG" to (2 to "AIRBAG"),
        "MDPS" to (3 to "STEERING"),
        "LOW_VOLT_BATTERY" to (4 to "BATTERY"),
        "LPI" to (5 to "FUEL"),
        "ENGINE_OIL_PRESSURE" to (6 to "ENGINE_OIL"),
        "ENGINE_TEMPERATURE" to (7 to "TEMPERATURE"),
        // 설명서: 브레이크 경고등은 전자식 주차 브레이크(EPB)를 체결 중인 경우에도 켜짐
        "PARKING_BRAKE" to (8 to "BRAKE"),
        "ABS" to (10 to "ABS"),
        "LOW_FUEL" to (12 to "FUEL"),
        "TPMS" to (13 to "TIRE"),
        "ELECTRONIC_PARKING_BRAKE" to (14 to "PARKING_BRAKE"),
        "MASTER_WARNING" to (15 to "GENERIC_WARNING"),
        "AUTO_HOLD" to (16 to "AUTO_HOLD"),
        "ENGINE" to (17 to "ENGINE"),
        "LED_HEADLAMP" to (19 to "LAMP"),
        "ESC" to (20 to "ESC"),
        "ESC_OFF" to (21 to "ESC"),
        "FCA" to (22 to "DRIVE_ASSIST"),
        "LKA" to (24 to "DRIVE_ASSIST"),
        "DRIVER_ATTENTION" to (27 to "DRIVE_ASSIST"),
        "DOOR_OPEN" to (28 to "DOOR"),
        "LFA" to (29 to "DRIVE_ASSIST"),
        "LCA_LEFT" to (30 to "DRIVE_ASSIST"),
        "LCA_RIGHT" to (30 to "DRIVE_ASSIST"),
        "AUTO_HIGH_BEAM" to (31 to "LAMP"),
        "HEADLAMP_HIGH" to (32 to "LAMP"),
        "POSITION_LAMP" to (33 to "LAMP"),
        "HEADLAMP_LOW" to (34 to "LAMP"),
        "TURN_SIGNAL_LEFT" to (35 to "TURN_SIGNAL"),
        "TURN_SIGNAL_RIGHT" to (35 to "TURN_SIGNAL"),
        // 설명서 (36) 도로 결빙 경고등은 색이 적혀 있지 않아 짝지어도 숨겨진다
        "ICY_ROAD" to (36 to "GENERIC_WARNING"),
    )

    fun groupOf(colors: List<ManualColor>): ColorGroup? = when (colors.firstOrNull()) {
        ManualColor.RED -> ColorGroup.RED
        ManualColor.ORANGE -> ColorGroup.YELLOW
        ManualColor.GREEN, ManualColor.BLUE -> ColorGroup.GREEN_BLUE
        else -> null
    }

    /** 표에 아이콘 색이 있으면 그 색, 없으면 본문의 초록색이나 파란색(흰색과 회색은 묶음에 쓰지 않음). */
    fun groupFor(s: ManualGN7.Section): ColorGroup? =
        groupOf(s.symbolColors) ?: groupOf(s.textColors.filter { it == ManualColor.GREEN || it == ManualColor.BLUE })

    private fun lampFor(type: String): WarningLamp? = when (type) {
        "TPMS" -> LampCatalog.LOW_TIRE_PRESSURE
        else -> LampCatalog.forTelltaleType(type).firstOrNull()
    }

    private fun entryOf(id: String, no: Int, icon: String, pairedLamp: WarningLamp?): CatalogEntry? {
        val s = ManualGN7.section(no)
        // 2026. 11. 14. 원문 대조: 설명서 항목에 해야 할 일 문장이 없는 상태 표시등(예: (29) 차로 유지 보조 표시등, 초록과 회색뿐)은
        // 고장 경고등이 아니므로 분류표 갈래(도착 후 점검)를 싣지 않는다.
        val lamp = pairedLamp?.takeIf { ManualGN7.actions(no).isNotEmpty() }
        val group = groupFor(s) ?: return null
        return CatalogEntry(
            id = id, nameKo = s.name, nameSource = ManualGN7.URL, manualNo = no,
            colorGroup = group, colors = (s.symbolColors + s.textColors).distinct(),
            tier = lamp?.tier,
            manualMeaning = ManualGN7.meaning(no),
            actions = ManualGN7.actions(no),
            manualLines = s.lines, manualSource = ManualGN7.sourceOf(no),
            iconKind = icon, lampId = lamp?.id,
        )
    }

    /** 짝지은 상수와 설명서 항목 번호(단위 시험용) */
    val pairedNo: Map<String, Int> get() = pairs.mapValues { it.value.first }

    /** 목록(SDK 순서). 설명서에서 이름과 색을 찾은 상수만. */
    val all: List<CatalogEntry> by lazy {
        monitored.mapNotNull { id -> pairs[id]?.let { (no, icon) -> entryOf(id, no, icon, lampFor(id)) } }
    }

    /** 목록에서 숨긴 상수(설명서에서 이름이나 색을 찾지 못함). 감시는 그대로 한다. */
    val hiddenIds: List<String> by lazy { monitored.filter { id -> all.none { it.id == id } } }

    fun byColor(group: ColorGroup): List<CatalogEntry> = all.filter { it.colorGroup == group }

    /** 색 묶음별 목록(빨강, 노랑, 초록과 파랑 순). */
    val grouped: Map<ColorGroup, List<CatalogEntry>> by lazy { ColorGroup.entries.associateWith(::byColor) }

    fun byId(id: String): CatalogEntry? = all.firstOrNull { it.id == id }

    /** Pleos 상수 짝이 없는 설명서 경고등: 브레이크 (8), EBD (9), 4륜구동 (11). 브레이크는 브레이크액 수준으로 켜짐을 본다. */
    val manualOnly: List<CatalogEntry> by lazy {
        listOf(
            Triple("BRAKE", 8, "BRAKE"), Triple("EBD", 9, "BRAKE"), Triple("FOUR_WHEEL_DRIVE", 11, "DRIVE_MODE"),
        ).mapNotNull { (id, no, icon) -> entryOf(id, no, icon, LampCatalog.byId(id)) }
    }
}
