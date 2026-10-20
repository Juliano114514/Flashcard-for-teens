package com.example.flashcardforteens.core.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import com.example.flashcardforteens.core.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

data class DefaultWordlistState(val loading: Boolean = false, val error: UiMessageKey? = null)

/** One application-scoped owner. The rebuilt library starts in its own database. */
class FlashcardRepositories(context: Context) {
  private val app = context.applicationContext
  private val database = Room.databaseBuilder(app, FlashcardDatabase::class.java, "flashcard-for-teens-v1.db")
    .addMigrations(Migration1To2).build()
  private val rawDao = database.flashcards()
  private val dao = rawDao
  private val seedMutex = Mutex()
  private val operationMutex = Mutex()
  private var prepared = false
  private val mutableDefaultWordlistState = MutableStateFlow(DefaultWordlistState())
  val defaultWordlistState = mutableDefaultWordlistState.asStateFlow()
  private val mutableDefaultWordlistProgress = MutableStateFlow<WordlistLoadProgress?>(null)
  val defaultWordlistProgress = mutableDefaultWordlistProgress.asStateFlow()

  val study: StudyRepository = LocalStudy()
  val settings: SettingsRepository = LocalSettings()
  val wordlist: WordlistRepository = LocalWordlist()
  val wordState: WordStateRepository = LocalWordState()
  private val csvImporter: CsvImporter = CsvImporter(app, database, rawDao, operationMutex,
    ::prepare, ::touch, ::reconcilePresetStages)
  val csvImport: CsvImportRepository = csvImporter

  suspend fun prepare() = prepareDefaultWordlist(forceRetry = false)

  suspend fun retryDefaultWordlist() = prepareDefaultWordlist(forceRetry = true)

  private suspend fun prepareDefaultWordlist(forceRetry: Boolean) = withContext(Dispatchers.IO) {
    seedMutex.withLock {
      if (prepared && !forceRetry) return@withLock
      val previousStatus = mutableDefaultWordlistState.value
      mutableDefaultWordlistProgress.value = WordlistLoadProgress()
      mutableDefaultWordlistState.value = DefaultWordlistState(loading = true)
      var hasSavedWordlist = false
      try {
        hasSavedWordlist = rawDao.appState()?.seeded == true
        csvImporter.cleanStaleSnapshots()
        csvImporter.bootstrapDefaultWordlist { mutableDefaultWordlistProgress.value = it }
        prepared = true
        mutableDefaultWordlistState.value = DefaultWordlistState()
      } catch (error: CancellationException) {
        mutableDefaultWordlistState.value = previousStatus.copy(loading = false)
        throw error
      } catch (error: Exception) {
        mutableDefaultWordlistState.value = DefaultWordlistState(error =
          if (error is PresetCardConflictException) error.code else UiMessageKey.DEFAULT_WORDLIST_COULDN_T_BE_LOADED)
        if (hasSavedWordlist) {
          // Preserve the usable library and avoid retrying on every snapshot in this process.
          prepared = true
        } else {
          throw UiMessageException(UiMessageKey.DEFAULT_WORDLIST_COULDN_T_BE_LOADED, error)
        }
      }
    }
  }

  private suspend fun <T> transaction(block: suspend () -> T): T {
    prepare()
    return operationMutex.withLock {
      database.withTransaction {
        block()
      }
    }
  }

  private fun today(): Long = LocalDate.now(ZoneId.systemDefault()).toEpochDay()
  private fun newId(): String = UUID.randomUUID().toString()
  private suspend fun appState(): AppStateEntity = checkNotNull(dao.appState())
  private suspend fun savedSettings(): StudySettings = checkNotNull(dao.settings()).toDomain().also { it.validate() }
  private suspend fun touch() {
    val state = appState()
    dao.putAppState(state.copy(revision = state.revision + 1))
  }

  private suspend fun reconcilePresetStages() {
    val words = dao.studyWords().associateBy { it.id }
    val states = dao.stageStates().associateBy { it.stage }.toMutableMap()
    for (stage in words.values.map { it.stage }.distinct()) {
      if (stage !in states) {
        val state = StageStateEntity(stage)
        dao.putStageState(state)
        states[stage] = state
      }
    }
    for (progress in dao.progress()) {
      val passedDay = progress.firstPassedDay ?: continue
      val word = words[progress.wordId] ?: continue
      dao.putStageCompletion(StageCompletionEntity(word.stage, 1, word.id, passedDay))
    }
    val saved = appState()
    val current = saved.currentCardId?.let { dao.card(it) }
    val currentOriginalStage = current?.takeIf {
      it.kind == StudyKind.NEW.name && it.phase != CardPhase.FINISHED.name
    }?.let { card ->
      dailyItem(card)?.originStage ?: dao.cycle(card.cycleId)?.originStage
    }
    val cycles = dao.activeCycles().filter { it.scope == "NORMAL" || it.scope == "STAGE_REPEAT" }
      .associateBy { it.id }.toMutableMap()
    for (cycle in cycles.values.toList()) {
      val word = words[cycle.wordId] ?: continue
      // Review relearning is shared and intentionally has no stage/lap ownership.
      if (cycle.originStage == null || cycle.originStage == word.stage) continue
      val shifted = cycle.copy(originStage = word.stage, originLap = checkNotNull(states[word.stage]).lap)
      dao.putCycle(shifted)
      cycles[cycle.id] = shifted
    }
    saved.day?.let { day ->
      val goal = dao.plan(day)?.dailyGoal
      for (item in dao.dailyItems(day).filter { it.kind == StudyKind.NEW.name || it.kind == StudyKind.CARRYOVER.name }) {
        val word = words[item.wordId] ?: continue
        val cycle = cycles[item.cycleId]
        if (item.originStage == null && cycle?.originStage == null) continue
        val lap = if (!item.completed && cycle?.originStage == word.stage) cycle.originLap else item.originLap
        val shifted = item.copy(originStage = word.stage, originLap = lap)
        if (shifted != item) dao.putDailyItem(shifted)
        if (item.kind == StudyKind.NEW.name && goal != null && dao.stagePlan(day, word.stage) == null) {
          // Preserve the frozen quota and all existing tasks; classification changes do not enqueue work.
          dao.putStagePlan(StageDailyPlanEntity(day, word.stage, goal))
        }
      }
    }
    if (currentOriginalStage != null) {
      current?.let { card -> words[card.wordId] }?.takeIf { it.stage != currentOriginalStage }?.let { word ->
        dao.putAppState(appState().copy(selectedStage = word.stage))
      }
    }
  }

  private inner class LocalSettings : SettingsRepository {
    override val settings: Flow<StudySettings> = flow {
      prepare()
      emitAll(dao.observeSettings().map { checkNotNull(it).toDomain().also(StudySettings::validate) }.distinctUntilChanged())
    }
    override suspend fun save(value: StudySettings) {
      value.validate()
      transaction {
        dao.putSettings(SettingsEntity(dailyWords = value.dailyWords, rounds = value.rounds,
          reviewDaysJson = daysJson(value.reviewDays), welcomed = value.welcomed,
          displayName = value.displayName.trim(), avatarId = value.avatarId, themeMode = value.themeMode.name,
          speechModesJson = JSONObject().apply {
            value.speechOptions.modes.forEach { (scene, mode) -> put(scene.name, mode.name) }
          }.toString(), explanationMask = value.explanationOptions.mask,
          speechPreloadWords = value.speechPreloadWords, speechCacheMiB = value.speechCacheMiB,
          homeShuffleQueueSize = value.homeShuffleQueueSize,
          waitForCorrectAnswerAudio = value.waitForCorrectAnswerAudio, reviewBatchSize = value.reviewBatchSize,
          learningSoundsEnabled = value.learningSoundsEnabled))
        touch()
      }
    }
  }

