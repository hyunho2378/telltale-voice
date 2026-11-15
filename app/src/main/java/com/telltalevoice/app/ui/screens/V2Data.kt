package com.telltalevoice.app.ui.screens

import com.telltalevoice.app.ui.pleos.Glyph
import com.telltalevoice.core.Catalog95
import com.telltalevoice.core.ColorGroup
import com.telltalevoice.core.DecisionBasis
import com.telltalevoice.core.DecisionRule
import com.telltalevoice.core.DecisionRules
import com.telltalevoice.core.LogRow
import com.telltalevoice.core.PreTripItem
import com.telltalevoice.core.PreTripKind
import com.telltalevoice.core.PreTripStatus
import com.telltalevoice.core.RefugeKind
import com.telltalevoice.core.RoadKind
import com.telltalevoice.core.Scenarios
import com.telltalevoice.core.Tier

/**
 * 화면이 쓰는 v2 데이터 모양. core 계약(docs/V2_CONTRACT.md)의 값을 이 파일에서만 화면 모양으로 바꾼다. 화면 코드는 이 모양만 본다.
 */

enum class RoadView { HIGHWAY, GENERAL, UNKNOWN }
enum class RefugeView { SHELTER, REST_AREA, REPAIR_SHOP, ROADSIDE }

/** 판정 근거(core DecisionBasis 와 같은 칸). */
data class BasisView(
    val tier: Tier,
    val speedKmh: Int?,
    val road: RoadView,
    val refuge: RefugeView?,
    val refugeName: String?,
    val refugeMeters: Int?,
    val sentence: String,
    val verdict: String = verdictLabel(tier),
) {
    fun refugeLabel() = when (refuge) {
        RefugeView.SHELTER -> "앞쪽 졸음쉼터"
        RefugeView.REST_AREA -> "앞쪽 휴게소"
        RefugeView.REPAIR_SHOP -> "서비스센터"
        RefugeView.ROADSIDE, null -> "정차 자리"
    }

    fun refugeValue(): String = refugeMeters?.let { distanceText(it) } ?: when (refuge) {
        RefugeView.ROADSIDE, null -> "도로 옆"
        else -> refugeName ?: "확인 중"
    }
}

fun distanceText(m: Int): String = if (m >= 1000) "${"%.1f".format(m / 1000.0).removeSuffix(".0")}km" else "${m}m"

/** 판정 갈래의 손님용 이름. */
fun verdictLabel(t: Tier): String = when (t) {
    Tier.STOP_NOW -> "즉시 정차"
    Tier.REST_AREA -> "휴게소까지 주행 가능"
    Tier.AFTER_ARRIVAL -> "도착 후 점검"
}

/** 미리 듣기 장면 한 개. situation 은 같은 경고등의 두 상황을 가르는 표시(시내, 고속도로). */
data class SceneView(val scenarioId: String, val lampId: String, val name: String, val situation: String?, val summary: String, val tier: Tier, val glyph: Glyph, val verdict: String = verdictLabel(tier))

/** 출발 전 점검 한 줄. */
enum class CheckStatus { OK, CHECK, UNKNOWN }
data class PreTripView(val name: String, val glyph: Glyph, val status: CheckStatus, val word: String, val kind: String = "", val lampId: String? = null, val meaning: String? = null, val actions: List<String> = emptyList())

/** 경고등 정보 한 줄. color 는 계기판 경고등 색 갈래. */
enum class LampColorGroup(val label: String) { RED("빨강"), YELLOW("노랑"), GREEN_BLUE("초록과 파랑") }
data class CatalogView(val id: String, val nameKo: String, val group: LampColorGroup, val tier: Tier?, val meaning: String?, val actions: List<String>, val glyph: Glyph, val lampId: String? = null, val verdict: String? = null)

/** 기록 한 줄. */
data class LogView(val lampId: String, val nameKo: String, val tier: Tier?, val timeLine: String, val stillOn: Boolean, val onAtMs: Long, val roadLabel: String? = null,
    val spokenText: String? = null, val onTimeText: String = "", val offTimeText: String? = null)

object V2Data {
    /** 미리 듣기 장면 이름표. core Scenarios.previews 의 id 와 같은 순서. */
    private val SCENES = listOf(
        SceneView("engine_oil", "ENGINE_OIL_PRESSURE", "엔진 오일 압력", null, "도로 옆 즉시 정차와 견인 요청", Tier.STOP_NOW, Glyph.Oil),
        SceneView("low_tire_city", "LOW_TIRE_PRESSURE", "저압 타이어", "시내", "서비스센터까지 서행", Tier.AFTER_ARRIVAL, Glyph.Tire),
        SceneView("low_tire_highway", "LOW_TIRE_PRESSURE", "저압 타이어", "고속도로", "속도를 낮추고 앞쪽 휴게소까지", Tier.REST_AREA, Glyph.Tire),
        SceneView("engine_arrival", "ENGINE", "엔진 점검", null, "도착 후 점검", Tier.AFTER_ARRIVAL, Glyph.Engine),
        SceneView("brake_highway", "BRAKE", "브레이크액", "고속도로", "갓길 대피 4단계와 견인 요청", Tier.STOP_NOW, Glyph.Brake),
    )

    /** 장면 다섯 개. 판정 갈래는 core 가 장면을 끝까지 돌려 얻은 판정(Scenarios.basisFor)을 쓴다. */
    val scenes: List<SceneView> by lazy {
        Scenarios.previews.mapNotNull { sc -> SCENES.firstOrNull { it.scenarioId == sc.id } }
            .map { v -> Scenarios.basisFor(v.scenarioId)?.let { v.copy(tier = it.tier, verdict = it.verdictLabel) } ?: v }
    }

