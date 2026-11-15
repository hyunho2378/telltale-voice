package com.telltalevoice.app.ui.sys

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.drawable.toBitmap
import com.telltalevoice.app.ui.pleos.Glyph

/**
 * Pleos 시스템 테마 앱(ai.pleos.theme.connect)의 공식 아이콘. 기기에 테마 앱이 있으면 그 벡터를 읽어 쓰고,
 * 없는 기기(휴대폰 AVD 등)에서는 앱이 그린 Glyph 를 쓴다. 아이콘 이름은 테마 앱 리소스 목록(theme_resources.txt)에서 확인한 값만 쓴다.
 */
object ThemeIcons {
    private const val PKG = "ai.pleos.theme.connect"
    private val NAMES = mapOf(
        Glyph.Phone to "ic_conn_phone",
        Glyph.Place to "ic_conn_pin_line",
        Glyph.Repeat to "ic_conn_refresh",
        Glyph.Alert to "ic_conn_annotationalerttri",
        Glyph.Clock to "ic_conn_clock_line",
        Glyph.Check to "ic_conn_checkregular",
        Glyph.Home to "ic_conn_home",
        Glyph.Info to "ic_conn_annotationinfo_line",
        Glyph.Speaker to "ic_conn_sound",
        Glyph.Back to "ic_conn_arrowleft",
        Glyph.Stop to "ic_conn_playerstop",
        Glyph.Play to "ic_conn_playerplay",
        Glyph.Fuel to "ic_conn_fuel",
        Glyph.Tow to "ic_conn_towing",
        Glyph.Route to "ic_conn_navigation",
        Glyph.Car to "ic_conn_vehicle",
        Glyph.Settings to "ic_conn_setting_line",
        Glyph.Washer to "ic_conn_washerfront",
    )

    @Volatile private var theme: Context? = null
    @Volatile private var tried = false
    private val cache = HashMap<String, ImageBitmap?>()

    @SuppressLint("DiscouragedApi")
    fun bitmap(context: Context, g: Glyph, px: Int): ImageBitmap? {
        val base = NAMES[g] ?: return null
        if (!tried) {
            tried = true
            theme = runCatching { context.createPackageContext(PKG, 0) }.getOrNull()
        }
        val t = theme ?: return null
        val key = "$base@$px"
        synchronized(cache) {
            if (cache.containsKey(key)) return cache[key]
            val bmp = runCatching {
                val id = t.resources.getIdentifier("${base}_bk_default_svg", "drawable", PKG)
                if (id == 0) null else t.resources.getDrawable(id, null)?.toBitmap(px, px)?.asImageBitmap()
            }.getOrNull()
            cache[key] = bmp
            return bmp
        }
    }
}

@Composable
fun themeBitmap(g: Glyph, size: Dp): ImageBitmap? {
    val ctx = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    return remember(g, px) { ThemeIcons.bitmap(ctx, g, px.coerceAtLeast(1)) }
}

/** 차 그림에서 경고 색으로 칠할 곳. */
enum class CarZone { TIRES, ENGINE_BAY, BRAKES, NONE }

fun carZoneFor(lampId: String?): CarZone = when (lampId) {
    "LOW_TIRE_PRESSURE", "TPMS", "TPMS_FAULT" -> CarZone.TIRES
    "ENGINE_OIL_PRESSURE", "ENGINE", "ENGINE_OIL", "COOLANT_TEMP" -> CarZone.ENGINE_BAY
    "BRAKE", "EBD", "ABS", "ELECTRONIC_PARKING_BRAKE" -> CarZone.BRAKES
    else -> CarZone.NONE
}

/**
 * 위에서 본 차 선 그림(빠른 설정 가운데 차량 그림 자리). 앞이 위쪽. 선은 시스템 아이콘처럼 검은 선, 굵기 stroke.
 * zone 은 경고 색 면과 선으로 칠한다: 타이어는 네 바퀴, 엔진은 앞쪽 엔진룸, 브레이크는 네 바퀴 안쪽 원판.
 */
