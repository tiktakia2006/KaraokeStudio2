package com.example.karaokestudio.domain.lyrics

data class LyricLine(val timeMs: Long, val text: String)

object LrcParser {

    private const val SUNG_SHARE = 0.85f
    private const val LAST_LINE_MS = 4_000L

    private val TIMED = Regex("""^\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?\]\s*(.*)$""")

    fun parse(raw: String, durationMs: Long): List<LyricLine> {
        val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }

        val timed = lines.mapNotNull { line ->
            val m = TIMED.matchEntire(line) ?: return@mapNotNull null
            val (min, sec, frac, text) = m.destructured
            val fractionMs = if (frac.isEmpty()) 0 else frac.padEnd(3, '0').take(3).toInt()
            LyricLine(min.toLong() * 60_000 + sec.toLong() * 1_000 + fractionMs, text.trim())
        }
        if (timed.isNotEmpty()) return timed.sortedBy { it.timeMs }

        val plain = lines.filterNot { it.startsWith("[") }
        if (plain.isEmpty()) return emptyList()
        val step = durationMs / plain.size
        return plain.mapIndexed { i, text -> LyricLine(i * step, text) }
    }

    fun progressAt(lines: List<LyricLine>, index: Int, positionMs: Long): Float {
        val start = lines[index].timeMs
        val next = lines.getOrNull(index + 1)?.timeMs ?: (start + LAST_LINE_MS)
        val span = ((next - start) * SUNG_SHARE).coerceAtLeast(1_000f)
        return ((positionMs - start) / span).coerceIn(0f, 1f)
    }

    fun indexAt(lines: List<LyricLine>, positionMs: Long): Int {
        var lo = 0
        var hi = lines.lastIndex
        var answer = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].timeMs <= positionMs) {
                answer = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return answer
    }
}
