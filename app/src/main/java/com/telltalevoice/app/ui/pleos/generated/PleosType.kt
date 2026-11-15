// GENERATED FILE. 손으로 고치지 않는다.
// 생성: pleos-implementation/codegen/generate.mjs, 원본 피그마 GdPsTozIBUidTxFQOH0wXa(Pleos Design System), 글자: Typography 1:903 표본 노드와 typo, form_factor_common 글자 단계 변수
// 모든 값의 출처(변수 이름, 변수 id, 노드 id)는 codegen/ledger.json 에 있다.
package com.telltalevoice.app.ui.pleos.generated

import androidx.compose.runtime.Immutable

/** 글자 한 단계. 크기와 행간은 sp, 굵기는 Asta Sans 가변 축 wght. */
@Immutable
data class PleosTypeSpec(val sizeSp: Int, val lineHeightSp: Int, val weight: Int)

@Immutable
class PleosTypeScale(
    /** 노드 I1:913;7872:116466;7872:116289, 크기 size_sp/level18, 행간 line_height_sp/level19 */
    val headlineLarge: PleosTypeSpec,
    /** 노드 I1:914;7872:116466;7872:116289, 크기 size_sp/level17, 행간 line_height_sp/level17 */
    val headlineMedium: PleosTypeSpec,
    /** 노드 I1:915;7872:116466;7872:116289, 크기 size_sp/level15, 행간 line_height_sp/level15 */
    val headlineSmall: PleosTypeSpec,
    /** 노드 I1:924;7872:116466;7872:116289, 크기 size_sp/level13, 행간 connect-s 노드 비율로 산출 */
    val titleLarge: PleosTypeSpec,
    /** 노드 I1:925;7872:116466;7872:116289, 크기 size_sp/level11, 행간 font/line_height_sp/level10 */
    val titleMedium: PleosTypeSpec,
    /** 노드 I1:926;7872:116466;7872:116289, 크기 size_sp/level9, 행간 line_height_sp/level9 */
    val titleSmall: PleosTypeSpec,
    /** 노드 I1:935;7872:116466;7872:116289, 크기 size_sp/level8, 행간 line_height_sp/level8 */
    val bodyLarge: PleosTypeSpec,
    /** 노드 I1:936;7872:116466;7872:116289, 크기 size_sp/level7, 행간 line_height_sp/level7 */
    val bodyMedium: PleosTypeSpec,
    /** 노드 I1:937;7872:116466;7872:116289, 크기 size_sp/level6, 행간 line_height_sp/level6 */
    val bodySmall: PleosTypeSpec,
    /** 노드 I1:946;7872:116466;7872:116289, 크기 size_sp/level5, 행간 line_height_sp/level3 */
    val labelMedium: PleosTypeSpec,
    /** 노드 I1:947;7872:116466;7872:116289, 크기 size_sp/level3, 행간 line_height_sp/level1 */
    val labelSmall: PleosTypeSpec,
)

val PleosTypeConnectS = PleosTypeScale(
    headlineLarge = PleosTypeSpec(60, 80, 800),
    headlineMedium = PleosTypeSpec(56, 72, 800),
    headlineSmall = PleosTypeSpec(48, 64, 800),
    titleLarge = PleosTypeSpec(40, 52, 800),
    titleMedium = PleosTypeSpec(36, 44, 800),
    titleSmall = PleosTypeSpec(32, 40, 700),
    bodyLarge = PleosTypeSpec(30, 38, 700),
    bodyMedium = PleosTypeSpec(28, 36, 700),
    bodySmall = PleosTypeSpec(26, 34, 700),
    labelMedium = PleosTypeSpec(24, 28, 400),
    labelSmall = PleosTypeSpec(20, 24, 400),
)

val PleosTypeConnectL = PleosTypeScale(
    headlineLarge = PleosTypeSpec(53, 65, 800),
    headlineMedium = PleosTypeSpec(49, 65, 800),
    headlineSmall = PleosTypeSpec(42, 55, 800),
    titleLarge = PleosTypeSpec(35, 46, 800),
    titleMedium = PleosTypeSpec(32, 39, 800),
    titleSmall = PleosTypeSpec(28, 35, 700),
    bodyLarge = PleosTypeSpec(26, 33, 700),
    bodyMedium = PleosTypeSpec(25, 32, 700),
    bodySmall = PleosTypeSpec(23, 28, 700),
    labelMedium = PleosTypeSpec(21, 25, 400),
    labelSmall = PleosTypeSpec(18, 21, 400),
)

val PleosTypeConnectW = PleosTypeScale(
    headlineLarge = PleosTypeSpec(50, 66, 800),
    headlineMedium = PleosTypeSpec(46, 60, 800),
    headlineSmall = PleosTypeSpec(40, 53, 800),
    titleLarge = PleosTypeSpec(33, 43, 800),
    titleMedium = PleosTypeSpec(30, 37, 800),
    titleSmall = PleosTypeSpec(27, 33, 700),
    bodyLarge = PleosTypeSpec(25, 32, 700),
    bodyMedium = PleosTypeSpec(23, 30, 700),
    bodySmall = PleosTypeSpec(22, 28, 700),
    labelMedium = PleosTypeSpec(20, 23, 400),
    labelSmall = PleosTypeSpec(17, 20, 400),
)

val PleosTypeConnectWaterfall = PleosTypeScale(
    headlineLarge = PleosTypeSpec(64, 84, 800),
    headlineMedium = PleosTypeSpec(59, 77, 800),
    headlineSmall = PleosTypeSpec(51, 68, 800),
    titleLarge = PleosTypeSpec(42, 55, 800),
    titleMedium = PleosTypeSpec(38, 47, 800),
    titleSmall = PleosTypeSpec(35, 42, 700),
    bodyLarge = PleosTypeSpec(34, 41, 700),
    bodyMedium = PleosTypeSpec(29, 38, 700),
    bodySmall = PleosTypeSpec(28, 36, 700),
    labelMedium = PleosTypeSpec(26, 29, 400),
    labelSmall = PleosTypeSpec(22, 26, 400),
)

fun pleosTypeOf(f: PleosFormFactor): PleosTypeScale = when (f) {
    PleosFormFactor.ConnectS -> PleosTypeConnectS
    PleosFormFactor.ConnectL -> PleosTypeConnectL
    PleosFormFactor.ConnectW -> PleosTypeConnectW
    PleosFormFactor.ConnectWaterfall -> PleosTypeConnectWaterfall
}

/** 부품 글자 굵기(부품 노드 값). 설명 글은 Regular, 제목과 단추 글자는 Bold 또는 ExtraBold. */
object PleosComponentWeight {
    /** ivi_basic_button Label 1:6127 */
    const val BUTTON_LABEL = 700
    /** ivi_toast_popup 1:6987 */
    const val TOAST = 700
    /** system_popup/header Title 1:6967 */
    const val POPUP_TITLE = 800
    /** system_popup/header Description 1:6969 */
    const val POPUP_DESCRIPTION = 400
    /** ivi_notification_system Title 1:7338 */
    const val NOTIFICATION_TITLE = 800
    /** ivi_notification_system Description 1:7339 */
    const val NOTIFICATION_DESCRIPTION = 400
    /** dropdown_item Label 1:6685 */
    const val LIST_LABEL = 700
    /** ivi_title Depth Title 1:6915 */
    const val PAGE_TITLE = 700
    /** ivi_text_button Label 1:6283 */
    const val TEXT_BUTTON = 700
}
