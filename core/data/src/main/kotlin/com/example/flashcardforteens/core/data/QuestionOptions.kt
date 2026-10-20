package com.example.flashcardforteens.core.data

import com.example.flashcardforteens.core.domain.WordEntry
import java.text.Normalizer
import java.util.Locale

internal fun normalizedChinese(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFC)
  .lowercase(Locale.ROOT).filterNot { it.isWhitespace() || it in "，。！？；：、,.!?;:()（）“”\"'‘’" }

/** Split only top-level senses; brackets often contain teaching context rather than another answer. */
internal fun chineseSenseKeys(value: String): Set<String> {
  val pieces = mutableListOf<String>()
  val part = StringBuilder()
  var depth = 0
  value.forEach { character ->
    when (character) {
      '(', '（', '[', '【' -> { depth++; part.append(character) }
      ')', '）', ']', '】' -> { depth = (depth - 1).coerceAtLeast(0); part.append(character) }
      ';', '；', '、' -> if (depth == 0) { pieces += part.toString(); part.clear() } else part.append(character)
      else -> part.append(character)
    }
  }
  pieces += part.toString()
  return pieces.map(::normalizedChinese).filter(String::isNotEmpty).toSet()
}
internal fun distinctChineseAnswers(values: List<String>): Boolean {
  val seen = mutableSetOf<String>()
  return values.all { text ->
    val keys = chineseSenseKeys(text)
    keys.isNotEmpty() && keys.none { it in seen }.also { if (it) seen += keys }
  }
}

/** Supplied word IDs remain the first choice; unsafe labels are replaced from the same semester first. */
internal suspend fun questionOptions(entry: WordEntry, dao: FlashcardDao): List<String> {
  val chosen = mutableListOf(entry.id)
  val headwords = mutableSetOf(entry.headword)
  val labels = mutableListOf(entry.chineseMeaning)
  suspend fun add(id: String) {
    if (id in chosen || chosen.size == 4) return
    val word = dao.word(id) ?: return
    if (word.headword in headwords || !distinctChineseAnswers(labels + word.chineseMeaning)) return
    chosen += id
    headwords += word.headword
    labels += word.chineseMeaning
  }
  for (id in entry.distractorWordIds) add(id)
  if (chosen.size < 4) {
    for (word in dao.words().filter { it.stage <= entry.stage.code }
      .sortedBy { if (it.stage == entry.stage.code) 0 else 1 }) {
      add(word.id)
      if (chosen.size == 4) break
    }
  }
  return if (chosen.size == 4) chosen.shuffled() else emptyList()
}
