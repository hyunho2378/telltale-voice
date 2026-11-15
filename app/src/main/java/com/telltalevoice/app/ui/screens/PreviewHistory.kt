package com.telltalevoice.app.ui.screens

import android.content.Context
import com.telltalevoice.core.Scenarios

/**
 * 미리 듣기 기록. 장면을 들은 시각(벽시계)을 기기에 남긴다. 경고 이력 화면은 이 목록으로
 * 실제 경고 기록과 미리 듣기 기록을 나눈다(미리 듣기 시간 안에 경고 이력은 실제 경고에서 뺀다).
 */
data class PreviewPlay(val scenarioId: String, val atWallMs: Long) {
    /** 이 미리 듣기가 엔진에 경고를 넣었을 수 있는 시간(장면 길이와 여유 1분). */
    val untilWallMs: Long get() = atWallMs + (Scenarios.byId(scenarioId)?.durationMs ?: 0L) + 60_000L
}

object PreviewHistory {
    private const val PREFS = "preview_history"
    private const val KEY = "plays"
    private const val MAX = 30

    fun all(context: Context): List<PreviewPlay> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()
            .split(";").mapNotNull { e ->
                val (id, at) = e.split("@").takeIf { it.size == 2 } ?: return@mapNotNull null
                at.toLongOrNull()?.let { PreviewPlay(id, it) }
            }.sortedByDescending { it.atWallMs }

    fun record(context: Context, scenarioId: String, atWallMs: Long = System.currentTimeMillis()): List<PreviewPlay> {
        val next = (listOf(PreviewPlay(scenarioId, atWallMs)) + all(context)).take(MAX)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, next.joinToString(";") { "${it.scenarioId}@${it.atWallMs}" }).apply()
        return next
    }

    /** 기록 한 줄이 미리 듣기 중에 켜진 것인지. */
    fun isPreview(onAtWallMs: Long, plays: List<PreviewPlay>): Boolean =
        plays.any { onAtWallMs >= it.atWallMs - 2_000L && onAtWallMs <= it.untilWallMs }
}
