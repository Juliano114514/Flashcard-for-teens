package com.example.flashcardforteens.core.data

import android.content.Context
import androidx.room.withTransaction
import com.example.flashcardforteens.core.domain.CsvImportPreview
import com.example.flashcardforteens.core.domain.UiMessageKey
import com.example.flashcardforteens.core.domain.UiMessageException
import com.example.flashcardforteens.core.domain.CsvImportReport
import com.example.flashcardforteens.core.domain.CsvImportRepository
import com.example.flashcardforteens.core.domain.CsvSource
import com.example.flashcardforteens.core.domain.WordlistLoadProgress
import com.example.flashcardforteens.core.domain.WordlistLoadStage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** User imports append only; only the bundled preset can refresh owned teaching content. */
internal class CsvImporter(private val context: Context, private val database: FlashcardDatabase,
  private val dao: FlashcardDao, private val operationMutex: Mutex,
  private val prepare: suspend () -> Unit, private val touch: suspend () -> Unit,
  private val reconcilePresetStages: suspend () -> Unit) : CsvImportRepository {
  private val pendingMutex = Mutex()
  private data class PendingCsv(val file: File, val totalRows: Int)
  private val pending = mutableMapOf<String, PendingCsv>()
  private val directory = File(context.cacheDir, "csv-import")
  private var cleaned = false
  private val mutableProgress = MutableStateFlow<WordlistLoadProgress?>(null)
  override val progress = mutableProgress.asStateFlow()

  /** Called under the repository's seed lock; never calls back into prepare(). */
  internal suspend fun bootstrapDefaultWordlist(onProgress: (WordlistLoadProgress) -> Unit): Unit = withContext(Dispatchers.IO) {
    val reporter = WordlistProgressReporter(onProgress)
    val version = defaultWordlistVersion()
    operationMutex.withLock {
      val saved = dao.appState()
      if (saved?.seeded == true && saved.presetCsvVersion == version) return@withLock
      val expectedRows = defaultWordlistRows()
      reporter.totalWords(expectedRows)
      val file = createSnapshot()
      try {
        copySource(CsvSource { context.assets.open("default-wordlist/wordlist.csv") }, file)
        check(snapshotHash(file) == version) { UiMessageKey.THE_BUNDLED_DEFAULT_WORDLIST_VERSION_DOES_NOT_MATCH_ITS_CONTENT }
        val issues = CsvIssues()
        val plan = validate(file, readExisting(), issues, reporter, expectedRows, preset = true)
        check(issues.count == 0) {
          (issues.displayed.firstOrNull()?.message ?: UiMessageKey.THE_BUNDLED_CSV_IS_INVALID).name
        }
        check(plan.totalRows == expectedRows) { UiMessageKey.THE_BUNDLED_WORDLIST_COUNT_DOES_NOT_MATCH_ITS_CONTENT }
        validatePendingCards(plan)
        currentCoroutineContext().ensureActive()
        database.withTransaction {
          if (dao.settings() == null) dao.putSettings(SettingsEntity())
          if (dao.appState() == null) dao.putAppState(AppStateEntity())
          val report = append(plan, reporter)
          // Stage ownership follows the final preset classification in this same atomic commit.
          reconcilePresetStages()
          if (report.addedWords > 0 || plan.refreshIds.isNotEmpty()) touch()
          currentCoroutineContext().ensureActive()
          reporter.update(WordlistLoadStage.COMMITTING)
          val latest = checkNotNull(dao.appState())
          dao.putAppState(latest.copy(seeded = true, presetCsvVersion = version))
        }
        reporter.finish()
      } finally {
        file.delete()
      }
    }
  }

  private fun defaultWordlistRows(): Int = context.assets.open("default-wordlist/rows.txt")
    .bufferedReader(Charsets.US_ASCII).use { reader ->
      val buffer = CharArray(32)
      val count = reader.read(buffer)
      check(count in 1..31 && reader.read() == -1) { UiMessageKey.THE_BUNDLED_WORDLIST_COUNT_IS_INVALID }
      String(buffer, 0, count).trim().toIntOrNull()?.takeIf { it in 1..CSV_MAX_ROWS }
        ?: error(UiMessageKey.THE_BUNDLED_WORDLIST_COUNT_IS_INVALID)
    }

  private fun defaultWordlistVersion(): String = context.assets.open("default-wordlist/version.txt")
    .bufferedReader(Charsets.US_ASCII).use { reader ->
      val text = StringBuilder()
      while (true) {
        val character = reader.read()
        if (character < 0) break
        check(text.length < 128) { UiMessageKey.THE_BUNDLED_DEFAULT_WORDLIST_VERSION_IS_INVALID }
        text.append(character.toChar())
      }
      text.toString().trim().also {
        check(it.matches(Regex("[0-9a-f]{64}"))) { UiMessageKey.THE_BUNDLED_DEFAULT_WORDLIST_VERSION_IS_INVALID }
      }
    }

  private suspend fun snapshotHash(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
      val block = ByteArray(8192)
      while (true) {
        currentCoroutineContext().ensureActive()
        val count = input.read(block)
        if (count < 0) break
        digest.update(block, 0, count)
      }
    }
    return digest.digest().joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
  }

  override suspend fun preview(source: CsvSource): CsvImportPreview {
    val reporter = WordlistProgressReporter({ mutableProgress.value = it }, previewOnly = true)
    var deliveredToken: String? = null
    try {
      return withContext(Dispatchers.IO) {
        prepare()
        val file = createSnapshot()
        var retained = false
        try {
          copySource(source, file)
          val issues = CsvIssues()
          val totalRows = CsvDecoder.countRows(file, issues)
          if (issues.count > 0)
            return@withContext CsvImportPreview(null, totalRows, 0, 0, issues.count, issues.displayed.toList())
          reporter.totalWords(totalRows)
          val existing = operationMutex.withLock { readExisting() }
          val plan = validate(file, existing, issues, reporter, totalRows)
          if (issues.count > 0)
            return@withContext CsvImportPreview(null, plan.totalRows, plan.newIds.size,
              plan.skipped, issues.count, issues.displayed.toList())
          currentCoroutineContext().ensureActive()
          val token = UUID.randomUUID().toString()
          pendingMutex.withLock {
            // Only one uncommitted preview is retained; commits remove their file before reading it.
            pending.values.forEach { it.file.delete() }
            pending.clear()
            pending[token] = PendingCsv(file, plan.totalRows)
          }
          deliveredToken = token
          retained = true
          currentCoroutineContext().ensureActive()
          reporter.finish()
          CsvImportPreview(token, plan.totalRows, plan.newIds.size, plan.skipped, 0, emptyList())
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) {
          val issues = CsvIssues().also { it.add(0, "CSV", UiMessageKey.THE_FILE_COULD_NOT_BE_READ_CHOOSE_A_VALID_UTF_8_CSV_UP_TO_32_MIB) }
          CsvImportPreview(null, 0, 0, 0, issues.count, issues.displayed.toList())
        } finally {
          if (!retained) file.delete()
        }
      }
    } catch (error: CancellationException) {
      // withContext may cancel delivery after the IO block has registered a valid token.
      deliveredToken?.let { token -> withContext(NonCancellable) { discard(token) } }
      throw error
    }
  }

  override suspend fun commit(previewId: String): CsvImportReport {
    val reporter = WordlistProgressReporter({ mutableProgress.value = it })
    return withContext(Dispatchers.IO) {
      val snapshot = pendingMutex.withLock { pending.remove(previewId) }
        ?: throw UiMessageException(UiMessageKey.THIS_PREVIEW_HAS_EXPIRED_CHOOSE_THE_CSV_AGAIN)
      val file = snapshot.file
      reporter.totalWords(snapshot.totalRows)
      try {
        prepare()
        operationMutex.withLock {
          val issues = CsvIssues()
          val plan = validate(file, readExisting(), issues, reporter, snapshot.totalRows)
          check(issues.count == 0) { UiMessageKey.THE_WORDBOOK_CHANGED_OR_THE_CSV_IS_INVALID_CHOOSE_THE_FILE_AGAIN }
          check(plan.totalRows == snapshot.totalRows) { UiMessageKey.THE_CSV_SNAPSHOT_CHANGED }
          currentCoroutineContext().ensureActive()
          val report = database.withTransaction {
            append(plan, reporter).also {
              reporter.update(WordlistLoadStage.COMMITTING)
            }
          }
          reporter.finish()
          report
        }
      } finally {
        // discard is idempotent and cannot delete a file already owned by a running commit.
        file.delete()
      }
    }
  }

  override suspend fun discard(previewId: String) = withContext(NonCancellable + Dispatchers.IO) {
    pendingMutex.withLock { pending.remove(previewId)?.file?.delete() }
    Unit
  }

  suspend fun cleanStaleSnapshots() = pendingMutex.withLock {
    if (cleaned) return@withLock
    check(directory.canonicalFile.parentFile == context.cacheDir.canonicalFile)
    if (directory.exists()) {
      check(directory.isDirectory) { UiMessageKey.THE_CSV_CACHE_IS_UNAVAILABLE }
      directory.listFiles()?.filter { it.isFile && it.name.startsWith("preview-") && it.name.endsWith(".csv") }
        ?.forEach { it.delete() }
    }
    cleaned = true
  }

  private suspend fun createSnapshot(): File {
    cleanStaleSnapshots()
    return pendingMutex.withLock {
      check(directory.canonicalFile.parentFile == context.cacheDir.canonicalFile)
      check(directory.isDirectory || directory.mkdirs()) { UiMessageKey.THE_CSV_CACHE_IS_UNAVAILABLE }
      File.createTempFile("preview-", ".csv", directory)
    }
  }

  private suspend fun copySource(source: CsvSource, target: File) {
    source.open().use { input ->
      target.outputStream().use { output ->
        val block = ByteArray(8192)
        var bytes = 0L
        while (true) {
          currentCoroutineContext().ensureActive()
          val count = input.read(block)
          if (count < 0) break
          if (count == 0) {
            val single = input.read()
            if (single < 0) break
            bytes++
            require(bytes <= CSV_MAX_BYTES) { UiMessageKey.THE_CSV_EXCEEDS_32_MIB }
            output.write(single)
          } else {
            bytes += count
            require(bytes <= CSV_MAX_BYTES) { UiMessageKey.THE_CSV_EXCEEDS_32_MIB }
            output.write(block, 0, count)
          }
        }
      }
    }
  }

  private suspend fun readExisting(): Map<String, WordEntity> = dao.words().associateBy { it.id }

  private suspend fun validate(file: File, existing: Map<String, WordEntity>, issues: CsvIssues,
    reporter: WordlistProgressReporter, totalRows: Int, preset: Boolean = false): ImportPlan {
    val rows = mutableListOf<CsvWord>()
    val existingHeadwords = existing.values.map { it.headword }.toSet()
    val seenIds = mutableSetOf<String>()
    val seenHeadwords = mutableSetOf<String>()
    val newIds = mutableSetOf<String>()
    val refreshIds = mutableSetOf<String>()
    var skipped = 0
    reporter.update(WordlistLoadStage.CHECKING, 0, totalRows)
    val total = CsvDecoder.scan(file, issues, onRecord = { count, word ->
      reporter.update(WordlistLoadStage.CHECKING, count, totalRows, word, processedWords = count)
    }) { word ->
      var valid = true
      if (!seenIds.add(word.id)) {
        issues.add(word.line, "词条ID", UiMessageKey.WORD_IDS_MUST_BE_UNIQUE_WITHIN_THE_CSV)
        valid = false
      }
      if (!seenHeadwords.add(word.headword)) {
        issues.add(word.line, "英语词条", UiMessageKey.THE_FIELD_IS_INVALID)
        valid = false
      }
      val sameId = existing[word.id]
      if (sameId != null && sameId.headword != word.headword) {
        issues.add(word.line, "词条ID", UiMessageKey.THIS_ID_ALREADY_BELONGS_TO_A_DIFFERENT_WORD_OR_PRONUNCIATION)
        valid = false
      }
      if (sameId == null && word.headword in existingHeadwords) {
        issues.add(word.line, "英语词条", UiMessageKey.THE_FIELD_IS_INVALID)
        valid = false
      }
      if (preset && sameId != null && sameId.presetSourceId != word.id) {
        issues.add(word.line, "词条ID", UiMessageKey.AN_OWNED_PRESET_ID_CANNOT_BE_REASSIGNED_TO_A_DIFFERENT_WORD)
        valid = false
      }
      if (valid) {
        if (sameId == null) newIds += word.id else {
          skipped++
          if (preset) refreshIds += word.id
        }
        rows += word
      }
    }
    if (existing.size + newIds.size > CSV_MAX_ROWS)
      issues.add(0, "CSV", UiMessageKey.THE_RESULTING_WORDBOOK_WOULD_CONTAIN_MORE_THAN_20_000_WORDS)
    val projected = existing.toMutableMap()
    rows.filter { it.id in newIds || it.id in refreshIds }.forEach { word ->
      projected[word.id] = word.entity(word.order, if (preset) word.id else "")
    }
    for (word in rows) {
      val ids = listOf(word.id) + word.distractorWordIds
      if (ids.any { it !in projected }) {
        issues.add(word.line, "干扰词ID", UiMessageKey.A_DISTRACTOR_WORD_IS_MISSING_FROM_THE_CSV_AND_SAVED_WORDBOOK)
      } else if (!distinctChineseAnswers(ids.map { projected.getValue(it).chineseMeaning })) {
        issues.add(word.line, "干扰词ID", UiMessageKey.THE_CORRECT_MEANING_AND_THREE_DISTRACTOR_MEANINGS_MUST_DIFFER)
      }
    }
    return ImportPlan(total, skipped, rows, newIds, refreshIds, preset, projected)
  }

  /** Cards freeze IDs and order. A preset refresh must preserve the already chosen answer. */
  private suspend fun validatePendingCards(plan: ImportPlan) {
    for (card in dao.unfinishedCardsAll()) {
      currentCoroutineContext().ensureActive()
      val optionIds = strings(JSONArray(card.optionsJson))
      if (optionIds.isEmpty()) continue
      val options = optionIds.map { plan.projected[it] ?: throw PresetCardConflictException() }
      if (optionIds.size != 4 || optionIds.distinct().size != 4 || card.correctWordId != card.wordId ||
          card.correctWordId !in optionIds || !distinctChineseAnswers(options.map { it.chineseMeaning }) ||
          options.map { it.headword }.distinct().size != 4 ||
          card.selectedOptionId?.let { it !in optionIds } == true) throw PresetCardConflictException()
    }
  }

  private suspend fun append(plan: ImportPlan, reporter: WordlistProgressReporter): CsvImportReport {
    val batch = mutableListOf<WordEntity>()
    val nextOrder = (dao.words().maxOfOrNull { it.sortOrder } ?: -1) + 1
    var added = 0
    reporter.update(WordlistLoadStage.SAVING, 0, plan.totalRows)
    plan.rows.forEachIndexed { index, word ->
      currentCoroutineContext().ensureActive()
      if (word.id in plan.newIds || word.id in plan.refreshIds) {
        val order = if (plan.preset) word.order else nextOrder + added
        batch += word.entity(order, if (plan.preset) word.id else "")
        if (word.id in plan.newIds) added++
        if (batch.size >= 100) { dao.putWords(batch.toList()); batch.clear() }
      }
      reporter.update(WordlistLoadStage.SAVING, index + 1, plan.totalRows, word.headword)
    }
    if (batch.isNotEmpty()) dao.putWords(batch.toList())
    check(added == plan.newIds.size) { UiMessageKey.THE_CSV_SNAPSHOT_CHANGED }
    if (added > 0 && !plan.preset) touch()
    return CsvImportReport(added, plan.skipped)
  }
}

private fun CsvWord.entity(order: Int, sourceId: String) = WordEntity(id, headword, ipa,
  chineseMeaning, chinesePinyin, partOfSpeech, stage, examplesJson(examples), stringsJson(distractorWordIds),
  order, sourcesJson, pronunciationNote, sourceNote, sourceId)
private data class ImportPlan(val totalRows: Int, val skipped: Int, val rows: List<CsvWord>,
  val newIds: Set<String>, val refreshIds: Set<String>, val preset: Boolean,
  val projected: Map<String, WordEntity>)

internal class PresetCardConflictException : UiMessageException(
  UiMessageKey.FINISH_THE_SAVED_STUDY_CARD_THEN_RETRY_THE_DEFAULT_WORDLIST_UPDATE)
