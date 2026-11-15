package com.telltalevoice.app.ui.sys

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.telltalevoice.app.ui.pleos.generated.PleosTypeSpec
import com.telltalevoice.app.ui.pleos.keepAll
import com.telltalevoice.app.ui.pleos.toStyle
import com.telltalevoice.app.ui.screens.PreviewHistory
import com.telltalevoice.app.ui.screens.PreviewPlay
import com.telltalevoice.app.ui.screens.RefugeView
import com.telltalevoice.app.ui.screens.Shop
import com.telltalevoice.app.ui.screens.distanceText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.telltalevoice.app.EngineHub
import com.telltalevoice.app.ui.WindowShape
import com.telltalevoice.app.ui.pleos.Glyph
import com.telltalevoice.app.ui.pleos.Pleos
import com.telltalevoice.app.ui.pleos.glyphForLamp
import com.telltalevoice.app.ui.screens.BasisView
import com.telltalevoice.app.ui.screens.CatalogView
import com.telltalevoice.app.ui.screens.CheckStatus
import com.telltalevoice.app.ui.screens.LampColorGroup
import com.telltalevoice.app.ui.screens.LampTime
import com.telltalevoice.app.ui.screens.LogView
import com.telltalevoice.app.ui.screens.PreTripView
import com.telltalevoice.app.ui.screens.SceneView
import com.telltalevoice.app.ui.screens.Shops
import com.telltalevoice.app.ui.screens.V2Data
import com.telltalevoice.app.ui.screens.verdictLabel
import com.telltalevoice.core.ButtonAction
import com.telltalevoice.core.EvacuationMachine
import com.telltalevoice.core.EvacuationStep
import com.telltalevoice.core.LampCatalog
import com.telltalevoice.core.SpeechBuilder
import com.telltalevoice.core.Tier
import com.telltalevoice.core.UiState

// 시스템 룩 화면 7개. 화면에는 운전자가 할 일(경고등 이름과 아이콘, 지금 할 행동, 갈 곳의 이름과 거리, 버튼)만 둔다.
// 판정 이유와 출처, 규칙 이름, 속도 숫자는 넣지 않는다(2026. 11. 11. 사용자 지시).

/** 홈 쪽 메뉴: 홈, 경고등 정보, 경고 이력. */
enum class HomePage { Home, Catalog, Log, Settings, VoiceTest }

@Composable
private fun ColumnScope.AppMenu(page: HomePage, onPage: (HomePage) -> Unit) {
    SysMenuRow("홈", Glyph.Home, page == HomePage.Home, { onPage(HomePage.Home) })
    SysMenuRow("경고등 정보", Glyph.Book, page == HomePage.Catalog, { onPage(HomePage.Catalog) })
    SysMenuRow("경고 이력", Glyph.Clock, page == HomePage.Log, { onPage(HomePage.Log) })
    SysMenuRow("설정", Glyph.Settings, page == HomePage.Settings || page == HomePage.VoiceTest, { onPage(HomePage.Settings) })
}

/** 격자 한 줄: 위와 아래 1dp 선, 칸 사이 1dp 선, 칸 폭 같게. */
@Composable
private fun Grid(height: Dp, cells: List<@Composable (Modifier) -> Unit>, top: Boolean = true) {
    Column(Modifier.fillMaxWidth()) {
        if (top) SysDivider()
        Row(Modifier.fillMaxWidth().height(height)) {
            cells.forEachIndexed { i, c ->
                if (i > 0) SysVDivider()
                c(Modifier.weight(1f).fillMaxHeight())
            }
        }
        SysDivider()
    }
}

// ---------------- 홈 ----------------

/** 홈에 보일 켜진 경고 한 개. */
data class ActiveLamp(val lampId: String, val nameKo: String, val tier: Tier?)

/**
 * 홈: 차 상태 화면. 켜진 경고, 출발 전 점검 다섯 항목 상태, 가까운 서비스센터, 최근 기록. 설명 문장 없이 이름과 상태 값만 둔다.
 */
@Composable
fun SysHome(
    preTrip: List<PreTripView>, active: List<ActiveLamp>, latest: LogView?, clock: String, shops: List<Shop>, voiceOn: Boolean?,
    onPage: (HomePage) -> Unit, onDial: (String) -> Unit,
) {
    val s = sys()
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    val item = preTrip.firstOrNull { it.kind == picked }
    if (item != null) { SysPreTripDetail(item, clock, { picked = null }, onPage); return }
    val first = active.firstOrNull()
    SysScaffold(scroll = false, gap = s.gapM, menuScroll = false, menu = {
        AppMenu(HomePage.Home, onPage)
        if (first != null) MenuCarPanel(carZoneFor(first.lampId), lampColor(first.tier), first.nameKo, "켜짐")
        else MenuCarPanel(CarZone.NONE, s.ink, "경고등 없음", null)
    }) {
        SysPageTitle("차량 상태") { ClockTrailing(clock) }
        Column(Modifier.fillMaxWidth().weight(1f)) {
            SysDivider()
            val warnCells: List<Pair<Float, Cell>> = if (active.isEmpty())
                listOf(2f to cell { m -> SysTile("경고등", "없음", Glyph.Check, m) })
            else active.take(2).map { a -> 1f to cell { m -> SysTile(a.nameKo, "켜짐", glyphForLamp(a.lampId, a.tier), m, iconTint = lampColor(a.tier), onClick = { onPage(HomePage.Log) }, go = true, stateOn = true, active = true) } }
            GridRow(Modifier.weight(1f), warnCells + listOf(1f to cell { m ->
                SysTile("경고 음성 안내", when (voiceOn) { false -> "꺼짐"; else -> "켜짐" }, Glyph.Speaker, m, stateOn = voiceOn != false, active = voiceOn != false, onClick = { onPage(HomePage.Settings) }, go = true)
            }))
            SysDivider()
        }
        SysSection("출발 전 점검")
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            // 다섯 칸 이름을 같은 글자 단계로: 가장 긴 이름이 한 줄에 들어가는 단계를 고른다
            val m0 = rememberTextMeasurer()
            val cellPx = constraints.maxWidth / preTrip.size.coerceAtLeast(1) - with(LocalDensity.current) { (s.d(20) * 2).roundToPx() }
            val nameSpec = remember(preTrip.map { it.name }, cellPx) {
                listOf(s.body, s.small, s.desc).firstOrNull { sp -> preTrip.none { p -> m0.measure(AnnotatedString(keepAll(p.name)), sp.toStyle(null, true), maxLines = 1, constraints = Constraints(maxWidth = cellPx.coerceAtLeast(1))).hasVisualOverflow } } ?: s.desc
            }
            Column(Modifier.fillMaxSize()) {
                SysDivider()
                GridRow(Modifier.weight(1f), preTrip.map { p -> 1f to cell { m ->
                    SysTile(p.name, p.word, p.glyph, m, iconTint = if (p.status == CheckStatus.CHECK) Pleos.colors.regulationOrange else null,
                        stateOn = p.status == CheckStatus.OK, nameSpec = nameSpec, onClick = { picked = p.kind })
                } })
                SysDivider()
            }
        }
        Column(Modifier.fillMaxWidth().weight(1f)) {
            SysDivider()
            val shop = shops.firstOrNull()
            GridRow(Modifier.weight(1f), listOf(
                2f to cell { m -> if (shop != null) SysTile("서비스센터 찾기", "${shop.name} ${distanceText((shop.km * 1000).toInt())}", Glyph.Place, m,
                    onClick = shop.phone?.let { ph -> { onDial(ph) } }, go = shop.phone != null, stateOn = true)
                    else SysTile("서비스센터 찾기", "확인 중", Glyph.Place, m) },
                1f to cell { m -> SysTile("최근 이력", latest?.let { "${it.nameKo}, ${it.timeLine}" } ?: "없음", Glyph.Clock, m, onClick = { onPage(HomePage.Log) }, go = true) },
            ))
            SysDivider()
        }
    }
}

