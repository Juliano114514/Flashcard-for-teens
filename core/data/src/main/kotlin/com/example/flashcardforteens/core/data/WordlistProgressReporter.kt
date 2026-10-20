package com.example.flashcardforteens.core.data

import com.example.flashcardforteens.core.domain.WordlistLoadProgress
import com.example.flashcardforteens.core.domain.WordlistLoadStage

/** Called by the serial import owner, never by shard workers. No timers or per-word jobs. */
internal class WordlistProgressReporter(
  private val publish: (WordlistLoadProgress) -> Unit,
  private val previewOnly: Boolean = false,
) {
  private var latest = WordlistLoadProgress()
  private var publishedAt = 0L

  init { emit(force = true) }

  fun totalWords(total: Int) {
    require(total in 0..CSV_MAX_ROWS)
    latest = latest.copy(totalWords = total)
    emit(force = true)
  }

  fun update(stage: WordlistLoadStage, completed: Int = 0, total: Int = 0,
    entry: String? = null, processedWords: Int = latest.processedWords) {
    val changedStage = latest.stage != stage
    val firstWord = latest.processedWords == 0 && processedWords > 0
    val portion = if (total > 0) (completed.toFloat() / total).coerceIn(0f, 1f) else 0f
    val fraction = when (stage) {
      WordlistLoadStage.PREPARING -> 0f
      WordlistLoadStage.CHECKING -> (if (previewOnly) .99f else .40f) * portion
      WordlistLoadStage.SAVING -> .40f + .59f * portion
      WordlistLoadStage.COMMITTING -> .99f
      WordlistLoadStage.COMPLETE -> 1f
    }
    latest = latest.copy(stage = stage,
      processedWords = maxOf(latest.processedWords, processedWords).coerceAtMost(latest.totalWords ?: CSV_MAX_ROWS),
      currentEntry = entry?.take(64)?.filterNot(Char::isISOControl) ?: latest.currentEntry,
      fraction = maxOf(latest.fraction, fraction).coerceAtMost(if (stage == WordlistLoadStage.COMPLETE) 1f else .99f))
    emit(force = changedStage || firstWord)
  }

  fun finish() {
    update(WordlistLoadStage.COMPLETE, processedWords = latest.totalWords ?: latest.processedWords)
  }

  private fun emit(force: Boolean) {
    val now = System.nanoTime()
    if (force || now - publishedAt >= 100_000_000L) {
      publishedAt = now
      publish(latest)
    }
  }
}
