package com.telltalevoice.core

/** 판정에 쓴 도로 종류. */
enum class RoadKind { HIGHWAY, GENERAL, UNKNOWN }

/** 대피나 점검 장소 종류. */
enum class RefugeKind { DROWSY_SHELTER, REST_AREA, REPAIR_SHOP }

/** 가까운 장소 한 곳. meters 는 현재 위치에서 직선 또는 경로 거리(미터). */
data class Refuge(val kind: RefugeKind, val name: String, val meters: Int)

/** 앱이 공공데이터로 찾은 가까운 장소. 엔진은 판정 규칙에 맞는 한 곳을 고른다. */
data class NearbyPlaces(
    /** 진행 방향 앞쪽 졸음쉼터 */
    val shelter: Refuge? = null,
    /** 진행 방향 앞쪽 휴게소(저압 타이어면 경정비 가능 휴게소 먼저) */
    val restArea: Refuge? = null,
    /** 가까운 서비스센터(이름에 블루핸즈가 들어간 곳 먼저) */
    val repairShop: Refuge? = null,
)

/**
 * 판정 규칙 이름. 화면과 시험이 같은 이름을 쓴다.
 * 모든 규칙은 설명서 행동 문장 범위 안에 있다(LampCatalog 의 manualActions).
 */
enum class DecisionRule(val label: String, val sentence: String) {
    /** 설명서가 주행 중 정차를 지시한 경고등, 고속도로 위 */
    STOP_NOW_HIGHWAY("즉시 정차", "고속도로 주행 중이고 설명서가 정차를 지시하는 경고등이라 즉시 정차와 대피를 안내합니다."),
    /** 설명서가 주행 중 정차를 지시한 경고등, 일반도로 또는 도로를 모름 */
    STOP_NOW_ROAD("즉시 정차", "설명서가 정차를 지시하는 경고등이라 도로 옆 정차를 안내합니다."),
    /** 저압 타이어, 고속도로: "속도를 낮추고 급격한 조향을 피하십시오" 와 앞쪽 휴게소 */
    TIRE_HIGHWAY("휴게소까지 서행", "고속도로 주행 중이라 속도를 낮춰 앞쪽 휴게소까지 가도록 안내합니다."),
    /** 저압 타이어, 일반도로: 같은 문장과 가까운 서비스센터(설명서 "블루핸즈에서 타이어 상태를 점검한 후") */
    TIRE_GENERAL("서비스센터까지 서행", "일반도로 주행 중이라 속도를 낮춰 가까운 서비스센터까지 가도록 안내합니다."),
    /** 충전 경고등: 2027 원문은 배터리 충전 상태 확인과 점검 지시뿐이라 가까운 안전한 곳에서 점검 */
    CHARGING_CHECK("안전한 곳에서 점검", "설명서가 배터리 충전 상태 확인과 점검을 지시하는 경고등이라 안전한 곳에서 점검을 안내합니다."),
    /** 속도를 낮추라는 경고등(도로를 모를 때의 저압 타이어 포함) */
    // 2026. 11. 14. 2027 원문 대조: 이 규칙을 쓰는 전동 파워 스티어링과 연료 부족, EBD 중 "속도를 낮추라"는 문장은 어느 쪽에도 없다(EBD 는 "고속 주행을 피하십시오").
    // 세 경고등 모두 정차 지시가 없다는 사실만 문장에 쓴다.
    REST_AREA_DEFAULT("휴게소까지 주행 가능", "설명서가 정차를 지시하지 않는 경고등이라 앞쪽 휴게소까지 주행을 안내합니다."),
    /** 빨간 경고등인데 설명서가 정차 지시 없이 점검만 적은 경고등(LPG 과충전) */
    RED_CHECK("안전한 곳에서 점검", "설명서가 정차 지시 없이 점검만 적은 빨간 경고등이라 안전한 곳에서 점검을 안내합니다."),
    /** 점검만 적은 경고등 */
    AFTER_ARRIVAL_DEFAULT("도착 후 점검", "설명서가 점검만 적은 경고등이라 도착 후 점검을 안내합니다."),
}

/**
 * 판정 근거. 엔진이 경고를 판정할 때 쓴 입력을 그대로 내보낸다.
 * speedKmh 는 반올림한 시속(모르면 null), refuge 는 규칙이 고른 장소(없으면 null).
 */
data class DecisionBasis(
    val lampId: String,
    val tier: Tier,
    val speedKmh: Int?,
    val road: RoadKind,
    val refuge: Refuge?,
    val rule: DecisionRule,
    /** 음성으로 말한 경고 문장(화면 글) */
    val spokenText: String,
    val atMs: Long,
) {
    val refugeName: String? get() = refuge?.name
    val refugeMeters: Int? get() = refuge?.meters
    /** 화면에 보일 한 문장 */
    val sentence: String get() = rule.sentence
    /** 화면 판정 카드의 판정 라벨("즉시 정차", "휴게소까지 서행", "서비스센터까지 서행" 등). 도로에 따라 바뀐다 */
    val verdictLabel: String get() = rule.label
}

