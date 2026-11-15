package com.telltalevoice.app.ui.pleos

import android.graphics.Paint
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.telltalevoice.app.R
import com.telltalevoice.app.ui.pleos.generated.PleosComponentWeight
import com.telltalevoice.app.ui.pleos.generated.PleosShadowLayer
import com.telltalevoice.app.ui.pleos.generated.PleosShadows
import com.telltalevoice.app.ui.pleos.generated.PleosTypeSpec

// Pleos 공식 부품(원본 GdPsTozIBUidTxFQOH0wXa)을 Compose로 옮긴 부품. 노드 id는 각 부품 주석에 있다.
// 눌림은 키트의 Pressed 변형 색으로만 보이고 물결, 크기 변화, 애니메이션은 없다(주행 중 움직임 금지).

// ---------- 기초 ----------

/**
 * 높이 단계 그림자. 층마다 피그마 DROP_SHADOW(흐림, y, 색)를 setShadowLayer로 그린다.
 * 반투명 바탕(다크 모드 dropdown/normal 10% 등) 뒤로 그림자용 면이 비치지 않게 부품 모양 안쪽은 잘라 내고 바깥 그림자만 남긴다.
 */
fun Modifier.pleosShadow(layers: List<PleosShadowLayer>, radius: Dp, surface: Color): Modifier = drawBehind {
    val r = radius.toPx()
    val shape = android.graphics.Path().apply { addRoundRect(0f, 0f, size.width, size.height, r, r, android.graphics.Path.Direction.CW) }
    drawIntoCanvas { canvas ->
        val nc = canvas.nativeCanvas
        nc.save()
        nc.clipOutPath(shape)
        layers.forEach { l ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = surface.copy(alpha = 1f).toArgb()
                setShadowLayer(figmaBlurToShadowRadius(l.blurDp.dp2px(density)), 0f, l.yDp.dp2px(density), l.color.toArgb())
            }
            nc.drawPath(shape, paint)
        }
        nc.restore()
    }
}

private fun Int.dp2px(density: Float) = this * density

enum class PleosIcon(val res: Int) {
    /** ic_annotation Type=alert, system_popup/header 1:6958 */
    Alert(R.drawable.pleos_ic_annotation),
    /** ic_arrow Direction=left, ivi_title Depth 1:6912 */
    ArrowLeft(R.drawable.pleos_ic_arrow_left),
    /** ic_check Type=basic, dropdown_item Selected 1:6686 */
    Check(R.drawable.pleos_ic_check),
}

@Composable
fun PleosIconImage(icon: PleosIcon, size: Dp, tint: Color, modifier: Modifier = Modifier, description: String? = null) {
    Image(painterResource(icon.res), contentDescription = description, modifier = modifier.size(size), colorFilter = ColorFilter.tint(tint))
}

// ---------- ivi_basic_button 1:6124 ----------

enum class PleosButtonSize { Large, Medium, Small }

/** Primary(옅은 바탕 1:6125)와 Secondary(채운 바탕 1:6161). Call 은 ivi_notification_system Type=Call(1:7347)의 색 덮어쓰기 방식. */
sealed interface PleosButtonType {
    data object Primary : PleosButtonType
    data object Secondary : PleosButtonType
    /** 키트 Call 알림처럼 바탕을 의미 색으로 덮어쓴 버튼. 눌림은 바탕 위에 button/basic/pressed 를 덧칠한다. */
    data class Tinted(val container: Color, val content: Color) : PleosButtonType
}