  private inner class LocalWordlist : WordlistRepository {
    override val entries: Flow<List<WordlistItem>> = flow {
      prepare()
      emitAll(dao.observeWordlist().map { rows -> rows.map { row ->
        val learningStatus = when {
          row.firstPassedDay != null -> WordlistStatus.LEARNED
          row.firstEncounterShown -> WordlistStatus.LEARNING
          else -> WordlistStatus.UNLEARNED
        }
        WordlistItem(row.id, row.headword, row.ipa, row.chineseMeaning, row.chinesePinyin,
          checkNotNull(VocabularyStage.fromCode(row.stage)),
          if (row.isSkipped) WordlistStatus.SKIPPED else learningStatus,
          row.correctRounds, row.targetRounds, row.isCollected, row.isSkipped, row.mistakePending,
          learningStatus, row.partOfSpeech)
      } }.distinctUntilChanged())
    }.flowOn(Dispatchers.Default)

    override suspend fun word(id: String): WordEntry? = transaction {
      dao.word(id)?.let { this@FlashcardRepositories.word(it) }
    }
  }

  private inner class LocalWordState : WordStateRepository {
    override fun observe(wordId: String): Flow<WordUserState> = flow {
      prepare()
      emitAll(dao.observeProgress(wordId).map { value ->
        WordUserState(value?.isCollected == true, value?.isSkipped == true, value?.mistakePending == true)
      }.distinctUntilChanged())
    }

    override suspend fun setCollected(wordId: String, value: Boolean) = transaction {
      checkNotNull(dao.word(wordId))
      val saved = dao.progress(wordId) ?: WordProgressEntity(wordId)
      if (saved.isCollected != value) {
        dao.putProgress(saved.copy(isCollected = value))
        touch()
      }
    }

    override suspend fun setSkipped(wordId: String, value: Boolean) = transaction {
      checkNotNull(dao.word(wordId))
      val day = ensureToday()
      val saved = dao.progress(wordId) ?: WordProgressEntity(wordId)
      if (saved.isSkipped == value) return@transaction
      dao.putProgress(saved.copy(isSkipped = value))
      if (value) {
        // Pausing a word preserves its cycle and due nodes, but closes every visible card.
        val state = appState()
        val current = state.currentCardId?.let { dao.card(it) }
        dao.unfinishedCards().filter { it.wordId == wordId }.forEach { card ->
          dao.putCard(card.copy(phase = CardPhase.FINISHED.name))
        }
        if (current?.wordId == wordId && current.phase != CardPhase.FINISHED.name) {
          val next = if (current.sessionId != null) createPracticeCard(current.sessionId, day)
          else createNext(day, StudyKind.valueOf(current.kind))
          dao.putAppState(appState().copy(currentCardId = next?.id ?: current.id))
        }
      } else restorePausedWork(day, wordId)
      touch()
    }
  }

