package com.example.flashcardforteens.core.data

import com.example.flashcardforteens.core.domain.CsvImportIssue
import com.example.flashcardforteens.core.domain.UiMessageKey
import com.example.flashcardforteens.core.domain.ExampleSentence
import com.example.flashcardforteens.core.domain.VocabularyStage
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONTokener
import java.io.File

internal class CsvIssues {
  var count: Int = 0
    private set
  val displayed = mutableListOf<CsvImportIssue>()
  fun add(line: Int, field: String, message: UiMessageKey,
    arguments: List<String> = emptyList(), fieldIndex: Int? = null) {
    count++
    if (displayed.size < 100) displayed += CsvImportIssue(line, field, message, arguments, fieldIndex)
  }
}

internal data class CsvWord(val line: Int, val order: Int, val id: String, val headword: String,
  val ipa: String, val chineseMeaning: String, val chinesePinyin: String, val partOfSpeech: String,
  val stage: Int, val examples: List<ExampleSentence>, val distractorWordIds: List<String>,
  val sourcesJson: String, val pronunciationNote: String, val sourceNote: String)

internal object CsvDecoder {
  private val wordIdPattern = Regex("[A-Za-z0-9_]{1,92}")
  private val columns = listOf("年级", "英语词条", "音标", "词条ID", "中文释义", "中文释义拼音", "词性",
    "例句JSON", "干扰词ID", "教材来源JSON", "发音说明", "来源说明")

  /** A record-only pass; quoted newlines do not increase the count. */
  suspend fun countRows(file: File, issues: CsvIssues): Int {
    var total = 0
    var names: List<String> = emptyList()
    try {
      CsvParser(file.inputStream()).use { parser ->
        names = parser.next()?.fields?.map(String::trim) ?: emptyList()
        while (true) {
          currentCoroutineContext().ensureActive()
          val record = parser.next() ?: break
          if (record.fields.size == 1 && record.fields[0].isBlank()) continue
          total++
          if (total > CSV_MAX_ROWS) {
            issues.add(record.line, "CSV", UiMessageKey.A_CSV_MAY_CONTAIN_AT_MOST_20_000_WORDS)
            break
          }
        }
      }
    } catch (error: CsvFormatException) {
      issues.add(error.line, names.getOrNull(error.fieldIndex).orEmpty(), error.code,
        fieldIndex = if (names.getOrNull(error.fieldIndex).isNullOrEmpty()) error.fieldIndex + 1 else null)
    } catch (_: java.nio.charset.CharacterCodingException) {
      issues.add(0, "CSV", UiMessageKey.THE_FILE_MUST_USE_VALID_UTF_8_ENCODING)
    }
    return total
  }

