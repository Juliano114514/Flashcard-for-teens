package com.example.flashcardforteens.feature.wordlist

import com.example.flashcardforteens.core.ui.uiString
import com.example.flashcardforteens.core.ui.R as UiR

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.flashcardforteens.core.domain.WordlistItem
import com.example.flashcardforteens.core.domain.WordlistStatus
import com.example.flashcardforteens.core.domain.VocabularyStage
import com.example.flashcardforteens.core.ui.FlashcardDialog
import com.example.flashcardforteens.core.ui.LearningPrimaryButton
import com.example.flashcardforteens.core.ui.FlashcardStyle
import com.example.flashcardforteens.core.ui.FlashcardLayout
import com.example.flashcardforteens.core.ui.FlashcardTheme
import com.example.flashcardforteens.core.ui.CollectionIcon
import com.example.flashcardforteens.core.ui.flashcardBackground
import com.example.flashcardforteens.core.ui.StageThemePreviewCase
import com.example.flashcardforteens.core.ui.glassPanel
import com.example.flashcardforteens.core.ui.glassPanelColor
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

@Composable
fun WordlistScreen(vm: WordlistViewModel, onWord: (String) -> Unit) {
  val state by vm.state.collectAsStateWithLifecycle()
  val listState = rememberLazyListState(vm.scrollIndex, vm.scrollOffset)
  val scope = rememberCoroutineScope()
  LaunchedEffect(vm, listState) {
    snapshotFlow { listState.isScrollInProgress }.filter { !it }.collect {
      if (!vm.state.value.loading) vm.rememberScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
    }
  }
  DisposableEffect(vm, listState) {
    onDispose { vm.rememberScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) }
  }
  val resetScroll = { scope.launch { listState.scrollToItem(0) }; Unit }
  WordlistContent(state, listState,
    onQuery = { vm.setQuery(it); resetScroll() },
    onStatus = { vm.setStatus(it); resetScroll() },
    onDifficulty = { vm.toggleDifficulty(it); resetScroll() },
    onCollection = { vm.setMyCollection(it); resetScroll() },
    onRetry = vm::retry, onWord = onWord)
}

