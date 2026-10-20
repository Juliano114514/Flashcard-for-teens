package com.example.flashcardforteens.core.domain

enum class SpeechScene {
  HOME_WORD, STUDY_PROMPT, STUDY_CONTEXT, STUDY_EXPLANATION, ANSWER_FEEDBACK,
  WORD_DETAIL, WORD_PRONUNCIATION, MEANING_PRONUNCIATION, EXAMPLE_PRONUNCIATION,
  STUDY_EXPLANATION_WORD, STUDY_EXPLANATION_EXAMPLE,
}

enum class SpeechMode {
  NONE, ONLY_EN, ONLY_CN, BOTH;

  fun allows(language: AppLanguage): Boolean = when (this) {
    NONE -> false
    ONLY_EN -> language == AppLanguage.EN
    ONLY_CN -> language == AppLanguage.CN
    BOTH -> true
  }

  companion object {
    fun forLanguage(language: AppLanguage) = if (language == AppLanguage.EN) ONLY_EN else ONLY_CN
  }
}

/** Standalone words default to both languages; question audio always remains English-only. */
data class SpeechOptions(val modes: Map<SpeechScene, SpeechMode> = emptyMap()) {
  fun mode(scene: SpeechScene, defaultLanguage: AppLanguage): SpeechMode =
    modes[scene] ?: when (scene) {
      SpeechScene.HOME_WORD, SpeechScene.WORD_DETAIL, SpeechScene.WORD_PRONUNCIATION -> SpeechMode.BOTH
      // Keep explicit legacy explanation preferences when splitting word and sentence playback.
      SpeechScene.STUDY_EXPLANATION_WORD, SpeechScene.STUDY_EXPLANATION_EXAMPLE ->
        modes[SpeechScene.STUDY_EXPLANATION] ?: SpeechMode.BOTH
      SpeechScene.MEANING_PRONUNCIATION, SpeechScene.EXAMPLE_PRONUNCIATION -> SpeechMode.forLanguage(defaultLanguage)
      else -> SpeechMode.ONLY_EN
    }

  fun withMode(scene: SpeechScene, mode: SpeechMode) = copy(modes = modes + (scene to mode))

  fun withLanguage(scene: SpeechScene, language: AppLanguage, enabled: Boolean): SpeechOptions {
    val current = mode(scene, AppLanguage.EN)
    val english = if (language == AppLanguage.EN) enabled else current.allows(AppLanguage.EN)
    val chinese = if (language == AppLanguage.CN) enabled else current.allows(AppLanguage.CN)
    return withMode(scene, when {
      english && chinese -> SpeechMode.BOTH
      english -> SpeechMode.ONLY_EN
      chinese -> SpeechMode.ONLY_CN
      else -> SpeechMode.NONE
    })
  }

  fun requests(request: SpeechRequest): List<SpeechRequest> {
    // Question audio cannot disclose the answer, even if a saved preference enables both languages.
    if (request.scene == SpeechScene.STUDY_PROMPT || request.scene == SpeechScene.STUDY_CONTEXT) {
      return if (request.language == AppLanguage.EN && mode(request.scene, AppLanguage.EN).allows(AppLanguage.EN))
        listOf(request.copy(counterpart = null, chinesePronunciation = null)) else emptyList()
    }
    val mode = mode(request.scene, request.language)
    val otherLanguage = if (request.language == AppLanguage.CN) AppLanguage.EN else AppLanguage.CN
    val candidates = listOfNotNull(request, request.counterpart?.takeIf(String::isNotBlank)?.let {
      SpeechRequest(it, otherLanguage, request.scene, chinesePronunciation = request.chinesePronunciation)
    })
    return candidates.filter { mode.allows(it.language) && it.text.isNotBlank() }
      .map { part -> part.copy(counterpart = null, chinesePronunciation = part.chinesePronunciation?.takeIf {
        part.language == AppLanguage.CN && it.text == part.text
      }) }
  }
}

data class ExplanationOptions(val original: Boolean = true,
  val literal: Boolean = false, val translation: Boolean = true) {
  val allSelected: Boolean get() = original && literal && translation
  val mask: Int get() = (if (original) 1 else 0) or (if (literal) 2 else 0) or (if (translation) 4 else 0)

  companion object {
    // Retain the stored bit layout while keeping both sentence languages visible.
    fun fromMask(mask: Int) = ExplanationOptions(literal = mask and 2 != 0)
  }
}
