package com.example.flashcardforteens.feature.wordlist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.flashcardforteens.core.domain.*
import com.example.flashcardforteens.core.ui.*
import com.example.flashcardforteens.core.ui.R as UiR
import kotlinx.coroutines.awaitCancellation

@Composable
fun WordDetailScreen(vm: WordDetailViewModel, onBack: () -> Unit, onSpeak: (SpeechRequest) -> Unit) {
  val state by vm.state.collectAsStateWithLifecycle()
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  val latestSpeak by rememberUpdatedState(onSpeak)
  LaunchedEffect(vm, lifecycle) {
    lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
      vm.setSpeechActive(true)
      try { awaitCancellation() } finally { vm.setSpeechActive(false) }
    }
  }
  BackHandler(enabled = state.busy) { }
  LaunchedEffect(vm, state.word?.id, state.loading, lifecycle) {
    val word = state.word
    if (word != null && !state.loading) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
      if (vm.claimAutoplay()) latestSpeak(word.detailSpeech())
      awaitCancellation()
    }
  }
  WordDetailContent(state, onBack, onSpeak, vm::setCollected, vm::setSkipped, vm::retry)
}

@Composable
private fun WordDetailContent(state: WordDetailUiState, onBack: () -> Unit,
  onSpeak: (SpeechRequest) -> Unit, onCollected: (Boolean) -> Unit, onSkipped: (Boolean) -> Unit,
  onRetry: () -> Unit) {
  val word = state.word
  val headingSource = rememberSpeechSourceId(word?.id to "heading")
  val enabled = !state.loading && !state.busy
  Scaffold(containerColor = Color.Transparent, contentWindowInsets = WindowInsets.safeDrawing,
    topBar = {
      FlashcardToolbar(uiString(UiR.string.ui_word), onBack,
        Modifier.padding(horizontal = 12.dp, vertical = 4.dp), enabled = !state.busy)
    }) { padding ->
    Column(Modifier.fillMaxSize().flashcardBackground().padding(padding)
      .verticalScroll(rememberScrollState()).padding(FlashcardLayout.pageInset),
      verticalArrangement = Arrangement.spacedBy(FlashcardLayout.sectionGap)) {
      if (state.loading) {
        CircularProgressIndicator()
        Text(uiString(UiR.string.ui_loading))
      } else if (word != null) {
        Surface(modifier = Modifier.fillMaxWidth().glassPanel(), shape = MaterialTheme.shapes.large,
          color = MaterialTheme.colorScheme.surface) {
          Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = MaterialTheme.shapes.small, color = stageAccent(word.stage).copy(alpha = .12f)) {
              Text(word.stage.label, Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge, color = stageAccent(word.stage))
            }
            WordHeading(word, enabled, onSpeak = { onSpeak(word.detailSpeech().copy(sourceId = headingSource)) },
              sourceId = headingSource, trailingOnNewLine = true, trailing = {
                WordUserActions(state.userState.isCollected, state.userState.isSkipped, enabled,
                  onCollected, onSkipped, wordId = word.id, wordLabel = word.headword)
              })
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            WordMeanings(word, onSpeak, enabled)
          }
        }
        WordExplanation(word, onSpeak, enabled = enabled, progressive = true)
        if (word.pronunciationNote.isNotBlank()) {
          Surface(modifier = Modifier.fillMaxWidth().glassPanel(), shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface) {
            Text(word.pronunciationNote, Modifier.padding(16.dp),
              style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
      state.error?.let {
        Text(uiString(it), Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
          color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onRetry, enabled = !state.busy) { Text(uiString(UiR.string.ui_try_again)) }
      }
    }
  }
}

private fun WordEntry.detailSpeech() = SpeechRequest(headword, AppLanguage.EN, SpeechScene.WORD_DETAIL,
  counterpart = chineseMeaning, chinesePronunciation = ChinesePronunciation(chineseMeaning, chinesePinyin))

@Preview(name = "Word detail", widthDp = 360, heightDp = 760)
@Preview(name = "Word detail · compact", widthDp = 320, heightDp = 760, fontScale = 1.3f)
@Composable
private fun WordDetailPreview() {
  val word = WordEntry("tomorrow", "tomorrow", "/təˈmɒrəʊ/", "明天", "míngtiān", "adv.",
    VocabularyStage.GRADE_4_FIRST,
    examples = listOf(ExampleSentence("See you tomorrow!", "见你明天！", "明天见！", "míngtiān jiàn", emptyList())),
    distractorWordIds = emptyList())
  FlashcardTheme {
    WordDetailContent(WordDetailUiState(loading = false, word = word), onBack = {}, onSpeak = {},
      onCollected = {}, onSkipped = {}, onRetry = {})
  }
}
