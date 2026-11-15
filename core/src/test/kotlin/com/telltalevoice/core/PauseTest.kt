package com.telltalevoice.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 쉼 규칙: 4어절 지시문은 2어절 뒤 쉼표, 3어절 이하는 쉼 없음. 음성 엔진 글에만 넣고 화면 글은 그대로. */
class PauseTest {
    private fun tts(id: String) = SpeechBuilder.warning(LampCatalog.byId(id)!!).ttsText
    private fun text(id: String) = SpeechBuilder.warning(LampCatalog.byId(id)!!).text

    @Test
    fun `바로 정차 4어절 문장은 2어절 뒤에 쉼표`() {
        assertEquals("냉각수 수온 경고등이 켜졌습니다. 비상 경고등을, 켜고 정차하십시오.", tts("ENGINE_TEMPERATURE"))
        assertEquals("브레이크 경고등이 켜졌습니다. 서서히 감속 후, 정차하십시오.", tts("BRAKE")) // 의존 명사 "후"는 앞말에 붙임
    }

    @Test
    fun `3어절 문장은 쉼 없이`() {
        assertEquals("엔진 오일 압력 경고등이 켜졌습니다. 도로 옆으로 정차하십시오.", tts("ENGINE_OIL_PRESSURE"))
        assertEquals("에이비에스 경고등이 켜졌습니다. 안전한 장소에 정차하십시오.", tts("ABS"))
    }

    @Test
    fun `화면 글에는 쉼표를 넣지 않는다`() {
        LampCatalog.all.forEach { l ->
            val t = text(l.id)
            assertFalse(t.substringAfter(". ").contains(","), "${l.id}: $t")
        }
    }

    @Test
    fun `모든 경고 문장의 쉼표는 4어절 행동 문장에만`() {
        LampCatalog.all.forEach { l ->
            val words = l.action.split(" ").size
            val second = tts(l.id).substringAfter(". ")
            assertEquals(words == 4, second.contains(","), "${l.id}: $second")
            assertFalse(tts(l.id).substringBefore(". ").contains(","), l.id)
        }
    }

    @Test
    fun `쉼 함수는 정확히 4어절에서만 쉼표를 넣는다`() {
        assertEquals("가 나, 다 라", SpeechBuilder.withPause("가 나 다 라"))
        assertEquals("가 나 다", SpeechBuilder.withPause("가 나 다"))
        assertEquals("가 나 다 라 마", SpeechBuilder.withPause("가 나 다 라 마"))
        assertEquals("가 나 후, 라", SpeechBuilder.withPause("가 나 후 라"))
        assertTrue(SpeechBuilder.PAUSE_WORDS == 4)
    }
}
