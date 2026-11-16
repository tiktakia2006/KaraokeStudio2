package com.example.karaokestudio

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.karaokestudio.data.local.KaraokeDatabase
import com.example.karaokestudio.data.local.MIGRATION_1_2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), KaraokeDatabase::class.java)

    @Test
    fun migration1To2KeepsDataAndFillsNewKeys() {
        helper.createDatabase(DB_NAME, 1).apply {
            execSQL(
                "INSERT INTO songs (id, title, artist, durationMs, lyrics, audioSource, coverSource, genre, isDemo, searchKey) " +
                    "VALUES (1, 'Ранкова хвиля', 'Hurt', 48000, '', 'demo:morning-wave', 'gradient:0', 'Поп', 1, 'ранкова хвиля hurt')"
            )
            execSQL(
                "INSERT INTO songs (id, title, artist, durationMs, lyrics, audioSource, coverSource, genre, isDemo, searchKey) " +
                    "VALUES (2, 'Моя пісня', 'Я', 60000, '', 'file:///data/a.m4a', 'gradient:2', 'Інше', 0, 'моя пісня я')"
            )
            execSQL("INSERT INTO performances (id, songId, performedAt, durationMs, score, isDemo) VALUES (1, 1, 1000, 48000, 90, 1)")
            execSQL("INSERT INTO performances (id, songId, performedAt, durationMs, score, isDemo) VALUES (2, 2, 2000, 60000, NULL, 0)")
            execSQL("INSERT INTO favorite_songs (songId, addedAt) VALUES (1, 5000)")
            close()
        }

        val db = helper.runMigrationsAndValidate(DB_NAME, 2, true, MIGRATION_1_2)

        db.query("SELECT id, slug FROM songs ORDER BY id").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("morning-wave", cursor.getString(1))
            assertTrue(cursor.moveToNext())
            assertNull(if (cursor.isNull(1)) null else cursor.getString(1))
        }
        db.query("SELECT clientId FROM performances").use { cursor ->
            val ids = mutableSetOf<String>()
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                assertEquals(32, id.length)
                ids.add(id)
            }
            assertEquals(2, ids.size)
        }
        db.query("SELECT COUNT(*) FROM favorite_songs").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM sync_outbox").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
    }

    private companion object {
        const val DB_NAME = "migration-test"
    }
}