  private inner class LocalStudy : StudyRepository {
    override val changes: Flow<Long> = flow {
      prepare()
      emitAll(dao.observeRevision().map { checkNotNull(it) }.distinctUntilChanged())
    }

    override suspend fun snapshot(): StudySnapshot = transaction { snapshotAt(ensureToday()) }

    override suspend fun peekSpeechWords(cardId: String, nextCount: Int,
      expectedContextVersion: Long): List<WordEntry> {
      require(nextCount in 0..20)
      // Unlike transaction(), this path must not bootstrap data or refresh the daily plan.
      return operationMutex.withLock {
        database.withTransaction {
          val speechDao = rawDao
          val state = speechDao.appState() ?: return@withTransaction emptyList()
          if (!state.seeded || state.contextVersion != expectedContextVersion || state.currentCardId != cardId)
            return@withTransaction emptyList()
          val card = speechDao.card(cardId) ?: return@withTransaction emptyList()
          if (card.phase == CardPhase.FINISHED.name || (card.sessionId == null && card.day != today()))
            return@withTransaction emptyList()
          val progress = speechDao.progress().associateBy { it.wordId }
          if (progress[card.wordId]?.isSkipped == true) return@withTransaction emptyList()
          val upcoming = if (card.sessionId != null) {
            val session = speechDao.practiceSession(card.sessionId) ?: return@withTransaction emptyList()
            val viewOnly = speechDao.viewOnlyPracticeWords(session.id).toSet()
            speechDao.practiceItems(session.id).filter {
              !it.completed && it.wordId !in viewOnly && progress[it.wordId]?.isSkipped != true &&
                (session.kind != StudyKind.MISTAKES.name || progress[it.wordId]?.mistakePending == true)
            }.sortedBy { it.queueOrder }.map { it.wordId }
          } else {
            val viewOnly = speechDao.viewOnlyDailyWords(card.day, card.kind).toSet()
            val stage = state.selectedStage ?: card.itemId?.let { speechDao.dailyItemById(it)?.originStage }
            speechDao.dailyItems(card.day).filter {
              it.kind == card.kind && !it.completed && it.wordId !in viewOnly && progress[it.wordId]?.isSkipped != true &&
                (card.kind != StudyKind.NEW.name || it.originStage == stage)
            }.sortedWith(compareBy<DailyItemEntity> { it.batch }.thenBy { it.queueOrder }).map { it.wordId }
          }
          val ids = listOf(card.wordId) + upcoming.asSequence().filter { it != card.wordId }
            .distinct().take(nextCount).toList()
          ids.mapNotNull { id -> speechDao.word(id)?.let { word(it) } }
        }
      }
    }

    override suspend fun peekLearnSpeechWords(maxWords: Int,
      expectedContextVersion: Long, expectedStage: VocabularyStage): List<WordEntry> {
      require(maxWords in 0..20)
      if (maxWords == 0) return emptyList()
      return operationMutex.withLock {
        database.withTransaction {
          val speechDao = rawDao
          val state = speechDao.appState() ?: return@withTransaction emptyList()
          val day = today()
          if (!state.seeded || state.day != day || state.contextVersion != expectedContextVersion ||
            state.selectedStage != expectedStage.code) return@withTransaction emptyList()
          val progress = speechDao.progress().associateBy { it.wordId }
          val viewOnly = speechDao.viewOnlyDailyWords(day, StudyKind.NEW.name).toSet()
          val items = speechDao.dailyItems(day)
          val pending = speechDao.unfinishedCards().filter {
            it.sessionId == null && it.day == day && it.kind == StudyKind.NEW.name &&
              progress[it.wordId]?.isSkipped != true
          }.sortedBy { it.createdAt }.firstOrNull { card ->
            (card.itemId?.let { speechDao.dailyItemById(it)?.originStage }
              ?: items.firstOrNull { it.wordId == card.wordId && it.kind == card.kind }?.originStage) == expectedStage.code
          }
          val upcoming = items.filter {
            it.kind == StudyKind.NEW.name && !it.completed && it.wordId !in viewOnly &&
              progress[it.wordId]?.isSkipped != true && it.originStage == expectedStage.code
          }.sortedWith(compareBy<DailyItemEntity> { it.batch }.thenBy { it.queueOrder })
          (listOfNotNull(pending?.wordId) + upcoming.map { it.wordId }).distinct().take(maxWords)
            .mapNotNull { id -> speechDao.word(id)?.let { word(it) } }
        }
      }
    }

    override suspend fun peekEntrySpeechWords(expectedContextVersion: Long): List<WordEntry> = operationMutex.withLock {
      // Preview only saved work. Normal startup owns seeding, midnight rollover and stage planning.
      database.withTransaction {
        val speechDao = rawDao
        val state = speechDao.appState() ?: return@withTransaction emptyList()
        val day = today()
        if (!state.seeded || state.day != day || state.contextVersion != expectedContextVersion)
          return@withTransaction emptyList()
        val stage = state.selectedStage?.takeIf { VocabularyStage.fromCode(it) != null }
          ?: return@withTransaction emptyList()
        val progress = speechDao.progress().associateBy { it.wordId }
        val items = speechDao.dailyItems(day).filter { progress[it.wordId]?.isSkipped != true }
        val unfinished = speechDao.unfinishedCards().filter { progress[it.wordId]?.isSkipped != true }
        val current = state.currentCardId?.let { id -> unfinished.firstOrNull { it.id == id } }
          ?.takeIf { card -> (card.day == day || card.sessionId != null) &&
            (card.kind != StudyKind.NEW.name || (card.itemId?.let { speechDao.dailyItemById(it) }
              ?: speechDao.dailyItem(day, card.wordId))?.originStage == stage) }
        val words = speechDao.studyWords()
        val ids = linkedSetOf<String>()
        current?.wordId?.let(ids::add)

        suspend fun pendingDaily(kind: StudyKind): CardEntity? = unfinished.filter {
          it.sessionId == null && it.day == day && it.kind == kind.name
        }.sortedBy { it.createdAt }.firstOrNull { card ->
          kind != StudyKind.NEW || (card.itemId?.let { speechDao.dailyItemById(it) }
            ?: speechDao.dailyItem(day, card.wordId))?.originStage == stage
        }

        suspend fun dailyWord(kind: StudyKind): String? {
          pendingDaily(kind)?.let { return it.wordId }
          val viewOnly = speechDao.viewOnlyDailyWords(day, kind.name).toSet()
          return items.filter { it.kind == kind.name && !it.completed && it.wordId !in viewOnly &&
            (kind != StudyKind.NEW || it.originStage == stage) }
            .minWithOrNull(compareBy<DailyItemEntity> { it.batch }.thenBy { it.queueOrder })?.wordId
        }

        suspend fun practiceWord(kind: StudyKind): String? {
          val sessions = speechDao.practiceSessions(kind.name).sortedBy { it.createdAt }
          val sessionItems = mutableMapOf<String, List<PracticeItemEntity>>()
          for (session in sessions) sessionItems[session.id] = speechDao.practiceItems(session.id)
          val eligible = words.filter { progress[it.id]?.isSkipped != true &&
            if (kind == StudyKind.COLLECTION) progress[it.id]?.isCollected == true
            else progress[it.id]?.mistakePending == true }
          // Home enables Collections for an unfinished saved batch even after an uncollect.
          val available = eligible.isNotEmpty() || (kind == StudyKind.COLLECTION && sessions.any { session ->
            session.status != "COMPLETED" && sessionItems.getValue(session.id).any {
              !it.completed && progress[it.wordId]?.isSkipped != true
            }
          })
          if (!available) return null
          for (session in sessions.filter { it.status != "COMPLETED" }) {
            val pending = speechDao.pendingPracticeCard(session.id)?.takeIf {
              progress[it.wordId]?.isSkipped != true && (kind != StudyKind.MISTAKES ||
                it.phase != CardPhase.QUESTION.name || progress[it.wordId]?.mistakePending == true)
            }
            if (pending != null) return pending.wordId
            val queued = sessionItems.getValue(session.id).filter { !it.completed &&
              progress[it.wordId]?.isSkipped != true &&
              (kind != StudyKind.MISTAKES || progress[it.wordId]?.mistakePending == true) }
            if (queued.isNotEmpty()) {
              val viewOnly = speechDao.viewOnlyPracticeWords(session.id).toSet()
              return queued.filter { it.wordId !in viewOnly }.minByOrNull { it.queueOrder }?.wordId
            }
          }
          val lap = sessions.maxOfOrNull { it.lap } ?: 1
          val completed = if (kind == StudyKind.COLLECTION) sessions.filter { it.lap == lap }
            .flatMap { sessionItems.getValue(it.id) }.filter { it.completed }.mapTo(mutableSetOf()) { it.wordId }
          else emptySet()
          // startPractice takes the first five in the saved vocabulary order, without shuffling.
          return (eligible.firstOrNull { it.id !in completed } ?: eligible.firstOrNull())?.id
        }

        val daily = items.filter { it.kind != StudyKind.NEW.name || it.originStage == stage }
        if (current?.kind !in listOf(StudyKind.NEW.name, StudyKind.CARRYOVER.name)) {
          when {
            daily.any { it.kind == StudyKind.CARRYOVER.name && !it.completed } ->
              dailyWord(StudyKind.CARRYOVER)?.let(ids::add)
            daily.any { it.kind == StudyKind.NEW.name && !it.completed } ->
              dailyWord(StudyKind.NEW)?.let(ids::add)
            daily.all { it.completed } -> {
              // Match canLearnMore, including pending feedback/view-only cards that block a new batch.
              var blocked = false
              for (kind in listOf(StudyKind.REVIEW, StudyKind.CARRYOVER, StudyKind.NEW)) {
                if (pendingDaily(kind) != null) { blocked = true; break }
              }
              val stageState = speechDao.stageState(stage)
              if (!blocked && stageState != null) {
                val completed = speechDao.stageCompletions(stage, stageState.lap).mapTo(mutableSetOf()) { it.wordId }
                val active = speechDao.activeCycles().filter { it.scope == "NORMAL" || it.scope == "STAGE_REPEAT" }
                  .mapTo(mutableSetOf()) { it.wordId }
                val eligible = words.filter { it.stage == stage && progress[it.id]?.isSkipped != true }
                val remaining = eligible.filter { it.id !in completed && it.id !in active }
                val candidate = if (remaining.isNotEmpty() || eligible.any { it.id !in completed }) remaining.firstOrNull()
                  else eligible.firstOrNull { it.id !in active }
                candidate?.id?.let(ids::add)
              }
            }
          }
        }
        if (current?.kind != StudyKind.REVIEW.name && daily.any { it.kind == StudyKind.REVIEW.name && !it.completed })
          dailyWord(StudyKind.REVIEW)?.let(ids::add)
        for (kind in listOf(StudyKind.COLLECTION, StudyKind.MISTAKES)) {
          if (current?.kind != kind.name) practiceWord(kind)?.let(ids::add)
        }
        ids.mapNotNull { id -> speechDao.word(id)?.let { word(it) } }
      }
    }

    override suspend fun context(): LearningContext = transaction {
      LearningContext(appState().contextVersion)
    }

    override suspend fun selectStage(stage: VocabularyStage): StudySnapshot = transaction {
      val day = ensureToday()
      val saved = appState()
      if (saved.selectedStage != stage.code) {
        val current = saved.currentCardId?.let { dao.card(it) }
        dao.putAppState(saved.copy(selectedStage = stage.code, contextVersion = saved.contextVersion + 1,
          currentCardId = if (current?.kind == StudyKind.NEW.name) null else saved.currentCardId))
        ensureStagePlan(day, stage)
        touch()
      }
      snapshotAt(day)
    }

    override suspend fun start(kind: StudyKind): StudySnapshot = transaction {
      val day = ensureToday()
      val previous = appState()
      if (previous.selectedKind != kind.name) {
        val old = previous.currentCardId?.let { dao.card(it) }
        if (old != null && old.correct == true &&
          old.phase in listOf(CardPhase.FEEDBACK.name, CardPhase.EXPLANATION.name) && cardCompleted(old)) {
          // A completed feedback card must not become stranded after its entry is disabled.
          dao.putCard(old.copy(phase = CardPhase.FINISHED.name))
          old.sessionId?.let { finishPracticeIfReady(it) }
        }
      }
      val card = if (kind.isPractice()) startPractice(day, kind)
      else pendingDailyCard(day, kind) ?: createNext(day, kind) ?: finishedDailyCard(day, kind)
      dao.putAppState(appState().copy(selectedKind = kind.name, currentCardId = card?.id))
      touch()
      snapshotAt(day)
    }

    override suspend fun learnMore(expectedDate: String, expectedNewPlanned: Int,
      expectedStage: VocabularyStage, expectedLap: Int): StudySnapshot {
      var changedDay = false
      val result = transaction {
        val day = ensureToday()
        val current = snapshotAt(day)
        if (current.today.date != expectedDate) {
          changedDay = true
          return@transaction current
        }
        if (current.today.stageProgress.stage != expectedStage || current.today.stageProgress.lap != expectedLap ||
          current.today.newPlanned != expectedNewPlanned || !current.today.canLearnMore) return@transaction current
        if (!enqueueStageWords(day, expectedStage, 5, allowNewLap = true)) return@transaction current
        val card = checkNotNull(createNext(day, StudyKind.NEW))
        dao.putAppState(appState().copy(selectedKind = StudyKind.NEW.name, currentCardId = card.id))
        touch()
        snapshotAt(day)
      }
      if (changedDay) throw NewStudyDayException()
      return result
    }

    override suspend fun reviewMore(expectedDate: String, expectedReviewPlanned: Int,
      expectedContextVersion: Long): StudySnapshot {
      var changedDay = false
      val result = transaction {
        val day = ensureToday()
        val current = snapshotAt(day)
        if (!matchesContext(expectedContextVersion)) return@transaction current
        if (current.today.date != expectedDate) {
          changedDay = true
          return@transaction current
        }
        if (current.today.reviewPlanned != expectedReviewPlanned || !current.today.canReviewMore ||
          pendingDailyCard(day, StudyKind.REVIEW) != null) return@transaction current
        val candidates = reviewCandidates(day).shuffled().take(savedSettings().reviewBatchSize)
        if (candidates.isEmpty()) return@transaction current
        val items = dao.dailyItems(day).filter { it.kind == StudyKind.REVIEW.name }
        val firstBatch = (items.maxOfOrNull { it.batch } ?: -1) + 1
        candidates.forEachIndexed { index, cycle ->
          // A fresh item ID preserves earlier reviews of this word, including repeats on the same day.
          dao.putDailyItem(DailyItemEntity(day, cycle.wordId, StudyKind.REVIEW.name, cycle.id,
            firstBatch + index / 5, index.toLong(), reviewRecall = true, id = newId()))
        }
        val card = checkNotNull(createNext(day, StudyKind.REVIEW))
        dao.putAppState(appState().copy(selectedKind = StudyKind.REVIEW.name, currentCardId = card.id))
        touch()
        snapshotAt(day)
      }
      if (changedDay) throw NewStudyDayException()
      return result
    }

    override suspend fun submit(cardId: String, optionId: String?,
      expectedContextVersion: Long?): StudySnapshot = cardTransaction(cardId, expectedContextVersion) { day, card ->
      if (card.phase != CardPhase.QUESTION.name || strings(JSONArray(card.optionsJson)).size != 4) return@cardTransaction snapshotAt(day)
      val optionIds = strings(JSONArray(card.optionsJson))
      require(optionId == null || optionId in optionIds)
      val correct = optionId != null && optionId == card.correctWordId
      if (!correct) {
        val progress = dao.progress(card.wordId) ?: WordProgressEntity(card.wordId)
        dao.putProgress(progress.copy(mistakePending = true))
      }
      var answered = card.copy(phase = CardPhase.FEEDBACK.name, selectedOptionId = optionId, correct = correct)
      if (card.sessionId != null) {
        val item = checkNotNull(dao.practiceItem(card.sessionId, card.wordId))
        val cycle = checkNotNull(dao.cycle(item.cycleId))
        check(cycle.status == "ACTIVE")
        val updated = cycle.copy(correctRounds = if (correct) cycle.correctRounds + 1 else 0)
        if (correct && updated.correctRounds >= updated.targetRounds) {
          dao.putCycle(updated.copy(status = "COMPLETED", passedDay = day))
          dao.putPracticeItem(item.copy(completed = true))
          if (card.kind == StudyKind.COLLECTION.name) rewardLearningCompletion()
          clearMistake(card.wordId, answeringCardId = card.id)
        } else dao.putCycle(updated)
      } else {
        val item = checkNotNull(dailyItem(card))
        val cycle = checkNotNull(dao.cycle(item.cycleId))
        if (card.reviewRecall) {
          if (correct) {
            dao.putReviewNodes(dao.dueNodes(day).filter { it.wordId == card.wordId }
              .map { it.copy(status = "CONSUMED") })
            dao.putDailyItem(item.copy(completed = true))
          } else {
            dao.putCycle(cycle.copy(status = "CANCELLED"))
            dao.putReviewNodes(dao.pendingNodes(cycle.id).map { it.copy(status = "CANCELLED") })
            val value = savedSettings()
            val relearn = CycleEntity(newId(), card.wordId, value.rounds, daysJson(value.reviewDays), startedDay = day)
            dao.putCycle(relearn)
            val progress = dao.progress(card.wordId) ?: WordProgressEntity(card.wordId)
            dao.putProgress(progress.copy(activeCycleId = relearn.id))
            dao.putDailyItem(item.copy(cycleId = relearn.id, reviewRecall = false, completed = false))
            answered = answered.copy(cycleId = relearn.id)
          }
        } else {
          check(cycle.status == "ACTIVE")
          val updated = cycle.copy(correctRounds = if (correct) cycle.correctRounds + 1 else 0)
          if (correct && updated.correctRounds >= updated.targetRounds) completeCycle(day, item, updated)
          else dao.putCycle(updated)
        }
      }
      dao.putCard(answered)
      touch()
      snapshotAt(day)
    }

    override suspend fun advance(cardId: String,
      expectedContextVersion: Long?): StudySnapshot = cardTransaction(cardId, expectedContextVersion) { day, card ->
      when (CardPhase.valueOf(card.phase)) {
        CardPhase.INTRO -> {
          val progress = dao.progress(card.wordId) ?: WordProgressEntity(card.wordId)
          dao.putProgress(progress.copy(firstEncounterShown = true))
          dao.putCard(card.copy(phase = if (strings(JSONArray(card.optionsJson)).size == 4)
            CardPhase.QUESTION.name else CardPhase.EXPLANATION.name))
        }
        CardPhase.FEEDBACK -> dao.putCard(card.copy(phase = CardPhase.EXPLANATION.name))
        CardPhase.EXPLANATION -> finishAndSelect(card, day)
        CardPhase.QUESTION, CardPhase.FINISHED -> return@cardTransaction snapshotAt(day)
      }
      touch()
      snapshotAt(day)
    }

    override suspend fun explain(cardId: String,
      expectedContextVersion: Long?): StudySnapshot = cardTransaction(cardId, expectedContextVersion) { day, card ->
      if (card.phase == CardPhase.FEEDBACK.name) {
        dao.putCard(card.copy(phase = CardPhase.EXPLANATION.name))
        touch()
      }
      snapshotAt(day)
    }

  }