@Composable
fun PleosBasicButton(
    label: String,
    type: PleosButtonType,
    size: PleosButtonSize,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Pleos.colors
    val d = Pleos.dimens
    val t = Pleos.type
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val height = when (size) { PleosButtonSize.Large -> d.buttonsBasicLargeHeight; PleosButtonSize.Medium -> d.buttonsBasicMediumHeight; PleosButtonSize.Small -> d.buttonsBasicSmallHeight }
    val minWidth = when (size) { PleosButtonSize.Large -> d.buttonsBasicLargeMinWidth; PleosButtonSize.Medium -> d.buttonsBasicMediumMinWidth; PleosButtonSize.Small -> d.buttonsBasicSmallMinWidth }
    val padH = when (size) { PleosButtonSize.Large -> d.buttonsBasicLargePaddingHorizontal; PleosButtonSize.Medium -> d.buttonsBasicMediumPaddingHorizontal; PleosButtonSize.Small -> d.buttonsBasicSmallPaddingHorizontal }
    val padV = when (size) { PleosButtonSize.Large -> d.buttonsBasicLargePaddingVertical; PleosButtonSize.Medium -> d.buttonsBasicMediumPaddingVertical; PleosButtonSize.Small -> d.buttonsBasicSmallPaddingVertical }
    val radius = when (size) { PleosButtonSize.Large -> d.buttonsBasicLargeRadiusTopLeft; PleosButtonSize.Medium -> d.buttonsBasicMediumRadiusTopLeftMedium; PleosButtonSize.Small -> d.buttonsBasicSmallRadiusBottomRight }
    // 글자: Large 1:6127(titleSmall 32/40), Medium 1:6139(bodyLarge 30/38), Small 1:6151(bodySmall 26/34)
    val spec = when (size) { PleosButtonSize.Large -> t.titleSmall; PleosButtonSize.Medium -> t.bodyLarge; PleosButtonSize.Small -> t.bodySmall }
    val (container, overlay, content) = when (type) {
        PleosButtonType.Primary -> Triple(if (pressed) c.buttonBasicPressed else c.buttonBasicEnabled, Color.Transparent, c.textDarkPrimary)
        PleosButtonType.Secondary -> Triple(if (pressed) c.buttonFilledPressed else c.buttonFilledEnabled, Color.Transparent, c.textLightPrimary)
        is PleosButtonType.Tinted -> Triple(type.container, if (pressed) c.buttonBasicPressed else Color.Transparent, type.content)
    }
    val shape = RoundedCornerShape(radius)
    Box(
        modifier
            .heightIn(min = height)
            .widthIn(min = minWidth)
            .clip(shape)
            .background(container)
            .background(overlay)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = padH, vertical = padV),
        contentAlignment = Alignment.Center,
    ) {
        PleosText(label, spec, content, weight = PleosComponentWeight.BUTTON_LABEL, align = TextAlign.Center, heading = true)
    }
}

// ---------- ivi_title 1:6891 (Type=Main 1:6892, Type=Depth 1:6910) ----------

/** 쪽 제목. onBack 이 있으면 Depth 변형(뒤로 화살표 1:6912 + 제목 1:6915), 없으면 Main 변형 높이와 여백에 제목만. */
@Composable
fun PleosPageTitle(title: String, modifier: Modifier = Modifier, onBack: (() -> Unit)? = null, suffix: @Composable RowScope.() -> Unit = {}) {
    val c = Pleos.colors
    val d = Pleos.dimens
    Row(
        modifier.fillMaxWidth().heightIn(min = d.pageNavigationContainerHeight)
            .padding(start = d.pageNavigationPaddingExtralarge, end = d.pageNavigationPaddingLarge),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(d.pageNavigationTitleDepthGapHorizontal),
    ) {
        val titleModifier = Modifier.weight(1f)
        if (onBack != null) {
            Row(
                titleModifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onBack),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(d.pageNavigationTitleDepthGapHorizontal),
            ) {
                PleosIconImage(PleosIcon.ArrowLeft, d.pageNavigationTitleDepthIconSquare, c.iconDarkPrimary)
                PleosText(title, Pleos.type.titleLarge, c.textDarkPrimary, weight = PleosComponentWeight.PAGE_TITLE, heading = true, maxLines = 2)
            }
        } else {
            PleosText(title, Pleos.type.titleLarge, c.textDarkPrimary, titleModifier, weight = PleosComponentWeight.PAGE_TITLE, heading = true, maxLines = 2)
        }
        suffix()
    }
}