/** 제목 오른쪽 "마지막 확인" 시각. */
@Composable
private fun ClockTrailing(clock: String) {
    val s = sys()
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(s.d(4))) {
        SysText("마지막 확인", s.desc, s.sub, maxLines = 1)
        SysText(clock, s.section, maxLines = 1)
    }
}

/** 출발 전 점검 한 항목 상세: 상태 값과 설명서 원인, 조치 방법. */
@Composable
private fun SysPreTripDetail(p: PreTripView, clock: String, onBack: () -> Unit, onPage: (HomePage) -> Unit) {
    val s = sys()
    val lamp = p.lampId?.let { LampCatalog.byId(it) }
    val zone = when (p.kind) { "TIRE" -> CarZone.TIRES; "ENGINE_OIL" -> CarZone.ENGINE_BAY; "BRAKE_FLUID" -> CarZone.BRAKES; else -> CarZone.NONE }
    val tint = if (p.status == CheckStatus.CHECK) Pleos.colors.regulationOrange else s.ink
    SysScaffold(scroll = false, gap = s.gapM, menuScroll = false, menu = {
        SysMenuRow("출발 전 점검", Glyph.Back, false, onBack); SysDivider(); AppMenu(HomePage.Home, onPage); StatusCarPanel()
    }) {
        SysPageTitle(p.name) { ClockTrailing(clock) }
        // 설명서 원인이 길면(엔진 오일 등) 큰 타일에서 잘리므로 오른쪽 목록 첫 줄 "원인"으로 옮긴다
        val mean = p.meaning ?: lamp?.manualMeaning
        val longMean = (mean?.length ?: 0) > 70
        Row(Modifier.fillMaxWidth().weight(1f)) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                SysDivider()
                HeroTile(p.glyph, tint, zone, null, p.word, listOfNotNull(mean.takeIf { !longMean }), Modifier.fillMaxWidth().weight(1.4f), listOf(s.hero, s.title, s.lead))
                SysDivider()
                val on = lamp != null && lamp.id in LocalDriver.current.activeIds
                GridRow(Modifier.weight(1f), listOf(
                    1f to cell { m -> if (lamp != null) SysTile(lamp.nameKo, if (on) "켜짐" else "꺼짐", glyphForLamp(lamp.id, lamp.tier), m, iconTint = lampColor(lamp.tier), stateOn = on, active = on) else RecordTile(m) },
                    1f to cell { m -> ShopTile(m) },
                ))
                SysDivider()
            }
            val acts = p.actions.ifEmpty { lamp?.manualActions.orEmpty() }
            if (acts.isNotEmpty()) {
                SysVDivider()
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    SysDivider()
                    FitActions(acts, Modifier.fillMaxSize().padding(start = s.gapM, top = s.gapM), meaning = mean.takeIf { longMean },
                        extras = if (lamp != null) listOf(1f to cell { m -> RecordTile(m, lamp.id) }) else emptyList())
                }
            }
        }
    }
}

// ---------------- 설정 ----------------

/** 안내를 일시 정지한 경고등 한 줄(시동을 끄면 풀림). */
data class MutedLamp(val lampId: String, val nameKo: String)

/**
 * 설정: 경고 음성 안내 켜기와 끄기(토글), 일시 정지한 안내 해제, 다시 듣기, 음성 미리 듣기.
 * voiceOn 과 muted 는 core 값이 연결되기 전에는 null 이라 그 줄을 숨긴다.
 */
@Composable
fun SysSettings(
    voiceOn: Boolean?, onVoice: ((Boolean) -> Unit)?, muted: List<MutedLamp>?, onUnmute: ((String?) -> Unit)?,
    onAction: (ButtonAction) -> Unit, onPage: (HomePage) -> Unit,
) {
    val s = sys()
    SysScaffold(scroll = false, gap = s.gapM, menuScroll = false, menu = { AppMenu(HomePage.Settings, onPage); StatusCarPanel() }) {
        SysPageTitle("설정")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(s.gapSection)) {
        if (voiceOn != null && onVoice != null) Column(Modifier.weight(1f)) {
            SysSection("음성")
            SysToggleRow("경고 음성 안내", if (voiceOn) "켜짐" else "꺼짐", voiceOn, onVoice)
        }
        if (muted != null && onUnmute != null) Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(s.gapS)) {
            SysSection("일시 정지한 안내")
            if (muted.isEmpty()) SysText("없음", s.body, s.sub, maxLines = 1)
            else SysSegment(muted.take(3).map { SysSeg("${it.nameKo} 해제", { onUnmute(it.lampId) }) } +
                (if (muted.size > 1) listOf(SysSeg("모두 해제", { onUnmute(null) }, selected = true)) else emptyList()))
        }
        }
        // 마지막으로 들은 시각(없으면 상태 줄 비움)
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val lastPlay = remember { com.telltalevoice.app.ui.screens.PreviewHistory.all(ctx).maxByOrNull { it.atWallMs } }
        Column(Modifier.fillMaxWidth().weight(1f)) {
            SysDivider()
            GridRow(Modifier.weight(1f), listOf(
                1f to cell { m -> RepeatTile(m, onAction) },
                1f to cell { m -> SysTile("음성 미리 듣기", lastPlay?.let { "${LampTime.wallClock(it.atWallMs)} 재생" }, Glyph.Play, m, onClick = { onPage(HomePage.VoiceTest) }, go = true) },
                1f to cell { m -> SysTile("경고등 정보", null, Glyph.Book, m, onClick = { onPage(HomePage.Catalog) }, go = true) },
            ))
            SysDivider()
            val d = LocalDriver.current
            GridRow(Modifier.weight(1f), listOf(
                1f to cell { m -> RecordTile(m) },
                1f to cell { m -> ShopTile(m) },
                1f to cell { m -> SysTile("마지막 확인", d.clock, Glyph.Clock, m) },
            ))
            SysDivider()
        }
    }
}

