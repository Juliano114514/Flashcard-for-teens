package com.example.flashcardforteens.feature.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flashcardforteens.core.domain.NewStudyDayException
import com.example.flashcardforteens.core.domain.SettingsRepository
import com.example.flashcardforteens.core.domain.StudyKind
import com.example.flashcardforteens.core.domain.StudyRepository
import com.example.flashcardforteens.core.domain.StudySettings
import com.example.flashcardforteens.core.domain.StudySnapshot
import com.example.flashcardforteens.core.domain.VocabularyStage
import com.example.flashcardforteens.core.domain.WordStateRepository
import com.example.flashcardforteens.core.domain.WordlistRepository
import com.example.flashcardforteens.core.domain.WordlistItem
import com.example.flashcardforteens.core.domain.AppLanguage
import com.example.flashcardforteens.core.domain.UiMessageKey
import com.example.flashcardforteens.core.domain.WordEntry
import com.example.flashcardforteens.core.domain.CardPhase
import com.example.flashcardforteens.core.domain.SpeechCacheController
import com.example.flashcardforteens.core.domain.SpeechCacheDemand
import com.example.flashcardforteens.core.domain.SpeechCacheOwner
import com.example.flashcardforteens.core.domain.SpeechCachePriority
import com.example.flashcardforteens.core.domain.SpeechCacheWindow
import com.example.flashcardforteens.core.domain.prepare
import com.example.flashcardforteens.core.domain.wordSpeechBasics
import com.example.flashcardforteens.core.domain.wordSpeechExamples
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext


data class StudyUiState(
  val loading: Boolean = true,
  val busy: Boolean = false,
  val settings: StudySettings = StudySettings(),
  val snapshot: StudySnapshot? = null,
  val error: UiMessageKey? = null,
  val homeWord: WordlistItem? = null,
  val homeWordError: Boolean = false,
  val homeWordStage: VocabularyStage? = null,
  val homeShuffleWaiting: Boolean = false,
  val homeShuffleReady: Int = 0,
  val homeShuffleFailed: Boolean = false,
  val homeSpeechEvent: HomeSpeechEvent? = null,
  val speechPreparing: Boolean = false,
  val directionGeneration: Long = 0,
) {
  val today get() = snapshot?.today
  val card get() = snapshot?.card
}

sealed interface StudyAction {
  data object Refresh : StudyAction
  data object Retry : StudyAction
  data class Start(val kind: StudyKind) : StudyAction
  data class SelectStage(val stage: VocabularyStage) : StudyAction
  data class LearnMore(val expectedDate: String, val expectedNewPlanned: Int,
    val expectedStage: VocabularyStage, val expectedLap: Int) : StudyAction
  data class ReviewMore(val expectedDate: String, val expectedReviewPlanned: Int, val generation: Long) : StudyAction
  data class SetCollected(val wordId: String, val value: Boolean) : StudyAction
  data class SetSkipped(val wordId: String, val value: Boolean) : StudyAction
  data class Submit(val cardId: String, val optionId: String?, val generation: Long? = null) : StudyAction
  data class Advance(val cardId: String, val generation: Long? = null) : StudyAction
  data class Explain(val cardId: String, val generation: Long? = null) : StudyAction
}

sealed interface StudyMutation {
  data object Busy : StudyMutation
  data object Idle : StudyMutation
  data class Loaded(val snapshot: StudySnapshot, val settings: StudySettings? = null) : StudyMutation
  data class Failed(val message: UiMessageKey) : StudyMutation
}

object StudyReducer {
  fun reduce(state: StudyUiState, mutation: StudyMutation): StudyUiState = when (mutation) {
    StudyMutation.Busy -> state.copy(busy = true, error = null)
    StudyMutation.Idle -> state.copy(busy = false)
    is StudyMutation.Loaded -> state.copy(loading = false,
      snapshot = state.snapshot?.takeIf { it == mutation.snapshot.copy(revision = it.revision) } ?: mutation.snapshot,
      settings = mutation.settings ?: state.settings, error = null,
      directionGeneration = mutation.snapshot.contextVersion)
    is StudyMutation.Failed -> state.copy(loading = false, error = mutation.message)
  }
}

