package com.telltalevoice.app.ui.screens

/**
 * EngineHub.nearbyShops 줄("이름, 3.6킬로미터, 033-652-6622", 전화 없으면 "이름, 3.6킬로미터")을 손님용 서비스센터로 바꾼다.
 * 30킬로미터 안의 서비스센터만 가까운 순으로 3곳까지 쓴다.
 */
data class Shop(val name: String, val km: Double, val phone: String?) {
    /** 아랫줄. 거리와 전화 사이는 두 칸 띄움, 전화가 없으면 거리만. */
    val detail: String get() = "${"%.1f".format(km)}킬로미터" + (phone?.let { "  $it" } ?: "")
}

object Shops {
    const val MAX_KM = 30.0
    private const val MAX_COUNT = 3
    private val kmRe = Regex("""^(\d+(?:\.\d+)?)킬로미터$""")

    fun parse(lines: List<String>): List<Shop> = lines.mapNotNull { line ->
        val parts = line.split(", ")
        val kmIndex = parts.indexOfLast { kmRe.matches(it.trim()) }
        if (kmIndex <= 0) return@mapNotNull null
        val km = kmRe.find(parts[kmIndex].trim())!!.groupValues[1].toDouble()
        val phone = parts.drop(kmIndex + 1).joinToString(", ").trim().ifEmpty { null }
        Shop(cleanName(parts.take(kmIndex).joinToString(", ")), km, phone)
    }.filter { it.km <= MAX_KM }.sortedBy { it.km }.take(MAX_COUNT)

    /** "현대 자동차 블루핸즈(음성남부)" 를 "현대자동차 블루핸즈 음성남부" 처럼. 지점명은 이름 뒤에 붙인다. */
    fun cleanName(raw: String): String = raw
        .replace("현대 자동차", "현대자동차")
        .replace(Regex("""\s*\(([^)]*)\)"""), " $1")
        .replace(Regex("""\s+"""), " ")
        .trim()
}