@Composable
private fun WordlistContent(state: WordlistUiState, listState: LazyListState,
  onQuery: (String) -> Unit, onStatus: (WordlistStatus?) -> Unit, onDifficulty: (Int?) -> Unit,
  onCollection: (Boolean) -> Unit, onRetry: () -> Unit, onWord: (String) -> Unit) {
  var choosingBooks by rememberSaveable { mutableStateOf(false) }
  val bookLabel = when (state.difficulties.size) {
    0 -> stringResource(R.string.wordbook_all_books)
    1 -> VocabularyStage.fromCode(state.difficulties.first())?.label.orEmpty()
    else -> stringResource(R.string.wordbook_selected_books, state.difficulties.size)
  }
  Surface(Modifier.fillMaxSize().flashcardBackground(), color = Color.Transparent) {
    LazyColumn(state = listState, contentPadding = PaddingValues(FlashcardLayout.pageInset),
      verticalArrangement = Arrangement.spacedBy(12.dp)) {
      item(key = "header", contentType = "header") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(bottom = 8.dp)) {
          Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(uiString(UiR.string.ui_wordlist), style = MaterialTheme.typography.headlineLarge, maxLines = 1)
              Text(stringResource(R.string.wordbook_progress, state.learned, state.total),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(onClick = { choosingBooks = true }, modifier = Modifier.widthIn(max = 156.dp),
              shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface,
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
              Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(bookLabel, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                  maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(20.dp))
              }
            }
          }
          Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WordbookTab(stringResource(R.string.wordbook_all_words), !state.myCollectionOnly,
              onClick = { onCollection(false) }, modifier = Modifier.weight(1f))
            WordbookTab(stringResource(R.string.wordbook_collection), state.myCollectionOnly,
              onClick = { onCollection(true) }, modifier = Modifier.weight(1f))
          }
          OutlinedTextField(value = state.query, onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth(), singleLine = true, shape = MaterialTheme.shapes.medium,
            textStyle = MaterialTheme.typography.bodyLarge,
            placeholder = { Text(uiString(UiR.string.ui_hanzi_pinyin_or_meaning), style = MaterialTheme.typography.bodyMedium) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(22.dp)) },
            trailingIcon = if (state.query.isNotEmpty()) {
              { IconButton(onClick = { onQuery("") }) { Icon(Icons.Default.Close, contentDescription = uiString(UiR.string.ui_clear_search)) } }
            } else null)
          Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            WordbookStatusTab(uiString(UiR.string.ui_all), state.status == null,
              onClick = { onStatus(null) }, modifier = Modifier.weight(1f))
            WordlistStatus.entries.forEach { status ->
              val description = status.label()
              WordbookStatusTab(status.shortLabel(), state.status == status,
                onClick = { onStatus(status) }, modifier = Modifier.weight(1f).semantics { contentDescription = description })
            }
          }
          state.error?.let { message ->
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text(uiString(message), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
              TextButton(shape = MaterialTheme.shapes.small, onClick = onRetry) { Text(uiString(UiR.string.ui_try_again)) }
            }
          }
          if (state.loading) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(28.dp))
          }
        }
      }
      if (!state.loading && state.error == null && state.entries.isEmpty()) {
        item(key = "empty", contentType = "empty") {
          Surface(modifier = Modifier.fillMaxWidth().glassPanel(), shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface) {
            Text(uiString(if (state.total == 0) UiR.string.ui_no_words_in_your_wordlist else UiR.string.ui_no_matching_words),
              modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
      items(state.entries, key = { "word/${it.id}" }, contentType = { "word" }) { entry ->
        WordlistRow(entry, onClick = { onWord(entry.id) })
      }
    }
  }
  if (choosingBooks) WordbookPicker(state.difficulties, onDifficulty, onDismiss = { choosingBooks = false })
}

@Composable
private fun WordbookTab(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
  Surface(modifier = modifier, shape = MaterialTheme.shapes.medium,
    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .25f)
      else MaterialTheme.colorScheme.outlineVariant)) {
    Box(Modifier.heightIn(min = 48.dp).selectable(selected, onClick = onClick, role = Role.Tab)
      .padding(horizontal = 10.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
      Text(label, style = MaterialTheme.typography.titleSmall,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
private fun WordbookStatusTab(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
  Surface(modifier = modifier, shape = MaterialTheme.shapes.small,
    color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent) {
    Box(Modifier.heightIn(min = 48.dp).selectable(selected, onClick = onClick, role = Role.Tab)
      .padding(horizontal = 2.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
      Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1,
        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
private fun WordbookPicker(selected: Set<Int>, onDifficulty: (Int?) -> Unit, onDismiss: () -> Unit) {
  FlashcardDialog(onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.wordbook_choose_books)) },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.wordbook_choose_books_hint), style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant)
        WordlistFilterChip(selected.isEmpty(), onClick = { onDifficulty(null) },
          label = stringResource(R.string.wordbook_all_books), modifier = Modifier.fillMaxWidth())
        VocabularyStage.entries.chunked(2).forEach { pair ->
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(pair.first().label.take(3), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            pair.forEach { stage ->
              WordlistFilterChip(stage.code in selected, onClick = { onDifficulty(stage.code) },
                label = stage.label.takeLast(2),
                modifier = Modifier.weight(1f).semantics { contentDescription = stage.label })
            }
          }
        }
      }
    }, confirmButton = { LearningPrimaryButton(onClick = onDismiss) { Text(uiString(UiR.string.ui_done)) } })
}

@Composable
private fun WordlistFilterChip(selected: Boolean, onClick: () -> Unit, label: String,
  modifier: Modifier = Modifier) {
  val colors = MaterialTheme.colorScheme
  FilterChip(selected = selected, onClick = onClick, label = { Text(label) }, modifier = modifier.heightIn(min = 48.dp),
    shape = MaterialTheme.shapes.small,
    colors = FilterChipDefaults.filterChipColors(
      containerColor = colors.surface, labelColor = colors.onSurfaceVariant,
      selectedContainerColor = colors.primaryContainer, selectedLabelColor = colors.onPrimaryContainer),
    border = BorderStroke(1.dp, if (selected) colors.primary.copy(alpha = 0.4f) else colors.outlineVariant))
}

@Composable
private fun WordlistRow(entry: WordlistItem, onClick: () -> Unit) {
  val statusColor = when (entry.status) {
    WordlistStatus.LEARNED -> FlashcardStyle.colors.success
    WordlistStatus.LEARNING -> MaterialTheme.colorScheme.primary
    WordlistStatus.UNLEARNED, WordlistStatus.SKIPPED -> MaterialTheme.colorScheme.onSurfaceVariant
  }
  Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().glassPanel(), shape = MaterialTheme.shapes.large,
    color = glassPanelColor()) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp),
          verticalAlignment = Alignment.CenterVertically) {
          Text(entry.headword, modifier = Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
          if (entry.isCollected) CollectionIcon(true, contentDescription = uiString(UiR.string.ui_in_my_collection))
        }
        Text(if (entry.status == WordlistStatus.LEARNING && entry.targetRounds > 0)
          stringResource(R.string.wordbook_learning_progress, entry.correctRounds, entry.targetRounds)
          else entry.status.shortLabel(), style = MaterialTheme.typography.labelSmall, color = statusColor)
      }
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(entry.ipa, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(entry.stage.label, style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
      }
      Text(entry.chineseMeaning, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
  }
}

