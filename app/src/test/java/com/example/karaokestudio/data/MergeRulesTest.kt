package com.example.karaokestudio.data

import com.example.karaokestudio.data.remote.PullChangeDto
import com.example.karaokestudio.data.sync.MergeAction
import com.example.karaokestudio.data.sync.MergeRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MergeRulesTest {

    private fun favorite(slug: String, deleted: Boolean = false) =
        PullChangeDto(type = "favorite", songSlug = slug, deleted = deleted, updatedAt = 10)

    private fun performance(clientId: String, deleted: Boolean = false) =
        PullChangeDto(
            type = "performance",
            songSlug = "morning-wave",
            deleted = deleted,
            updatedAt = 10,
            clientId = clientId,
            performedAt = 1000,
            durationMs = 48000,
            score = 80
        )

    @Test
    fun buildsKeysPerEntity() {
        assertEquals("favorite:neon-dance", MergeRules.keyOf(favorite("neon-dance")))
        assertEquals("performance:abc", MergeRules.keyOf(performance("abc")))
        assertNull(MergeRules.keyOf(PullChangeDto(type = "performance", songSlug = "x")))
        assertNull(MergeRules.keyOf(PullChangeDto(type = "other", songSlug = "x")))
    }

    @Test
    fun pendingLocalChangeBeatsRemoteOne() {
        val pending = setOf("favorite:neon-dance")
        assertEquals(MergeAction.SKIP_PENDING, MergeRules.decide(favorite("neon-dance"), pending, existsLocally = true, sameAsLocal = true))
        assertEquals(MergeAction.SKIP_PENDING, MergeRules.decide(favorite("neon-dance", deleted = true), pending, existsLocally = true, sameAsLocal = true))
    }

    @Test
    fun appliesRemoteUpsertWhenMissingOrDifferent() {
        assertEquals(MergeAction.APPLY_UPSERT, MergeRules.decide(favorite("a"), emptySet(), existsLocally = false, sameAsLocal = false))
        assertEquals(MergeAction.APPLY_UPSERT, MergeRules.decide(performance("p"), emptySet(), existsLocally = true, sameAsLocal = false))
    }

    @Test
    fun skipsRemoteUpsertWhenIdentical() {
        assertEquals(MergeAction.SKIP_UNCHANGED, MergeRules.decide(performance("p"), emptySet(), existsLocally = true, sameAsLocal = true))
    }

    @Test
    fun appliesRemoteDeleteOnlyWhenLocalExists() {
        assertEquals(MergeAction.APPLY_DELETE, MergeRules.decide(favorite("a", deleted = true), emptySet(), existsLocally = true, sameAsLocal = false))
        assertEquals(MergeAction.SKIP_UNCHANGED, MergeRules.decide(favorite("a", deleted = true), emptySet(), existsLocally = false, sameAsLocal = false))
    }

    @Test
    fun ignoresUnknownChangeTypes() {
        val unknown = PullChangeDto(type = "other", songSlug = "x")
        assertEquals(MergeAction.SKIP_UNCHANGED, MergeRules.decide(unknown, emptySet(), existsLocally = false, sameAsLocal = false))
    }
}
