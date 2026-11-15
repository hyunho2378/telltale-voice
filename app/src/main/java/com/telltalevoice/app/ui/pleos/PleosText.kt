package com.telltalevoice.app.ui.pleos

import android.util.Log
import androidx.compose.foundation.text.BasicText
import com.telltalevoice.app.BuildConfig
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import com.telltalevoice.app.ui.pleos.generated.PleosTypeSpec

/**
 * Pleos 글자. 모든 화면 글자가 이 부품 하나로 그려진다.
 * 1. 어절 안에서 줄이 끊기지 않는다(keepAll).
 * 2. 여러 줄이면 줄 길이를 고르게 맞춘다(balancedWidth). 줄 수는 그대로 두고, 같은 줄 수가 나오는 폭 후보 33개 가운데
 *    마지막 줄 어절 2개 이상(전체 어절이 2개 이상일 때)을 지키면서 가장 짧은 줄과 가장 긴 줄의 비율이 가장 큰 폭을 고른다.
 *    비율 60% 이상이고 첫 줄이 가장 긴 후보를 먼저 고르고, 비슷하면 쉼표와 연결 어미, 조사 같은 구절 경계에서 끊기는 폭을 고른다.
 */
@Composable
fun PleosText(
    text: String,
    spec: PleosTypeSpec,
    color: Color,
    modifier: Modifier = Modifier,
    weight: Int? = null,
    align: TextAlign = TextAlign.Start,
    heading: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
) {
    val shown = remember(text) { keepAll(text) }
    val style = spec.toStyle(weight, heading).copy(color = color, textAlign = align)
    val measurer = rememberTextMeasurer(cacheSize = 32)
    Layout(
        content = {
            BasicText(
                text = shown,
                style = style,
                maxLines = maxLines,
                overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
                // 디버그 빌드: 잘린 글(말줄임)을 로그로 남겨 화면 점검에서 센다
                onTextLayout = { r -> if (BuildConfig.DEBUG && r.hasVisualOverflow) Log.d("PleosTextCut", text) },
            )
        },
        modifier = modifier,
    ) { measurables, c ->
        val width = if (c.hasBoundedWidth && maxLines > 1) balancedWidth(measurer, shown, style, c.maxWidth, maxLines) else c.maxWidth
        val p = measurables.first().measure(c.copy(minWidth = 0, maxWidth = width.coerceAtLeast(0)))
        val w = maxOf(c.minWidth, p.width)
        layout(w, maxOf(c.minHeight, p.height)) {
            val x = when (align) {
                TextAlign.Center -> (w - p.width) / 2
                TextAlign.End, TextAlign.Right -> w - p.width
                else -> 0
            }
            p.place(x, 0)
        }
    }
}

private fun measure(m: TextMeasurer, text: String, style: TextStyle, width: Int, maxLines: Int): TextLayoutResult =
    m.measure(text, style, maxLines = maxLines, constraints = Constraints(maxWidth = width))

private fun words(s: String) = s.trim().split(' ').count { it.isNotBlank() }

private fun lineText(r: TextLayoutResult, text: String, i: Int) = text.substring(r.getLineStart(i), r.getLineEnd(i, visibleEnd = true))

private fun lineWidth(r: TextLayoutResult, i: Int) = r.getLineRight(i) - r.getLineLeft(i)

/** 마지막 줄에 어절이 2개 이상인지(전체 어절이 2개 이상일 때). */
private fun lastLineOk(r: TextLayoutResult, text: String): Boolean =
    r.lineCount < 2 || words(text) < 2 || words(lineText(r, text, r.lineCount - 1)) >= 2

/** 가장 짧은 줄과 가장 긴 줄의 비율(1에 가까울수록 고름). */
private fun evenness(r: TextLayoutResult): Float {
    val ws = (0 until r.lineCount).map { lineWidth(r, it) }
    return ws.min() / ws.max()
}

/** 줄 끝이 구절 경계(쉼표, 연결 어미 "면 고 며 서 후 나 도 지만")면 1점씩. */
private fun phraseScore(r: TextLayoutResult, text: String): Int = (0 until r.lineCount - 1).count { i ->
    val end = lineText(r, text, i).replace("\u2060", "").trimEnd()
    end.endsWith(",") || PHRASE_ENDS.any { end.endsWith(it) }
}

