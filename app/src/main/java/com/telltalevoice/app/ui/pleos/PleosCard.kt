package com.telltalevoice.app.ui.pleos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import com.telltalevoice.app.ui.pleos.generated.PleosComponentWeight
import com.telltalevoice.app.ui.pleos.generated.PleosShadows

/**
 * 카드. 앱 창(1:28084) 위에 띄운 면 하나.
 * 바탕 surface/basic, 모서리 vehicle_settings/list(32), 안쪽 여백 notification/padding_large(32), 그림자 1단계(1:28084 효과).
 * face 가 있으면 바탕 위에 심각도 옅은 면(Tone.face)을 한 번 더 깐다(현대차 제안 카드의 연한 붉은 면 자리).
 * 테두리는 쓰지 않는다. onClick 이 있으면 카드 전체가 눌리고 눌림은 dropdown/pressed 를 덧칠한다.
 */
@Composable
fun PleosCard(
    modifier: Modifier = Modifier,
    face: Color? = null,
    onClick: (() -> Unit)? = null,
    padding: Dp = Pleos.dimens.notificationPaddingLarge,
    gap: Dp = Pleos.dimens.notificationGapMedium,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(gap),
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Pleos.colors
    val d = Pleos.dimens
    val r = d.vehicleSettingsListRadiusTopLeft
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Column(
        modifier
            .pleosShadow(PleosShadows.level1(Pleos.theme.formFactor), r, c.surfaceBasic)
            .clip(RoundedCornerShape(r))
            .background(c.surfaceBasic)
            .then(if (face != null) Modifier.background(face) else Modifier)
            .then(if (pressed) Modifier.background(c.dropdownPressed) else Modifier)
            .then(if (onClick != null) Modifier.clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick) else Modifier)
            .padding(padding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

/** 카드 머리 줄: 제목 titleSmall Bold(32/40)와 오른쪽 덧붙임. */
@Composable
fun PleosCardTitle(title: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Pleos.dimens.notificationGapMedium)) {
        PleosText(title, Pleos.type.titleSmall, Pleos.colors.textDarkPrimary, Modifier.weight(1f), weight = PleosComponentWeight.BUTTON_LABEL, heading = true, maxLines = 1)
        trailing()
    }
}

/** 판정 근거 한 칸: 그림, 이름표(bodySmall Regular), 값(titleMedium ExtraBold). */
data class BasisCell(val glyph: Glyph, val label: String, val value: String)

/**
 * 판정 근거 줄. 칸 3개를 같은 폭으로 놓는다. 칸 바탕은 button/basic/enabled(5%), 모서리 vehicle_settings/list/item(24),
 * 안쪽 여백 vehicle_settings/list/item 위아래 16 좌우 20, 칸 사이 notification/gap_medium(16).
 * 아래 한 문장(bodyLarge Regular)은 같은 경고등이 상황에 따라 다른 안내가 되는 이유를 말한다.
 */