  private fun StudyKind.isPractice() = this == StudyKind.COLLECTION || this == StudyKind.MISTAKES

  private suspend fun clearMistake(wordId: String, answeringCardId: String? = null) {
    val saved = dao.progress(wordId) ?: WordProgressEntity(wordId)
    if (saved.mistakePending) {
      dao.putProgress(saved.copy(mistakePending = false))
      val state = appState()
      val cleared = state.clearedMistakes + 1
      dao.putAppState(state.copy(clearedMistakes = cleared,
        growthDays = state.growthDays + cleared / 20 - state.clearedMistakes / 20))
    }
    // Mastery ends the old mistake attempt, so a later error cannot inherit its partial rounds.
    val sessions = dao.practiceSessions(StudyKind.MISTAKES.name)
    for (session in sessions) {
      val item = dao.practiceItem(session.id, wordId) ?: continue
      if (!item.completed) {
        dao.putPracticeItem(item.copy(completed = true))
        dao.cycle(item.cycleId)?.takeIf { it.status == "ACTIVE" }?.let { cycle ->
          dao.putCycle(cycle.copy(status = "CANCELLED"))
        }
      }
    }
    dao.unfinishedCards().filter { it.kind == StudyKind.MISTAKES.name && it.wordId == wordId &&
      it.phase == CardPhase.QUESTION.name && it.id != answeringCardId }
      .forEach { dao.putCard(it.copy(phase = CardPhase.FINISHED.name)) }
    for (session in sessions.filter { it.status != "COMPLETED" }) {
      // Feedback and explanation remain readable until the learner advances or changes chains.
      finishPracticeIfReady(session.id)
    }
  }

