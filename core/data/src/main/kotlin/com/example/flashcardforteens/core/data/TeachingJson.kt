package com.example.flashcardforteens.core.data

import com.example.flashcardforteens.core.domain.ExampleChunk
import com.example.flashcardforteens.core.domain.ExampleSentence
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer

internal fun JSONArray.objects(limit: Int): List<JSONObject> {
  require(length() <= limit)
  return List(length()) { getJSONObject(it) }
}
internal fun JSONArray.strings(limit: Int): List<String> {
  require(length() <= limit)
  return List(length()) { index ->
    require(get(index) is String)
    getString(index)
  }
}
internal fun strings(json: JSONArray): List<String> = json.strings(2000)
internal fun stringsJson(values: List<String>): String = JSONArray(values).toString()
internal fun daysJson(values: List<Int>): String = JSONArray(values).toString()
internal fun days(json: String): List<Int> = JSONArray(json).let { array ->
  require(array.length() in 1..6)
  List(array.length()) { array.getInt(it) }
}

internal fun decodeExamples(array: JSONArray): List<ExampleSentence> {
  require(array.length() in 1..8)
  return array.objects(8).map { example ->
    val required = setOf("english", "literalChinese", "chinese", "chinesePinyin", "chunks")
    val keys = example.keys().asSequence().toSet()
    require(keys == required || keys == required + "wordIpa")
    val chunks = example.getJSONArray("chunks").objects(64).also { require(it.isNotEmpty()) }.map { chunk ->
      require(chunk.keys().asSequence().toSet() == setOf("english", "chinese", "chinesePinyin"))
      ExampleChunk(englishText(chunk.teachingText("english", 1000)),
        chunk.teachingText("chinese", 1000), pinyinText(chunk.teachingText("chinesePinyin", 2000)))
    }
    val english = englishText(example.teachingText("english", 2000))
    val literal = example.teachingText("literalChinese", 3000)
    require(chunks.joinToString(" ") { it.english } == english)
    require(chunks.joinToString(" | ") { it.chinese } == literal)
    ExampleSentence(english, literal, example.teachingText("chinese", 2000),
      pinyinText(example.teachingText("chinesePinyin", 4000)), chunks,
      if (example.has("wordIpa")) ipaText(example.teachingText("wordIpa", 300)) else null)
  }
}

internal fun JSONObject.teachingText(name: String, maximum: Int): String {
  require(opt(name) is String)
  return getString(name).also {
    require(it.isNotBlank() && it.length <= maximum && it.none(Char::isISOControl))
  }
}
internal fun englishText(value: String): String = value.also {
  require(it.any { character -> character in 'A'..'Z' || character in 'a'..'z' })
  require(it.codePoints().toArray().none { point -> Character.UnicodeScript.of(point) == Character.UnicodeScript.HAN })
}
/** Shape validation only: mixed ASCII names, numbers, punctuation and neutral syllables are valid. */
internal fun pinyinText(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFC).also {
  require(it.isNotBlank() && it.none(Char::isISOControl))
  require(it.codePoints().toArray().all { point ->
    val script = Character.UnicodeScript.of(point)
    script == Character.UnicodeScript.LATIN || script == Character.UnicodeScript.COMMON ||
      script == Character.UnicodeScript.INHERITED
  })
}
internal fun ipaText(value: String): String = value.also {
  require(it.startsWith('/') && it.endsWith('/') && it.none(Char::isISOControl))
}
internal fun examplesJson(values: List<ExampleSentence>): String = JSONArray(values.map {
  JSONObject().put("english", it.english).put("literalChinese", it.literalChinese)
    .put("chinese", it.chinese).put("chinesePinyin", it.chinesePinyin)
    .put("chunks", JSONArray(it.chunks.map { chunk ->
      JSONObject().put("english", chunk.english).put("chinese", chunk.chinese)
        .put("chinesePinyin", chunk.chinesePinyin)
    })).also { json -> it.wordIpa?.let { ipa -> json.put("wordIpa", ipa) } }
}).toString()
