package com.telltalevoice.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Gleo Interface App Schema 점검(DEV_V2 5번). app-integration-guide 규칙:
 * 파일은 assets/schema.json, actionCalls 최대 3개, function 이름은 대문자와 밑줄, 매니페스트에 function 마다 action filter.
 */
class GleoSchemaTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
    private val schema = File(root, "app/src/main/assets/schema.json").readText()
    private val manifest = File(root, "app/src/main/AndroidManifest.xml").readText()
    private val names = Regex("\"name\"\\s*:\\s*\"([^\"]+)\"").findAll(schema).map { it.groupValues[1] }.toList()

    @Test
    fun `패키지 이름과 function 세 개`() {
        assertTrue(schema.contains("\"packageName\": \"com.telltalevoice.app\""))
        assertEquals(listOf("TELL_ACTIVE_WARNING_LAMPS", "REPEAT_LAST_GUIDANCE", "FIND_NEARBY_REPAIR_SHOP"), names)
        assertEquals(3, Regex("\"type\"\\s*:\\s*\"function\"").findAll(schema).count())
    }

    @Test
    fun `이름 규칙과 service 호출, 영어 설명`() {
        names.forEach { assertTrue(it.matches(Regex("[A-Z]+(_[A-Z]+)*")), it) }
        assertEquals(3, Regex("\"component\"\\s*:\\s*\"service\"").findAll(schema).count())
        val descriptions = Regex("\"description\"\\s*:\\s*\"([^\"]+)\"").findAll(schema).map { it.groupValues[1] }.toList()
        assertEquals(3, descriptions.size)
        descriptions.forEach { d -> assertTrue(d.none { it in '\uAC00'..'\uD7A3' }, d) }
    }

    @Test
    fun `매니페스트 수신부가 function 마다 action 을 받는다`() {
        assertTrue(manifest.contains("android:name=\".GleoActionReceiver\""))
        names.forEach { assertTrue(manifest.contains("<action android:name=\"$it\" />"), it) }
    }

    /**
     * 공식 문서 App Schema Writing Guide 2-1 필드 대조(2026. 11. 15.).
     * 최상위 필드는 packageName 과 appDescription, version, actionCalls 이고 invokeWord 는 문서 예시(리마인더 앱)에 있는 필드다.
     */
    @Test
    fun `최상위 필드는 문서 명세와 예시 그대로`() {
        val top = schema.lines().filter { it.startsWith("  \"") }.map { it.substringAfter("\"").substringBefore("\"") }
        assertEquals(listOf("packageName", "appDescription", "invokeWord", "version", "actionCalls"), top)
        assertTrue(schema.contains("\"invokeWord\": \"경고등 안내\""))
    }

    @Test
    fun `앱 버전은 앱 모듈 versionName 과 같다`() {
        val version = Regex("\"version\"\\s*:\\s*\"([^\"]+)\"").find(schema)!!.groupValues[1]
        val gradle = File(root, "app/build.gradle.kts").readText()
        val versionName = Regex("versionName\\s*=\\s*\"([^\"]+)\"").find(gradle)!!.groupValues[1]
        assertEquals(versionName, version)
    }

    @Test
    fun `function 마다 문서가 정한 다섯 칸을 갖춘다`() {
        // type 은 function 고정, parameters 는 type object 와 properties, required 를 갖춘다
        assertEquals(3, Regex("\"type\"\\s*:\\s*\"function\"").findAll(schema).count())
        assertEquals(3, Regex("\"type\"\\s*:\\s*\"object\"").findAll(schema).count())
        assertEquals(3, Regex("\"properties\"").findAll(schema).count())
        assertEquals(3, Regex("\"required\"\\s*:\\s*\\[").findAll(schema).count())
        // 세 function 모두 입력 값이 없으므로 properties 와 required 는 비어 있다
        assertEquals(3, Regex("\"properties\"\\s*:\\s*\\{\\s*\\}").findAll(schema).count())
        assertEquals(3, Regex("\"required\"\\s*:\\s*\\[\\s*\\]").findAll(schema).count())
    }

    @Test
    fun `설명은 영어 한 문장에서 두 문장까지`() {
        val all = Regex("\"(appDescription|description)\"\\s*:\\s*\"([^\"]+)\"").findAll(schema).map { it.groupValues[2] }.toList()
        assertEquals(4, all.size)
        all.forEach { d ->
            assertTrue(d.none { it in '\uAC00'..'\uD7A3' }, d)
            assertTrue(d.count { it == '.' } in 1..2, d)
            assertTrue(d.first().isUpperCase(), d)
        }
    }

    @Test
    fun `function 이름은 두에서 네 낱말이고 겹치지 않는다`() {
        assertEquals(names.size, names.toSet().size)
        names.forEach { assertTrue(it.split("_").size in 2..4, it) }
    }

    @Test
    fun `음성 명령 문장 문서가 세 function 을 모두 덮는다`() {
        val doc = File(root, "docs/GLEO_UTTERANCES.md").readText()
        names.forEach { assertTrue(doc.contains(it), it) }
        // 표 줄(세로선으로 시작하는 줄) 가운데 머리글과 구분 줄을 뺀 문장 수
        val rows = doc.lines().filter { it.startsWith("|") && !it.startsWith("|---") && !it.contains("말할 문장") }
        assertTrue(rows.size >= 10, "문장 수 ${rows.size}")
        assertTrue(doc.contains("확인하지 못함"))
    }
}
