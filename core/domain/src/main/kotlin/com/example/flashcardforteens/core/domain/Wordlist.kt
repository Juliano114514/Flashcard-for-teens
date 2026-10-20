package com.example.flashcardforteens.core.domain

import kotlinx.coroutines.flow.Flow

enum class WordlistStatus { UNLEARNED, LEARNING, LEARNED, SKIPPED }

data class WordlistItem(
  val id: String,
  val headword: String,
  val ipa: String,
  val chineseMeaning: String,
  val chinesePinyin: String,
  val stage: VocabularyStage,
  val status: WordlistStatus,
  val correctRounds: Int,
  val targetRounds: Int,
  val isCollected: Boolean = false,
  val isSkipped: Boolean = false,
  val mistakePending: Boolean = false,
  val learningStatus: WordlistStatus = status,
  val partOfSpeech: String = "",
)

data class WordUserState(val isCollected: Boolean = false, val isSkipped: Boolean = false,
  val mistakePending: Boolean = false)

interface WordStateRepository {
  fun observe(wordId: String): Flow<WordUserState>
  suspend fun setCollected(wordId: String, value: Boolean)
  suspend fun setSkipped(wordId: String, value: Boolean)
}

/** Browsing does not create daily plans, learning cards, or review completions. */
interface WordlistRepository {
  val entries: Flow<List<WordlistItem>>
  suspend fun word(id: String): WordEntry?
}