/** 미리 듣기 화면 위쪽 닫기. ivi_title Type=Depth(1:6910)의 뒤로 화살표와 제목 자리에 "닫기". */
@Composable
fun PleosCloseBar(onClose: () -> Unit, modifier: Modifier = Modifier, content: Color = Pleos.colors.textDarkPrimary, suffix: @Composable RowScope.() -> Unit = {}) {
    val d = Pleos.dimens
    Row(
        modifier.fillMaxWidth().heightIn(min = d.pageNavigationContainerHeight)
            .padding(start = d.pageNavigationPaddingExtralarge, end = d.pageNavigationPaddingLarge),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(d.pageNavigationTitleDepthGapHorizontal),
    ) {
        Row(
            Modifier.weight(1f).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClose),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(d.pageNavigationTitleDepthGapHorizontal),
        ) {
            PleosIconImage(PleosIcon.ArrowLeft, d.pageNavigationTitleDepthIconSquare, content)
            PleosText("닫기", Pleos.type.titleLarge, content, weight = PleosComponentWeight.PAGE_TITLE, maxLines = 1)
        }
        suffix()
    }
}

// ---------- ivi_system_popup 1:6933 (Size=Large 1:6938, Size=Medium 1:6934) ----------

/**
 * 시스템 팝업. 머리(system_popup/header 1:6962): 알림 아이콘 80, 제목 titleLarge ExtraBold 가운데, 설명 bodyLarge Regular 가운데.
 * 버튼 줄(system_popup/buttons 1:6970): Large 버튼, 사이 24. 원본 테두리(2px, 5%)는 화면 규칙에 따라 빼고 3단계 그림자로 띄운다.
 */