@Composable
fun PleosBasisRow(cells: List<BasisCell>, sentence: String?, tone: Tone, modifier: Modifier = Modifier, compactValue: Boolean = false, stacked: Boolean = false) {
    val c = Pleos.colors
    val d = Pleos.dimens
    Column(modifier, verticalArrangement = Arrangement.spacedBy(d.notificationGapMedium)) {
        val cellBox: @Composable (Modifier, BasisCell) -> Unit = { m, cell ->
                Row(
                    m
                        .heightIn(min = if (stacked) d.buttonsBasicSmallHeight else d.vehicleSettingsListItemHeight)
                        .clip(RoundedCornerShape(d.vehicleSettingsListItemContainerRadiusTopLeft))
                        .background(c.buttonBasicEnabled)
                        .padding(horizontal = d.vehicleSettingsListItemContainerPaddingHorizontal, vertical = d.vehicleSettingsListItemContainerPaddingVertical),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(d.dropdownPaddingSmall),
                ) {
                    PleosGlyph(cell.glyph, tone, GlyphSize.Small, solid = false)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(d.popupSystemGapExtrasmall)) {
                        PleosText(cell.label, Pleos.type.bodySmall, c.textDarkTertiary, weight = PleosComponentWeight.NOTIFICATION_DESCRIPTION, maxLines = 1)
                        PleosText(cell.value, if (compactValue) Pleos.type.bodyLarge else Pleos.type.titleSmall, c.textDarkPrimary, weight = PleosComponentWeight.LIST_LABEL, maxLines = 1)
                    }
                }
        }
        // stacked: 좁은 칸에서는 칸 3개를 위아래로 쌓는다
        if (stacked) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(d.dropdownGapVerticalSmall)) { cells.forEach { cellBox(Modifier.fillMaxWidth(), it) } }
        else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(d.notificationGapMedium)) { cells.forEach { cellBox(Modifier.weight(1f), it) } }
        if (sentence != null) PleosText(sentence, Pleos.type.bodyLarge, c.textDarkSecondary, Modifier.fillMaxWidth(), weight = PleosComponentWeight.POPUP_DESCRIPTION)
    }
}

/**
 * 카드 안 한 줄(vehicle_setting List/item 26339:5633 값): 높이 112 이상, 좌우 20 위아래 16, 모서리 24.
 * 왼쪽 그림, 가운데 이름(bodyLarge Bold)과 부연(bodySmall Regular), 오른쪽 덧붙임(버튼이나 상태 글자).
 */
@Composable
fun PleosCardRow(
    title: String,
    detail: String?,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    selected: Boolean = false,
    minHeight: Dp = Pleos.dimens.vehicleSettingsListItemHeight,
    onClick: (() -> Unit)? = null,
    titleSpec: com.telltalevoice.app.ui.pleos.generated.PleosTypeSpec = Pleos.type.bodyLarge,
    titleMaxLines: Int = 1,
    titleWeight: Int = PleosComponentWeight.LIST_LABEL,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val c = Pleos.colors
    val d = Pleos.dimens
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Row(
        modifier.fillMaxWidth()
            .heightIn(min = minHeight)
            .clip(RoundedCornerShape(d.vehicleSettingsListItemContainerRadiusTopLeft))
            .background(when { pressed -> c.dropdownPressed; selected -> c.buttonBasicEnabled; else -> Color.Transparent })
            .then(if (onClick != null) Modifier.clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = d.vehicleSettingsListItemContainerPaddingHorizontal, vertical = d.vehicleSettingsListItemContainerPaddingVertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(d.notificationGapMedium),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(d.popupSystemGapExtrasmall)) {
            PleosText(title, titleSpec, c.textDarkPrimary, weight = titleWeight, maxLines = titleMaxLines)
            if (detail != null) PleosText(detail, Pleos.type.bodySmall, c.textDarkTertiary, weight = PleosComponentWeight.NOTIFICATION_DESCRIPTION, maxLines = 2)
        }
        trailing()
    }
}

/** 단계 버튼 줄(설정 화면 0 1 2 3 단계 버튼 모양, 참고 video/toybro_0841). 고른 칸은 Secondary(채운 면), 나머지 Primary. ivi_basic_button Small. */
@Composable
fun PleosSegment(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, small: Boolean = false) {
    // small: Small 버튼, 칸 폭은 글자 수에 비례(긴 이름이 두 줄로 꺾이지 않게)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Pleos.dimens.buttonsBasicMediumGapHorizontal)) {
        labels.forEachIndexed { i, l ->
            PleosBasicButton(l, if (i == selected) PleosButtonType.Secondary else PleosButtonType.Primary, if (small) PleosButtonSize.Small else PleosButtonSize.Medium, { onSelect(i) },
                Modifier.weight(if (small) (l.length + 2).toFloat() else 1f))
        }
    }
}