/** 음성 미리 듣기: 경고 안내 장면을 골라 들어 본다(누르면 미리 듣기 화면). */
@Composable
fun SysVoiceTest(scenes: List<SceneView>, plays: List<PreviewPlay>, onPlay: (SceneView) -> Unit, onPage: (HomePage) -> Unit) {
    val s = sys()
    SysScaffold(scroll = false, gap = s.gapM, menuScroll = false, menu = {
        SysMenuRow("설정", Glyph.Back, false, { onPage(HomePage.Settings) }); SysDivider(); AppMenu(HomePage.VoiceTest, onPage); StatusCarPanel()
    }) {
        SysPageTitle("음성 미리 듣기")
        val cells: List<Cell> = scenes.map { sc -> cell { m ->
            val last = plays.firstOrNull { it.scenarioId == sc.scenarioId }
            SysTile(listOfNotNull(sc.name, sc.situation).joinToString(", "), last?.let { "${LampTime.wallClock(it.atWallMs)} 재생" } ?: sc.verdict, sc.glyph, m,
                onClick = { onPlay(sc) }, iconTint = lampColor(sc.tier), stateOn = last != null, figure = carZoneFor(sc.lampId))
        } } + cell { m -> SysTile("마지막 재생", plays.maxByOrNull { it.atWallMs }?.let { LampTime.wallClock(it.atWallMs) } ?: "없음", Glyph.Clock, m) }
        Column(Modifier.fillMaxWidth().weight(1f)) {
            SysDivider()
            cells.chunked(3).forEach { row -> GridRow(Modifier.weight(1f), row.map { 1f to it }); SysDivider() }
        }
    }
}

// ---------------- 공통 부품 ----------------

/** 화면 어디서나 채움 타일에 쓰는 운전자 정보(가까운 서비스센터, 기록, 시각, 켜진 경고). AppRoot 가 넣는다. */
data class DriverCtx(
    val shops: List<Shop> = emptyList(), val logs: List<LogView> = emptyList(), val clock: String = "",
    val activeIds: Set<String> = emptySet(), val onDial: (String) -> Unit = {}, val onPage: (HomePage) -> Unit = {},
)
val LocalDriver = androidx.compose.runtime.staticCompositionLocalOf { DriverCtx() }

/** 메뉴 아래 차 그림 판: 켜진 경고가 있으면 그 부위, 없으면 "경고등 없음". */
@Composable
fun ColumnScope.StatusCarPanel() {
    val s = sys()
    val lamp = LocalDriver.current.activeIds.firstNotNullOfOrNull { LampCatalog.byId(it) }
    if (lamp != null) MenuCarPanel(carZoneFor(lamp.id), lampColor(lamp.tier), lamp.nameKo, "켜짐")
    else MenuCarPanel(CarZone.NONE, s.ink, "경고등 없음", null)
}

/** 가까운 서비스센터 타일(전화 있으면 누르면 전화). */
@Composable
private fun ShopTile(m: Modifier) {
    val d = LocalDriver.current
    val shop = d.shops.firstOrNull()
    if (shop != null) SysTile("서비스센터 찾기", "${shop.name} ${distanceText((shop.km * 1000).toInt())}", Glyph.Place, m,
        onClick = shop.phone?.let { ph -> { d.onDial(ph) } }, go = shop.phone != null, stateOn = true)
    else SysTile("서비스센터 찾기", "확인 중", Glyph.Place, m)
}

/** 최근 기록 타일. lampId 를 주면 그 경고등의 최근 기록. */
@Composable
private fun RecordTile(m: Modifier, lampId: String? = null) {
    val d = LocalDriver.current
    val r = d.logs.firstOrNull { lampId == null || it.lampId == lampId }
    SysTile(if (lampId == null) "최근 이력" else "최근 켜짐", r?.let { if (lampId == null) "${it.nameKo}, ${it.timeLine}" else it.timeLine } ?: "없음",
        Glyph.Clock, m, onClick = { d.onPage(HomePage.Log) }, go = true)
}


/**
 * 메뉴 아래 차 그림 판(빠른 설정 화면 왼쪽 차량 그림 자리). 메뉴 줄 아래 남는 높이를 채운다.
 * 켜진 경고등이 있으면 그 부위를 경고 색으로 칠한다.
 */
@Composable
fun ColumnScope.MenuCarPanel(zone: CarZone, warn: Color, caption: String, sub: String?) {
    val s = sys()
    SysDivider()
    Column(Modifier.fillMaxWidth().weight(1f).padding(horizontal = s.iconLeft, vertical = s.gapM), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(s.gapS)) {
        CarFigure(zone, warn, Modifier.fillMaxWidth().weight(1f))
        SysText(caption, s.body, maxLines = 1, align = TextAlign.Center)
        if (sub != null) SysText(sub, s.desc, s.sub, maxLines = 1, align = TextAlign.Center)
    }
}

/** 메뉴 맨 아래 상태 줄 두 개: 음성 안내 상태와 마지막 확인 시각. 위쪽 빈 높이는 띄운다. */
@Composable
fun ColumnScope.MenuFooter(speaking: Boolean, running: Boolean) {
    val s = sys()
    val clock = remember { LampTime.nowClock() }
    Spacer(Modifier.weight(1f))
    SysDivider()
    SysMenuRow("경고 음성 안내", Glyph.Speaker, false, null, detail = when { speaking -> "안내 중"; running -> "대기"; else -> "켜짐" }, iconTint = if (speaking) s.on else null)
    SysMenuRow("마지막 확인", Glyph.Clock, false, null, detail = clock)
}

/** 칸 폭에 맞는 가장 큰 글자 단계를 골라 maxLines 안에 그린다(줄 수를 실제로 재서 고름). */
@Composable
fun FitText(text: String, specs: List<PleosTypeSpec>, maxLines: Int, modifier: Modifier = Modifier, color: Color = sys().ink, bold: Boolean = false) {
    BoxWithConstraints(modifier) {
        val m = rememberTextMeasurer()
        val px = constraints.maxWidth
        // 정한 줄 수 안에 들어가는 가장 큰 단계, 없으면 한 줄 더 허용(잘림 대신 줄바꿈)
        val (spec, lines) = remember(text, px, constraints.maxHeight, specs, maxLines) {
            fun fits(sp: PleosTypeSpec, n: Int): Boolean {
                val r = m.measure(AnnotatedString(keepAll(text)), sp.toStyle(if (bold) 700 else null, true), constraints = Constraints(maxWidth = px.coerceAtLeast(1)))
                return r.lineCount <= n && !r.hasVisualOverflow && (!constraints.hasBoundedHeight || r.size.height <= constraints.maxHeight)
            }
            specs.firstOrNull { fits(it, maxLines) }?.let { it to maxLines }
                ?: specs.firstOrNull { fits(it, maxLines + 1) }?.let { it to maxLines + 1 }
                ?: (specs.last() to maxLines + 1)
        }
        SysText(text, spec, color, maxLines = lines, bold = bold)
    }
}

/** 갈 곳 한 칸(이름과 상태, 전화). */
data class PlaceView(val name: String, val state: String, val phone: String?)

/**
 * 갈 곳 고르기. 정차 뒤(parked)는 가까운 서비스센터가 먼저, 주행 중은 판정이 고른 곳(휴게소, 졸음쉼터, 서비스센터, 도로 옆) 먼저.
 * 판정 장소 이름이 "휴게소"처럼 종류 이름뿐이면 "앞쪽 휴게소"로 쓰고 거리를 상태에 쓴다.
 */