@Composable
fun PleosSystemPopup(
    title: String,
    description: List<String>,
    modifier: Modifier = Modifier,
    stackButtons: Boolean = false,
    showIcon: Boolean = true,
    compact: Boolean = false,
    actions: List<PleosPopupAction>,
) {
    val c = Pleos.colors
    val d = Pleos.dimens
    val shape = RoundedCornerShape(d.popupSystemRadiusTopLeft)
    Column(
        modifier
            .pleosShadow(PleosShadows.level3(Pleos.theme.formFactor), d.popupSystemRadiusTopLeft, c.backgroundPopup)
            .clip(shape)
            .background(c.backgroundPopup)
            // 좁은 창(키트 최소 세로 창 폭 window/apps/portrait_width connect-l 628 미만)은 좌우 여백을 popup/system/gap_large 로 줄인다
            .padding(horizontal = if (compact) d.popupSystemGapLarge else d.popupSystemPaddingLarge, vertical = d.popupSystemPaddingLarge),
        verticalArrangement = Arrangement.spacedBy(d.popupSystemGapExtralarge),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = if (compact) d.popupSystemGapExtrasmall else d.popupSystemHeaderPaddingHorizontal),
            verticalArrangement = Arrangement.spacedBy(d.popupSystemGapLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showIcon) PleosIconImage(PleosIcon.Alert, d.popupSystemIconSquare, c.iconDarkSecondary)
            PleosText(title, Pleos.type.titleLarge, c.textDarkPrimary, Modifier.fillMaxWidth(), weight = PleosComponentWeight.POPUP_TITLE, align = TextAlign.Center, heading = true)
            description.forEach {
                PleosText(it, Pleos.type.bodyLarge, c.textDarkSecondary, Modifier.fillMaxWidth(), weight = PleosComponentWeight.POPUP_DESCRIPTION, align = TextAlign.Center, heading = true)
            }
        }
        if (stackButtons) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(d.popupSystemGapLarge)) {
                actions.forEach { PleosBasicButton(it.label, it.type, PleosButtonSize.Large, it.onClick, Modifier.fillMaxWidth()) }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(d.popupSystemGapLarge)) {
                actions.forEach { PleosBasicButton(it.label, it.type, PleosButtonSize.Large, it.onClick, Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * 경고 카드. 현대차 경고 알림 카드(왼쪽 경고 아이콘, 굵은 제목, 부연 한 줄, 버튼) 구조를 키트 부품 값으로 만든 조합.
 * 바깥: ivi_system_popup Size=Large(1:6938) 폭 large_width, 안쪽 여백 padding_large, 모서리 48, 바탕 background/popup, 그림자 3단계(1:7327).
 * 머리: ivi_notification_system Header(1:7335) 배치(가로, 왼쪽 그림 칸, 오른쪽 text_group 1:7337)에 system_popup/header 값
 *       (아이콘 popup/system/icon_square, 제목 titleLarge ExtraBold 1:6967, 설명 bodyLarge Regular 1:6969).
 * 버튼: system_popup/buttons(1:6970) Large 버튼, 사이 gap_large. 원본 외곽선은 화면 규칙에 따라 뺀다.
 * 키트에 연한 붉은 면 색과 삼각형 경고 아이콘이 없어서 바탕은 background/popup, 아이콘은 ic_annotation alert(1:6958)을 등급 색으로 칠한다.
 */
@Composable
fun PleosAlertCard(
    title: String,
    description: List<String>,
    accent: Color,
    titleColor: Color,
    modifier: Modifier = Modifier,
    stackButtons: Boolean = false,
    fill: Boolean = false,
    actions: List<PleosPopupAction>,
) {
    val c = Pleos.colors
    val d = Pleos.dimens
    // fill: 카드가 받은 높이(popup/system/max_height 이하)를 채운다. 남는 높이는 제목 묶음, 설명, 버튼 줄 사이로 나눈다.
    Column(
        modifier
            .then(if (fill) Modifier.heightIn(max = d.popupSystemMaxHeight).fillMaxHeight() else Modifier)
            .pleosShadow(PleosShadows.level3(Pleos.theme.formFactor), d.popupSystemRadiusTopLeft, c.backgroundPopup)
            .clip(RoundedCornerShape(d.popupSystemRadiusTopLeft))
            .background(c.backgroundPopup)
            .padding(d.popupSystemPaddingLarge),
        verticalArrangement = if (fill) spreadAtLeast(d.popupSystemGapExtralarge) else Arrangement.spacedBy(d.popupSystemGapExtralarge),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(d.notificationGapLarge), verticalAlignment = Alignment.CenterVertically) {
            PleosIconImage(PleosIcon.Alert, d.popupSystemIconSquare, accent)
            // fill(넓은 창): 키트 Headline 단계(Typography 1:913 headlineMedium 56/72 ExtraBold)로 키워 카드 높이를 글자로 채운다. 기본은 팝업 제목 titleLarge(1:6967)
            PleosText(title, if (fill) Pleos.type.headlineMedium else Pleos.type.titleLarge, titleColor, Modifier.weight(1f), weight = PleosComponentWeight.POPUP_TITLE, heading = true)
        }
        if (description.isNotEmpty()) Column(
            Modifier.padding(start = d.popupSystemIconSquare + d.notificationGapLarge),
            verticalArrangement = Arrangement.spacedBy(d.notificationSystemTextGapVertical),
        ) {
            // fill: 설명은 Title 단계 titleMedium(1:925, 36/44), 기본은 bodyLarge(1:6969)
            description.forEach { PleosText(it, if (fill) Pleos.type.titleMedium else Pleos.type.bodyLarge, c.textDarkSecondary, weight = PleosComponentWeight.POPUP_DESCRIPTION) }
        }
        if (stackButtons) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(d.popupSystemGapLarge)) {
                actions.forEach { PleosBasicButton(it.label, it.type, PleosButtonSize.Large, it.onClick, Modifier.fillMaxWidth()) }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(d.popupSystemGapLarge)) {
                actions.forEach { PleosBasicButton(it.label, it.type, PleosButtonSize.Large, it.onClick, Modifier.weight(1f)) }
            }
        }
    }
}

/** 팝업 버튼 하나(system_popup/buttons 1:6970 의 ivi_basic_button Large). */
data class PleosPopupAction(val label: String, val type: PleosButtonType, val onClick: () -> Unit)

