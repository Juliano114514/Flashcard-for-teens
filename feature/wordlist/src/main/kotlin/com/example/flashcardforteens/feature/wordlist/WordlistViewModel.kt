package com.example.flashcardforteens.feature.wordlist

import com.example.flashcardforteens.core.domain.UiMessageKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flashcardforteens.core.domain.StudyRepository
import com.example.flashcardforteens.core.domain.WordEntry
import com.example.flashcardforteens.core.domain.WordlistItem
import com.example.flashcardforteens.core.domain.WordlistRepository
import com.example.flashcardforteens.core.domain.WordlistStatus
import com.example.flashcardforteens.core.domain.VocabularyStage
import com.example.flashcardforteens.core.domain.WordStateRepository
import com.example.flashcardforteens.core.domain.AppLanguage
import com.example.flashcardforteens.core.domain.WordUserState
import com.example.flashcardforteens.core.domain.SpeechCacheController
import com.example.flashcardforteens.core.domain.SpeechCacheDemand
import com.example.flashcardforteens.core.domain.SpeechCacheOwner
import com.example.flashcardforteens.core.domain.SpeechCachePriority
import com.example.flashcardforteens.core.domain.SpeechCacheWindow
import com.example.flashcardforteens.core.domain.wordSpeechBasics
import com.example.flashcardforteens.core.domain.wordSpeechExamples
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class WordlistUiState(
  val loading: Boolean = true,
  val entries: List<WordlistItem> = emptyList(),
  val total: Int = 0,
  val learned: Int = 0,
  val learning: Int = 0,
  val unlearned: Int = 0,
  val query: String = "",
  val status: WordlistStatus? = null,
  val difficulties: Set<Int> = emptySet(),
  val myCollectionOnly: Boolean = false,
  val skipped: Int = 0,
  val error: UiMessageKey? = null,
)

class WordlistViewModel(private val repository: WordlistRepository) : ViewModel() {
  private val mutableState = MutableStateFlow(WordlistUiState())
  val state = mutableState.asStateFlow()
  private data class SearchEntry(val item: WordlistItem, val headword: String, val meaning: String)
  private data class CatalogUpdate(val entries: List<SearchEntry>, val learned: Int, val learning: Int,
    val unlearned: Int, val skipped: Int)
  private var searchEntries = emptyList<SearchEntry>()
  private var observation: Job? = null
  private var searchJob: Job? = null
  private var searchGeneration = 0L
  var scrollIndex: Int = 0
    private set
  var scrollOffset: Int = 0
    private set

  init { observe() }

  fun setQuery(value: String) {
    if (value == state.value.query) return
    resetScroll()
    mutableState.update { it.copy(query = value) }
    updateResults()
  }

  fun setStatus(value: WordlistStatus?) {
    if (value == state.value.status) return
    resetScroll()
    mutableState.update { it.copy(status = value) }
    updateResults()
  }

  fun setMyCollection(value: Boolean) {
    if (value == state.value.myCollectionOnly) return
    resetScroll()
    mutableState.update { it.copy(myCollectionOnly = value) }
    updateResults()
  }

  /** An empty set is All. Selecting All clears the numbered choices. */
  fun toggleDifficulty(value: Int?) {
    if (value != null && VocabularyStage.fromCode(value) == null) return
    val current = state.value.difficulties
    val next = if (value == null) emptySet() else if (value in current) current - value else current + value
    if (next == current) return
    resetScroll()
    mutableState.update { it.copy(difficulties = next) }
    updateResults()
  }

  fun rememberScroll(index: Int, offset: Int) {
    scrollIndex = index.coerceAtLeast(0)
    scrollOffset = offset.coerceAtLeast(0)
  }

  fun retry() = observe()

  private fun resetScroll() { scrollIndex = 0; scrollOffset = 0 }

  private fun observe() {
    observation?.cancel()
    mutableState.update { it.copy(loading = searchEntries.isEmpty(), error = null) }
    observation = viewModelScope.launch {
      try {
        repository.entries.collectLatest { items ->
          // Progress/collection changes reuse the catalog's normalized text.
          val previous = searchEntries
          val updated = withContext(Dispatchers.Default) {
            val cached = previous.associateBy { it.item.id }
            val entries = items.map { item ->
              val entry = cached[item.id]
              if (entry != null && entry.item.headword == item.headword && entry.item.chineseMeaning == item.chineseMeaning) {
                if (entry.item == item) entry else entry.copy(item = item)
              } else SearchEntry(item, searchText(item.headword), searchText(item.chineseMeaning))
            }
            CatalogUpdate(entries, items.count { it.learningStatus == WordlistStatus.LEARNED },
              items.count { it.learningStatus == WordlistStatus.LEARNING },
              items.count { it.learningStatus == WordlistStatus.UNLEARNED }, items.count { it.isSkipped })
          }
          searchEntries = updated.entries
          mutableState.update { it.copy(error = null, total = items.size,
            learned = updated.learned, learning = updated.learning,
            unlearned = updated.unlearned, skipped = updated.skipped) }
          updateResults()
        }
      } catch (error: CancellationException) {
        throw error
      } catch (_: Exception) {
        mutableState.update { it.copy(loading = false, error = UiMessageKey.YOUR_WORDLIST_COULDN_T_BE_LOADED_TRY_AGAIN) }
      }
    }
  }