private val PHRASE_ENDS = listOf("면", "고", "며", "서", "후", "나", "도", "지만", "하여", "니다.", "에", "을", "를", "이", "가", "은", "는")

internal fun balancedWidth(m: TextMeasurer, text: String, style: TextStyle, maxWidth: Int, maxLines: Int): Int {
    val full = measure(m, text, style, maxWidth, maxLines)
    val n = full.lineCount
    if (n < 2 || full.hasVisualOverflow) return maxWidth
    // 같은 줄 수를 지키는 가장 좁은 폭(이진 탐색)
    var lo = 1
    var hi = maxWidth
    while (lo < hi) {
        val mid = (lo + hi) / 2
        val r = measure(m, text, style, mid, maxLines)
        if (r.lineCount <= n && !r.hasVisualOverflow) hi = mid else lo = mid + 1
    }
    // 가장 좁은 폭부터 최대 폭까지 후보를 재어, 마지막 줄 어절 조건을 지키면서
    // 줄 길이가 가장 고르고(가장 짧은 줄 / 가장 긴 줄), 같으면 구절 경계에서 끊기는 폭을 고른다.
    var best = maxWidth
    var bestScore = -1f
    for (k in 0..CANDIDATES) {
        val w = hi + (maxWidth - hi) * k / CANDIDATES
        val r = measure(m, text, style, w, maxLines)
        if (r.lineCount != n || r.hasVisualOverflow || !lastLineOk(r, text)) continue
        val e = evenness(r)
        val score = e + PHRASE_WEIGHT * phraseScore(r, text) + (if (e >= MIN_LAST_RATIO) 1f else 0f) + (if (firstLongest(r)) FIRST_WEIGHT else 0f)
        if (score > bestScore + 1e-3f) { bestScore = score; best = w }
    }
    if (BuildConfig.DEBUG) {
        val r = measure(m, text, style, best, maxLines)
        val ws = (0 until r.lineCount).map { lineWidth(r, it) }
        Log.d("PleosText", "lines=${r.lineCount} last/max=${"%.2f".format(ws.last() / ws.max())} min/max=${"%.2f".format(ws.min() / ws.max())} lastWords=${words(lineText(r, text, r.lineCount - 1))} text=${text.replace("\u2060", "")}")
    }
    return best
}

/** 첫 줄이 가장 긴 줄(2% 오차 안)인지. 짧은 첫 줄 금지(2026. 11. 11. 사용자 지시). */
private fun firstLongest(r: TextLayoutResult): Boolean {
    val first = lineWidth(r, 0)
    val others = (1 until r.lineCount).maxOf { lineWidth(r, it) }
    return first >= others * 0.98f
}

private const val FIRST_WEIGHT = 0.5f
private const val CANDIDATES = 48
/** 구절 경계 가산점. 고르기 차이가 0.08 안이면 구절 경계 쪽을 고른다. */
private const val PHRASE_WEIGHT = 0.06f

/** 마지막 줄 최소 길이(가장 긴 윗줄 대비). 사용자 줄바꿈 규칙 2026. 11. 11. */
private const val MIN_LAST_RATIO = 0.6f

/**
 * 한국어 어절 안에서 줄이 끊기지 않게 한다(CSS word-break: keep-all 과 같은 결과).
 * 에뮬레이터 실측(2026. 11. 11.)에서 LineBreak.Heading 만으로는 어절 중간이 끊겨서,
 * 한글과 숫자, 문장부호 사이에 WORD JOINER(U+2060, 폭 0)를 넣어 띄어쓰기에서만 줄이 바뀌게 한다.
 */
fun keepAll(text: String): String {
    if (text.length < 2) return text
    val sb = StringBuilder(text.length * 2)
    for (i in text.indices) {
        val ch = text[i]
        sb.append(ch)
        if (i + 1 < text.length && joinable(ch) && joinable(text[i + 1])) sb.append('\u2060')
    }
    return sb.toString()
}

private fun joinable(c: Char) = c in '\uAC00'..'\uD7A3' || c in '\u3131'..'\u318E' || c.isLetterOrDigit() || c in ".,?!:-)("