// ---------- dropdown_item 1:6678 (Size=Medium 1:6679, Size=Small 1:6716) ----------

/** 목록 한 줄. Selected 는 button/basic/enabled 바탕과 체크 아이콘, 눌림은 dropdown/pressed 바탕. */
@Composable
fun PleosListItem(label: String, selected: Boolean, small: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, trailingAction: String? = null) {
    val c = Pleos.colors
    val d = Pleos.dimens
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val radius = if (small) d.dropdownSmallRadiusTopLeft else d.dropdownMediumRadiusTopLeft
    val bg = when { pressed -> c.dropdownPressed; selected -> c.buttonBasicEnabled; else -> Color.Transparent }
    Row(
        modifier.fillMaxWidth()
            .heightIn(min = if (small) d.dropdownSmallHeight else d.dropdownMediumHeight)
            .clip(RoundedCornerShape(radius))
            .background(bg)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (small) d.dropdownPaddingMedium else d.dropdownPaddingLarge),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Label 프레임 1:6684 좌우 12
        PleosText(label, Pleos.type.bodyLarge, c.textDarkPrimary, Modifier.weight(1f).padding(horizontal = d.dropdownPaddingSmall),
            weight = PleosComponentWeight.LIST_LABEL, heading = true)
        if (selected) PleosIconImage(PleosIcon.Check, d.dropdownSuffixIconSquare, c.iconDarkPrimary)
        // 줄이 눌리는 목록임을 알리는 글자. ivi_text_button Medium(1:6281) 글자 모양과 색, 따로 눌리는 버튼은 아님
        if (trailingAction != null) PleosText(trailingAction, Pleos.type.bodyLarge, c.informativePositive, Modifier.padding(horizontal = d.dropdownPaddingSmall),
            weight = PleosComponentWeight.TEXT_BUTTON, maxLines = 1)
    }
}

// ---------- vehicle_setting_popup_content/List/item I1:6936;1:6978;26339:5633 ----------

/**
 * 글 판 안 한 줄. 원본 줄 높이 vehicle_settings/list/item/height(112), 안쪽 여백 위아래 16 좌우 20, 모서리 24.
 * 윗줄 이름 bodyLarge Bold(원본 Label 1:26339:5594 30/38) 한 줄, 아랫줄 설명 bodySmall Regular 한 줄.
 * onClick 이 있으면 줄 전체가 눌리고 눌림은 dropdown/pressed 바탕.
 */
@Composable
fun PleosListRow(title: String, detail: String?, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, large: Boolean = false, trailingAction: String? = null) {
    val c = Pleos.colors
    val d = Pleos.dimens
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Column(
        modifier.fillMaxWidth()
            .heightIn(min = d.vehicleSettingsListItemHeight)
            .clip(RoundedCornerShape(d.vehicleSettingsListItemContainerRadiusTopLeft))
            .background(if (pressed) c.dropdownPressed else Color.Transparent)
            .then(if (onClick != null) Modifier.clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = d.vehicleSettingsListItemContainerPaddingHorizontal, vertical = d.vehicleSettingsListItemContainerPaddingVertical),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // large: 넓은 창의 홈 목록. 이름 titleSmall(32/40), 설명 bodyLarge(30/38). 기본은 이름 bodyLarge, 설명 bodySmall.
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(d.popupSystemGapExtrasmall)) {
                PleosText(title, if (large) Pleos.type.titleSmall else Pleos.type.bodyLarge, c.textDarkPrimary, weight = PleosComponentWeight.LIST_LABEL, maxLines = 1)
                if (detail != null) PleosText(detail, if (large) Pleos.type.bodyLarge else Pleos.type.bodySmall, c.textDarkTertiary,
                    weight = PleosComponentWeight.NOTIFICATION_DESCRIPTION, maxLines = 2)
            }
            // 줄이 눌리는 목록임을 알리는 글자. ivi_text_button Medium(1:6281) 글자 모양과 색
            if (trailingAction != null) PleosText(trailingAction, Pleos.type.bodyLarge, c.informativePositive, Modifier.padding(start = d.dropdownPaddingSmall),
                weight = PleosComponentWeight.TEXT_BUTTON, maxLines = 1)
        }
    }
}

