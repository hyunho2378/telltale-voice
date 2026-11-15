package com.telltalevoice.app.ui.sys

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import com.telltalevoice.app.ui.pleos.keepAll
import com.telltalevoice.app.ui.pleos.toStyle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.telltalevoice.app.ui.pleos.Glyph
import com.telltalevoice.app.ui.pleos.Pleos
import com.telltalevoice.app.ui.pleos.PleosText
import com.telltalevoice.app.ui.pleos.generated.PleosTypeSpec
import com.telltalevoice.core.Tier

/**
 * 시스템 룩. 기준은 Pleos 에뮬레이터 안 실제 시스템 화면(차량 설정 앱 ai.umos.vehiclecontrol) 실측이다.
 * 출처: Pleos 에뮬레이터 시스템 화면 실측(ref_real/system sys_00 빠른 설정, sys_01 주행, sys_02 라이트의 xml 좌표와 픽셀, 2560 x 1440, 1px = 1dp).
 * 피그마 In-Car App 키트의 둥근 카드와 주황 버튼, 회색 둥근 면을 쓰지 않는다(docs/SYSTEM_LOOK.md, 2026. 11. 11. 사용자 판정).
 *
 * 실측값(connect-s 창 기준)
 * - 바탕 #FFFFFF, 비활성 칸 #F5F5F5(sys_02 2200,875), 눌린 타일 #F0F0F0(sys_00 도어 타일)
 * - 선 1dp: 세그먼트 바깥선 #000000(sys_02 1318,409), 구역 나눔선과 왼쪽 메뉴 경계선 #A6A6A6(sys_02 1253,600)
 * - 글자: 기본 #000000, 설명 #7C7C7C, 켜짐 상태 파랑 #1966F5(sys_00 도어 "잠김")
 * - 페이지 제목 줄 64(약 48sp) 보통 굵기, 구역 이름 줄 38(약 28sp), 설명 줄 29(약 22sp), 세그먼트 칸 글자 줄 34(약 26sp)
 * - 세그먼트 높이 100, 왼쪽 메뉴 폭 400(853~1253), 줄 높이 100, 아이콘 왼쪽 32, 이름 왼쪽 88
 * - 내용 좌우 여백 65(메뉴 경계 1253에서 제목 1318), 제목 위 여백 약 70
 * - 모서리 없음(세그먼트와 선택 칸, 타일 모두 각진 사각형). 그림자 없음
 * 낮은 창(connect-l, connect-w)은 키트 글자 단계 비율(titleLarge 모드 값 / connect-s 값)로 같은 값을 줄인다.
 */
@Immutable
class SysLook(val k: Float) {
    val bg = Color(0xFFFFFFFF)
    val inactive = Color(0xFFF5F5F5)
    val pressed = Color(0xFFF0F0F0)
    val ink = Color(0xFF000000)
    val line = Color(0xFFA6A6A6)
    val sub = Color(0xFF7C7C7C)
    val on = Color(0xFF1966F5)
    val onInk = Color(0xFFFFFFFF)

    fun d(v: Int): Dp = (v * k).dp
    val stroke: Dp = 1.dp
    val menuWidth get() = d(400)
    val rowHeight get() = d(100)
    val segHeight get() = d(100)
    val padX get() = d(65)
    val padTop get() = d(70)
    val iconLeft get() = d(32)
    val iconSize get() = d(40)
    val iconBig get() = d(56)
    val iconHero get() = d(96)
    val gapSection get() = d(50)
    val gapS get() = d(16)
    val gapM get() = d(24)
    val tilePad get() = d(32)

    /** 글자 단계(크기 sp, 줄 높이 sp, 굵기). 보통 굵기 400 이 기본, 강조만 700. */
    val title get() = PleosTypeSpec((48 * k).toInt(), (64 * k).toInt(), 400)
    val hero get() = PleosTypeSpec((60 * k).toInt(), (80 * k).toInt(), 700)
    val section get() = PleosTypeSpec((28 * k).toInt(), (38 * k).toInt(), 400)
    /** 지금 나오는 안내 문장(제목과 구역 이름 사이 단계) */
    val lead get() = PleosTypeSpec((34 * k).toInt(), (46 * k).toInt(), 400)
    val body get() = PleosTypeSpec((28 * k).toInt(), (38 * k).toInt(), 400)
    val seg get() = PleosTypeSpec((26 * k).toInt(), (34 * k).toInt(), 400)
    val desc get() = PleosTypeSpec((22 * k).toInt(), (29 * k).toInt(), 400)
    /** 좁은 타일 이름(구역 이름과 설명 사이) */
    val small get() = PleosTypeSpec((25 * k).toInt(), (33 * k).toInt(), 400)
}