fun placeFor(b: BasisView?, shops: List<Shop>, parked: Boolean): PlaceView {
    val shop = shops.firstOrNull()
    val shopView = shop?.let { PlaceView("서비스센터 찾기", "${it.name} ${distanceText((it.km * 1000).toInt())}", it.phone) }
    if (parked && shopView != null) return shopView
    return when (b?.refuge) {
        RefugeView.REPAIR_SHOP, null -> shopView ?: when {
            b?.refugeName != null && b.refugeMeters != null -> PlaceView("서비스센터 찾기", "${b.refugeName} ${distanceText(b.refugeMeters)}", null)
            else -> PlaceView("서비스센터 찾기", "확인 중", null)
        }
        RefugeView.ROADSIDE -> PlaceView("정차 자리", "도로 옆 안전한 곳", null)
        RefugeView.REST_AREA, RefugeView.SHELTER -> {
            val generic = b.refugeName == null || b.refugeName == "휴게소" || b.refugeName == "졸음쉼터"
            val dist = b.refugeMeters?.let { distanceText(it) }
            if (generic) PlaceView(b.refugeLabel(), dist ?: "확인 중", null)
            else PlaceView(b.refugeName!!, listOfNotNull(if (b.refuge == RefugeView.SHELTER) "앞쪽 졸음쉼터" else "앞쪽 휴게소", dist).joinToString(" "), null)
        }
    }
}

/** 격자 칸 사이 선을 그리며 가로로 놓는다. 칸마다 weight. */
@Composable
private fun GridRow(modifier: Modifier, cells: List<Pair<Float, @Composable (Modifier) -> Unit>>) {
    Row(modifier.fillMaxWidth()) {
        cells.forEachIndexed { i, (w, c) ->
            if (i > 0) SysVDivider()
            c(Modifier.weight(w).fillMaxHeight())
        }
    }
}

/**
 * 큰 경고 타일. 현대차 경고 카드(미디어데이 경고 알림 카드)와 같은 순서: 경고 아이콘, 굵은 행동 글(headline), 그 아래 회색 사실 줄(under).
 * over 는 행동 글 위 작은 회색 머리말(없어도 됨). 오른쪽에는 차 그림으로 경고 위치를 보인다.
 */
@Composable
private fun HeroTile(
    glyph: Glyph, tint: Color, zone: CarZone, over: String?, headline: String, under: List<String>, modifier: Modifier,
    headSpecs: List<PleosTypeSpec>, headLines: Int = 1, showCar: Boolean = true,
    /** 행동 글 오른쪽 아래 운전자용 표시 하나(켜진 시각 등). 아이콘과 짧은 값 */
    badge: Pair<Glyph, String>? = null,
) {
    val s = sys()
    // 빠른 설정 가운데 차량 그림처럼 차 그림을 타일 가운데에 크게 둔다. 위 왼쪽 경고 아이콘, 아래 행동 글과 사실 줄.
    Column(modifier.padding(s.tilePad), verticalArrangement = Arrangement.spacedBy(s.gapS)) {
        SysIcon(glyph, s.iconHero, tint)
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            if (showCar) {
                // 남는 칸이 넓으면 차를 가로로 눕혀 폭을 채운다
                val wide = maxWidth > maxHeight
                CarFigure(zone, tint, Modifier.fillMaxSize(), horizontal = wide)
            }
            // 차 그림 위 오른쪽 빈 띠에 운전자 표시 하나(켜진 시각, 대피 단계)
            if (badge != null) Row(Modifier.align(Alignment.TopEnd).widthIn(max = maxWidth * 0.6f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(s.d(12))) {
                SysIcon(badge.first, s.iconSize, s.ink)
                FitText(badge.second, listOf(s.body, s.desc), 2, Modifier, s.ink)
            }
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(s.gapM)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(s.d(6))) {
                if (over != null) SysText(over, s.desc, s.sub, maxLines = 1)
                FitText(headline, headSpecs, headLines, Modifier.fillMaxWidth(), bold = headSpecs.first() == s.hero)
                under.forEach { FitText(it, listOf(s.body, s.desc), 3, Modifier.fillMaxWidth(), s.sub) }
            }
        }
    }
}

/** 음성 경고 문장의 행동 부분(첫 문장 "~켜졌습니다." 다음). 정차 뒤 첫 줄 요약에 쓴다. */
fun actionSummary(lampId: String): String? = LampCatalog.byId(lampId)?.let { SpeechBuilder.warning(it).text }
    ?.split(". ")?.drop(1)?.joinToString(". ")?.trim()?.ifBlank { null }

/** 정차 뒤와 대피 화면 맨 위 줄: 왼쪽 닫기(있을 때), 오른쪽 회색 화면 이름. */
@Composable
private fun TopBar(title: String, onClose: (() -> Unit)?, closeLabel: String = "닫기") {
    val s = sys()
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (onClose != null) Row(Modifier.sysClick(onClose, s.bg).padding(end = s.gapM), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(s.d(12))) {
            SysIcon(Glyph.Back, s.iconBig, s.ink); SysText(closeLabel, s.section, maxLines = 1)
        }
        Spacer(Modifier.weight(1f))
        SysText(title, s.section, s.sub, maxLines = 1)
    }
}

@Composable
private fun PlaceTile(p: PlaceView, m: Modifier, onDial: ((String) -> Unit)? = null) {
    SysTile(p.name, p.state, Glyph.Place, m, onClick = if (p.phone != null && onDial != null) ({ onDial(p.phone) }) else null, go = p.phone != null && onDial != null, stateOn = true)
}

@Composable
private fun RepeatTile(m: Modifier, onAction: (ButtonAction) -> Unit) {
    val waiting by EngineHub.repeatWaiting.collectAsState()
    SysTile(ButtonAction.REPEAT.label, "마지막 안내", Glyph.Repeat, m, onClick = { onAction(ButtonAction.REPEAT) }, stateOn = waiting)
}

/** 경유지 질문 답 단추: 검은 면 "경유지 추가"와 "취소"를 같은 폭으로. */
@Composable
private fun WaypointAnswer(onAnswer: (Boolean) -> Unit) {
    SysSegment(listOf(
        SysSeg("경유지 추가", { onAnswer(true) }, selected = true, icon = Glyph.Route),
        SysSeg("취소", { onAnswer(false) }),
    ))
}

// ---------------- 미리 듣기 ----------------

