package com.example.karaokestudio

import com.example.karaokestudio.domain.lyrics.LrcParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

    @Test
    fun parsesTimestampsAndText() {
        val lines = LrcParser.parse("[00:05.50]Привіт\n[00:10.00]Світе", 20_000)
        assertEquals(2, lines.size)
        assertEquals(5_500L, lines[0].timeMs)
        assertEquals("Привіт", lines[0].text)
        assertEquals(10_000L, lines[1].timeMs)
    }

    @Test
    fun sortsLinesByTime() {
        val lines = LrcParser.parse("[00:20.00]Другий\n[00:05.00]Перший", 30_000)
        assertEquals(listOf("Перший", "Другий"), lines.map { it.text })
    }

    @Test
    fun keepsEmptyTextAsInstrumentalGap() {
        val lines = LrcParser.parse("[00:00.00]\n[00:04.00]Рядок", 10_000)
        assertEquals(2, lines.size)
        assertEquals("", lines[0].text)
    }

    @Test
    fun spreadsPlainLinesEvenly() {
        val lines = LrcParser.parse("Один\nДва\nТри\nЧотири", 40_000)
        assertEquals(listOf(0L, 10_000L, 20_000L, 30_000L), lines.map { it.timeMs })
    }

    @Test
    fun emptyInputGivesEmptyList() {
        assertTrue(LrcParser.parse("", 10_000).isEmpty())
    }

    @Test
    fun progressGrowsInsideLineAndIsClamped() {
        val lines = LrcParser.parse("[00:10.00]А\n[00:20.00]Б", 30_000)
        assertEquals(0f, LrcParser.progressAt(lines, 0, 9_000), 0.001f)
        assertEquals(0.5f, LrcParser.progressAt(lines, 0, 14_250), 0.001f)
        assertEquals(1f, LrcParser.progressAt(lines, 0, 25_000), 0.001f)
        assertEquals(0f, LrcParser.progressAt(lines, 1, 20_000), 0.001f)
    }

    @Test
    fun indexAtFindsCurrentLine() {
        val lines = LrcParser.parse("[00:05.00]А\n[00:10.00]Б\n[00:15.00]В", 20_000)
        assertEquals(-1, LrcParser.indexAt(lines, 1_000))
        assertEquals(0, LrcParser.indexAt(lines, 5_000))
        assertEquals(1, LrcParser.indexAt(lines, 12_000))
        assertEquals(2, LrcParser.indexAt(lines, 99_000))
    }
}