@Composable
fun sys(): SysLook {
    val t = Pleos.type.titleLarge.sizeSp
    return remember(t) { SysLook(t / 40f) }
}

/** 경고등 아이콘 색. 화면에서 색은 경고등 아이콘 하나에만 쓴다(키트 법규 색). */
@Composable
fun lampColor(t: Tier?): Color = when (t) {
    Tier.STOP_NOW -> Pleos.colors.regulationRed
    Tier.REST_AREA -> Pleos.colors.regulationOrange
    Tier.AFTER_ARRIVAL -> Pleos.colors.regulationYellow
    null -> sys().ink
}

/** 선 아이콘. Pleos 테마 앱 공식 아이콘이 있으면 그것을, 없으면 앱이 그린 그림을 쓴다. */
@Composable
fun SysIcon(g: Glyph, size: Dp, color: Color, modifier: Modifier = Modifier, contentDescription: String? = null) {
    // 글 옆 아이콘은 장식이라 설명 없음(null). 글 없이 혼자 쓰일 때만 contentDescription 을 준다
    val theme = themeBitmap(g, size)
    if (theme != null) Image(theme, contentDescription, modifier.size(size), colorFilter = ColorFilter.tint(color))
    else Image(g.vector, contentDescription, modifier.size(size), colorFilter = ColorFilter.tint(color))
}

@Composable
fun SysText(text: String, spec: PleosTypeSpec, color: Color = sys().ink, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE, align: TextAlign = TextAlign.Start, bold: Boolean = false) {
    PleosText(text, spec, color, modifier, weight = if (bold) 700 else spec.weight, align = align, maxLines = maxLines, heading = true)
}

/** 1dp 나눔선(#A6A6A6). */
@Composable
fun SysDivider(modifier: Modifier = Modifier) {
    val s = sys()
    Box(modifier.fillMaxWidth().height(s.stroke).background(s.line))
}

@Composable
fun SysVDivider(modifier: Modifier = Modifier) {
    val s = sys()
    Box(modifier.fillMaxHeight().width(s.stroke).background(s.line))
}

/** 페이지 제목(줄 64, 보통 굵기)과 아래 회색 설명. */
@Composable
fun SysPageTitle(title: String, desc: String? = null, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    val s = sys()
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(s.d(8))) {
            SysText(title, s.title, maxLines = 2)
            if (desc != null) SysText(desc, s.desc, s.sub, maxLines = 2)
        }
        trailing()
    }
}

@Composable
fun SysSection(title: String, modifier: Modifier = Modifier, desc: String? = null) {
    val s = sys()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(s.d(6))) {
        SysText(title, s.section, maxLines = 1)
        if (desc != null) SysText(desc, s.desc, s.sub, maxLines = 2)
    }
}

/** 눌리는 면 공통: 눌림은 #F0F0F0. */
@Composable
fun Modifier.sysClick(onClick: (() -> Unit)?, base: Color, selected: Boolean = false): Modifier {
    val s = sys()
    if (onClick == null) return this.background(if (selected) s.ink else base)
    val src = remember { MutableInteractionSource() }
    val p by src.collectIsPressedAsState()
    return this.background(when { selected -> s.ink; p -> s.pressed; else -> base })
        .clickable(interactionSource = src, indication = null, role = Role.Button, onClick = onClick)
}

/**
 * 세그먼트(sys_02 전조등과 프렁크등 줄): 높이 100, 칸 폭 같게, 바깥선 1dp 검정, 칸 사이 1dp 선, 고른 칸은 검은 면 흰 글자.
 * disabled 칸은 #F5F5F5 면에 회색 글자.
 */
data class SysSeg(val label: String, val onClick: (() -> Unit)?, val selected: Boolean = false, val icon: Glyph? = null, val disabled: Boolean = false)

