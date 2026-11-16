package com.example.karaokestudio

import com.example.karaokestudio.data.local.entity.Song
import com.example.karaokestudio.data.repository.SongRepository
import com.example.karaokestudio.data.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SongAndSettingsTest {

    @Test
    fun normalizeLowercasesCyrillic() {
        assertEquals("ранкова", SongRepository.normalizeQuery("  РАНКОВА "))
    }

    @Test
    fun normalizeRemovesLikeWildcards() {
        assertEquals("ab", SongRepository.normalizeQuery("a%b_"))
    }

    @Test
    fun wildcardOnlyQueryBecomesEmpty() {
        assertEquals("", SongRepository.normalizeQuery("  %_ "))
    }

    @Test
    fun searchKeyIsLowercase() {
        assertEquals("ранкова хвиля demo band", Song.buildSearchKey("Ранкова хвиля", "Demo Band"))
    }

    @Test
    fun settingsAreClampedToValidRanges() {
        val fixed = AppSettings(
            nickname = "   ",
            countdownSeconds = 99,
            lyricsSizeSp = 5,
            micSensitivity = 500,
            musicVolume = -20
        ).sanitized()
        assertEquals("Співак", fixed.nickname)
        assertEquals(10, fixed.countdownSeconds)
        assertEquals(18, fixed.lyricsSizeSp)
        assertEquals(100, fixed.micSensitivity)
        assertEquals(0, fixed.musicVolume)
    }

    private fun song(title: String = "Назва", duration: Long = 10_000, source: String = "file:///x.mp3") =
        Song(title = title, artist = "A", durationMs = duration, lyrics = "", audioSource = source)

    @Test
    fun validSongPassesValidation() {
        assertNull(SongRepository.validate(song()))
    }

    @Test
    fun blankTitleIsRejected() {
        assertNotNull(SongRepository.validate(song(title = "   ")))
    }

    @Test
    fun tooLongTitleIsRejected() {
        assertNotNull(SongRepository.validate(song(title = "я".repeat(SongRepository.MAX_TITLE + 1))))
    }

    @Test
    fun zeroDurationOrMissingAudioIsRejected() {
        assertNotNull(SongRepository.validate(song(duration = 0)))
        assertNotNull(SongRepository.validate(song(source = " ")))
    }
}