/** 미리 듣기 단계: 0 경고 전, 1 주행 중 음성, 2 끝. */
@Composable
fun SysPreview(
    scene: SceneView, scenes: List<SceneView>, spoken: String?, step: Int, running: Boolean, speaking: Boolean,
    shops: List<Shop>, askWaypoint: Boolean,
    onClose: () -> Unit, onPick: (SceneView) -> Unit, onStop: () -> Unit, onWaypoint: (Boolean) -> Unit, onAction: (ButtonAction) -> Unit,
) {
    val s = sys()
    val basis = V2Data.basisForScene(scene.scenarioId)
    val place = placeFor(basis, shops, parked = false)
    SysScaffold(scroll = false, gap = s.gapM, menuScroll = false, menu = {
        SysMenuRow("닫기", Glyph.Back, false, onClose)
        SysDivider()
        scenes.forEach { sc ->
            SysMenuRow(listOfNotNull(sc.name, sc.situation).joinToString(", "), sc.glyph, sc.scenarioId == scene.scenarioId,
                { if (sc.scenarioId != scene.scenarioId) onPick(sc) }, detail = sc.verdict, iconTint = lampColor(sc.tier))
        }
        MenuFooter(speaking, running)
    }) {
        SysPageTitle(listOfNotNull(scene.name, scene.situation).joinToString(", "), "음성 미리 듣기") { VoiceState(speaking, running) }
        Column(Modifier.fillMaxWidth().weight(1f)) {
            SysDivider()
            GridRow(Modifier.weight(1.35f), listOf(
                2f to { m -> HeroTile(scene.glyph, lampColor(scene.tier), carZoneFor(scene.lampId), null, scene.verdict, listOfNotNull(LampCatalog.byId(scene.lampId)?.nameKo), m, listOf(s.hero, s.title, s.lead, s.section)) },
                1f to { m -> Column(m) {
                    PlaceTile(place, Modifier.fillMaxWidth().weight(1f))
                    SysDivider()
                    RepeatTile(Modifier.fillMaxWidth().weight(1f), onAction)
                } },
            ))
            SysDivider()
            GridRow(Modifier.weight(1f), listOf(
                2f to { m ->
                    Column(m.padding(s.tilePad), verticalArrangement = Arrangement.SpaceBetween) {
                        SysIcon(Glyph.Speaker, s.iconBig, if (speaking) s.on else s.ink)
                        Column(verticalArrangement = Arrangement.spacedBy(s.d(6))) {
                            SysText("재생 중", s.desc, s.sub, maxLines = 1)
                            FitText(spoken ?: when (step) { 0, 1 -> "대기"; else -> "재생 끝" },
                                listOf(s.lead, s.body, s.desc), 3, Modifier.fillMaxWidth())
                        }
                    }
                },
                1f to { m -> NextSteps(scene, step, m) },
            ))
            SysDivider()
        }
        if (askWaypoint) WaypointAnswer(onWaypoint)
        else SysSegment(listOf(
            if (running) SysSeg("정지", onStop, selected = true, icon = Glyph.Stop) else SysSeg("처음부터", { onPick(scene) }, selected = true, icon = Glyph.Play),
            SysSeg("다음 장면", { scenes.getOrNull((scenes.indexOfFirst { it.scenarioId == scene.scenarioId } + 1) % scenes.size)?.let(onPick) }),
        ))
    }
}

/** 이어서 나올 안내 타일: 주행 중 음성, 그다음 화면. 지난 단계는 확인 표시, 지금 단계는 파랑. */
@Composable
private fun NextSteps(scene: SceneView, step: Int, m: Modifier) {
    val s = sys()
    val names = listOf(
        "주행 중 음성 안내",
        when (scene.scenarioId) { "brake_highway" -> "고속도로 대피 안내"; "engine_arrival" -> "도착 후 점검"; else -> "정차 후 안내" },
    )
    Column(m.padding(s.tilePad), verticalArrangement = Arrangement.SpaceBetween) {
        SysText("다음 안내", s.body, maxLines = 1)
        Column(verticalArrangement = Arrangement.spacedBy(s.d(10))) {
            names.forEachIndexed { i, n ->
                val done = step > i + 1 || (step >= 2)
                val now = step == i + 1 || (step == 0 && i == 0)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(s.d(12))) {
                    if (done) SysIcon(Glyph.Check, s.iconSize, s.ink)
                    else SysText("${i + 1}", s.section, if (now) s.on else s.sub, maxLines = 1, modifier = Modifier.width(s.iconSize), align = TextAlign.Center)
                    SysText(n, s.desc, if (now && !done) s.on else if (done) s.ink else s.sub, maxLines = 2)
                }
            }
        }
    }
}

@Composable
fun VoiceState(speaking: Boolean, running: Boolean = true) {
    val s = sys()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(s.d(12))) {
        SysIcon(Glyph.Speaker, s.iconSize, if (speaking) s.on else s.sub)
        SysText(when { speaking -> "음성 안내 중"; running -> "대기"; else -> "재생 끝" }, s.body, if (speaking) s.on else s.sub, maxLines = 1)
    }
}

// ---------------- 정차 뒤 안내 ----------------

/**
 * 정차 뒤 안내(이옥근 교수 자문: 한 화면 세 가지 이내).
 * 첫 화면: 큰 경고 타일(아이콘, 지금 할 행동, 경고등 이름, 첫 줄 요약, 차 그림), 다음 행동 타일(바로 정차는 긴급견인 전화, 그 밖은 가까운 서비스센터와 전화),
 * "자세히 보기" 타일. 나머지(보험사, 다시 듣기, 켜진 시각, 원인, 조치 방법 전체)는 자세히 보기를 누르면 같은 화면 안 타일 격자로 펼치고 닫기로 돌아온다.
 */