@Composable
fun SysSegment(items: List<SysSeg>, modifier: Modifier = Modifier, height: Dp = sys().segHeight) {
    val s = sys()
    Row(modifier.fillMaxWidth().height(height).border(s.stroke, s.ink)) {
        items.forEachIndexed { i, it ->
            if (i > 0) Box(Modifier.fillMaxHeight().width(s.stroke).background(if (it.selected || items[i - 1].selected) s.ink else s.line))
            val fg = when { it.selected -> s.onInk; it.disabled -> s.sub; else -> s.ink }
            Row(
                Modifier.weight(1f).fillMaxHeight().sysClick(if (it.disabled) null else it.onClick, if (it.disabled) s.inactive else s.bg, it.selected),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                if (it.icon != null) { SysIcon(it.icon, s.iconSize, fg); Spacer(Modifier.width(s.gapS)) }
                SysText(it.label, s.seg, fg, maxLines = 1, align = TextAlign.Center)
            }
        }
    }
}

/** 왼쪽 메뉴 한 줄(sys_02 메뉴): 높이 100, 왼쪽 32 아이콘, 88 이름, 고른 줄은 검은 면 흰 글자. */
@Composable
fun SysMenuRow(label: String, icon: Glyph?, selected: Boolean, onClick: (() -> Unit)?, modifier: Modifier = Modifier, detail: String? = null, iconTint: Color? = null) {
    val s = sys()
    val fg = if (selected) s.onInk else s.ink
    Row(
        modifier.fillMaxWidth().heightIn(min = s.rowHeight).sysClick(onClick, s.bg, selected).padding(start = s.iconLeft, end = s.gapM),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(s.d(16)),
    ) {
        if (icon != null) SysIcon(icon, s.iconSize, if (selected) s.onInk else (iconTint ?: s.ink))
        Column(Modifier.weight(1f).padding(vertical = s.d(12)), verticalArrangement = Arrangement.spacedBy(s.d(2))) {
            SysText(label, s.body, fg, maxLines = 2)
            if (detail != null) SysText(detail, s.desc, if (selected) s.onInk else s.sub, maxLines = 1)
        }
    }
}

/**
 * 왼쪽 메뉴와 오른쪽 내용 틀(차량 설정 앱과 같은 틀). 메뉴 폭 400, 메뉴와 내용 사이 1dp #A6A6A6 선.
 * 내용은 좌우 65, 위 70 여백. scroll 이면 내용이 길 때 스크롤(정차 상태 화면).
 */
@Composable
fun SysScaffold(menu: @Composable ColumnScope.() -> Unit, scroll: Boolean = true, gap: Dp = sys().gapSection, menuScroll: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val s = sys()
    Row(Modifier.fillMaxSize().background(s.bg)) {
        Column(Modifier.width(s.menuWidth).fillMaxHeight().then(if (menuScroll) Modifier.verticalScroll(rememberScrollState()) else Modifier), content = menu)
        SysVDivider()
        Column(
            Modifier.weight(1f).fillMaxHeight().then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(start = s.padX, end = s.padX, top = if (scroll) s.padTop else s.d(48), bottom = s.d(40)),
            verticalArrangement = Arrangement.spacedBy(gap), content = content,
        )
    }
}

/**
 * 타일(sys_00 빠른 설정): 각진 칸, 왼쪽 위 선 아이콘, 아래 이름(줄 38)과 상태(줄 29, 회색. 켜짐은 파랑).
 * 오른쪽 위 화살표는 다른 화면으로 가는 타일만. 타일 사이 선은 SysGrid 가 그린다.
 */