  private suspend fun selectedStage(): VocabularyStage {
    val state = appState()
    state.selectedStage?.let { VocabularyStage.fromCode(it) }?.let { return it }
    val stage = VocabularyStage.GRADE_3_FIRST
    dao.putAppState(state.copy(selectedStage = stage.code))
    return stage
  }

  private suspend fun stageState(stage: VocabularyStage): StageStateEntity {
    dao.stageState(stage.code)?.let { return it }
    val state = StageStateEntity(stage.code)
    dao.putStageState(state)
    val progress = dao.progress().associateBy { it.wordId }
    dao.studyWords().filter { it.stage == stage.code }.forEach { word ->
      progress[word.id]?.firstPassedDay?.let { day ->
        dao.putStageCompletion(StageCompletionEntity(stage.code, state.lap, word.id, day))
      }
    }
    return state
  }

  private suspend fun activeLearningCycles(): List<CycleEntity> = dao.activeCycles()
    .filter { it.scope == "NORMAL" || it.scope == "STAGE_REPEAT" }

  private suspend fun ensureStagePlan(day: Long, stage: VocabularyStage) {
    stageState(stage)
    if (dao.stagePlan(day, stage.code) != null) return
    val goal = checkNotNull(dao.plan(day)).dailyGoal
    dao.putStagePlan(StageDailyPlanEntity(day, stage.code, goal))
    enqueueStageWords(day, stage, goal, allowNewLap = false)
  }

  private suspend fun stageCandidates(stage: VocabularyStage, allowNewLap: Boolean): List<WordStudyRow> {
    val state = stageState(stage)
    val progress = dao.progress().associateBy { it.wordId }
    val words = dao.studyWords()
    val completed = dao.stageCompletions(stage.code, state.lap).map { it.wordId }.toSet()
    return stageCandidates(stage, allowNewLap, words, progress, completed)
  }

  private suspend fun stageCandidates(stage: VocabularyStage, allowNewLap: Boolean,
    words: List<WordStudyRow>, progress: Map<String, WordProgressEntity>, completed: Set<String>): List<WordStudyRow> {
    val eligible = words.filter { it.stage == stage.code && progress[it.id]?.isSkipped != true }
    val active = activeLearningCycles().map { it.wordId }.toSet()
    val remaining = eligible.filter { it.id !in completed && it.id !in active }
    if (remaining.isNotEmpty() || !allowNewLap || eligible.isEmpty() || eligible.any { it.id !in completed }) return remaining
    return eligible.filter { it.id !in active }
  }

  private suspend fun enqueueStageWords(day: Long, stage: VocabularyStage, limit: Int, allowNewLap: Boolean): Boolean {
    var state = stageState(stage)
    val candidates = stageCandidates(stage, allowNewLap).take(limit)
    if (candidates.isEmpty()) return false
    val completed = dao.stageCompletions(stage.code, state.lap).map { it.wordId }.toSet()
    if (candidates.all { it.id in completed }) {
      state = state.copy(lap = state.lap + 1)
      dao.putStageState(state)
    }
    val settings = savedSettings()
    val items = dao.dailyItems(day).filter { it.kind == StudyKind.NEW.name && it.originStage == stage.code }
    val firstBatch = (items.maxOfOrNull { it.batch } ?: -1) + 1
    candidates.forEachIndexed { index, word ->
      val saved = dao.progress(word.id) ?: WordProgressEntity(word.id)
      val normal = saved.firstPassedDay == null
      val cycle = CycleEntity(newId(), word.id, settings.rounds, daysJson(settings.reviewDays), startedDay = day,
        scope = if (normal) "NORMAL" else "STAGE_REPEAT", originStage = stage.code, originLap = state.lap)
      dao.putCycle(cycle)
      if (normal) dao.putProgress(saved.copy(activeCycleId = cycle.id))
      dao.putDailyItem(DailyItemEntity(day, word.id, StudyKind.NEW.name, cycle.id,
        firstBatch + index / 5, index.toLong(), originStage = stage.code, originLap = state.lap))
    }
    return true
  }

  private suspend fun reviewCandidates(day: Long): List<CycleEntity> =
    reviewCandidates(day, dao.studyWords(), dao.progress().associateBy { it.wordId })

