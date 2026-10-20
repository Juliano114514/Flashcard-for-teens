package com.example.flashcardforteens.core.domain

data class ExampleChunk(val english: String, val chinese: String, val chinesePinyin: String)

data class ExampleSentence(val english: String, val literalChinese: String, val chinese: String,
  val chinesePinyin: String, val chunks: List<ExampleChunk>, val wordIpa: String? = null)

data class WordEntry(val id: String, val headword: String, val ipa: String,
  val chineseMeaning: String, val chinesePinyin: String, val partOfSpeech: String,
  val stage: VocabularyStage, val examples: List<ExampleSentence>,
  val distractorWordIds: List<String>, val sourcesJson: String = "[]",
  val pronunciationNote: String = "", val sourceNote: String = "")

data class StudySettings(val dailyWords: Int = 10, val rounds: Int = StudySettingsRules.DEFAULT_ROUNDS,
  val reviewDays: List<Int> = StudySettingsRules.defaultReviewDays, val welcomed: Boolean = false,
  val displayName: String = "", val avatarId: String = "1", val themeMode: ThemeMode = ThemeMode.SYSTEM,
  val speechOptions: SpeechOptions = SpeechOptions(), val explanationOptions: ExplanationOptions = ExplanationOptions(),
  val speechPreloadWords: Int = StudySettingsRules.DEFAULT_SPEECH_PRELOAD_WORDS,
  val speechCacheMiB: Int = StudySettingsRules.DEFAULT_SPEECH_CACHE_MIB,
  val homeShuffleQueueSize: Int = StudySettingsRules.DEFAULT_HOME_SHUFFLE_QUEUE_SIZE,
  val waitForCorrectAnswerAudio: Boolean = false,
  val reviewBatchSize: Int = StudySettingsRules.DEFAULT_REVIEW_BATCH_SIZE,
  val learningSoundsEnabled: Boolean = true) {
  fun validate() {
    require(dailyWords in StudySettingsRules.storedDailyWords && rounds in StudySettingsRules.rounds)
    require(reviewDays.isNotEmpty() && reviewDays == reviewDays.distinct().sorted())
    require(reviewDays.all { it in StudySettingsRules.reviewDays })
    require(displayName.length <= 40 && displayName.none { it.isISOControl() })
    require(avatarId.length <= 40 && avatarId.none { it.isISOControl() })
    require(speechPreloadWords in StudySettingsRules.speechPreloadWords)
    require(speechCacheMiB in StudySettingsRules.speechCacheMiB)
    require(homeShuffleQueueSize in StudySettingsRules.homeShuffleQueueSize)
    require(reviewBatchSize in StudySettingsRules.reviewBatchSize)
  }
}

enum class StudyKind { NEW, REVIEW, CARRYOVER, COLLECTION, MISTAKES }
enum class CardPhase { INTRO, QUESTION, FEEDBACK, EXPLANATION, FINISHED }

data class AnswerOption(val id: String, val chinese: String, val chinesePinyin: String = "")

data class StudyCard(val id: String, val word: WordEntry, val kind: StudyKind,
  val phase: CardPhase, val round: Int, val targetRounds: Int, val reviewRecall: Boolean,
  val options: List<AnswerOption>, val selectedOptionId: String? = null,
  val correct: Boolean? = null, val isCollected: Boolean = false, val isSkipped: Boolean = false,
  val sessionId: String? = null, val correctWordId: String = word.id,
  val contextVersion: Long = 0) {
  val showContext: Boolean get() = !reviewRecall && round <= 2
  val canAnswer: Boolean get() = options.size == 4
}

data class TodaySummary(val date: String, val dailyGoal: Int, val totalWords: Int,
  val remainingWords: Int, val learnedWords: Int, val newPlanned: Int, val newCompleted: Int,
  val reviewPlanned: Int, val reviewCompleted: Int, val carryoverPlanned: Int,
  val carryoverCompleted: Int, val todayNewWords: List<WordEntry> = emptyList(),
  val nextReviewDate: String? = null, val availableNewWords: Int = 0,
  val learnMoreBlocker: StudyKind? = null, val stageProgress: StageProgress = StageProgress(),
  val collectionsAvailable: Int = 0, val mistakesAvailable: Int = 0,
  val growth: StudyGrowth = StudyGrowth(), val contextVersion: Long = 0,
  val availableReviewWords: Int = 0) {
  val planned: Int get() = newPlanned + reviewPlanned + carryoverPlanned
  val completed: Int get() = newCompleted + reviewCompleted + carryoverCompleted
  val allComplete: Boolean get() = planned > 0 && completed == planned
  val showLearnMore: Boolean get() = newCompleted == newPlanned
  val canLearnMore: Boolean get() = showLearnMore && completed == planned &&
    learnMoreBlocker == null && availableNewWords > 0
  val canReviewMore: Boolean get() = reviewCompleted == reviewPlanned && availableReviewWords > 0
}

data class PracticeProgress(val kind: StudyKind, val completed: Int, val planned: Int,
  val paused: Boolean = false)

data class StudySnapshot(val today: TodaySummary, val card: StudyCard?,
  val practice: PracticeProgress? = null, val revision: Long = 0,
  val contextVersion: Long = today.contextVersion,
  val notice: UiMessageKey? = if (card != null && !card.canAnswer) {
    if (card.phase == CardPhase.FINISHED)
      UiMessageKey.NO_RELIABLE_QUESTION_IS_AVAILABLE_FOR_THIS_WORD_NO_LEARNING_PROGRESS_WAS_RECORDED
    else UiMessageKey.NO_RELIABLE_QUESTION_IS_AVAILABLE_FOR_THIS_WORD_VIEWING_IT_DOES_NOT_COUNT_AS_LEARNED
  } else null)

interface SettingsRepository {
  val settings: kotlinx.coroutines.flow.Flow<StudySettings>
  suspend fun save(value: StudySettings)
}

interface StudyRepository {
  val changes: kotlinx.coroutines.flow.Flow<Long>
  suspend fun context(): LearningContext
  suspend fun snapshot(): StudySnapshot
  /** Reads queue candidates without creating or advancing study work. */
  suspend fun peekSpeechWords(cardId: String, nextCount: Int,
    expectedContextVersion: Long): List<WordEntry>
  suspend fun peekLearnSpeechWords(maxWords: Int, expectedContextVersion: Long,
    expectedStage: VocabularyStage): List<WordEntry>
  suspend fun peekEntrySpeechWords(expectedContextVersion: Long): List<WordEntry>
  suspend fun start(kind: StudyKind): StudySnapshot
  suspend fun selectStage(stage: VocabularyStage): StudySnapshot
  suspend fun learnMore(expectedDate: String, expectedNewPlanned: Int,
    expectedStage: VocabularyStage, expectedLap: Int): StudySnapshot
  suspend fun reviewMore(expectedDate: String, expectedReviewPlanned: Int,
    expectedContextVersion: Long): StudySnapshot
  suspend fun submit(cardId: String, optionId: String?,
    expectedContextVersion: Long? = null): StudySnapshot
  suspend fun advance(cardId: String, expectedContextVersion: Long? = null): StudySnapshot
  suspend fun explain(cardId: String, expectedContextVersion: Long? = null): StudySnapshot
}

class NewStudyDayException : UiMessageException(UiMessageKey.A_NEW_DAY_HAS_STARTED_YOUR_DAILY_PLAN_HAS_BEEN_REFRESHED)