  suspend fun scan(file: File, issues: CsvIssues,
    onRecord: (Int, String?) -> Unit = { _, _ -> }, onWord: suspend (CsvWord) -> Unit): Int {
    var total = 0
    var headerNames: List<String> = emptyList()
    try {
      CsvParser(file.inputStream()).use { parser ->
        val header = parser.next()
        if (header == null) {
          issues.add(1, "CSV", UiMessageKey.THE_CSV_FILE_IS_EMPTY)
          return 0
        }
        val names = header.fields.map(String::trim)
        headerNames = names
        if (names.distinct().size != names.size) {
          issues.add(header.line, "表头", UiMessageKey.COLUMN_NAMES_MUST_BE_UNIQUE)
          return 0
        }
        val missing = columns.filterNot { it in names }
        if (missing.isNotEmpty()) {
          missing.forEach { issues.add(header.line, it, UiMessageKey.A_REQUIRED_COLUMN_IS_MISSING) }
          return 0
        }
        if (names != columns) {
          issues.add(header.line, "表头", UiMessageKey.USE_EXACTLY_THE_TWELVE_CURRENT_CSV_COLUMNS)
          return 0
        }
        val positions = names.withIndex().associate { it.value to it.index }
        while (true) {
          currentCoroutineContext().ensureActive()
          val record = parser.next() ?: break
          if (record.fields.size == 1 && record.fields[0].isBlank()) continue
          total++
          onRecord(total, record.fields.getOrNull(positions.getValue("英语词条")))
          if (total > CSV_MAX_ROWS) {
            issues.add(record.line, "CSV", UiMessageKey.A_CSV_MAY_CONTAIN_AT_MOST_20_000_WORDS)
            break
          }
          if (record.fields.size != names.size) {
            issues.add(record.line, "CSV", UiMessageKey.THE_NUMBER_OF_FIELDS_DOES_NOT_MATCH_THE_HEADER)
            continue
          }
          try { onWord(decode(record, positions, total - 1)) }
          catch (error: CsvFieldException) { issues.add(record.line, error.field, error.code) }
        }
      }
    } catch (error: CsvFormatException) {
      issues.add(error.line, headerNames.getOrNull(error.fieldIndex).orEmpty(), error.code,
        fieldIndex = if (headerNames.getOrNull(error.fieldIndex).isNullOrEmpty()) error.fieldIndex + 1 else null)
    } catch (_: java.nio.charset.CharacterCodingException) {
      issues.add(0, "CSV", UiMessageKey.THE_FILE_MUST_USE_VALID_UTF_8_ENCODING)
    }
    if (total == 0 && issues.count == 0) issues.add(2, "CSV", UiMessageKey.THE_CSV_CONTAINS_NO_WORDS)
    return total
  }

  private fun decode(record: CsvRecord, positions: Map<String, Int>, order: Int): CsvWord {
    fun invalid(field: String, message: UiMessageKey = UiMessageKey.THE_FIELD_IS_INVALID): Nothing =
      throw CsvFieldException(field, message)
    fun field(name: String, max: Int, optional: Boolean = false): String {
      val value = record.fields[positions.getValue(name)].trim()
      if ((!optional && value.isBlank()) || value.length > max || value.any {
          it.isISOControl() && !(name in setOf("例句JSON", "干扰词ID", "教材来源JSON") && it in " \t\r\n")
        })
        invalid(name, UiMessageKey.THIS_FIELD_IS_REQUIRED_AND_MUST_FIT_ITS_LENGTH_LIMIT)
      return value
    }
    fun id(value: String, name: String): String {
      if (!value.matches(wordIdPattern)) invalid(name, UiMessageKey.IDS_MUST_CONTAIN_1_92_ASCII_LETTERS_DIGITS_OR_UNDERSCORES)
      return value
    }
    fun <T> checked(name: String, block: () -> T): T = try { block() }
      catch (error: CsvFieldException) { throw error }
      catch (_: Exception) { invalid(name) }
    val stage = VocabularyStage.fromCsvGrade(field("年级", 3))?.code ?: invalid("年级")
    val wordId = id(field("词条ID", 92), "词条ID")
    // Case is teaching content: US/us and Miss/miss must remain separate entries.
    val headword = checked("英语词条") { englishText(field("英语词条", 300)) }
    val ipa = checked("音标") { ipaText(field("音标", 300)) }
    val chinese = field("中文释义", 1000)
    val pinyin = checked("中文释义拼音") { pinyinText(field("中文释义拼音", 2000)) }
    val part = field("词性", 100)
    val examples = checked("例句JSON") { decodeExamples(boundedArray(field("例句JSON", 24000))) }
    val distractors = checked("干扰词ID") {
      val array = boundedArray(field("干扰词ID", 400))
      if (array.length() != 3) invalid("干扰词ID", UiMessageKey.PROVIDE_EXACTLY_THREE_WORD_IDS_AS_A_JSON_STRING_ARRAY)
      array.strings(3).map { id(it, "干扰词ID") }
    }
    if (distractors.distinct().size != 3 || wordId in distractors)
      invalid("干扰词ID", UiMessageKey.DISTRACTOR_IDS_MUST_BE_DISTINCT_AND_CANNOT_INCLUDE_THIS_WORD)
    val sourceText = field("教材来源JSON", 12000)
    checked("教材来源JSON") {
      val source = boundedArray(sourceText)
      require(source.length() in 1..64)
      val textKeys = setOf("grade", "file", "page", "section", "module", "original", "chinese")
      source.objects(64).forEach { item ->
        require(item.keys().asSequence().toSet() == textKeys + setOf("line", "starred"))
        textKeys.forEach { key -> require(item.opt(key) is String && item.getString(key).length <= 2000 &&
          item.getString(key).none(Char::isISOControl)) }
        require(VocabularyStage.fromCsvGrade(item.getString("grade")) != null)
        val line = item.opt("line")
        require(line is Int && line >= 0)
        require(item.opt("starred") is Boolean)
      }
    }
    return CsvWord(record.line, order, wordId, headword, ipa, chinese, pinyin, part, stage, examples,
      distractors, sourceText, field("发音说明", 2000, optional = true), field("来源说明", 2000))
  }
}

