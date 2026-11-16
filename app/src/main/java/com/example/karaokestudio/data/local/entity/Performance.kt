package com.example.karaokestudio.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "performances",
    foreignKeys = [
        ForeignKey(
            entity = Song::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("songId"),
        Index("performedAt"),
        Index(value = ["clientId"], unique = true)
    ]
)
data class Performance(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: Long,
    val performedAt: Long,
    val durationMs: Long,
    val score: Int? = null,
    val isDemo: Boolean = false,
    val clientId: String = UUID.randomUUID().toString()
)