class StudyViewModel(
  private val study: StudyRepository,
  private val settings: SettingsRepository,
  private val wordState: WordStateRepository,
  private val wordlist: WordlistRepository,
  private val speechCache: SpeechCacheController,
) : ViewModel() {
  private val mutableState = MutableStateFlow(StudyUiState())
  val state = mutableState.asStateFlow()
  private val operationLock = Mutex()
  private var observation: Job? = null
  private var failedAction: StudyAction = StudyAction.Refresh
  private var lastSnapshotRevision = 0L
  private var refreshing = false

  private var homeWords = emptyList<WordlistItem>()
  private var homeWordObservation: Job? = null
  private var homeSpeechToken = 0L
  private var homeSpeechActive = false
  private val homeSpeechVisibility = MutableStateFlow(false)
  private val studySpeechVisibility = MutableStateFlow(false)
  private val studySpeechOwner = SpeechCacheOwner("study/window")
  private val currentSpeechOwner = SpeechCacheOwner("study/current")
  private val entrySpeechOwner = SpeechCacheOwner("home/entries")
  private val homeQueue = HomeSpeechQueue(viewModelScope,
    prepare = { requests, priority -> speechCache.prepare(SpeechCacheOwner("home/shuffle"),
      SpeechCacheWindow(state.value.directionGeneration,
        requests.map { SpeechCacheDemand(it, priority) })) },
    isReady = speechCache::isReady,
    currentWord = { state.value.homeWord },
    onWaiting = { waiting, failed -> mutableState.update { it.copy(homeShuffleWaiting = waiting, homeShuffleFailed = failed) } },
    onSelected = { word, request -> mutableState.update { current ->
      if (homeSpeechActive && !current.busy && !current.loading &&
        current.homeWordStage == current.today?.stageProgress?.stage &&
        word.stage.code == current.today?.stageProgress?.stage?.code &&
        request.language == AppLanguage.EN) {
        current.copy(homeWord = word, homeWordError = false, homeSpeechEvent = HomeSpeechEvent(++homeSpeechToken, request))
      } else current
    } }, onReadyCount = { count -> mutableState.update { it.copy(homeShuffleReady = count) } })

  init {
    observe(); observeHomeWords(); observeSpeechWindows()
    viewModelScope.launch { speechCache.changes.collect { homeQueue.refreshReadiness(retryMissing = false) } }
    viewModelScope.launch { speechCache.state.collect { windows ->
      val current = state.value
      val window = windows[currentSpeechOwner]
      val preparing = studySpeechVisibility.value && current.card?.phase != CardPhase.FINISHED &&
        window != null && window.contextVersion == current.directionGeneration && (window.queued > 0 || window.preparing > 0)
      mutableState.update { it.copy(speechPreparing = preparing) }
    } }
  }

  fun shuffleHomeWord() {
    if (state.value.busy || state.value.loading) return
    if (homeWordObservation?.isActive != true) { observeHomeWords(); return }
    if (state.value.homeWordStage != state.value.today?.stageProgress?.stage) return
    homeQueue.shuffle()
  }

  fun setHomeSpeechActive(active: Boolean) {
    homeSpeechActive = active
    homeSpeechVisibility.value = active
    syncHomeSpeechActivity()
  }

  fun setStudySpeechActive(active: Boolean) {
    studySpeechVisibility.value = active
    if (!active) mutableState.update { it.copy(speechPreparing = false) }
  }

  private fun syncHomeSpeechActivity() {
    val current = state.value
    val active = homeSpeechActive && !current.busy && !current.loading &&
      current.homeWordStage == current.today?.stageProgress?.stage
    if (!active || current.homeSpeechEvent?.request?.language?.let { it != AppLanguage.EN } == true)
      mutableState.update { it.copy(homeSpeechEvent = null) }
    homeQueue.setActive(active)
  }

  fun consumeHomeSpeech(token: Long) { mutableState.update {
    if (it.homeSpeechEvent?.token == token) it.copy(homeSpeechEvent = null) else it
  } }

  private data class StudySpeechWindowKey(val cardId: String, val word: WordEntry,
    val contextVersion: Long, val upcomingCount: Int)

  private fun observeSpeechWindows() {
    viewModelScope.launch {
      combine(state.map { current ->
        current.card?.takeIf { !current.loading && !it.isSkipped && it.phase != CardPhase.FINISHED }?.let {
          StudySpeechWindowKey(it.id, it.word, current.directionGeneration,
            current.settings.speechPreloadWords)
        }
      }.distinctUntilChanged(), studySpeechVisibility) { key, visible -> key.takeIf { visible } }
        .distinctUntilChanged().collectLatest { key ->
          if (key == null) {
            speechCache.release(currentSpeechOwner)
            speechCache.release(studySpeechOwner)
            return@collectLatest
          }
          var currentRegistered = false
          try {
            val basic = wordSpeechBasics(key.word)
            speechCache.updateWindow(currentSpeechOwner, SpeechCacheWindow(key.contextVersion,
              basic.map { SpeechCacheDemand(it, SpeechCachePriority.CURRENT_WORD) }))
            currentRegistered = true
            val upcoming = if (key.upcomingCount == 0) emptyList() else withContext(Dispatchers.IO) {
              study.peekSpeechWords(key.cardId, key.upcomingCount, key.contextVersion)
            }.filter { it.id != key.word.id }
            val demands = buildList {
              basic.forEach { add(SpeechCacheDemand(it, SpeechCachePriority.CURRENT_WORD)) }
              upcoming.forEachIndexed { index, word -> wordSpeechBasics(word).forEach {
                add(SpeechCacheDemand(it, SpeechCachePriority.UPCOMING_WORD + index))
              } }
              wordSpeechExamples(key.word).forEach { add(SpeechCacheDemand(it, SpeechCachePriority.CURRENT_EXAMPLE)) }
              upcoming.forEachIndexed { index, word -> wordSpeechExamples(word).forEach {
                add(SpeechCacheDemand(it, SpeechCachePriority.UPCOMING_EXAMPLE + index))
              } }
            }
            speechCache.updateWindow(studySpeechOwner, SpeechCacheWindow(key.contextVersion, demands))
          } catch (error: CancellationException) { throw error }
          catch (_: Exception) {
            if (!currentRegistered) speechCache.release(currentSpeechOwner)
            speechCache.release(studySpeechOwner)
          }
          catch (_: LinkageError) {
            if (!currentRegistered) speechCache.release(currentSpeechOwner)
            speechCache.release(studySpeechOwner)
          }
        }
    }
    viewModelScope.launch {
      combine(state.map { current -> current.snapshot.takeIf {
        !current.loading && current.settings.speechPreloadWords > 0
      } }.distinctUntilChanged(),
        homeSpeechVisibility) { snapshot, visible -> snapshot.takeIf { visible } }
        .distinctUntilChanged().collectLatest { snapshot ->
          if (snapshot == null) { speechCache.release(entrySpeechOwner); return@collectLatest }
          try {
            val words = withContext(Dispatchers.IO) {
              study.peekEntrySpeechWords(snapshot.contextVersion)
            }
            speechCache.updateWindow(entrySpeechOwner, SpeechCacheWindow(snapshot.contextVersion,
              words.flatMapIndexed { index, word -> wordSpeechBasics(word).map {
                SpeechCacheDemand(it, SpeechCachePriority.HOME_ENTRY + index)
              } }))
          } catch (error: CancellationException) { throw error }
          catch (_: Exception) { speechCache.release(entrySpeechOwner) }
          catch (_: LinkageError) { speechCache.release(entrySpeechOwner) }
        }
    }
  }

  override fun onCleared() {
    speechCache.release(currentSpeechOwner)
    speechCache.release(studySpeechOwner)
    speechCache.release(entrySpeechOwner)
    super.onCleared()
  }

  private fun observeHomeWords() {
    homeWordObservation?.cancel()
    homeWordObservation = viewModelScope.launch {
      try {
        combine(state.map { it.today?.stageProgress?.stage to it.settings }.distinctUntilChanged(), wordlist.entries) {
          (stage, preferences), words -> Triple(stage, preferences,
            if (stage == null) emptyList() else words.filter { it.stage.code == stage.code })
        }.distinctUntilChanged().flowOn(Dispatchers.Default).collect { (stage, preferences, words) ->
          if (stage != state.value.today?.stageProgress?.stage) return@collect
          homeWords = words
          mutableState.update { current -> current.copy(
            homeWord = words.firstOrNull { it.id == current.homeWord?.id } ?: words.randomOrNull(),
            homeWordError = false, homeWordStage = stage) }
          homeQueue.configure(words, AppLanguage.EN, preferences.speechOptions, preferences.homeShuffleQueueSize)
          syncHomeSpeechActivity()
        }
      } catch (error: CancellationException) {
        throw error
      } catch (_: Exception) {
        homeWords = emptyList()
        homeQueue.configure(emptyList(), AppLanguage.EN,
          state.value.settings.speechOptions, state.value.settings.homeShuffleQueueSize)
        mutableState.update { it.copy(homeWord = null, homeWordError = true) }
      }
    }
  }

  fun onAction(action: StudyAction) {
    when (action) {
      StudyAction.Retry -> {
        if (observation?.isActive != true) observe()
        execute(failedAction)
      }
      else -> execute(action)
    }
  }

  fun start(kind: StudyKind) = onAction(StudyAction.Start(kind))
  fun selectStage(stage: VocabularyStage) = onAction(StudyAction.SelectStage(stage))
  fun setCollected(wordId: String, value: Boolean) = onAction(StudyAction.SetCollected(wordId, value))
  fun setSkipped(wordId: String, value: Boolean) = onAction(StudyAction.SetSkipped(wordId, value))
  fun learnMore() { state.value.today?.let {
    onAction(StudyAction.LearnMore(it.date, it.newPlanned, it.stageProgress.stage, it.stageProgress.lap))
  } }
  fun reviewMore() { state.value.today?.let {
    onAction(StudyAction.ReviewMore(it.date, it.reviewPlanned, it.contextVersion))
  } }
  fun refresh() = onAction(StudyAction.Refresh)
  fun retry() = onAction(StudyAction.Retry)
  fun advance() { state.value.card?.let { onAction(StudyAction.Advance(it.id, state.value.directionGeneration)) } }
  fun submit(optionId: String?) { state.value.card?.let { onAction(StudyAction.Submit(it.id, optionId, state.value.directionGeneration)) } }
  fun explain() { state.value.card?.let { onAction(StudyAction.Explain(it.id, state.value.directionGeneration)) } }

  private fun mutate(mutation: StudyMutation) {
    if (mutation is StudyMutation.Loaded) {
      lastSnapshotRevision = mutation.snapshot.revision
      if (state.value.snapshot?.contextVersion != mutation.snapshot.contextVersion) {
        failedAction = StudyAction.Refresh
      }
    }
    mutableState.update { StudyReducer.reduce(it, mutation) }
    syncHomeSpeechActivity()
  }

  private fun observe() {
    observation?.cancel()
    observation = viewModelScope.launch {
      try {
        combine(settings.settings, study.changes.onStart { emit(0L) }) { value, revision -> value to revision }
          .collect { (value, revision) ->
            operationLock.withLock {
              val loaded = state.value
              // Actions already return the snapshot saved at this revision. Keep later external changes observable.
              if (loaded.snapshot != null &&
                revision <= lastSnapshotRevision && value == loaded.settings) {
                return@withLock
              }
              val (snapshot, value) = withContext(Dispatchers.IO) {
                var snapshot = study.snapshot()
                val value = settings.settings.first()
                snapshot to value
              }
              mutate(StudyMutation.Loaded(snapshot, value))
            }
          }
      } catch (error: CancellationException) {
        throw error
      } catch (_: Exception) {
        failedAction = StudyAction.Refresh
        mutate(StudyMutation.Failed(UiMessageKey.YOUR_SAVED_PROGRESS_COULDN_T_BE_LOADED_TRY_AGAIN))
      }
    }
  }

  private fun execute(action: StudyAction) {
    // Set busy before launch: two quick taps cannot queue duplicate writes.
    if (state.value.busy) return
    val refresh = action == StudyAction.Refresh
    if (refresh && refreshing) return
    val showBusy = !refresh || state.value.snapshot == null
    val generation = when (action) {
      is StudyAction.ReviewMore -> action.generation
      is StudyAction.Submit -> action.generation
      is StudyAction.Advance -> action.generation
      is StudyAction.Explain -> action.generation
      else -> null
    }
    if (generation != null && generation != state.value.directionGeneration) return
    if (refresh) refreshing = true
    if (showBusy) mutate(StudyMutation.Busy)
    viewModelScope.launch {
      var stored = false
      try {
        operationLock.withLock {
          if (generation != null && generation != state.value.directionGeneration) return@withLock
          val currentSettings = settings.settings.first()
          val snapshot = withContext(Dispatchers.IO) {
            when (action) {
              is StudyAction.Start -> study.start(action.kind)
              is StudyAction.SelectStage -> study.selectStage(action.stage)
              is StudyAction.LearnMore -> study.learnMore(action.expectedDate, action.expectedNewPlanned,
                action.expectedStage, action.expectedLap)
              is StudyAction.ReviewMore -> study.reviewMore(action.expectedDate, action.expectedReviewPlanned,
                action.generation)
              is StudyAction.SetCollected -> {
                wordState.setCollected(action.wordId, action.value)
                stored = true
                study.snapshot()
              }
              is StudyAction.SetSkipped -> {
                wordState.setSkipped(action.wordId, action.value)
                stored = true
                study.snapshot()
              }
              is StudyAction.Submit -> study.submit(action.cardId, action.optionId,
                expectedContextVersion = generation ?: state.value.directionGeneration)
              is StudyAction.Advance -> study.advance(action.cardId,
                expectedContextVersion = generation ?: state.value.directionGeneration)
              is StudyAction.Explain -> study.explain(action.cardId,
                expectedContextVersion = generation ?: state.value.directionGeneration)
              else -> study.snapshot()
            }
          }
          stored = true
          mutate(StudyMutation.Loaded(snapshot))
        }
      } catch (error: CancellationException) {
        throw error
      } catch (_: NewStudyDayException) {
        failedAction = StudyAction.Refresh
        try {
          operationLock.withLock {
            mutate(StudyMutation.Loaded(withContext(Dispatchers.IO) { study.snapshot() }))
          }
        } catch (error: CancellationException) {
          throw error
        } catch (_: Exception) {
          // Preserve the last saved snapshot and keep retry available.
        }
        mutate(StudyMutation.Failed(UiMessageKey.A_NEW_DAY_HAS_STARTED_YOUR_DAILY_PLAN_HAS_BEEN_REFRESHED))
      } catch (_: Exception) {
        failedAction = if (stored) StudyAction.Refresh else action
        mutate(StudyMutation.Failed(UiMessageKey.THIS_STEP_COULDN_T_BE_COMPLETED_TRY_AGAIN))
      } finally {
        if (refresh) refreshing = false
        if (showBusy) mutate(StudyMutation.Idle)
      }
    }
  }



}