/** 판정 결과: 근거와, 갈래와 음성 문장을 상황에 맞춘 경고등. */
data class LampDecision(val lamp: WarningLamp, val basis: DecisionBasis)

/**
 * 상황별 판정 규칙. 같은 경고등이 도로 종류에 따라 다른 갈래와 문장, 장소가 된다.
 * 저압 타이어(설명서 원문)
 * - "저압 타이어 경고등이 켜지면 속도를 낮추고 급격한 조향을 피하며 ... 타이어 공기압을 조절하십시오."(2027 TPMS 쪽)
 * - "당사 직영 하이테크센터나 블루핸즈에서 타이어 상태를 점검한 후 추천 공기압에 맞도록 공기압을 조절하십시오."
 * 갈래(긴급도)와 음성 문장은 도로와 상관없이 휴게소까지(REST_AREA)와 설명서 첫 문장 그대로다("즉시 속도를 낮추고"와 어긋나지 않게).
 * 달라지는 것은 안내 장소와 화면 판정 라벨, 한 문장이다. 고속도로는 "휴게소까지 서행"과 앞쪽 휴게소, 일반도로는 "서비스센터까지 서행"과 가까운 서비스센터.
 * 도로를 모르면 원래 규칙(휴게소까지 주행 가능)으로 둔다.
 */
object DecisionRules {
    fun roadKind(route: RouteContext): RoadKind = when {
        route.onHighway -> RoadKind.HIGHWAY
        route.turns.isNotEmpty() || route.roadLabel != null -> RoadKind.GENERAL
        else -> RoadKind.UNKNOWN
    }

    fun decide(
        lamp: WarningLamp,
        route: RouteContext,
        speedKmh: Double?,
        nearby: NearbyPlaces,
        nowMs: Long,
    ): LampDecision {
        val road = roadKind(route)
        val routeRest = route.nextRestArea?.let {
            Refuge(RefugeKind.REST_AREA, it.description.ifBlank { "다음 휴게소" }, it.distanceMeters.toInt())
        }
        val restArea = nearby.restArea ?: routeRest
        val (effective, rule, refuge) = when {
            lamp.tier == Tier.STOP_NOW && road == RoadKind.HIGHWAY ->
                Triple(lamp, DecisionRule.STOP_NOW_HIGHWAY, nearest(nearby.shelter, restArea))
            lamp.tier == Tier.STOP_NOW -> Triple(lamp, DecisionRule.STOP_NOW_ROAD, null)
            lamp.id == LampCatalog.LOW_TIRE_PRESSURE.id && road == RoadKind.GENERAL ->
                Triple(lamp, DecisionRule.TIRE_GENERAL, nearby.repairShop)
            lamp.id == LampCatalog.LOW_TIRE_PRESSURE.id && road == RoadKind.HIGHWAY ->
                Triple(lamp, DecisionRule.TIRE_HIGHWAY, restArea)
            lamp.id == LampCatalog.CHARGING.id -> Triple(lamp, DecisionRule.CHARGING_CHECK, nearest(nearby.shelter, restArea))
            lamp.id == LampCatalog.LPG_OVERCHARGE.id -> Triple(lamp, DecisionRule.RED_CHECK, nearest(nearby.shelter, restArea))
            lamp.tier == Tier.REST_AREA -> Triple(lamp, DecisionRule.REST_AREA_DEFAULT, restArea)
            else -> Triple(lamp, DecisionRule.AFTER_ARRIVAL_DEFAULT, nearby.repairShop)
        }
        val basis = DecisionBasis(
            lampId = lamp.id, tier = effective.tier,
            speedKmh = speedKmh?.let { Math.round(it).toInt() },
            road = road, refuge = refuge, rule = rule,
            spokenText = SpeechBuilder.warning(effective).text, atMs = nowMs,
        )
        return LampDecision(effective, basis)
    }

    /**
     * 경고등별 기본 판정 규칙. 도로와 위치를 모를 때 decide 가 고르는 규칙과 같다(경고등 정보 라벨용).
     * 분류표(LampCatalog)에 없는 id 면 null.
     */
    fun defaultRule(lampId: String): DecisionRule? {
        val lamp = LampCatalog.byId(lampId) ?: return null
        return decide(lamp, RouteContext(), null, NearbyPlaces(), 0).basis.rule
    }

    private fun nearest(a: Refuge?, b: Refuge?): Refuge? = listOfNotNull(a, b).minByOrNull { it.meters }
}