// ---------- 목록 판 ivi_dropdown_menus 1:6614 (Size=Medium 1:6615, Size=Small 1:6622) ----------

/** dropdown_item 줄을 담는 판. 바탕 dropdown/normal, 모서리 dropdown/medium(32)과 small(28), 안쪽 여백과 줄 사이 dropdown/padding_medium(16), 그림자 1단계(1:6615 효과 24/6/0.08). 원본 2px 외곽선은 화면 규칙에 따라 뺀다. */
@Composable
fun PleosMenu(small: Boolean, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = Pleos.colors
    val d = Pleos.dimens
    val r = if (small) d.dropdownSmallRadiusTopLeft else d.dropdownMediumRadiusTopLeft
    Column(
        modifier
            .pleosShadow(PleosShadows.level1(Pleos.theme.formFactor), r, c.dropdownNormal)
            .clip(RoundedCornerShape(r))
            .background(c.dropdownNormal)
            .padding(d.dropdownPaddingMedium),
        verticalArrangement = Arrangement.spacedBy(d.dropdownGapVerticalMedium),
        content = content,
    )
}

// ---------- 글 판 vehicle_setting_popup_content/List I1:6936;1:6978 ----------

/** 글을 담는 판. 원본 그대로 모서리 vehicle_settings/list(32), 위아래 24, 좌우 20, 바탕 #131417 5%(button/basic/enabled 와 같은 값), 그림자 없음. */
@Composable
fun PleosPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = Pleos.colors
    val d = Pleos.dimens
    Column(
        modifier
            .clip(RoundedCornerShape(d.vehicleSettingsListRadiusTopLeft))
            .background(c.buttonBasicEnabled)
            .padding(horizontal = d.vehicleSettingsListPaddingHorizontal, vertical = d.vehicleSettingsListPaddingVertical),
        content = content,
    )
}

// ---------- ivi_toast_popup 1:6983 (Margin(Default)=on 1:6988) ----------

@Composable
fun PleosToast(message: String, modifier: Modifier = Modifier) {
    val c = Pleos.colors
    val d = Pleos.dimens
    Box(Modifier.padding(start = d.popupToastMarginHorizontal, end = d.popupToastMarginHorizontal, bottom = d.popupToastMarginBottom).then(modifier)) {
        Box(
            Modifier.widthIn(max = d.popupToastMaxWidth)
                .pleosShadow(PleosShadows.level2(Pleos.theme.formFactor), d.popupToastRadiusTopLeft, c.buttonFilledEnabled)
                .clip(RoundedCornerShape(d.popupToastRadiusTopLeft))
                .background(c.buttonFilledEnabled)
                .padding(horizontal = d.popupToastPaddingHorizontal, vertical = d.popupToastPaddingVertical),
        ) {
            PleosText(message, Pleos.type.bodySmall, c.textLightPrimary, weight = PleosComponentWeight.TOAST, align = TextAlign.Center, heading = true)
        }
    }
}

/** 판 안 소제목과 본문 묶음(ivi_notification_system text_group 1:7337: 제목과 설명 사이 12). */
@Composable
fun PleosTextGroup(title: String, lines: List<String>, modifier: Modifier = Modifier) {
    val c = Pleos.colors
    val d = Pleos.dimens
    Column(modifier, verticalArrangement = Arrangement.spacedBy(d.notificationSystemTextGapVertical)) {
        PleosText(title, Pleos.type.titleSmall, c.textDarkPrimary, weight = PleosComponentWeight.BUTTON_LABEL, heading = true)
        lines.forEach { PleosText(it, Pleos.type.bodySmall, c.textDarkSecondary, weight = PleosComponentWeight.NOTIFICATION_DESCRIPTION) }
    }
}

@Composable
fun VGap(h: Dp) = Box(Modifier.height(h))
