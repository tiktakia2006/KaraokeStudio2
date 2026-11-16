package com.example.karaokestudio

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.karaokestudio.data.local.entity.Song
import com.example.karaokestudio.ui.components.DemoBadge
import com.example.karaokestudio.ui.components.EmptyView
import com.example.karaokestudio.ui.components.ErrorView
import com.example.karaokestudio.ui.components.GradientButton
import com.example.karaokestudio.ui.components.ScoreChip
import com.example.karaokestudio.ui.components.SongCard
import com.example.karaokestudio.ui.theme.KaraokeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComponentsUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val song = Song(
        id = 1,
        title = "Ранкова хвиля",
        artist = "Demo Band",
        durationMs = 65_000,
        lyrics = "",
        audioSource = "demo:x",
        isDemo = true
    )

    @Test
    fun emptyViewShowsTextAndRunsAction() {
        var clicked = false
        composeRule.setContent {
            KaraokeTheme {
                EmptyView(
                    icon = Icons.Rounded.MusicNote,
                    title = "Порожньо",
                    subtitle = "Пояснення",
                    actionText = "Дія",
                    onAction = { clicked = true }
                )
            }
        }
        composeRule.onNodeWithText("Порожньо").assertIsDisplayed()
        composeRule.onNodeWithText("Дія").performClick()
        assertTrue(clicked)
    }

    @Test
    fun errorViewShowsMessageAndRetries() {
        var retries = 0
        composeRule.setContent {
            KaraokeTheme { ErrorView("Немає доступу до бази", onRetry = { retries++ }) }
        }
        composeRule.onNodeWithText("Немає доступу до бази").assertIsDisplayed()
        composeRule.onNodeWithText("Завантажити знову").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun songCardShowsDataAndTogglesFavorite() {
        var toggled = false
        composeRule.setContent {
            KaraokeTheme {
                SongCard(song, isFavorite = false, onClick = {}, onToggleFavorite = { toggled = true })
            }
        }
        composeRule.onNodeWithText("Ранкова хвиля").assertIsDisplayed()
        composeRule.onNodeWithText("Demo Band").assertIsDisplayed()
        composeRule.onNodeWithText("1:05").assertIsDisplayed()
        composeRule.onNodeWithText("Тест").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Додати до обраного").performClick()
        assertTrue(toggled)
    }

    @Test
    fun disabledGradientButtonIgnoresClicks() {
        var clicks = 0
        composeRule.setContent {
            KaraokeTheme { GradientButton("Старт", onClick = { clicks++ }, enabled = false) }
        }
        composeRule.onNodeWithText("Старт").performClick()
        assertEquals(0, clicks)
    }

    @Test
    fun enabledGradientButtonRunsAction() {
        var clicks = 0
        composeRule.setContent {
            KaraokeTheme { GradientButton("Старт", onClick = { clicks++ }) }
        }
        composeRule.onNodeWithText("Старт").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun scoreChipShowsNumberOrDash() {
        composeRule.setContent {
            KaraokeTheme {
                ScoreChip(86)
                ScoreChip(null)
            }
        }
        composeRule.onNodeWithText("86").assertIsDisplayed()
        composeRule.onNodeWithText("—").assertIsDisplayed()
    }

    @Test
    fun demoBadgeIsLabelledAsTest() {
        composeRule.setContent { KaraokeTheme { DemoBadge() } }
        composeRule.onNodeWithText("Тест").assertIsDisplayed()
    }
}