@Composable
fun SysParked(s0: UiState.ParkedDetail, shopLines: List<String>, basisFor: (String) -> BasisView?, situation: String?, onClose: (() -> Unit)?, onDial: (String) -> Unit, onAction: (ButtonAction) -> Unit) {
    val s = sys()
    val shops = remember(shopLines) { Shops.parse(shopLines) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var open by rememberSaveable { mutableStateOf(false) }
    val item = s0.items.firstOrNull { it.lamp.id == selectedId } ?: s0.items.first()
    val lamp = item.lamp
    val basis = basisFor(lamp.id)
    val tier = basis?.tier ?: lamp.tier
    val times = remember(s0) { LampTime.lines(EngineHub.warningLogText()) }
    val place = placeFor(basis, shops, parked = true)
    val stop = tier == Tier.STOP_NOW
    val towTile: Cell = cell { m -> SysTile("견인 요청", "한국도로공사 ${EvacuationMachine.EX_CALL_CENTER}", Glyph.Tow, m, onClick = { onAction(ButtonAction.CALL_EX_TOW) }, go = true) }
    val placeTile: Cell = cell { m -> PlaceTile(place, m, onDial) }
    val title = if (s0.arrived) "도착 후 점검 안내" else "정차 후 안내"
    Column(Modifier.fillMaxSize().background(s.bg).padding(start = s.padX, end = s.padX, top = s.d(28), bottom = s.d(36)), verticalArrangement = Arrangement.spacedBy(s.gapM)) {
        if (open) TopBar("자세히", { open = false }) else TopBar(title, onClose)
        if (s0.items.size > 1) SysSegment(s0.items.map { SysSeg(it.lamp.nameKo, { selectedId = it.lamp.id; open = false }, selected = it.lamp.id == lamp.id) }, height = s.d(80))
        if (!open) Column(Modifier.fillMaxWidth().weight(1f)) {
            SysDivider()
            GridRow(Modifier.weight(1f), listOf(
                2f to { m -> HeroTile(glyphForLamp(lamp.id, lamp.tier), lampColor(tier), carZoneFor(lamp.id), null, basis?.verdict ?: verdictLabel(tier),
                    listOfNotNull(lamp.nameKo, actionSummary(lamp.id)), m, listOf(s.hero, s.title, s.lead, s.section),
                    badge = times[lamp.id]?.let { Glyph.Clock to it }) },
                1f to { m -> Column(m) {
                    (if (stop) towTile else placeTile)(Modifier.fillMaxWidth().weight(1f))
                    SysDivider()
                    SysTile("자세히 보기", "원인과 조치 방법", Glyph.Info, Modifier.fillMaxWidth().weight(1f), onClick = { open = true }, go = true)
                } },
            ))
            SysDivider()
        } else Row(Modifier.fillMaxWidth().weight(1f)) {
            Column(Modifier.weight(1.65f).fillMaxHeight()) {
                SysDivider()
                GridRow(Modifier.weight(1f), listOf(
                    1f to (if (stop) placeTile else towTile),
                    1f to { m -> SysTile(ButtonAction.CALL_INSURANCE.label, "가입한 보험사", Glyph.Phone, m, onClick = { onAction(ButtonAction.CALL_INSURANCE) }, go = true) },
                    1f to { m -> RepeatTile(m, onAction) },
                ))
                SysDivider()
                GridRow(Modifier.weight(1f), listOf(
                    1.4f to { m -> SysTile("켜진 시각", times[lamp.id] ?: if (item.stillOn) "켜짐" else "꺼짐", Glyph.Clock, m) },
                    1f to { m ->
                        val mutedIds by EngineHub.mutedLampIds.collectAsState()
                        val muted = lamp.id in mutedIds
                        SysTile("안내 일시 정지", if (muted) "정지" else "해제", Glyph.Speaker, m, stateOn = muted, active = muted,
                            onClick = { if (muted) EngineHub.unmute(lamp.id) else EngineHub.mute(lamp.id) })
                    },
                    1.6f to { m -> Column(m.padding(s.tilePad), verticalArrangement = Arrangement.SpaceBetween) {
                        SysIcon(Glyph.Info, s.iconBig, s.ink)
                        Column(verticalArrangement = Arrangement.spacedBy(s.d(4))) {
                            SysText("원인", s.desc, s.sub, maxLines = 1)
                            FitText(lamp.manualMeaning, listOf(s.body, s.desc), 3, Modifier.fillMaxWidth())
                        }
                    } },
                ))
                SysDivider()
            }
            SysVDivider()
            Column(Modifier.weight(1f).fillMaxHeight()) {
                SysDivider()
                FitActions(lamp.manualActions, Modifier.fillMaxSize().padding(start = s.gapM, top = s.gapM))
            }
        }
    }
}

/**
 * 조치 방법 목록. 주어진 높이에 다 들어가는 가장 큰 글자 단계를 재서 고르고, 그래도 넘치면 스크롤한다(차가 서 있는 화면).
 */
@Composable
private fun FitActions(actions: List<String>, modifier: Modifier, meaning: String? = null, extras: List<Pair<Float, Cell>> = emptyList()) {
    val s = sys()
    // 목록 줄: 원인(있으면)과 조치 방법. 번호 칸은 원인 줄에서 "원인" 글자.
    val items = listOfNotNull(meaning?.let { "원인" to it }) + actions.mapIndexed { i, a -> "${i + 1}" to a }
    BoxWithConstraints(modifier) {
        val m = rememberTextMeasurer()
        val density = LocalDensity.current
        val wPx = constraints.maxWidth
        val hPx = constraints.maxHeight
        val numW = with(density) { (s.iconSize + s.d(16) + s.gapM).roundToPx() }
        val headPx = with(density) { (s.section.lineHeightSp.toFloat().dp + s.gapS).roundToPx() }
        val padPx = with(density) { (s.d(16) * 2).roundToPx() + 1 }
        // 글자 단계는 시스템 룩 토큰 둘(항목 이름 28sp, 설명 22sp)만 쓴다(docs/SYSTEM_LOOK.md). 다 들어가는 큰 단계를 고름
        val specs = listOf(s.body, s.desc)
        val (spec, heights) = remember(items, wPx, hPx) {
            specs.map { sp ->
                sp to items.map { (_, a) ->
                    m.measure(AnnotatedString(keepAll(a)), sp.toStyle(null, true), constraints = Constraints(maxWidth = (wPx - numW).coerceAtLeast(1))).size.height + padPx
                }
            }.firstOrNull { (_, hs) -> headPx + hs.sum() + hs.size <= hPx } ?: (specs.last() to emptyList())
        }
        val fits = heights.isNotEmpty()
        val listPx = headPx + heights.sum() + heights.size
        // 목록이 칸의 60% 아래면 목록은 제 높이로 두고 남는 칸을 extras 타일(가까운 서비스센터, 최근 기록 등)로 채운다
        val useExtras = fits && extras.isNotEmpty() && listPx < hPx * 0.6f
        Column(Modifier.fillMaxSize().then(if (fits) Modifier else Modifier.verticalScroll(rememberScrollState())), verticalArrangement = Arrangement.spacedBy(s.gapS)) {
            SysSection("조치 방법")
            Column(if (fits && !useExtras) Modifier.weight(1f) else Modifier) {
                items.forEachIndexed { i, (n, a) ->
                    if (i > 0) SysDivider()
                    Row(Modifier.fillMaxWidth().then(if (fits && !useExtras) Modifier.weight(heights[i].toFloat()) else Modifier).padding(vertical = s.d(16)),
                        verticalAlignment = if (fits) Alignment.CenterVertically else Alignment.Top, horizontalArrangement = Arrangement.spacedBy(s.gapM)) {
                        SysText(n, spec, if (n == "원인") s.sub else s.ink, maxLines = 1, modifier = Modifier.width(s.iconSize + s.d(16)), align = TextAlign.Center)
                        SysText(a, spec, if (n == "원인") s.sub else s.ink, modifier = Modifier.weight(1f))
                    }
                }
            }
            if (useExtras) Column(Modifier.fillMaxWidth().weight(1f)) {
                SysDivider()
                GridRow(Modifier.weight(1f), extras)
                SysDivider()
            }
        }
    }
}

// ---------------- 경고 팝업 ----------------

/**
 * 시스템 대화상자 모양: 어두운 바깥(dim) 위 흰 각진 판. 왼쪽 위 경고등 아이콘(색은 이 아이콘과 차 그림 경고 부위에만)과 지금 할 행동,
 * 엔진 문장 3줄 이하, 갈 곳, 아래 버튼은 세그먼트처럼 같은 폭(주 버튼 검은 면). 경유지 질문이 있으면 "경유지 추가"와 "취소".
 */
@Composable
fun SysAlert(st: UiState.Driving, speaking: Boolean, basis: BasisView?, shops: List<Shop>, askWaypoint: Boolean, onWaypoint: (Boolean) -> Unit, onEnd: (() -> Unit)?, onAction: (ButtonAction) -> Unit) {
    // 주행 중 경고 화면은 정차 뒤 안내 첫 화면과 같은 틀(같은 자리의 경고 타일과 다음 행동 타일)로 그린다.
    // 기어 P 순간 화면이 갈아 끼워지지 않고 제목과 오른쪽 아래 타일만 바뀌어, 정차 직전 다른 카드가 잠깐 보이지 않는다.
    val s = sys()
    val tier = basis?.tier ?: st.tier
    val lamp = LampCatalog.byId(st.lampId)
    val place = placeFor(basis, shops, parked = false)
    val stop = tier == Tier.STOP_NOW
    Column(Modifier.fillMaxSize().background(s.bg).padding(start = s.padX, end = s.padX, top = s.d(28), bottom = s.d(36)), verticalArrangement = Arrangement.spacedBy(s.gapM)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onEnd != null) Row(Modifier.sysClick(onEnd, s.bg).padding(end = s.gapM), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(s.d(12))) {
                SysIcon(Glyph.Back, s.iconBig, s.ink, contentDescription = "닫기"); SysText("닫기", s.section, maxLines = 1)
            }
            Spacer(Modifier.weight(1f))
            VoiceState(speaking)
        }
        Column(Modifier.fillMaxWidth().weight(1f)) {
            SysDivider()
            GridRow(Modifier.weight(1f), listOf(
                2f to { m -> HeroTile(glyphForLamp(st.lampId, st.tier), lampColor(tier), carZoneFor(st.lampId), null, basis?.verdict ?: verdictLabel(tier),
                    listOfNotNull(lamp?.nameKo, actionSummary(st.lampId)), m, listOf(s.hero, s.title, s.lead, s.section),
                    badge = LampTime.lines(EngineHub.warningLogText())[st.lampId]?.let { Glyph.Clock to it }) },
                1f to { m -> Column(m) {
                    if (stop) SysTile("견인 요청", "한국도로공사 ${EvacuationMachine.EX_CALL_CENTER}", Glyph.Tow, Modifier.fillMaxWidth().weight(1f), onClick = { onAction(ButtonAction.CALL_EX_TOW) }, go = true)
                    else PlaceTile(place, Modifier.fillMaxWidth().weight(1f))
                    SysDivider()
                    RepeatTile(Modifier.fillMaxWidth().weight(1f), onAction)
                } },
            ))
            SysDivider()
        }
        if (askWaypoint) WaypointAnswer(onWaypoint)
    }
}

