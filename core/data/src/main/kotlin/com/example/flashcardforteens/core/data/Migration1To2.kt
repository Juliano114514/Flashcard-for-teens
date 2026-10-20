package com.example.flashcardforteens.core.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.flashcardforteens.core.domain.SpeechMode
import com.example.flashcardforteens.core.domain.SpeechScene
import org.json.JSONException
import org.json.JSONObject

/** Preserve learning history while separating sound effects from feedback narration. */
internal object Migration1To2 : Migration(1, 2) {
  override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL("ALTER TABLE settings ADD COLUMN learningSoundsEnabled INTEGER NOT NULL DEFAULT 1")
    val preferences = db.query("SELECT id, speechModesJson FROM settings").use { cursor ->
      buildList {
        while (cursor.moveToNext()) add(cursor.getInt(0) to soundsEnabled(cursor.getString(1)))
      }
    }
    preferences.forEach { (id, enabled) ->
      db.execSQL("UPDATE settings SET learningSoundsEnabled = ?, explanationMask = 5 WHERE id = ?",
        arrayOf<Any>(if (enabled) 1 else 0, id))
    }
  }

  private fun soundsEnabled(json: String): Boolean {
    // Match the existing parser: missing, invalid or unknown modes use its enabled default.
    val values = try { JSONObject(json) } catch (_: JSONException) { return true }
    return values.optString(SpeechScene.ANSWER_FEEDBACK.name) != SpeechMode.NONE.name
  }
}