  private suspend fun reviewCandidates(day: Long, words: List<WordStudyRow>,
    progress: Map<String, WordProgressEntity>): List<CycleEntity> {
    val active = activeLearningCycles().mapTo(mutableSetOf()) { it.wordId }
    val viewOnly = dao.viewOnlyDailyWords(day, StudyKind.REVIEW.name).toSet()
    val eligible = words.filter { progress[it.id]?.firstPassedDay != null &&
      progress[it.id]?.isSkipped != true && it.id !in active && it.id !in viewOnly }.mapTo(mutableSetOf()) { it.id }
    return dao.learnedReviewCycles().filter { it.wordId in eligible }
  }

  /** Practice cards survive midnight; normal daily cards retain the existing stale-date guard. */
  private suspend fun matchesContext(expectedVersion: Long?): Boolean =
    expectedVersion == null || expectedVersion == appState().contextVersion

  private suspend fun cardTransaction(id: String, expectedContextVersion: Long?,
    operation: suspend (Long, CardEntity) -> StudySnapshot): StudySnapshot {
    var changedDay = false
    val result = transaction {
      val day = ensureToday()
      if (!matchesContext(expectedContextVersion)) return@transaction snapshotAt(day)
      val saved = dao.card(id)
      if (saved != null && saved.sessionId == null && saved.day != day) {
        changedDay = true
        snapshotAt(day)
      } else {
        val current = currentCard(id, day)
        if (current == null) snapshotAt(day) else operation(day, current)
      }
    }
    if (changedDay) throw NewStudyDayException()
    return result
  }

  private suspend fun ensureToday(): Long {
    val day = today()
    val state = appState()
    if (state.day != day || dao.plan(day) == null) {
      dao.oldCards(day).filter { it.sessionId == null }.forEach { dao.putCard(it.copy(phase = CardPhase.FINISHED.name)) }
      if (dao.plan(day) == null) {
        val settings = savedSettings()
        dao.putPlan(DailyPlanEntity(day, settings.dailyWords))
        val words = dao.studyWords()
        val progress = dao.progress().associateBy { it.wordId }
        val order = words.mapIndexed { rank, word -> word.id to rank }.toMap()
        val active = activeLearningCycles().filter { progress[it.wordId]?.isSkipped != true }
          .sortedBy { order[it.wordId] ?: Int.MAX_VALUE }
        active.forEachIndexed { index, cycle ->
          dao.putCycle(cycle.copy(correctRounds = 0))
          dao.putDailyItem(DailyItemEntity(day, cycle.wordId, StudyKind.CARRYOVER.name,
            cycle.id, index / 5, index.toLong(), originStage = cycle.originStage, originLap = cycle.originLap))
        }
        val normalActiveIds = active.filter { it.scope == "NORMAL" }.map { it.wordId }.toSet()
        val due = dao.dueNodes(day).filter { it.wordId !in normalActiveIds && progress[it.wordId]?.isSkipped != true }
          .distinctBy { it.wordId }
        due.forEachIndexed { index, node ->
          dao.putDailyItem(DailyItemEntity(day, node.wordId, StudyKind.REVIEW.name,
            node.cycleId, index / 5, index.toLong(), reviewRecall = true))
        }
      }
      val current = state.currentCardId?.let { dao.card(it) }
      dao.putAppState(appState().copy(day = day,
        selectedKind = if (current?.sessionId != null) state.selectedKind else null,
        currentCardId = current?.takeIf { it.sessionId != null }?.id, revision = appState().revision + 1))
    }
    ensureStagePlan(day, selectedStage())
    val plan = checkNotNull(dao.plan(day))
    if (!plan.loginRewarded) {
      dao.putPlan(plan.copy(loginRewarded = true))
      val saved = appState()
      dao.putAppState(saved.copy(growthDays = saved.growthDays + 1, revision = saved.revision + 1))
    }
    return day
  }

  private suspend fun restorePausedWork(day: Long, wordId: String) {
    val items = dao.dailyItems(day)
    val cycles = activeLearningCycles().filter { it.wordId == wordId }
    cycles.forEach { cycle ->
      if (items.none { it.cycleId == cycle.id && !it.completed }) {
        val order = (items.filter { it.kind == StudyKind.CARRYOVER.name }.maxOfOrNull { it.queueOrder } ?: -1) + 1
        dao.putDailyItem(DailyItemEntity(day, wordId, StudyKind.CARRYOVER.name, cycle.id,
          0, order, originStage = cycle.originStage, originLap = cycle.originLap))
      }
    }
    if (cycles.none { it.scope == "NORMAL" } && items.none { it.wordId == wordId && it.kind == StudyKind.REVIEW.name }) {
      dao.dueNodes(day).firstOrNull { it.wordId == wordId }?.let { node ->
        val order = (items.filter { it.kind == StudyKind.REVIEW.name }.maxOfOrNull { it.queueOrder } ?: -1) + 1
        dao.putDailyItem(DailyItemEntity(day, wordId, StudyKind.REVIEW.name, node.cycleId, 0, order, reviewRecall = true))
      }
    }
  }

  private suspend fun currentCard(id: String, day: Long): CardEntity? {
    val card = dao.card(id) ?: return null
    if (card.sessionId == null && card.day != day) return null
    if (appState().currentCardId != id || card.phase == CardPhase.FINISHED.name || dao.progress(card.wordId)?.isSkipped == true)
      return null
    return card
  }

  private suspend fun dailyItem(card: CardEntity): DailyItemEntity? =
    card.itemId?.let { dao.dailyItemById(it) } ?: dao.dailyItem(card.day, card.wordId)

  private suspend fun cardCompleted(card: CardEntity): Boolean = if (card.sessionId != null)
    dao.practiceItem(card.sessionId, card.wordId)?.completed == true else dailyItem(card)?.completed == true

  private suspend fun pendingDailyCard(day: Long, kind: StudyKind): CardEntity? {
    val stage = selectedStage()
    val progress = dao.progress().associateBy { it.wordId }
    return dao.unfinishedCards().filter { it.sessionId == null && it.day == day && it.kind == kind.name &&
      progress[it.wordId]?.isSkipped != true }.sortedBy { it.createdAt }.firstOrNull {
      kind != StudyKind.NEW || dailyItem(it)?.originStage == stage.code
    }
  }

  private suspend fun finishedDailyCard(day: Long, kind: StudyKind): CardEntity? {
    // Each new task normally has a pending card; the last finished card supplies the completion screen.
    return dao.finishedCard(day, kind.name)?.takeIf {
      kind != StudyKind.NEW || dailyItem(it)?.originStage == selectedStage().code
    }
  }

  private suspend fun createNext(day: Long, kind: StudyKind, stage: Int? = null): CardEntity? {
    check(!kind.isPractice())
    val selected = stage ?: selectedStage().code
    val progress = dao.progress().associateBy { it.wordId }
    val viewOnly = dao.viewOnlyDailyWords(day, kind.name).toSet()
    val item = dao.dailyItems(day).filter { it.kind == kind.name && !it.completed && it.wordId !in viewOnly && progress[it.wordId]?.isSkipped != true &&
      (kind != StudyKind.NEW || it.originStage == selected) }
      .minWithOrNull(compareBy<DailyItemEntity> { it.batch }.thenBy { it.queueOrder }) ?: return null
    val cycle = checkNotNull(dao.cycle(item.cycleId))
    val entry = word(item.wordId)
    val saved = progress[item.wordId] ?: WordProgressEntity(item.wordId)
    val introduction = cycle.scope == "NORMAL" && !item.reviewRecall && !saved.firstEncounterShown
    val options = questionOptions(entry, dao)
    val card = CardEntity(newId(), day, item.wordId, cycle.id, kind.name,
      if (introduction) CardPhase.INTRO.name else if (options.isEmpty()) CardPhase.EXPLANATION.name else CardPhase.QUESTION.name,
      if (item.reviewRecall) 1 else cycle.correctRounds + 1,
      if (item.reviewRecall) 1 else cycle.targetRounds, item.reviewRecall,
      stringsJson(options), createdAt = System.currentTimeMillis(), itemId = item.id, correctWordId = entry.id)
    dao.putCard(card)
    return card
  }

