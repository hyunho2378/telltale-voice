// GENERATED FILE. 손으로 고치지 않는다.
// 생성: pleos-implementation/codegen/generate.mjs, 원본 피그마 GdPsTozIBUidTxFQOH0wXa(Pleos Design System), 그림자: typo 묶음 shadow 변수(흐림, y)와 노드 효과 색
// 모든 값의 출처(변수 이름, 변수 id, 노드 id)는 codegen/ledger.json 에 있다.
package com.telltalevoice.app.ui.pleos.generated

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class PleosShadowLayer(val blurDp: Int, val yDp: Int, val color: Color)

/** 높이 단계. 1 앱 창(1:28084), 2 앱 아이콘(1:6895), 3 시스템 알림(1:7327). */
object PleosShadows {
    /** 1단계: 앱 창 application 1:28084. shadow/level1/blur, shadow/level1/y */
    fun level1(f: PleosFormFactor): List<PleosShadowLayer> = when (f.ordinal) {
        0 -> listOf(PleosShadowLayer(24, 6, Color(0x14000000)))
        1 -> listOf(PleosShadowLayer(21, 5, Color(0x14000000)))
        2 -> listOf(PleosShadowLayer(20, 5, Color(0x14000000)))
        3 -> listOf(PleosShadowLayer(26, 6, Color(0x14000000)))
        else -> error("모드 없음")
    }
    /** 2단계: ivi_title 앱 아이콘 1:6895. shadow/level2/blur1, shadow/level2/y1 / shadow/level2/blur2, shadow/level2/y2 */
    fun level2(f: PleosFormFactor): List<PleosShadowLayer> = when (f.ordinal) {
        0 -> listOf(PleosShadowLayer(32, 8, Color(0x29000000)), PleosShadowLayer(2, 2, Color(0x14000000)))
        1 -> listOf(PleosShadowLayer(28, 7, Color(0x29000000)), PleosShadowLayer(2, 2, Color(0x14000000)))
        2 -> listOf(PleosShadowLayer(27, 7, Color(0x29000000)), PleosShadowLayer(2, 2, Color(0x14000000)))
        3 -> listOf(PleosShadowLayer(32, 9, Color(0x29000000)), PleosShadowLayer(3, 3, Color(0x14000000)))
        else -> error("모드 없음")
    }
    /** 3단계: ivi_notification_system 1:7327. shadow/level3/blur1, shadow/level3/y1 / shadow/level3/blur2, shadow/level3/y2 */
    fun level3(f: PleosFormFactor): List<PleosShadowLayer> = when (f.ordinal) {
        0 -> listOf(PleosShadowLayer(40, 20, Color(0x3D000000)), PleosShadowLayer(4, 2, Color(0x33000000)))
        1 -> listOf(PleosShadowLayer(35, 18, Color(0x3D000000)), PleosShadowLayer(4, 2, Color(0x33000000)))
        2 -> listOf(PleosShadowLayer(33, 17, Color(0x3D000000)), PleosShadowLayer(3, 2, Color(0x33000000)))
        3 -> listOf(PleosShadowLayer(42, 22, Color(0x3D000000)), PleosShadowLayer(4, 3, Color(0x33000000)))
        else -> error("모드 없음")
    }
}
