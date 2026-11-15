package com.telltalevoice.core.publicdata

/**
 * 작은 CSV 읽개. 따옴표 칸, 칸 안 쉼표와 줄바꿈, 겹따옴표(""), 머리 BOM, "#" 주석 줄을 다룬다.
 */
object Csv {
    fun parse(text: String): List<List<String>> {
        val src = text.removePrefix("\uFEFF")
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var atLineStart = true
        var i = 0
        fun endCell() { row += cell.toString(); cell.clear() }
        fun endRow() {
            endCell()
            if (!(row.size == 1 && row[0].isEmpty())) rows += row
            row = mutableListOf(); atLineStart = true
        }
        while (i < src.length) {
            val c = src[i]
            if (atLineStart && !quoted && c == '#') {
                while (i < src.length && src[i] != '\n') i++
                i++; continue
            }
            atLineStart = false
            when {
                quoted && c == '"' && i + 1 < src.length && src[i + 1] == '"' -> { cell.append('"'); i++ }
                c == '"' -> quoted = !quoted
                !quoted && c == ',' -> endCell()
                !quoted && c == '\r' -> Unit
                !quoted && c == '\n' -> endRow()
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) endRow()
        require(!quoted) { "따옴표가 닫히지 않음" }
        return rows
    }

    /** 첫 줄을 머리로 보고 칸 이름으로 찾는 지도 목록을 만든다. */
    fun parseWithHeader(text: String): List<Map<String, String>> {
        val rows = parse(text)
        if (rows.isEmpty()) return emptyList()
        val header = rows.first().map { it.trim() }
        return rows.drop(1).map { r -> header.indices.associate { header[it] to (r.getOrNull(it)?.trim() ?: "") } }
    }
}
