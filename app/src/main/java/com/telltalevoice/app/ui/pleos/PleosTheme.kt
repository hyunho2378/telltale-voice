package com.telltalevoice.app.ui.pleos

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telltalevoice.app.R
import com.telltalevoice.app.ui.pleos.generated.PleosColors
import com.telltalevoice.app.ui.pleos.generated.PleosDarkColors
import com.telltalevoice.app.ui.pleos.generated.PleosDimens
import com.telltalevoice.app.ui.pleos.generated.PleosFormFactor
import com.telltalevoice.app.ui.pleos.generated.PleosLightColors
import com.telltalevoice.app.ui.pleos.generated.PleosTypeScale
import com.telltalevoice.app.ui.pleos.generated.PleosTypeSpec
import com.telltalevoice.app.ui.pleos.generated.pleosDimensOf
import com.telltalevoice.app.ui.pleos.generated.pleosTypeOf

/**
 * Pleos 테마. 색과 치수, 글자는 generated/ 의 생성 토큰만 쓴다(손으로 적은 값 없음).
 * Material3 테마와 부품을 쓰지 않는다.
 */
@Immutable
class PleosTheme(
    val colors: PleosColors,
    val dimens: PleosDimens,
    val type: PleosTypeScale,
    val formFactor: PleosFormFactor,
    val dark: Boolean,
)

val LocalPleos = staticCompositionLocalOf<PleosTheme> { error("PleosTheme 없음") }

object Pleos {
    val theme: PleosTheme @Composable get() = LocalPleos.current
    val colors: PleosColors @Composable get() = LocalPleos.current.colors
    val dimens: PleosDimens @Composable get() = LocalPleos.current.dimens
    val type: PleosTypeScale @Composable get() = LocalPleos.current.type
    /** 낮은 창(connect-l, connect-w, waterfall). 앱 창 높이가 connect-s(1184)보다 낮아 카드 안쪽 여백과 줄 높이를 한 단계 작은 키트 값으로 쓴다. */
    val tight: Boolean @Composable get() = LocalPleos.current.formFactor != PleosFormFactor.ConnectS
    /** waterfall 창(1480 x 802). 글자와 부품 값이 connect-s 보다 커서 줄 높이를 한 단계 낮은 키트 값으로, 그림을 한 단계 작게 쓴다. */
    val dense: Boolean @Composable get() = LocalPleos.current.formFactor == PleosFormFactor.ConnectWaterfall
    /** 글자에 비해 좁은 창(connect-l, waterfall). 앱 창 폭이 시스템 팝업 Large 폭의 1.3배보다 작다. 두 칸 비율을 2:1로 바꾼다. */
    val narrow: Boolean @Composable get() = LocalPleos.current.dimens.let { it.windowAppsLandscapeWidth < it.popupSystemLargeWidth * 1.3f }
}

/**
 * 앱 창 크기로 피그마 폼팩터 모드를 고른다.
 * 기준은 원격 변수 window/apps/application_height(connect-s 1184, connect-l 855, connect-w 810)다.
 * 창 높이가 connect-s 앱 창 높이 이상이면 connect-s, connect-l 앱 창 높이 이상이면 connect-l, 그보다 낮으면 connect-w(값이 가장 작은 모드).
 * connect-waterfall은 창 크기로 가를 수 없는 전용 화면이라 고르지 않는다.
 */
fun formFactorFor(windowHeight: Dp): PleosFormFactor {
    val s = pleosDimensOf(PleosFormFactor.ConnectS).windowAppsApplicationHeight
    val l = pleosDimensOf(PleosFormFactor.ConnectL).windowAppsApplicationHeight
    return when {
        windowHeight >= s -> PleosFormFactor.ConnectS
        windowHeight >= l -> PleosFormFactor.ConnectL
        else -> PleosFormFactor.ConnectW
    }
}

@Composable
fun PleosTheme(formFactor: PleosFormFactor, dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val theme = PleosTheme(
        colors = if (dark) PleosDarkColors else PleosLightColors,
        dimens = pleosDimensOf(formFactor),
        type = pleosTypeOf(formFactor),
        formFactor = formFactor,
        dark = dark,
    )
    CompositionLocalProvider(LocalPleos provides theme, content = content)
}

/** Asta Sans 가변 글꼴(wght 300~800). 키트 Typography(1:903)가 쓰는 굵기 400, 700, 800을 축 값으로 고정한다. */
@OptIn(ExperimentalTextApi::class)
private fun astaFont(weight: Int) = Font(
    resId = R.font.asta_sans,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

@OptIn(ExperimentalTextApi::class)
val AstaSans: FontFamily = FontFamily(astaFont(400), astaFont(700), astaFont(800))

/**
 * 글자 단계 하나를 TextStyle로 바꾼다. 자간은 키트 표본 값 0%(1:903), 행간은 단계 값 그대로.
 * 줄 높이 안 글자는 가운데, 위아래 잘라 내지 않음(피그마 줄 상자와 같은 높이).
 * 한국어 줄바꿈은 구절 단위(제목 Heading, 본문 Paragraph).
 */
fun PleosTypeSpec.toStyle(weightOverride: Int? = null, heading: Boolean = false): TextStyle = TextStyle(
    fontFamily = AstaSans,
    fontWeight = FontWeight(weightOverride ?: weight),
    fontSize = sizeSp.sp,
    lineHeight = lineHeightSp.sp,
    letterSpacing = 0.sp,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    lineBreak = if (heading) LineBreak.Heading else LineBreak.Paragraph,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

/** API 33 아래는 구절 줄바꿈이 없어서 같은 규칙을 쓰되 결과가 다를 수 있다. */
val phraseLineBreakSupported: Boolean get() = Build.VERSION.SDK_INT >= 33

/** 그림자 흐림 값(피그마 blur, CSS와 같은 정의)을 안드로이드 setShadowLayer 반경으로 바꾼다. sigma = blur / 2, radius = (sigma - 0.5) / 0.57735 */
internal fun figmaBlurToShadowRadius(blurPx: Float): Float = ((blurPx / 2f - 0.5f) / 0.57735f).coerceAtLeast(0.1f)