/** Reject lenient org.json extensions before decoding a bounded array of typed teaching objects. */
internal fun boundedArray(value: String): JSONArray {
  StrictJson(value).read()
  val tokener = JSONTokener(value)
  val array = tokener.nextValue()
  require(array is JSONArray && tokener.nextClean() == '\u0000')
  return array
}

private class StrictJson(private val text: String) {
  private var at = 0
  fun read() {
    whitespace()
    require(at < text.length && text[at] == '[')
    value(0)
    whitespace()
    require(at == text.length)
  }
  private fun whitespace() { while (at < text.length && text[at] in " \t\r\n") at++ }
  private fun value(depth: Int) {
    whitespace()
    require(at < text.length)
    when (text[at]) {
      '"' -> string()
      '[', '{' -> container(depth + 1)
      't' -> literal("true")
      'f' -> literal("false")
      'n' -> literal("null")
      else -> number()
    }
  }
  private fun container(depth: Int) {
    require(depth <= 4)
    val objectValue = text[at++] == '{'
    val end = if (objectValue) '}' else ']'
    val keys = mutableSetOf<String>()
    whitespace()
    if (at < text.length && text[at] == end) { at++; return }
    while (true) {
      if (objectValue) {
        whitespace()
        val start = at
        string()
        val key = JSONTokener(text.substring(start, at)).nextValue() as String
        require(keys.add(key))
        whitespace()
        require(at < text.length && text[at++] == ':')
      }
      value(depth)
      whitespace()
      require(at < text.length)
      when (text[at++]) {
        end -> return
        ',' -> Unit
        else -> error("Invalid JSON separator")
      }
    }
  }
  private fun string() {
    require(at < text.length && text[at++] == '"')
    while (at < text.length) {
      val character = text[at++]
      when {
        character == '"' -> return
        character == '\\' -> {
          require(at < text.length)
          when (text[at++]) {
            '"', '\\', '/', 'b', 'f', 'n', 'r', 't' -> Unit
            'u' -> {
              require(at + 4 <= text.length && text.substring(at, at + 4).all { it in "0123456789abcdefABCDEF" })
              at += 4
            }
            else -> error("Invalid JSON escape")
          }
        }
        character.code < 0x20 -> error("Unescaped JSON control character")
      }
    }
    error("Unclosed JSON string")
  }
  private fun literal(word: String) { require(text.startsWith(word, at)); at += word.length }
  private fun number() {
    val start = at
    if (at < text.length && text[at] == '-') at++
    require(at < text.length)
    if (text[at] == '0') at++ else {
      require(text[at] in '1'..'9')
      while (at < text.length && text[at] in '0'..'9') at++
    }
    if (at < text.length && text[at] == '.') {
      at++
      val fraction = at
      while (at < text.length && text[at] in '0'..'9') at++
      require(at > fraction)
    }
    if (at < text.length && text[at] in "eE") {
      at++
      if (at < text.length && text[at] in "+-") at++
      val exponent = at
      while (at < text.length && text[at] in '0'..'9') at++
      require(at > exponent)
    }
    require(at > start)
  }
}

private class CsvFieldException(val field: String, val code: UiMessageKey) : IllegalArgumentException(code.name)
