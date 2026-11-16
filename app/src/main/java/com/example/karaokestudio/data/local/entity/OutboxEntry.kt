package com.example.karaokestudio.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sync_outbox",
    indices = [Index(value = ["type", "entityKey"], unique = true)]
)
data class OutboxEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val entityKey: String,
    val op: String,
    val changedAt: Long,
    val attempts: Int = 0
) {
    companion object {
        const val TYPE_FAVORITE = "favorite"
        const val TYPE_PERFORMANCE = "performance"
        const val OP_UPSERT = "upsert"
        const val OP_DELETE = "delete"
    }
}