// ---------------- 대피 ----------------

/**
 * 고속도로 대피. 왼쪽 메뉴는 대피 단계(지금 단계 검은 면). 내용은 타일 격자: 큰 타일에 지금 단계(경고 삼각형, 큰 글, 차 그림),
 * 둘레에 긴급견인, 보험사, 갈 곳(앞쪽 졸음쉼터나 휴게소 거리), 다시 듣기, 다음 단계.
 */
@Composable
fun SysEvac(st: UiState.Evacuation, speaking: Boolean, basis: BasisView?, shops: List<Shop>, onEnd: (() -> Unit)?, onAction: (ButtonAction) -> Unit) {
    val s = sys()
    val now = STEPS.indexOfFirst { it.first == st.step }.coerceAtLeast(0)
    val place = placeFor(basis, shops, parked = false)
    var open by rememberSaveable { mutableStateOf(false) }
    SysScaffold(scroll = false, gap = s.gapM, menuScroll = false, menu = {
        if (onEnd != null) { SysMenuRow("닫기", Glyph.Back, false, onEnd); SysDivider() }
        STEPS.forEachIndexed { i, (_, name) ->
            SysMenuRow(name, if (i < now) Glyph.Check else null, i == now, null, detail = "대피 ${i + 1}단계")
        }
        MenuFooter(speaking, true)
    }) {
        SysPageTitle("고속도로 대피 안내", "대피 ${now + 1}단계, ${STEPS[now].second}") { VoiceState(speaking) }
        // 한 화면에 큰 타일 하나와 행동 두 개(긴급견인 전화, 자세히 보기). 나머지는 자세히 보기에서 펼친다
        Column(Modifier.fillMaxWidth().weight(1f)) {
            SysDivider()
            GridRow(Modifier.weight(1f), listOf(
                2f to { m ->
                    if (!open) HeroTile(Glyph.Alert, Pleos.colors.regulationRed, CarZone.BRAKES, "대피 ${now + 1}단계", st.lines.firstOrNull().orEmpty(), emptyList(), m, listOf(s.title, s.lead, s.body), headLines = 3,
                        badge = Glyph.Check to "${STEPS.size}단계 중 ${now + 1}단계")
                    else Column(m) {
                        GridRow(Modifier.weight(1f), listOf(
                            1f to { mm -> SysTile(ButtonAction.CALL_INSURANCE.label, "가입한 보험사", Glyph.Phone, mm, onClick = { onAction(ButtonAction.CALL_INSURANCE) }, go = true) },
                            1f to { mm -> PlaceTile(place, mm) },
                        ))
                        SysDivider()
                        GridRow(Modifier.weight(1f), listOf(
                            1f to { mm -> RepeatTile(mm, onAction) },
                            1f to { mm -> STEPS.getOrNull(now + 1)?.let { SysTile("다음 단계", it.second, Glyph.Check, mm) }
                                ?: SysTile("마지막 단계", "가드레일 밖 대기", Glyph.Check, mm, stateOn = true) },
                        ))
                    }
                },
                1f to { m -> Column(m) {
                    SysTile("견인 요청", "한국도로공사 ${EvacuationMachine.EX_CALL_CENTER}", Glyph.Tow, Modifier.fillMaxWidth().weight(1f), onClick = { onAction(ButtonAction.CALL_EX_TOW) }, go = true)
                    SysDivider()
                    if (!open) SysTile("자세히 보기", null, Glyph.Info, Modifier.fillMaxWidth().weight(1f), onClick = { open = true }, go = true)
                    else SysTile("닫기", null, Glyph.Back, Modifier.fillMaxWidth().weight(1f), onClick = { open = false })
                } },
            ))
            SysDivider()
        }
    }
}

private val STEPS = listOf(
    EvacuationStep.ASK_HAZARD to "비상등 켜기",
    EvacuationStep.ASK_TRUNK to "트렁크 열기",
    EvacuationStep.ASK_EXIT to "가드레일 밖 대피",
    EvacuationStep.OUTSIDE to "견인 요청",
)

// ---------------- 경고등 정보 ----------------

@Composable
fun SysCatalog(entries: List<CatalogView>, onPage: (HomePage) -> Unit) {
    val s = sys()
    val groups = LampColorGroup.entries.filter { g -> entries.any { it.group == g } }
    var gi by rememberSaveable { mutableStateOf(0) }
    val group = groups.getOrElse(gi) { groups.first() }
    val shown = entries.filter { it.group == group }
    var pickedId by rememberSaveable { mutableStateOf<String?>(null) }
    val e = shown.firstOrNull { it.id == pickedId } ?: shown.first()
    SysScaffold(scroll = false, gap = s.gapM, menu = {
        SysMenuRow("홈", Glyph.Back, false, { onPage(HomePage.Home) })
        SysDivider()
        shown.forEach { c -> SysMenuRow(c.nameKo, c.glyph, c.id == e.id, { pickedId = c.id }, iconTint = groupColor(c.group)) }
    }) {
        SysPageTitle("경고등 정보")
        SysSegment(groups.map { g -> SysSeg("${g.label} ${entries.count { it.group == g }}", { gi = groups.indexOf(g); pickedId = null }, selected = g == group) })
        Row(Modifier.fillMaxWidth().weight(1f)) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                SysDivider()
                val zone = carZoneFor(e.lampId ?: e.id)
                HeroTile(e.glyph, groupColor(e.group), zone, "${e.group.label} 경고등", e.nameKo, listOfNotNull(e.verdict),
                    Modifier.fillMaxWidth().weight(1f), listOf(s.title, s.lead, s.section), headLines = 2)
                SysDivider()
                Column(Modifier.fillMaxWidth().weight(1f).padding(s.tilePad), verticalArrangement = Arrangement.spacedBy(s.gapS)) {
                    SysIcon(Glyph.Info, s.iconBig, s.ink)
                    Spacer(Modifier.weight(1f))
                    SysText("원인", s.desc, s.sub, maxLines = 1)
                    FitText(e.meaning ?: e.nameKo, listOf(s.lead, s.body, s.desc), 6, Modifier.fillMaxWidth())
                }
                SysDivider()
            }
            SysVDivider()
            Column(Modifier.weight(1.5f).fillMaxHeight()) {
                SysDivider()
                if (e.actions.isNotEmpty()) FitActions(e.actions, Modifier.fillMaxSize().padding(start = s.gapM, top = s.gapM),
                    extras = listOf(1f to cell { m -> RecordTile(m, e.lampId ?: e.id) }, 1f to cell { m -> ShopTile(m) }))
                else Column(Modifier.padding(start = s.gapM, top = s.gapM), verticalArrangement = Arrangement.spacedBy(s.gapS)) { SysSection("조치 방법"); SysText("없음", s.body, s.sub) }
            }
        }
    }
}

