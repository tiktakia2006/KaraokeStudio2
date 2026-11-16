package com.example.karaokestudio.data.sync

import com.example.karaokestudio.data.remote.PullChangeDto

enum class MergeAction { APPLY_UPSERT, APPLY_DELETE, SKIP_PENDING, SKIP_UNCHANGED }

object MergeRules {

    const val FAVORITE = "favorite"
    const val PERFORMANCE = "performance"

    fun keyOf(change: PullChangeDto): String? = when (change.type) {
        FAVORITE -> "$FAVORITE:${change.songSlug}"
        PERFORMANCE -> change.clientId?.let { "$PERFORMANCE:$it" }
        else -> null
    }

    fun decide(change: PullChangeDto, pendingKeys: Set<String>, existsLocally: Boolean, sameAsLocal: Boolean): MergeAction {
        val key = keyOf(change) ?: return MergeAction.SKIP_UNCHANGED
        if (key in pendingKeys) return MergeAction.SKIP_PENDING
        return if (change.deleted) {
            if (existsLocally) MergeAction.APPLY_DELETE else MergeAction.SKIP_UNCHANGED
        } else {
            if (existsLocally && sameAsLocal) MergeAction.SKIP_UNCHANGED else MergeAction.APPLY_UPSERT
        }
    }
}