@Composable
fun SysTile(
    name: String,
    state: String?,
    icon: Glyph?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    iconTint: Color? = null,
    stateOn: Boolean = false,
    /** 켜져 있는 상태(빠른 설정 "도어 잠김" 타일처럼 회색 면 #F0F0F0과 파란 상태 글) */
    active: Boolean = false,
    go: Boolean = false,
    big: Boolean = false,
    nameSpec: PleosTypeSpec? = null,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
    /** 타일 가운데 작은 차 그림(경고 위치). null 이면 그리지 않음 */
    figure: CarZone? = null,
) {
    val s = sys()
    // 칸 높이에 맞춰 세 단계로 놓는다: 넉넉하면 아이콘 위와 글 아래(빠른 설정 그대로), 낮으면 여백과 아이콘을 줄이고,
    // 더 낮으면 아이콘을 글 왼쪽에 둔다. 글은 칸 폭에 맞는 단계(FitText)로 한 줄에 넣어 잘림이 없게 한다.
    BoxWithConstraints(modifier.sysClick(onClick, if (active) s.pressed else s.bg)) {
        val nSpec = nameSpec ?: s.body
        val sSpec = if (big) s.body else s.desc
        val textH = (nSpec.lineHeightSp + (if (state != null) sSpec.lineHeightSp + 4 else 0)).toFloat().dp
        val iconBig = if (big) s.iconHero else s.iconBig
        val roomy = maxHeight >= s.tilePad * 2 + iconBig + textH + s.d(24)
        val mid = !roomy && maxHeight >= s.d(20) * 2 + s.iconSize + textH + s.d(12)
        val pad = if (roomy) s.tilePad else s.d(20)
        val icSize = if (roomy) iconBig else s.iconSize
        val names = listOf(nSpec, s.small, s.desc).distinct().filter { it.sizeSp <= nSpec.sizeSp }
        val states = listOf(sSpec, s.desc).distinct()
        if (roomy || mid) {
            Column(Modifier.fillMaxSize().padding(pad), verticalArrangement = Arrangement.SpaceBetween) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    if (icon != null) SysIcon(icon, icSize, iconTint ?: s.ink)
                    Spacer(Modifier.weight(1f))
                    if (go) SysIcon(Glyph.Go, s.iconSize, s.ink)
                }
                if (figure != null && roomy) Box(Modifier.fillMaxWidth().weight(1f).padding(vertical = s.d(8)), contentAlignment = Alignment.Center) {
                    CarFigure(figure, iconTint ?: s.ink, Modifier.fillMaxSize(), horizontal = true)
                }
                Column(verticalArrangement = Arrangement.spacedBy(s.d(4))) {
                    TileText(name, names, s.ink)
                    if (state != null) TileText(state, states, if (stateOn) s.on else s.sub)
                    extra?.invoke(this)
                }
            }
        } else {
            Row(Modifier.fillMaxSize().padding(horizontal = pad, vertical = s.d(12)), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(s.gapS)) {
                if (icon != null) SysIcon(icon, s.iconSize, iconTint ?: s.ink)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(s.d(2))) {
                    TileText(name, names, s.ink)
                    if (state != null) TileText(state, states, if (stateOn) s.on else s.sub)
                }
                if (go) SysIcon(Glyph.Go, s.iconSize, s.ink)
            }
        }
    }
}

/** 타일 글 한 줄: 칸 폭에 맞는 가장 큰 단계. */
@Composable
private fun TileText(text: String, specs: List<PleosTypeSpec>, color: Color) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val m = rememberTextMeasurer()
        val px = constraints.maxWidth
        // 한 줄에 드는 가장 큰 단계, 없으면 가장 작은 단계로 두 줄(말줄임 대신 줄바꿈)
        val fit = remember(text, px, specs) {
            specs.firstOrNull { sp -> !m.measure(AnnotatedString(keepAll(text)), sp.toStyle(null, true), maxLines = 1, constraints = Constraints(maxWidth = px.coerceAtLeast(1))).hasVisualOverflow }
        }
        SysText(text, fit ?: specs.last(), color, maxLines = if (fit != null) 1 else 3)
    }
}

/**
 * 토글 줄(sys_02 에스코트 조명 줄): 왼쪽 둥근 토글, 오른쪽 이름(줄 38)과 회색 한 줄. 켜짐은 검은 면, 꺼짐은 회색 면.
 * 움직임 없이 바로 바뀐다(차량 화면 움직임 금지).
 */
@Composable
fun SysToggleRow(name: String, desc: String?, on: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val s = sys()
    Row(modifier.fillMaxWidth().heightIn(min = s.rowHeight).sysClick({ onChange(!on) }, s.bg).padding(vertical = s.d(12)),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(s.gapM)) {
        Box(Modifier.width(s.d(72)).height(s.d(40)).background(if (on) s.ink else Color(0xFFD9D9D9), androidx.compose.foundation.shape.RoundedCornerShape(50)),
            contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart) {
            Box(Modifier.padding(s.d(4)).size(s.d(32)).background(s.bg, androidx.compose.foundation.shape.CircleShape))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(s.d(4))) {
            SysText(name, s.section, maxLines = 1)
            if (desc != null) SysText(desc, s.desc, s.sub, maxLines = 2)
        }
    }
}

/** 타일 격자 한 줄: 칸 사이 1dp 선. */
@Composable
fun SysTileRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier.fillMaxWidth(), content = content)
}

/** 대화상자 바깥 어둡게(키트 dim/dark/primary). */
@Composable
fun SysDim(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(sys().bg).background(Pleos.colors.dimDarkSecondary), contentAlignment = Alignment.Center, content = content)
}