@Composable
fun CarFigure(zone: CarZone, warn: Color, modifier: Modifier = Modifier, horizontal: Boolean = false) {
    val s = sys()
    val ink = s.ink
    val strokeDp = s.d(3)
    Canvas(modifier) { rotate(if (horizontal) 90f else 0f, center) {
        // 그림판 비율 100 x 200(폭 x 길이). 영역 안에 가운데 맞춤. 가로 그림은 앞이 오른쪽(낮고 넓은 칸)
        val bw = if (horizontal) size.height else size.width
        val bh = if (horizontal) size.width else size.height
        val unit = minOf(bw / 120f, bh / 210f)
        val w = 100f * unit; val h = 200f * unit
        val ox = center.x - w / 2f; val oy = center.y - h / 2f
        fun p(x: Float, y: Float) = Offset(ox + x * unit, oy + y * unit)
        val sw = strokeDp.toPx()
        val line = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val soft = warn.copy(alpha = 0.18f)

        // 바퀴(몸통 바깥으로 조금 나옴)
        val wheels = listOf(p(2f, 34f), p(84f, 34f), p(2f, 140f), p(84f, 140f))
        val wheelSize = Size(14f * unit, 34f * unit)
        wheels.forEach { o ->
            val on = zone == CarZone.TIRES
            drawRoundRect(if (on) warn else Color.White, o, wheelSize, CornerRadius(5f * unit))
            drawRoundRect(if (on) warn else ink, o, wheelSize, CornerRadius(5f * unit), style = line)
        }
        // 몸통
        val body = Path().apply {
            moveTo(ox + 30f * unit, oy + 4f * unit)
            cubicTo(ox + 12f * unit, oy + 6f * unit, ox + 10f * unit, oy + 20f * unit, ox + 10f * unit, oy + 40f * unit)
            lineTo(ox + 10f * unit, oy + 172f * unit)
            cubicTo(ox + 10f * unit, oy + 190f * unit, ox + 16f * unit, oy + 196f * unit, ox + 30f * unit, oy + 197f * unit)
            lineTo(ox + 70f * unit, oy + 197f * unit)
            cubicTo(ox + 84f * unit, oy + 196f * unit, ox + 90f * unit, oy + 190f * unit, ox + 90f * unit, oy + 172f * unit)
            lineTo(ox + 90f * unit, oy + 40f * unit)
            cubicTo(ox + 90f * unit, oy + 20f * unit, ox + 88f * unit, oy + 6f * unit, ox + 70f * unit, oy + 4f * unit)
            close()
        }
        drawPath(body, Color.White)
        // 엔진룸(앞 범퍼에서 앞유리까지)
        if (zone == CarZone.ENGINE_BAY) {
            val bay = Path().apply {
                moveTo(ox + 30f * unit, oy + 4f * unit)
                cubicTo(ox + 12f * unit, oy + 6f * unit, ox + 10f * unit, oy + 20f * unit, ox + 10f * unit, oy + 40f * unit)
                lineTo(ox + 10f * unit, oy + 52f * unit)
                lineTo(ox + 90f * unit, oy + 52f * unit)
                lineTo(ox + 90f * unit, oy + 40f * unit)
                cubicTo(ox + 90f * unit, oy + 20f * unit, ox + 88f * unit, oy + 6f * unit, ox + 70f * unit, oy + 4f * unit)
                close()
            }
            drawPath(bay, soft)
            drawPath(bay, warn, style = line)
            engineMark(p(50f, 28f), 9f * unit, warn, sw)
        }
        drawPath(body, ink, style = line)
        // 앞유리, 지붕, 뒷유리
        val glass = Path().apply {
            moveTo(ox + 18f * unit, oy + 64f * unit); quadraticBezierTo(ox + 50f * unit, oy + 50f * unit, ox + 82f * unit, oy + 64f * unit)
            lineTo(ox + 76f * unit, oy + 84f * unit); quadraticBezierTo(ox + 50f * unit, oy + 76f * unit, ox + 24f * unit, oy + 84f * unit); close()
        }
        drawPath(glass, ink, style = line)
        drawRoundRect(ink, p(24f, 92f), Size(52f * unit, 58f * unit), CornerRadius(6f * unit), style = line)
        val rear = Path().apply {
            moveTo(ox + 24f * unit, oy + 158f * unit); quadraticBezierTo(ox + 50f * unit, oy + 154f * unit, ox + 76f * unit, oy + 158f * unit)
            lineTo(ox + 80f * unit, oy + 176f * unit); quadraticBezierTo(ox + 50f * unit, oy + 182f * unit, ox + 20f * unit, oy + 176f * unit); close()
        }
        drawPath(rear, ink, style = line)
        // 사이드 미러
        drawLine(ink, p(10f, 62f), p(2f, 58f), sw, StrokeCap.Round)
        drawLine(ink, p(90f, 62f), p(98f, 58f), sw, StrokeCap.Round)
        // 브레이크 원판(네 바퀴 안쪽)
        if (zone == CarZone.BRAKES) {
            wheels.forEachIndexed { i, o ->
                val c = Offset(o.x + (if (i % 2 == 0) 22f else -8f) * unit, o.y + 17f * unit)
                drawCircle(soft, 9f * unit, c)
                drawCircle(warn, 9f * unit, c, style = line)
                drawCircle(warn, 3f * unit, c)
            }
        }
    } }
}

/** 엔진룸 안 작은 엔진 표시(Glyph.Engine 을 줄인 선 그림). */
private fun DrawScope.engineMark(c: Offset, r: Float, color: Color, sw: Float) {
    val l = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val path = Path().apply {
        moveTo(c.x - r, c.y - r * 0.4f); lineTo(c.x - r * 0.5f, c.y - r * 0.4f); lineTo(c.x - r * 0.5f, c.y - r * 0.8f)
        lineTo(c.x + r * 0.4f, c.y - r * 0.8f); lineTo(c.x + r * 0.4f, c.y - r * 0.4f); lineTo(c.x + r, c.y - r * 0.1f)
        lineTo(c.x + r, c.y + r * 0.7f); lineTo(c.x - r * 0.6f, c.y + r * 0.7f); lineTo(c.x - r, c.y + r * 0.3f); close()
    }
    drawPath(path, color, style = l)
}