@Composable
private fun groupColor(g: LampColorGroup) = when (g) {
    LampColorGroup.RED -> Pleos.colors.regulationRed
    LampColorGroup.YELLOW -> Pleos.colors.regulationOrange
    LampColorGroup.GREEN_BLUE -> Pleos.colors.informativeActive
}

// ---------------- 기록 ----------------

/**
 * 경고 이력: 실제 경고만(음성 미리 듣기 중에 경고 이력은 뺀다). 줄을 누르면 그 경고의 상세.
 */
@Composable
fun SysLog(rows: List<LogView>, plays: List<PreviewPlay>, clock: String, onPage: (HomePage) -> Unit) {
    val s = sys()
    val real = rows.filterNot { PreviewHistory.isPreview(it.onAtMs, plays) }
    var pickedAt by rememberSaveable { mutableStateOf<Long?>(null) }
    real.firstOrNull { it.onAtMs == pickedAt }?.let { SysLogDetail(it, clock, { pickedAt = null }, onPage); return }
    val lastOn = real.firstOrNull { it.stillOn }
    SysScaffold(scroll = real.size > 6, gap = s.gapM, menuScroll = false, menu = {
        AppMenu(HomePage.Log, onPage)
        if (lastOn != null) MenuCarPanel(carZoneFor(lastOn.lampId), lampColor(lastOn.tier), lastOn.nameKo, "켜짐")
        else MenuCarPanel(CarZone.NONE, s.ink, "경고등 없음", null)
    }) {
        SysPageTitle("경고 이력") { ClockTrailing(clock) }
        if (real.size <= 2) {
            // 기록이 적을 때도 빈 칸 없이 두 줄 격자: 위 줄 기록(없으면 "없음")과 건수, 아래 줄 마지막 확인, 경고등 정보, 설정
            Column(Modifier.fillMaxWidth().weight(1f)) {
                SysDivider()
                GridRow(Modifier.weight(1f), (if (real.isEmpty()) listOf(1f to cell { m -> SysTile("경고등", "없음", Glyph.Check, m) }, 1f to cell { m -> ShopTile(m) })
                    else real.map { r -> (2f / real.size) to cell { m -> SysTile(r.nameKo, r.timeLine, glyphForLamp(r.lampId, r.tier), m, iconTint = lampColor(r.tier), stateOn = r.stillOn, onClick = { pickedAt = r.onAtMs }, go = true, figure = carZoneFor(r.lampId)) } })
                    + listOf(1f to cell { m -> SysTile("이력", "${real.size}건", Glyph.Clock, m) }))
                SysDivider()
                GridRow(Modifier.weight(1f), listOf(
                    1f to cell { m -> SysTile("마지막 확인", clock, Glyph.Clock, m) },
                    1f to cell { m -> SysTile("경고등 정보", null, Glyph.Book, m, onClick = { onPage(HomePage.Catalog) }, go = true) },
                    1f to cell { m -> SysTile("설정", null, Glyph.Settings, m, onClick = { onPage(HomePage.Settings) }, go = true) },
                ))
                SysDivider()
            }
            return@SysScaffold
        }
        val cells: List<Cell> = real.map { r -> cell { m -> SysTile(r.nameKo, r.timeLine, glyphForLamp(r.lampId, r.tier), m, iconTint = lampColor(r.tier), stateOn = r.stillOn, onClick = { pickedAt = r.onAtMs }, go = true) } }
        val rowsOfCells = cells.chunked(3)
        Column(Modifier.fillMaxWidth().then(if (real.size > 6) Modifier.height(s.d(190) * rowsOfCells.size) else Modifier.weight(1f))) {
            SysDivider()
            rowsOfCells.forEach { row ->
                GridRow(Modifier.weight(1f), row.map { c -> 1f to c } + List(3 - row.size) { 1f to cell { m -> Box(m) } })
                SysDivider()
            }
        }
    }
}

/** 경고 이력 한 줄 상세: 지금 상태, 켜진 시각과 꺼진 시각, 당시 도로, 안내 문장, 조치 방법. */
@Composable
private fun SysLogDetail(r: LogView, clock: String, onBack: () -> Unit, onPage: (HomePage) -> Unit) {
    val s = sys()
    val lamp = LampCatalog.byId(r.lampId)
    val spoken = r.spokenText
    SysScaffold(scroll = false, gap = s.gapM, menuScroll = false, menu = {
        SysMenuRow("경고 이력", Glyph.Back, false, onBack); SysDivider(); AppMenu(HomePage.Log, onPage); StatusCarPanel()
    }) {
        SysPageTitle(r.nameKo) { ClockTrailing(clock) }
        Row(Modifier.fillMaxWidth().weight(1f)) {
            Column(Modifier.weight(1.4f).fillMaxHeight()) {
                SysDivider()
                HeroTile(glyphForLamp(r.lampId, r.tier), lampColor(r.tier), carZoneFor(r.lampId), null, if (r.stillOn) "켜짐" else "꺼짐",
                    listOf(r.timeLine), Modifier.fillMaxWidth().weight(1.2f), listOf(s.hero, s.title, s.lead))
                SysDivider()
                GridRow(Modifier.weight(1f), listOfNotNull(
                    1f to cell { m -> SysTile("켜진 시각", r.onTimeText.ifBlank { r.timeLine }, Glyph.Clock, m) },
                    1f to cell { m -> SysTile("꺼진 시각", r.offTimeText ?: "켜짐", Glyph.Check, m, stateOn = r.offTimeText == null) },
                ))
                SysDivider()
                GridRow(Modifier.weight(1f), listOfNotNull(
                    2f to cell { m -> Column(m.padding(s.tilePad), verticalArrangement = Arrangement.SpaceBetween) {
                        SysIcon(Glyph.Speaker, s.iconBig, s.ink)
                        Column(verticalArrangement = Arrangement.spacedBy(s.d(4))) {
                            SysText("안내 문장", s.desc, s.sub, maxLines = 1)
                            FitText(spoken ?: "없음", listOf(s.body, s.desc), 3, Modifier.fillMaxWidth())
                        }
                    } },
                    r.roadLabel?.let { road -> 1f to cell { m -> SysTile("주행 도로", road, Glyph.Road, m) } },
                ))
                SysDivider()
            }
            if (lamp != null) {
                SysVDivider()
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    SysDivider()
                    FitActions(lamp.manualActions, Modifier.fillMaxSize().padding(start = s.gapM, top = s.gapM), extras = listOf(1f to cell { m -> ShopTile(m) }))
                }
            }
        }
    }
}

private typealias Cell = @Composable (Modifier) -> Unit
private fun cell(c: @Composable (Modifier) -> Unit): Cell = c

@Composable
private fun LogTile(r: LogView, m: Modifier) {
    SysTile(r.nameKo, r.timeLine, glyphForLamp(r.lampId, r.tier), m, iconTint = lampColor(r.tier), stateOn = r.stillOn)
}
