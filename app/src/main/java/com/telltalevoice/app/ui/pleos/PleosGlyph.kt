package com.telltalevoice.app.ui.pleos

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.telltalevoice.core.Tier

/**
 * 경고등 그림. 키트(GdPsTozIBUidTxFQOH0wXa)에 경고등 아이콘이 없어서 계기판 경고등 모양을 Compose 벡터 경로로 그린다.
 * 경로 좌표는 24 x 24 그림판 안 값이고 화면 크기는 부품이 키트 치수로 정한다. 색은 그릴 때 키트 토큰으로 칠한다.
 */
enum class Glyph(internal val strokes: List<String>, internal val fills: List<String> = emptyList()) {
    /** 엔진 오일 압력: 오일 주전자와 떨어지는 방울 */
    Oil(listOf("M3 10H13L15 12L21 9V10.5L15 17H3Z", "M8 10V7", "M5.5 7H10.5"), listOf("M20 13.2C20.8 14.4 21.2 15.2 21.2 15.8A1.2 1.2 0 0 1 18.8 15.8C18.8 15.2 19.2 14.4 20 13.2Z")),
    /** 타이어 공기압: 타이어 단면과 느낌표 */
    Tire(listOf("M7 4C4.3 8 4.3 15 7 19H17C19.7 15 19.7 8 17 4", "M6.5 19V21", "M10 19V21", "M14 19V21", "M17.5 19V21", "M12 8V12.5"), listOf(dot(12f, 15.5f))),
    /** 엔진 점검: 엔진 윤곽 */
    Engine(listOf("M6 9H8V7H14V9H17L19 11V12H21V9.5", "M21 9.5V16.5", "M19 14H21", "M19 12V16L17 18H9L7 16H6V9Z", "M3 11V15", "M3 13H6", "M10 7V5H13", "M8 5H15")),
    /** 브레이크: 원 안 느낌표와 양옆 괄호 */
    Brake(listOf(circle(12f, 12f, 6.5f), "M5 5.5C2.3 9 2.3 15 5 18.5", "M19 5.5C21.7 9 21.7 15 19 18.5", "M12 8.5V12.5"), listOf(dot(12f, 15.3f))),
    /** 일반 경고: 삼각형 안 느낌표 */
    Alert(listOf("M12 3.5L21.5 20H2.5Z", "M12 9.5V14"), listOf(dot(12f, 17f))),
    /** 안내: 원 안 i */
    Info(listOf(circle(12f, 12f, 9f), "M12 11V16.5"), listOf(dot(12f, 7.8f))),
    /** 연료 */
    Fuel(listOf("M5 20V5H13V20Z", "M5 10H13", "M3.5 20H14.5", "M13 9H15L17.5 11.5V16.5A1.5 1.5 0 0 0 20.5 16.5V8L18 5.5")),
    /** 와셔액: 앞 유리와 분사 */
    Washer(listOf("M3 18Q12 8 21 18Z", "M12 12V6.5", "M8.5 12.5L6.5 8", "M15.5 12.5L17.5 8")),
    /** 경고등 정보: 책 */
    Book(listOf("M6 3.5H18V18H7.5A1.5 1.5 0 0 0 7.5 21H18", "M6 3.5V19.5", "M9.5 8H15", "M9.5 11.5H14")),
    /** 기록: 시계 */
    Clock(listOf(circle(12f, 12f, 9f), "M12 7V12L15.5 14")),
    /** 도로 종류: 차로 */
    Road(listOf("M8.5 3L5 21", "M15.5 3L19 21", "M12 4V7", "M12 10.5V13.5", "M12 17V20")),
    /** 속도: 계기 바늘 */
    Speed(listOf("M3.5 17A8.5 8.5 0 1 1 20.5 17", "M12 15L16 9")),
    /** 장소: 위치 표시 */
    Place(listOf("M12 21C8.5 16.5 5.5 13.5 5.5 9.5A6.5 6.5 0 0 1 18.5 9.5C18.5 13.5 15.5 16.5 12 21Z", circle(12f, 9.5f, 2.3f))),
    /** 음성: 스피커와 소리 */
    Speaker(listOf("M4 9.5H7.5L12 5.5V18.5L7.5 14.5H4Z", "M15.5 9Q17.5 12 15.5 15", "M18 6.5Q22 12 18 17.5")),
    /** 전화 */
    Phone(listOf("M6.5 3.5L9.5 3.5L11 8L9 9.5C10 11.8 12.2 14 14.5 15L16 13L20.5 14.5V17.5C20.5 19 19.3 20.5 17.5 20.3C10 19.6 4.4 14 3.7 6.5C3.5 4.7 5 3.5 6.5 3.5Z")),
    /** 확인 표시. 키트 ic_check basic(1:6686)과 같은 꺾쇠 모양 */
    Check(listOf("M5 12.5L10 17.5L19.5 7")),
    /** 다른 화면으로 가기(빠른 설정 타일 오른쪽 위) */
    Go(listOf("M4.5 12H19", "M13 6L19 12L13 18")),
    /** 뒤로 */
    Back(listOf("M15 4.5L7.5 12L15 19.5")),
    /** 홈 */
    Home(listOf("M3.5 11L12 4L20.5 11", "M6 9.5V20H18V9.5", "M10 20V14.5H14V20")),
    /** 다시 듣기: 돌아가는 화살표 */
    Repeat(listOf("M19.5 12A7.5 7.5 0 1 1 17.3 6.7", "M19.5 4V8.5H15")),
    /** 정지 */
    Stop(listOf("M7 7H17V17H7Z")),
    /** 재생 */
    Play(listOf("M8 5.5V18.5L18.5 12Z")),
    /** 긴급견인: 견인차와 고리 */
    Tow(listOf("M2.5 16.5V10H11V16.5", "M11 12H15.5L18.5 14.5V16.5", circle(6f, 17.5f, 1.8f), circle(15.5f, 17.5f, 1.8f), "M11 10L20 5.5", "M20 5.5V9", "M20 9A1.3 1.3 0 1 1 18.7 10.3")),
    /** 경유지: 길 안내 화살표 */
    Route(listOf("M12 3L19 20L12 16L5 20Z")),
    /** 설정: 톱니 */
    Settings(listOf(circle(12f, 12f, 3f), circle(12f, 12f, 7f), "M12 2.5V5", "M12 19V21.5", "M2.5 12H5", "M19 12H21.5", "M5.3 5.3L7 7", "M17 17L18.7 18.7", "M5.3 18.7L7 17", "M17 7L18.7 5.3")),
    /** 차량 */
    Car(listOf("M4 16V11.5L6.5 6.5H17.5L20 11.5V16Z", "M4 11.5H20", "M5.5 16V18.5", "M18.5 16V18.5"), listOf(dot(7.5f, 13.8f), dot(16.5f, 13.8f))),
    ;