  private suspend fun startPractice(day: Long, kind: StudyKind): CardEntity? {
    check(kind.isPractice())
    val progress = dao.progress().associateBy { it.wordId }
    val sessions = dao.practiceSessions(kind.name).sortedBy { it.createdAt }
    for (session in sessions.filter { it.status != "COMPLETED" }) {
      val pending = dao.pendingPracticeCard(session.id)?.takeIf { progress[it.wordId]?.isSkipped != true &&
        (kind != StudyKind.MISTAKES || it.phase != CardPhase.QUESTION.name || progress[it.wordId]?.mistakePending == true) }
      val available = dao.practiceItems(session.id).any { !it.completed && progress[it.wordId]?.isSkipped != true &&
        (kind != StudyKind.MISTAKES || progress[it.wordId]?.mistakePending == true) }
      if (pending != null || available) {
        if (session.status != "ACTIVE") dao.putPracticeSession(session.copy(status = "ACTIVE"))
        return pending ?: createPracticeCard(session.id, day)
      }
      finishPracticeIfReady(session.id)
    }
    val eligible = dao.studyWords().filter { progress[it.id]?.isSkipped != true &&
      if (kind == StudyKind.COLLECTION) progress[it.id]?.isCollected == true else progress[it.id]?.mistakePending == true }
    if (eligible.isEmpty()) return null
    var lap = sessions.maxOfOrNull { it.lap } ?: 1
    val completed = mutableSetOf<String>()
    if (kind == StudyKind.COLLECTION) {
      sessions.filter { it.lap == lap }.forEach { session ->
        completed += dao.practiceItems(session.id).filter { it.completed }.map { it.wordId }
      }
    }
    var candidates = eligible.filter { it.id !in completed }
    if (candidates.isEmpty()) {
      lap += 1
      candidates = eligible
    }
    val session = PracticeSessionEntity(newId(), kind.name, lap = lap, createdAt = System.currentTimeMillis())
    dao.putPracticeSession(session)
    val settings = savedSettings()
    candidates.take(5).forEachIndexed { index, word ->
      val cycle = CycleEntity(newId(), word.id, settings.rounds, daysJson(settings.reviewDays),
        startedDay = day, scope = kind.name)
      dao.putCycle(cycle)
      dao.putPracticeItem(PracticeItemEntity(session.id, word.id, cycle.id, index.toLong()))
    }
    return createPracticeCard(session.id, day)
  }

  private suspend fun createPracticeCard(sessionId: String, day: Long): CardEntity? {
    val session = checkNotNull(dao.practiceSession(sessionId))
    val progress = dao.progress().associateBy { it.wordId }
    val viewOnly = dao.viewOnlyPracticeWords(sessionId).toSet()
    val item = dao.practiceItems(sessionId).filter { !it.completed && it.wordId !in viewOnly && progress[it.wordId]?.isSkipped != true &&
      (session.kind != StudyKind.MISTAKES.name || progress[it.wordId]?.mistakePending == true) }
      .minByOrNull { it.queueOrder }
    if (item == null) {
      finishPracticeIfReady(sessionId)
      return null
    }
    val cycle = checkNotNull(dao.cycle(item.cycleId))
    val entry = word(item.wordId)
    val options = questionOptions(entry, dao)
    val card = CardEntity(newId(), day, item.wordId, cycle.id, session.kind,
      if (options.isEmpty()) CardPhase.EXPLANATION.name else CardPhase.QUESTION.name,
      cycle.correctRounds + 1, cycle.targetRounds, false, stringsJson(options),
      createdAt = System.currentTimeMillis(), sessionId = sessionId, correctWordId = entry.id)
    dao.putCard(card)
    return card
  }

  private suspend fun finishPracticeIfReady(sessionId: String) {
    val session = checkNotNull(dao.practiceSession(sessionId))
    val progress = dao.progress().associateBy { it.wordId }
    val items = dao.practiceItems(sessionId)
    val remaining = items.filter { !it.completed &&
      (session.kind != StudyKind.MISTAKES.name || progress[it.wordId]?.mistakePending == true) }
    val pending = dao.pendingPracticeCard(sessionId)?.let { progress[it.wordId]?.isSkipped != true } == true
    val status = when {
      pending || remaining.any { progress[it.wordId]?.isSkipped != true } -> "ACTIVE"
      remaining.isNotEmpty() -> "PAUSED"
      else -> "COMPLETED"
    }
    if (session.status != status) dao.putPracticeSession(session.copy(status = status))
  }

  private suspend fun finishAndSelect(card: CardEntity, day: Long) {
    dao.putCard(card.copy(phase = CardPhase.FINISHED.name))
    val next = if (card.sessionId != null) {
      val item = checkNotNull(dao.practiceItem(card.sessionId, card.wordId))
      if (!item.completed) {
        val last = dao.practiceItems(card.sessionId).maxOfOrNull { it.queueOrder } ?: item.queueOrder
        dao.putPracticeItem(item.copy(queueOrder = last + 1))
      }
      createPracticeCard(card.sessionId, day)
    } else {
      val item = checkNotNull(dailyItem(card))
      if (!item.completed) {
        val last = dao.dailyItems(day).filter { it.kind == item.kind && it.batch == item.batch &&
          (item.kind != StudyKind.NEW.name || it.originStage == item.originStage) }
          .maxOfOrNull { it.queueOrder } ?: item.queueOrder
        dao.putDailyItem(item.copy(queueOrder = last + 1))
      }
      createNext(day, StudyKind.valueOf(card.kind), item.originStage)
    }
    dao.putAppState(appState().copy(currentCardId = next?.id ?: card.id))
  }

  private suspend fun completeCycle(day: Long, item: DailyItemEntity, cycle: CycleEntity) {
    dao.putCycle(cycle.copy(status = "COMPLETED", passedDay = day))
    dao.putDailyItem(item.copy(completed = true))
    if (cycle.scope == "NORMAL") {
      val previous = dao.progress(item.wordId) ?: WordProgressEntity(item.wordId)
      dao.putProgress(previous.copy(firstPassedDay = previous.firstPassedDay ?: day,
        activeCycleId = if (previous.activeCycleId == cycle.id) null else previous.activeCycleId))
      dao.putReviewNodes(days(cycle.reviewDaysJson).map { offset ->
        ReviewNodeEntity("${cycle.id}:$offset", item.wordId, cycle.id, day + offset)
      })
    }
    val stage = cycle.originStage ?: item.originStage
    val lap = cycle.originLap ?: item.originLap
    if (stage != null && lap != null) dao.putStageCompletion(StageCompletionEntity(stage, lap, item.wordId, day))
    if (stage != null && item.kind in listOf(StudyKind.NEW.name, StudyKind.CARRYOVER.name)) {
      rewardLearningCompletion()
      if (item.kind == StudyKind.NEW.name) rewardDailyLearn(day, stage)
    }
    clearMistake(item.wordId)
  }

