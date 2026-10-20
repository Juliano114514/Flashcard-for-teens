package com.example.flashcardforteens.feature.study

import android.os.SystemClock
import com.example.flashcardforteens.core.domain.CardPhase
import com.example.flashcardforteens.core.domain.ExampleSentence
import com.example.flashcardforteens.core.domain.StudyCard
import com.example.flashcardforteens.core.domain.AnswerOption
import com.example.flashcardforteens.core.domain.AppLanguage
import com.example.flashcardforteens.core.domain.SpeechRequest
import com.example.flashcardforteens.core.domain.ChinesePronunciation
import com.example.flashcardforteens.core.domain.SpeechScene
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job

/** Main-thread playback coordination only. Study progress remains owned by the ViewModel/repository. */
internal class StudyPlayback(private val requests: (SpeechRequest) -> List<SpeechRequest> = { listOf(it) },
  private val speakAndWait: suspend (SpeechRequest) -> Boolean) {
  private var activeSequence: Job? = null
  private var lastCompletedAt: Long? = null

  suspend fun sequence(block: suspend Sequence.() -> Unit) = coroutineScope {
    cancel()
    val sequence = currentCoroutineContext().job
    activeSequence = sequence
    try { Sequence().block() }
    finally { if (activeSequence === sequence) activeSequence = null }
  }

  fun cancel() { activeSequence?.cancel(); activeSequence = null }

  inner class Sequence internal constructor() {
    suspend fun speak(request: SpeechRequest, immediate: Boolean = false): Boolean {
      val first = requests(request).firstOrNull() ?: return true
      if (request.text.isBlank()) return false
      if (!immediate) lastCompletedAt?.let { completedAt ->
        val gap = if (first.language == AppLanguage.EN) 250L else 500L
        delay((gap - (SystemClock.elapsedRealtime() - completedAt)).coerceAtLeast(0L))
      }
      currentCoroutineContext().ensureActive()
      val played = try { speakAndWait(request) }
      catch (error: CancellationException) { throw error }
      catch (_: Exception) { false }
      currentCoroutineContext().ensureActive()
      lastCompletedAt = if (played) SystemClock.elapsedRealtime() else null
      return played
    }
  }
}

internal fun StudyCard.contextExample(): ExampleSentence? = if (showContext && word.examples.isNotEmpty()) {
  word.examples[(round - 1).coerceAtLeast(0) % word.examples.size]
} else null

internal fun StudyCard.playbackExamples(): List<ExampleSentence> = when (phase) {
  CardPhase.INTRO, CardPhase.EXPLANATION -> word.examples
  CardPhase.QUESTION -> listOfNotNull(contextExample())
  else -> emptyList()
}

internal fun StudyCard.promptSpeech(): SpeechRequest = targetSpeech().copy(
  scene = if (phase == CardPhase.QUESTION) SpeechScene.STUDY_PROMPT else SpeechScene.WORD_PRONUNCIATION)

internal fun StudyCard.targetSpeech(): SpeechRequest {
  val scene = when (phase) {
    CardPhase.QUESTION -> SpeechScene.STUDY_PROMPT
    CardPhase.FEEDBACK -> SpeechScene.ANSWER_FEEDBACK
    else -> SpeechScene.STUDY_EXPLANATION
  }
  // Never attach the Chinese answer to a question's playback request.
  val revealed = phase != CardPhase.QUESTION
  return SpeechRequest(word.headword, AppLanguage.EN, scene,
    counterpart = word.chineseMeaning.takeIf { revealed },
    chinesePronunciation = if (revealed) ChinesePronunciation(word.chineseMeaning, word.chinesePinyin) else null)
}

internal fun StudyCard.optionSpeech(option: AnswerOption): SpeechRequest =
  SpeechRequest(option.chinese, AppLanguage.CN, SpeechScene.ANSWER_FEEDBACK,
    chinesePronunciation = ChinesePronunciation(option.chinese, option.chinesePinyin))

internal fun StudyCard.optionLabel(option: AnswerOption): String = option.chinese

internal fun StudyCard.exampleSpeech(example: ExampleSentence): SpeechRequest {
  val question = phase == CardPhase.QUESTION
  return SpeechRequest(example.english, AppLanguage.EN,
    if (question) SpeechScene.STUDY_CONTEXT else SpeechScene.STUDY_EXPLANATION,
    counterpart = example.chinese.takeIf { !question },
    chinesePronunciation = if (question) null else ChinesePronunciation(example.chinese, example.chinesePinyin))
}
