package com.example.flashcardforteens.core.domain

/** Complete playable word content, independent of automatic speech preferences. */
fun wordSpeechResources(word: WordEntry): List<SpeechRequest> =
  (wordSpeechBasics(word) + wordSpeechExamples(word)).distinctBy { Triple(it.language, it.text, it.chinesePronunciation) }

fun wordSpeechBasics(word: WordEntry): List<SpeechRequest> = buildList<SpeechRequest> {
  add(SpeechRequest(word.headword, AppLanguage.EN))
  add(SpeechRequest(word.chineseMeaning, AppLanguage.CN, SpeechScene.MEANING_PRONUNCIATION,
    chinesePronunciation = ChinesePronunciation(word.chineseMeaning, word.chinesePinyin)))
}.filter { it.text.isNotBlank() }.distinctBy { Triple(it.language, it.text, it.chinesePronunciation) }

fun wordSpeechExamples(word: WordEntry): List<SpeechRequest> = buildList<SpeechRequest> {
  word.examples.forEach { example ->
    add(SpeechRequest(example.english, AppLanguage.EN, SpeechScene.EXAMPLE_PRONUNCIATION))
    add(SpeechRequest(example.chinese, AppLanguage.CN, SpeechScene.EXAMPLE_PRONUNCIATION,
      chinesePronunciation = ChinesePronunciation(example.chinese, example.chinesePinyin)))
  }
}.filter { it.text.isNotBlank() }.distinctBy { Triple(it.language, it.text, it.chinesePronunciation) }