  private suspend fun rewardLearningCompletion() {
    val saved = appState()
    val completed = saved.learnedCompletions + 1
    dao.putAppState(saved.copy(learnedCompletions = completed,
      growthDays = saved.growthDays + completed / 10 - saved.learnedCompletions / 10))
  }

  private suspend fun rewardDailyLearn(day: Long, stage: Int) {
    val plan = checkNotNull(dao.plan(day))
    if (plan.learnRewarded) return
    val progress = dao.progress().associateBy { it.wordId }
    val items = dao.dailyItems(day).filter { it.kind == StudyKind.NEW.name && it.originStage == stage &&
      progress[it.wordId]?.isSkipped != true }
    if (items.isNotEmpty() && items.all { it.completed }) {
      dao.putPlan(plan.copy(learnRewarded = true))
      val saved = appState()
      dao.putAppState(saved.copy(growthDays = saved.growthDays + 1))
    }
  }

  private suspend fun snapshotAt(day: Long): StudySnapshot {
    val card = appState().currentCardId?.let { dao.card(it) }?.takeIf { it.day == day || it.sessionId != null }
    val practice = card?.sessionId?.let { id ->
      val session = checkNotNull(dao.practiceSession(id))
      val progress = dao.progress().associateBy { it.wordId }
      val items = dao.practiceItems(id).filter { progress[it.wordId]?.isSkipped != true &&
        (session.kind != StudyKind.MISTAKES.name || it.completed || progress[it.wordId]?.mistakePending == true) }
      PracticeProgress(StudyKind.valueOf(session.kind), items.count { it.completed }, items.size,
        paused = session.status == "PAUSED")
    }
    val summary = summaryAt(day)
    val studyCard = card?.let { cardDomain(it) }
    return StudySnapshot(summary, studyCard, practice, revision = appState().revision)
  }

  private suspend fun summaryAt(day: Long): TodaySummary {
    val stage = selectedStage()
    val state = stageState(stage)
    val plan = checkNotNull(dao.stagePlan(day, stage.code))
    val progress = dao.progress().associateBy { it.wordId }
    val items = dao.dailyItems(day).filter { progress[it.wordId]?.isSkipped != true }
    fun count(kind: StudyKind, complete: Boolean? = null): Int = items.count {
      it.kind == kind.name && (kind != StudyKind.NEW || it.originStage == stage.code) &&
        (complete == null || it.completed == complete)
    }
    val words = dao.studyWords()
    val stageWords = words.filter { it.stage == stage.code && progress[it.id]?.isSkipped != true }
    val completed = dao.stageCompletions(stage.code, state.lap).map { it.wordId }.toSet()
    val stageProgress = StageProgress(stage, stageWords.count { it.id in completed }, stageWords.size, state.lap)
    val newWords = words.filter { progress[it.id]?.firstPassedDay == day && progress[it.id]?.isSkipped != true }
      .map { word(it.id) }
    val available = stageCandidates(stage, allowNewLap = true,
      words = words, progress = progress, completed = completed).size
    val blocker = learnMoreBlocker(day, items, stage)
    val collectionIds = words.filter { progress[it.id]?.isCollected == true && progress[it.id]?.isSkipped != true }
      .mapTo(mutableSetOf()) { it.id }
    dao.practiceSessions(StudyKind.COLLECTION.name).filter { it.status != "COMPLETED" }.forEach { session ->
      // A collection toggle changes the next batch; an existing batch remains resumable.
      collectionIds += dao.practiceItems(session.id).filter { !it.completed && progress[it.wordId]?.isSkipped != true }
        .map { it.wordId }
    }
    return TodaySummary(LocalDate.ofEpochDay(day).toString(), plan.dailyGoal, dao.wordCount(),
      dao.wordCount() - dao.startedCount(), dao.learnedCount(), count(StudyKind.NEW), count(StudyKind.NEW, true),
      count(StudyKind.REVIEW), count(StudyKind.REVIEW, true), count(StudyKind.CARRYOVER), count(StudyKind.CARRYOVER, true),
      newWords,
      dao.nextReviewDay(day)?.let { LocalDate.ofEpochDay(it).toString() }, available, blocker, stageProgress,
      collectionIds.size,
      words.count { progress[it.id]?.mistakePending == true && progress[it.id]?.isSkipped != true },
      appState().let { StudyGrowth(it.growthDays, it.learnedCompletions, it.clearedMistakes) },
      contextVersion = appState().contextVersion,
      availableReviewWords = reviewCandidates(day, words, progress).size)
  }

  private suspend fun learnMoreBlocker(day: Long, items: List<DailyItemEntity>, stage: VocabularyStage): StudyKind? {
    for (kind in listOf(StudyKind.REVIEW, StudyKind.CARRYOVER, StudyKind.NEW)) {
      if (items.any { it.kind == kind.name && !it.completed &&
          (kind != StudyKind.NEW || it.originStage == stage.code) } || pendingDailyCard(day, kind) != null) return kind
    }
    return null
  }

  private suspend fun cardDomain(card: CardEntity): StudyCard {
    val options = strings(JSONArray(card.optionsJson)).map { id ->
      val value = checkNotNull(dao.word(id))
      AnswerOption(value.id, value.chineseMeaning, value.chinesePinyin)
    }
    val state = dao.progress(card.wordId)
    return StudyCard(card.id, word(card.wordId), StudyKind.valueOf(card.kind), CardPhase.valueOf(card.phase),
      card.round, card.targetRounds, card.reviewRecall, options, card.selectedOptionId,
      card.correct, state?.isCollected == true, state?.isSkipped == true, card.sessionId,
      card.correctWordId, appState().contextVersion)
  }

  private suspend fun word(id: String): WordEntry = word(checkNotNull(dao.word(id)))

  private fun word(value: WordEntity): WordEntry = WordEntry(value.id, value.headword, value.ipa,
    value.chineseMeaning, value.chinesePinyin, value.partOfSpeech,
    checkNotNull(VocabularyStage.fromCode(value.stage)), decodeExamples(JSONArray(value.examplesJson)),
    strings(JSONArray(value.distractorsJson)), value.sourcesJson, value.pronunciationNote, value.sourceNote)

  private fun SettingsEntity.toDomain() = StudySettings(dailyWords, rounds, days(reviewDaysJson), welcomed,
    displayName, avatarId, ThemeMode.valueOf(themeMode),
    speechOptions(speechModesJson), ExplanationOptions.fromMask(explanationMask),
    speechPreloadWords, speechCacheMiB, homeShuffleQueueSize, waitForCorrectAnswerAudio, reviewBatchSize,
    learningSoundsEnabled)

  private fun speechOptions(json: String): SpeechOptions {
    val values = try { JSONObject(json) } catch (_: JSONException) { return SpeechOptions() }
    return SpeechOptions(SpeechScene.entries.mapNotNull { scene ->
      SpeechMode.entries.firstOrNull { it.name == values.optString(scene.name) }?.let { scene to it }
    }.toMap())
  }
}