    fun scene(id: String): SceneView? = scenes.firstOrNull { it.scenarioId == id }

    /** 장면의 판정 근거(core Scenarios.basisFor). */
    fun basisForScene(id: String): BasisView? = Scenarios.basisFor(id)?.let(::view)

    /** core 판정 근거를 화면 모양으로. 장소가 없는 바로 정차는 "정차 자리, 도로 옆". */
    fun view(b: DecisionBasis): BasisView = BasisView(
        tier = b.tier, speedKmh = b.speedKmh,
        road = when (b.road) { RoadKind.HIGHWAY -> RoadView.HIGHWAY; RoadKind.GENERAL -> RoadView.GENERAL; RoadKind.UNKNOWN -> RoadView.UNKNOWN },
        refuge = when (b.refuge?.kind) {
            RefugeKind.DROWSY_SHELTER -> RefugeView.SHELTER
            RefugeKind.REST_AREA -> RefugeView.REST_AREA
            RefugeKind.REPAIR_SHOP -> RefugeView.REPAIR_SHOP
            null -> if (b.tier == Tier.STOP_NOW) RefugeView.ROADSIDE else when (b.rule) {
                DecisionRule.TIRE_GENERAL, DecisionRule.AFTER_ARRIVAL_DEFAULT -> RefugeView.REPAIR_SHOP
                else -> RefugeView.REST_AREA
            }
        },
        refugeName = b.refugeName, refugeMeters = b.refugeMeters, sentence = b.sentence, verdict = b.verdictLabel,
    )

    /** 판정 근거가 아직 없을 때(엔진이 판정을 내기 전) 갈래만으로 만든 근거. 문장은 core DecisionRule 문장. */
    fun basisForTier(t: Tier): BasisView = when (t) {
        Tier.STOP_NOW -> BasisView(t, null, RoadView.UNKNOWN, RefugeView.ROADSIDE, null, null, DecisionRule.STOP_NOW_ROAD.sentence)
        Tier.REST_AREA -> BasisView(t, null, RoadView.UNKNOWN, RefugeView.REST_AREA, null, null, DecisionRule.REST_AREA_DEFAULT.sentence)
        Tier.AFTER_ARRIVAL -> BasisView(t, null, RoadView.UNKNOWN, RefugeView.REPAIR_SHOP, null, null, DecisionRule.AFTER_ARRIVAL_DEFAULT.sentence)
    }

    /** 출발 전 점검 다섯 줄(core PreTripItem). 신호가 없으면 "확인 중". */
    fun preTrip(items: List<PreTripItem>): List<PreTripView> = items.map { i ->
        PreTripView(
            i.kind.label,
            when (i.kind) { PreTripKind.TIRE -> Glyph.Tire; PreTripKind.ENGINE_OIL -> Glyph.Oil; PreTripKind.BRAKE_FLUID -> Glyph.Brake; PreTripKind.WASHER -> Glyph.Washer; PreTripKind.FUEL -> Glyph.Fuel },
            when (i.status) { PreTripStatus.OK -> CheckStatus.OK; PreTripStatus.CHECK -> CheckStatus.CHECK; PreTripStatus.UNKNOWN -> CheckStatus.UNKNOWN },
            when { i.valueText != null -> i.valueText!!; i.status == PreTripStatus.UNKNOWN -> if (i.signal == com.telltalevoice.core.PreTripSignal.NO_SIGNAL) "확인 불가" else "확인 중"; i.status == PreTripStatus.OK -> "정상"; else -> "점검 필요" },
            i.kind.name, i.lampId, i.kind.manualMeaning, i.kind.manualActions,
        )
    }

    /** 경고등 정보 95종(core Catalog95). */
    val catalog: List<CatalogView> by lazy {
        Catalog95.all.map { e ->
            val group = when (e.colorGroup) { ColorGroup.RED -> LampColorGroup.RED; ColorGroup.YELLOW -> LampColorGroup.YELLOW; ColorGroup.GREEN_BLUE -> LampColorGroup.GREEN_BLUE }
            CatalogView(e.id, e.nameKo, group, e.tier, e.manualMeaning, e.actions, glyphForIcon(e.iconKind, group), e.lampId,
                // 경고등 정보 라벨은 엔진 판정 라벨과 같은 출처(도로와 위치를 모를 때 엔진이 고르는 규칙의 라벨)
                e.lampId?.let { DecisionRules.defaultRule(it)?.label })
        }
    }

    /** 기록(core LogRow). */
    fun log(rows: List<LogRow>): List<LogView> = rows.map { LogView(it.lampId, it.nameKo, it.tier, it.timeText, it.offAtWallMs == null, it.onAtWallMs, it.roadLabel, it.spokenText, it.onTimeText, it.offTimeText) }

    /** 감시하는 계기판 경고등 수(Pleos Vehicle SDK TelltaleType 상수 수). */
    val watchedCount: Int get() = Catalog95.monitored.size
}

/** core 경고등 정보 iconKind 를 그림으로. */
fun glyphForIcon(kind: String, group: LampColorGroup): Glyph = when (kind) {
    "ENGINE_OIL" -> Glyph.Oil
    "TIRE" -> Glyph.Tire
    "ENGINE" -> Glyph.Engine
    "BRAKE", "ABS", "PARKING_BRAKE", "AUTO_HOLD" -> Glyph.Brake
    "FUEL" -> Glyph.Fuel
    else -> if (group == LampColorGroup.RED) Glyph.Alert else Glyph.Info
}