    internal val vector: ImageVector by lazy { build(strokes, fills) }
}

private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r} ${cy}A$r $r 0 1 0 ${cx + r} ${cy}A$r $r 0 1 0 ${cx - r} ${cy}Z"
private fun dot(cx: Float, cy: Float) = circle(cx, cy, 1.25f)

private fun build(strokes: List<String>, fills: List<String>): ImageVector {
    // 그림판 24 x 24. 기본 크기 값은 그릴 때 부품 치수로 덮어써서 화면에 쓰이지 않는다.
    val b = ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
    strokes.forEach {
        b.addPath(addPathNodes(it), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
    }
    fills.forEach { b.addPath(addPathNodes(it), fill = SolidColor(Color.Black)) }
    return b.build()
}

/**
 * 심각도 색. 모두 키트 토큰이다.
 * - 바로 정차와 대피: regulation/red
 * - 휴게소까지 주행: regulation/orange
 * - 도착 뒤 점검: regulation/yellow(글자와 그림은 static/dark/100)
 * - 안내와 정상: informative/positive(파랑)
 * face 는 카드 위에 까는 옅은 면. 같은 색에 키트 alpha/dark/100(10%)의 불투명도를 쓴다.
 */
@Immutable
data class Tone(val solid: Color, val onSolid: Color, val face: Color, val text: Color)

enum class ToneKind { Stop, Rest, Arrival, Info }

@Composable
fun toneOf(kind: ToneKind): Tone {
    val c = Pleos.colors
    val a = c.alphaDark100.alpha
    return when (kind) {
        ToneKind.Stop -> Tone(c.regulationRed, c.staticLight100, c.regulationRed.copy(alpha = a), c.regulationRed)
        ToneKind.Rest -> Tone(c.regulationOrange, c.staticLight100, c.regulationOrange.copy(alpha = a), c.textDarkPrimary)
        ToneKind.Arrival -> Tone(c.regulationYellow, c.staticDark100, c.regulationYellow.copy(alpha = a), c.textDarkPrimary)
        ToneKind.Info -> Tone(c.informativePositive, c.staticLight100, c.informativePositive.copy(alpha = a), c.informativePositive)
    }
}

fun Tier.toneKind(): ToneKind = when (this) {
    Tier.STOP_NOW -> ToneKind.Stop
    Tier.REST_AREA -> ToneKind.Rest
    Tier.AFTER_ARRIVAL -> ToneKind.Arrival
}

@Composable
fun toneOf(tier: Tier): Tone = toneOf(tier.toneKind())

/** 그림 크기 세 단계. 모두 키트 치수 변수 */
enum class GlyphSize { Hero, Medium, Small }

/**
 * 원 안 경고등 그림. 원 모양은 키트 notification_system/profile 의 999 모서리(원).
 * - Hero: 원 notification/system/imagary_square(100), 그림 page_navigation depth_icon_square(56)
 * - Medium: 원 vehicle_settings/list/item/icon_container_square(80), 그림 dropdown/suffix_icon_square(48)
 * - Small: 원 notification/system/profile_app_square(48), 그림 page_navigation title_dropdown_icon_square(28)
 * solid 면 원은 색으로 채우고 그림은 흰색(또는 노랑 위 검정), 아니면 옅은 면에 색 그림.
 */
@Composable
fun PleosGlyph(glyph: Glyph, tone: Tone, size: GlyphSize, modifier: Modifier = Modifier, solid: Boolean = true, description: String? = null) {
    val d = Pleos.dimens
    val (outer, inner) = glyphDims(size)
    Box(modifier.size(outer).clip(CircleShape).background(if (solid) tone.solid else tone.face), contentAlignment = Alignment.Center) {
        Image(glyph.vector, contentDescription = description, modifier = Modifier.size(inner), colorFilter = ColorFilter.tint(if (solid) tone.onSolid else tone.solid))
    }
}

@Composable
fun glyphDims(size: GlyphSize): Pair<Dp, Dp> {
    val d = Pleos.dimens
    return when (size) {
        GlyphSize.Hero -> d.notificationSystemImagarySquare to d.pageNavigationTitleDepthIconSquare
        GlyphSize.Medium -> d.vehicleSettingsListItemIconContainerSquare to d.dropdownSuffixIconSquare
        GlyphSize.Small -> d.notificationSystemProfileAppSquare to d.pageNavigationTitleDropdownIconSquare
    }
}

/** 원 안 숫자(조치 방법 1, 2, 3과 대피 단계). 원과 글자 크기는 PleosGlyph Small 과 같은 키트 값, 글자 bodyLarge Bold */
@Composable
fun PleosNumberCircle(n: Int, tone: Tone, size: GlyphSize, solid: Boolean, modifier: Modifier = Modifier) {
    val (outer, _) = glyphDims(size)
    Box(modifier.size(outer).clip(CircleShape).background(if (solid) tone.solid else tone.face), contentAlignment = Alignment.Center) {
        PleosText(n.toString(), if (size == GlyphSize.Small) Pleos.type.bodyLarge else Pleos.type.titleMedium, if (solid) tone.onSolid else tone.solid,
            weight = com.telltalevoice.app.ui.pleos.generated.PleosComponentWeight.BUTTON_LABEL, maxLines = 1)
    }
}

/** 경고등 id 를 그림으로. 경고등 정보과 기록이 함께 쓴다. */
fun glyphForLamp(id: String, tier: Tier?): Glyph = when (id) {
    "ENGINE_OIL_PRESSURE" -> Glyph.Oil
    "LOW_TIRE_PRESSURE", "TPMS_FAULT" -> Glyph.Tire
    "ENGINE" -> Glyph.Engine
    "BRAKE", "EBD", "ABS", "ELECTRONIC_PARKING_BRAKE", "AUTO_HOLD" -> Glyph.Brake
    "LOW_FUEL" -> Glyph.Fuel
    else -> if (tier == Tier.STOP_NOW) Glyph.Alert else Glyph.Info
}