  private fun updateResults() {
    searchJob?.cancel()
    val generation = ++searchGeneration
    val filters = state.value
    val catalog = searchEntries
    searchJob = viewModelScope.launch {
      val filtered = withContext(Dispatchers.Default) {
        val needle = searchText(filters.query.trim())
        catalog.filter { entry ->
          (filters.status == null || filters.status == entry.item.status) &&
            (!filters.myCollectionOnly || entry.item.isCollected) &&
            (filters.difficulties.isEmpty() || entry.item.stage.code in filters.difficulties) &&
            (needle.isEmpty() || entry.headword.contains(needle) || entry.meaning.contains(needle))
        }.map(SearchEntry::item)
      }
      // A catalog refresh or newer keystroke cancels this job and owns the result.
      if (generation == searchGeneration) mutableState.update { it.copy(entries = filtered, loading = false) }
    }
  }
}

private fun searchText(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
  .filterNot { Character.getType(it) == Character.NON_SPACING_MARK.toInt() }
  .lowercase(Locale.ROOT)

data class WordDetailUiState(
  val loading: Boolean = true,
  val busy: Boolean = false,
  val word: WordEntry? = null,
  val error: UiMessageKey? = null,
  val userState: WordUserState = WordUserState(),
  val contextVersion: Long = 0,
)

class WordDetailViewModel(
  val wordId: String,
  private val repository: WordlistRepository,
  private val study: StudyRepository,
  private val wordState: WordStateRepository,
  private val speechCache: SpeechCacheController,
) : ViewModel() {
  private val mutableState = MutableStateFlow(WordDetailUiState())
  val state = mutableState.asStateFlow()
  private var loading: Job? = null
  private var spoken = false
  private var retryAction: () -> Unit = ::load
  private val speechVisible = MutableStateFlow(false)
  private val speechOwner = SpeechCacheOwner("word/detail")

  init { load(); observeSpeechWindow() }

  fun setSpeechActive(active: Boolean) { speechVisible.value = active }

  private fun observeSpeechWindow() {
    viewModelScope.launch {
      combine(state.map { it.word to it.contextVersion }.distinctUntilChanged(), speechVisible) { content, visible ->
        content.takeIf { visible }
      }.distinctUntilChanged().collectLatest { content ->
        if (content == null) { speechCache.release(speechOwner); return@collectLatest }
        val word = content.first
        if (word == null) { speechCache.release(speechOwner); return@collectLatest }
        try {
          val demands = wordSpeechBasics(word).map { SpeechCacheDemand(it, SpeechCachePriority.CURRENT_WORD) } +
            wordSpeechExamples(word).map { SpeechCacheDemand(it, SpeechCachePriority.CURRENT_EXAMPLE) }
          speechCache.updateWindow(speechOwner, SpeechCacheWindow(content.second, demands))
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { speechCache.release(speechOwner) }
        catch (_: LinkageError) { speechCache.release(speechOwner) }
      }
    }
  }

  override fun onCleared() {
    speechCache.release(speechOwner)
    super.onCleared()
  }

  fun retry() {
    if (state.value.busy) return
    retryAction()
  }

  /** Autoplay is claimed once for this detail visit. */
  fun claimAutoplay(): Boolean {
    if (spoken || state.value.loading || state.value.word == null) return false
    spoken = true
    return true
  }



  fun setCollected(value: Boolean) {
    updateLabel({ setCollected(value) }) { wordState.setCollected(wordId, value) }
  }

  fun setSkipped(value: Boolean) {
    updateLabel({ setSkipped(value) }) { wordState.setSkipped(wordId, value) }
  }

  private fun updateLabel(retry: () -> Unit, operation: suspend () -> Unit) {
    if (state.value.loading || state.value.busy || state.value.word == null) return
    retryAction = retry
    mutableState.update { it.copy(busy = true, error = null) }
    viewModelScope.launch {
      try {
        withContext(Dispatchers.IO) { operation() }
      } catch (error: CancellationException) {
        throw error
      } catch (_: Exception) {
        mutableState.update { it.copy(error = UiMessageKey.THIS_CHANGE_COULDN_T_BE_SAVED_TRY_AGAIN) }
      } finally {
        mutableState.update { it.copy(busy = false) }
      }
    }
  }



  private fun load() {
    retryAction = ::load
    loading?.cancel()
    mutableState.update { it.copy(loading = true, error = null) }
    loading = viewModelScope.launch {
      try {
        val context = withContext(Dispatchers.IO) { study.context() }
        val word = withContext(Dispatchers.IO) { repository.word(wordId) }
        val labels = if (word != null) wordState.observe(wordId).first() else WordUserState()
        mutableState.update { it.copy(loading = false, word = word, userState = labels, contextVersion = context.version,
          error = if (word == null) UiMessageKey.THIS_WORD_IS_NO_LONGER_AVAILABLE else null) }
        if (word != null) wordState.observe(wordId).collectLatest { labels ->
          mutableState.update { it.copy(userState = labels) }
        }
      } catch (error: CancellationException) {
        throw error
      } catch (_: Exception) {
        mutableState.update { it.copy(loading = false, error = UiMessageKey.THIS_WORD_COULDN_T_BE_LOADED_TRY_AGAIN) }
      }
    }
  }
}