@Composable
private fun WordlistStatus.label(): String = when (this) {
  WordlistStatus.UNLEARNED -> uiString(UiR.string.ui_not_started)
  WordlistStatus.LEARNING -> uiString(UiR.string.ui_learning_now)
  WordlistStatus.LEARNED -> uiString(UiR.string.ui_learned)
  WordlistStatus.SKIPPED -> uiString(UiR.string.ui_skipped)
}

@Composable
private fun WordlistStatus.shortLabel(): String = stringResource(when (this) {
  WordlistStatus.UNLEARNED -> R.string.wordbook_unlearned
  WordlistStatus.LEARNING -> R.string.wordbook_learning
  WordlistStatus.LEARNED -> R.string.wordbook_learned
  WordlistStatus.SKIPPED -> R.string.wordbook_skipped
})

@Preview(name = "Wordlist", widthDp = 360, heightDp = 760)
@Preview(name = "Wordlist · compact", widthDp = 320, heightDp = 640, fontScale = 1.3f)
@Composable
private fun WordlistPreview() {
  val theme = StageThemePreviewCase(VocabularyStage.GRADE_3_FIRST, false)
  val entries = listOf(
    WordlistItem("hello", "hello", "/həˈləʊ/", "你好", "nǐ hǎo", VocabularyStage.GRADE_3_FIRST, WordlistStatus.LEARNED, 0, 0, isCollected = true),
    WordlistItem("thanks", "thank you", "/θæŋk juː/", "谢谢", "xièxie", VocabularyStage.GRADE_3_FIRST, WordlistStatus.LEARNING, 2, 4),
    WordlistItem("tomorrow", "tomorrow", "/təˈmɒrəʊ/", "明天", "míngtiān", VocabularyStage.GRADE_4_FIRST, WordlistStatus.SKIPPED, 0, 0,
      isCollected = true, isSkipped = true, learningStatus = WordlistStatus.UNLEARNED),
  )
  FlashcardTheme(darkTheme = theme.darkTheme, stage = theme.stage) {
    Box(Modifier.fillMaxSize().padding(bottom = 72.dp)) {
      WordlistContent(WordlistUiState(loading = false, entries = entries, total = 3, learned = 1, learning = 1, unlearned = 1),
        rememberLazyListState(), onQuery = {}, onStatus = {}, onDifficulty = {}, onCollection = {}, onRetry = {}, onWord = {})
    }
  }
}
