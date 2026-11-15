package com.telltalevoice.app.ui.pleos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp

/**
 * 세로 배치. 요소 사이를 키트 간격(min) 이상으로 두고, 남는 높이는 요소 사이에 고르게 나눈다.
 * 판 안쪽 여백은 늘리지 않고 요소 사이만 넓힌다(남는 높이를 빈 구멍 하나로 두지 않음).
 */
fun spreadAtLeast(min: Dp): Arrangement.Vertical = object : Arrangement.Vertical {
    override val spacing: Dp = min
    override fun Density.arrange(totalSize: Int, sizes: IntArray, outPositions: IntArray) {
        val n = sizes.size
        if (n == 0) return
        val gap = min.roundToPx()
        val extra = (totalSize - sizes.sum() - gap * (n - 1)).coerceAtLeast(0)
        var y = 0
        for (i in 0 until n) {
            outPositions[i] = y
            y += sizes[i] + gap + if (n > 1) extra / (n - 1) else 0
        }
    }
}

/**
 * 주어진 높이를 채우는 세로 묶음. 내용이 높이보다 짧으면 요소 사이를 넓혀 채우고, 길면 스크롤한다.
 * 정차 뒤 화면처럼 스크롤이 허용된 곳에서만 쓴다(scroll = false 면 스크롤 없이 채우기만).
 */
@Composable
fun PleosFillColumn(modifier: Modifier = Modifier, minGap: Dp, scroll: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(modifier) {
        val base = Modifier.fillMaxWidth()
        Column(
            (if (scroll) base.verticalScroll(rememberScrollState()) else base).heightIn(min = maxHeight),
            verticalArrangement = spreadAtLeast(minGap),
            content = content,
        )
    }
}

/**
 * 세로 묶음. 마지막 요소가 남는 높이를 모두 받는다(최소 높이로). 내용이 받은 높이보다 길면 스크롤한다.
 * 정차 뒤 화면처럼 스크롤이 허용된 곳에서 카드가 창 아래 끝까지 닿게 할 때 쓴다.
 */
@Composable
fun PleosFillLastColumn(modifier: Modifier = Modifier, gap: Dp, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier) {
        val total = constraints.maxHeight
        androidx.compose.ui.layout.Layout(content, Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) { ms, c ->
            val g = gap.roundToPx()
            val loose = androidx.compose.ui.unit.Constraints(minWidth = c.maxWidth, maxWidth = c.maxWidth)
            val head = ms.dropLast(1).map { it.measure(loose) }
            val used = head.sumOf { it.height } + g * (ms.size - 1).coerceAtLeast(0)
            val last = ms.lastOrNull()?.measure(androidx.compose.ui.unit.Constraints(minWidth = c.maxWidth, maxWidth = c.maxWidth, minHeight = (total - used).coerceAtLeast(0)))
            val all = head + listOfNotNull(last)
            val h = maxOf(total, used + (last?.height ?: 0))
            layout(c.maxWidth, h) {
                var y = 0
                all.forEach { p -> p.place(0, y); y += p.height + g }
            }
        }
    }
}
