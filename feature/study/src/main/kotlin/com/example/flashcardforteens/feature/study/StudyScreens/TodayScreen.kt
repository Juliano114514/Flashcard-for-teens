package com.example.flashcardforteens.feature.study

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.flashcardforteens.core.domain.AppLanguage
import com.example.flashcardforteens.core.domain.CardPhase
import com.example.flashcardforteens.core.domain.SpeechRequest
import com.example.flashcardforteens.core.domain.StudyCard
import com.example.flashcardforteens.core.domain.StudyKind
import com.example.flashcardforteens.core.domain.StudySnapshot
import com.example.flashcardforteens.core.domain.TodaySummary
import com.example.flashcardforteens.core.domain.VocabularyStage
import com.example.flashcardforteens.core.domain.WordlistItem
import com.example.flashcardforteens.core.domain.WordlistStatus
import com.example.flashcardforteens.core.ui.EditableAvatar
import com.example.flashcardforteens.core.ui.FlashcardDialog
import com.example.flashcardforteens.core.ui.FlashcardIcons
import com.example.flashcardforteens.core.ui.FlashcardLayout
import com.example.flashcardforteens.core.ui.FlashcardTheme
import com.example.flashcardforteens.core.ui.LearningIllustration
import com.example.flashcardforteens.core.ui.LearningPrimaryButton
import com.example.flashcardforteens.core.ui.SpeechPreparingIndicator
import com.example.flashcardforteens.core.ui.flashcardBackground
import com.example.flashcardforteens.core.ui.glassPanel
import com.example.flashcardforteens.core.ui.glassPanelColor
import com.example.flashcardforteens.core.ui.isSpeechPreparing
import com.example.flashcardforteens.core.ui.localizedLabel
import com.example.flashcardforteens.core.ui.localizedPartOfSpeech
import com.example.flashcardforteens.core.ui.rememberSpeechSourceId
import com.example.flashcardforteens.core.ui.uiString
import com.example.flashcardforteens.core.ui.R as UiR

private enum class HomeAction { RESUME, CARRYOVER, REVIEW, REVIEW_MORE, LEARN, LEARN_MORE, COLLECTIONS, MISTAKES }

private data class HomeActionItem(val title: String, val subtitle: String, val icon: ImageVector,
  val enabled: Boolean, val action: HomeAction)

@Composable
fun TodayScreen(
  vm: StudyViewModel,
  onStart: (StudyKind) -> Unit,
  onResume: () -> Unit,
  onLearnMore: () -> Unit,
  onProfile: () -> Unit,
  onWordlist: () -> Unit,
  onSpeak: (SpeechRequest) -> Unit,
  onReviewMore: () -> Unit = {},
) {
  val state by vm.state.collectAsStateWithLifecycle()
  val latestSpeak by androidx.compose.runtime.rememberUpdatedState(onSpeak)
  LaunchedEffect(state.homeSpeechEvent?.token) {
    state.homeSpeechEvent?.let { event ->
      vm.consumeHomeSpeech(event.token)
      if (event.request.language == AppLanguage.EN && !vm.state.value.busy) latestSpeak(event.request)
    }
  }
  var showLearnMoreBlocker by rememberSaveable { mutableStateOf(false) }
  LaunchedEffect(state.today?.learnMoreBlocker) {
    if (state.today?.learnMoreBlocker == null) showLearnMoreBlocker = false
  }
  TodayContent(state, onStart, onResume, onLearnMore,
    onReviewMore = onReviewMore,
    onLearnMoreBlocked = { showLearnMoreBlocker = true }, onStage = vm::selectStage,
    onRetry = vm::retry, onReplay = { sourceId -> vm.state.value.homeReplayRequest(sourceId)?.let(latestSpeak) },
    onShuffle = vm::shuffleHomeWord, onProfile = onProfile, onWordlist = onWordlist)
  state.today?.learnMoreBlocker?.takeIf { showLearnMoreBlocker }?.let { blocker ->
    FlashcardDialog(onDismissRequest = { if (!state.busy) showLearnMoreBlocker = false },
      title = { Text(uiString(UiR.string.ui_finish_today_s_plan)) }, text = {
        Text(when (blocker) {
          StudyKind.REVIEW -> uiString(UiR.string.ui_review_the_due_words_before_learning_more)
          StudyKind.CARRYOVER -> uiString(UiR.string.ui_finish_the_unfinished_words_before_learning_more)
          else -> uiString(UiR.string.ui_finish_the_current_word_before_learning_more)
        })
      }, confirmButton = {
        TextButton(shape = MaterialTheme.shapes.small, onClick = { showLearnMoreBlocker = false; onStart(blocker) }, enabled = !state.busy) {
          Text(when (blocker) {
            StudyKind.REVIEW -> uiString(UiR.string.ui_go_to_review)
            StudyKind.CARRYOVER -> uiString(UiR.string.ui_go_to_continue)
            else -> uiString(UiR.string.ui_continue_learning)
          })
        }
      }, dismissButton = {
        TextButton(shape = MaterialTheme.shapes.small, onClick = { showLearnMoreBlocker = false }, enabled = !state.busy) {
          Text(uiString(UiR.string.ui_back))
        }
      })
  }
}

@Composable
private fun TodayContent(
  state: StudyUiState,
  onStart: (StudyKind) -> Unit,
  onResume: () -> Unit,
  onLearnMore: () -> Unit,
  onLearnMoreBlocked: () -> Unit,
  onStage: (VocabularyStage) -> Unit,
  onRetry: () -> Unit,
  onReplay: (String) -> Unit,
  onShuffle: () -> Unit,
  onProfile: () -> Unit,
  onWordlist: () -> Unit,
  onReviewMore: () -> Unit = {},
) {
  val today = state.today
  val stage = today?.stageProgress?.stage ?: VocabularyStage.GRADE_3_FIRST
  val replaySourceId = rememberSpeechSourceId(state.homeWord?.id)
  val busy = state.busy || state.loading
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
    .padding(horizontal = FlashcardLayout.pageInset).padding(top = 16.dp, bottom = 24.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val name = state.settings.displayName.ifBlank { uiString(R.string.home_friend) }
        Text(uiString(R.string.home_greeting, name),
          style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(uiString(UiR.string.ui_today_adventure), style = MaterialTheme.typography.headlineLarge)
      }
      EditableAvatar(state.settings.avatarId, size = 48.dp,
        accessibilityLabel = uiString(UiR.string.ui_open_profile), onClick = onProfile)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StagePicker(stage, !busy, onStage, Modifier.weight(1f))
        TextButton(onClick = onWordlist, enabled = !busy, shape = MaterialTheme.shapes.small) {
          Text(uiString(UiR.string.ui_wordlist), style = MaterialTheme.typography.labelLarge)
        }
      }
      today?.stageProgress?.let { progress ->
        val learned = progress.learned.coerceIn(0, progress.total.coerceAtLeast(0))
        val fraction = if (progress.total > 0) learned.toFloat() / progress.total else 0f
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(uiString(R.string.home_term_progress, learned, progress.total), Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Text(uiString(R.string.home_term_percent, (fraction * 100).toInt()),
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().height(6.dp),
          color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.primaryContainer)
      }
    }
    if (today != null) {
      HomeStudyPanel(today = today, card = state.card, busy = busy,
        reviewBatchSize = state.settings.reviewBatchSize, onReviewMore = onReviewMore,
        onStart = onStart, onResume = onResume, onLearnMore = onLearnMore,
        onLearnMoreBlocked = onLearnMoreBlocked)
    } else if (state.loading) LoadingNotice()
    state.error?.let { ErrorNotice(it, !busy, onRetry) }
    HomeWordShowcase(word = state.homeWord, wordError = state.homeWordError, wordStage = state.homeWordStage,
      hasToday = today != null, loading = state.loading, busy = state.busy, stage = stage,
      onShuffle = onShuffle, onReplay = { onReplay(replaySourceId) },
      replayEnabled = state.homeReplayRequest(replaySourceId) != null,
      replayPreparing = isSpeechPreparing(replaySourceId),
      shuffleWaiting = state.homeShuffleWaiting || isSpeechPreparing(HOME_SHUFFLE_SOURCE_ID),
      shuffleFailed = state.homeShuffleFailed)
  }
}

@Composable
private fun HomeStudyPanel(
  today: TodaySummary,
  card: StudyCard?,
  busy: Boolean,
  reviewBatchSize: Int,
  onStart: (StudyKind) -> Unit,
  onResume: () -> Unit,
  onLearnMore: () -> Unit,
  onReviewMore: () -> Unit,
  onLearnMoreBlocked: () -> Unit,
) {
  val resume = card?.takeIf { it.phase != CardPhase.FINISHED }
  val open: (HomeAction) -> Unit = { action ->
    if (!busy) when (action) {
      HomeAction.RESUME -> onResume()
      HomeAction.CARRYOVER -> onStart(StudyKind.CARRYOVER)
      HomeAction.REVIEW -> onStart(StudyKind.REVIEW)
      HomeAction.REVIEW_MORE -> onReviewMore()
      HomeAction.LEARN -> onStart(StudyKind.NEW)
      HomeAction.LEARN_MORE -> if (today.learnMoreBlocker != null) onLearnMoreBlocked() else onLearnMore()
      HomeAction.COLLECTIONS -> onStart(StudyKind.COLLECTION)
      HomeAction.MISTAKES -> onStart(StudyKind.MISTAKES)
    }
  }
  // One recommended next step, with every study mode still directly available below.
  val nextAction = when {
    resume != null -> HomeAction.RESUME
    today.carryoverCompleted < today.carryoverPlanned -> HomeAction.CARRYOVER
    today.reviewCompleted < today.reviewPlanned -> HomeAction.REVIEW
    today.newCompleted < today.newPlanned -> HomeAction.LEARN
    today.availableNewWords > 0 || today.learnMoreBlocker != null -> HomeAction.LEARN_MORE
    today.canReviewMore -> HomeAction.REVIEW_MORE
    else -> null
  }
  Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
    color = Color.Transparent,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .12f))) {
    Column(Modifier.background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer,
      MaterialTheme.colorScheme.secondaryContainer))).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
          val title = when {
            today.allComplete -> uiString(R.string.home_goal_complete)
            today.planned > 0 -> uiString(R.string.home_goal_remaining, (today.planned - today.completed).coerceAtLeast(0))
            else -> uiString(R.string.home_goal_first)
          }
          Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
          Text(uiString(UiR.string.ui_progress_count, today.completed, today.planned),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .75f))
        }
        LearningIllustration(Modifier.size(56.dp))
      }
      DailyProgress(today.completed, today.planned)
      LearningPrimaryButton(onClick = { nextAction?.let(open) }, enabled = !busy && nextAction != null,
        modifier = Modifier.fillMaxWidth()) {
        Icon(FlashcardIcons.Play, contentDescription = null, modifier = Modifier.size(20.dp))
        Text(uiString(when (nextAction) {
          HomeAction.RESUME, HomeAction.CARRYOVER -> UiR.string.ui_continue_learning
          HomeAction.REVIEW -> R.string.home_start_review
          HomeAction.LEARN_MORE -> UiR.string.ui_learn_more
          HomeAction.REVIEW_MORE -> R.string.home_review_more
          null -> R.string.home_stage_complete
          else -> R.string.home_start_learning
        }), Modifier.weight(1f).padding(horizontal = 8.dp), textAlign = TextAlign.Center)
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(20.dp))
      }
    }
  }
  val remainingNew = (today.newPlanned - today.newCompleted).coerceAtLeast(0)
  val remainingCarryover = (today.carryoverPlanned - today.carryoverCompleted).coerceAtLeast(0)
  val remainingReview = (today.reviewPlanned - today.reviewCompleted).coerceAtLeast(0)
  val learnAction = when {
    resume?.kind == StudyKind.NEW || resume?.kind == StudyKind.CARRYOVER -> HomeAction.RESUME
    remainingCarryover > 0 -> HomeAction.CARRYOVER
    remainingNew > 0 -> HomeAction.LEARN
    else -> HomeAction.LEARN_MORE
  }
  val learn = HomeActionItem(uiString(UiR.string.ui_learn_2118cb),
    when {
      learnAction == HomeAction.RESUME -> uiString(R.string.home_pick_up_word)
      remainingCarryover + remainingNew > 0 -> uiString(R.string.home_words_remaining, remainingCarryover + remainingNew)
      today.learnMoreBlocker != null -> uiString(when (today.learnMoreBlocker) {
        StudyKind.REVIEW -> R.string.home_finish_review
        StudyKind.CARRYOVER -> R.string.home_finish_carryover
        else -> R.string.home_finish_current
      })
      today.availableNewWords > 0 -> uiString(R.string.home_add_words, minOf(5, today.availableNewWords))
      else -> uiString(R.string.home_all_learned)
    }, Icons.AutoMirrored.Filled.List, !busy && (learnAction != HomeAction.LEARN_MORE ||
      today.availableNewWords > 0 || today.learnMoreBlocker != null), learnAction)
  val review = HomeActionItem(uiString(R.string.home_review_words),
    when {
      resume?.kind == StudyKind.REVIEW -> uiString(R.string.home_pick_up_word)
      remainingReview > 0 -> uiString(R.string.home_words_remaining, remainingReview)
      today.canReviewMore -> uiString(R.string.home_review_count, minOf(reviewBatchSize, today.availableReviewWords))
      else -> uiString(UiR.string.ui_nothing_due)
    }, Icons.Default.Refresh, !busy && (resume?.kind == StudyKind.REVIEW || remainingReview > 0 || today.canReviewMore),
    when {
      resume?.kind == StudyKind.REVIEW -> HomeAction.RESUME
      today.canReviewMore -> HomeAction.REVIEW_MORE
      else -> HomeAction.REVIEW
    })
  val collections = HomeActionItem(uiString(UiR.string.ui_collections),
    if (today.collectionsAvailable > 0) uiString(R.string.home_word_count, today.collectionsAvailable)
    else uiString(UiR.string.ui_none_saved), Icons.Default.FavoriteBorder,
    !busy && (today.collectionsAvailable > 0 || resume?.kind == StudyKind.COLLECTION),
    if (resume?.kind == StudyKind.COLLECTION) HomeAction.RESUME else HomeAction.COLLECTIONS)
  val mistakes = HomeActionItem(uiString(UiR.string.ui_mistakes_3bd9fb),
    if (today.mistakesAvailable > 0) uiString(R.string.home_word_count, today.mistakesAvailable)
    else uiString(UiR.string.ui_none_to_review), FlashcardIcons.Complete,
    !busy && (today.mistakesAvailable > 0 || resume?.kind == StudyKind.MISTAKES),
    if (resume?.kind == StudyKind.MISTAKES) HomeAction.RESUME else HomeAction.MISTAKES)
  val fontScale = LocalDensity.current.fontScale
  BoxWithConstraints(Modifier.fillMaxWidth()) {
    val actions = listOf(learn, review, collections, mistakes)
    val columns = if (maxWidth >= 300.dp && fontScale <= 1.15f) 2 else 1
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
      // Full-width tiles keep labels readable on narrow screens and with larger text.
      actions.chunked(columns).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          row.forEach { item -> HomeActionTile(item, Modifier.weight(1f)) { open(item.action) } }
        }
      }
    }
  }
}

@Composable
private fun HomeActionTile(item: HomeActionItem, modifier: Modifier, onClick: () -> Unit) {
  Surface(onClick = onClick, enabled = item.enabled, modifier = modifier,
    shape = MaterialTheme.shapes.large, color = glassPanelColor(),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f))) {
    Column(Modifier.heightIn(min = 88.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(item.icon, contentDescription = null, modifier = Modifier.size(18.dp),
          tint = if (item.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(item.title, style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp),
          color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
      Text(item.subtitle, style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
  }
}

@Composable
private fun StagePicker(stage: VocabularyStage, enabled: Boolean, onStage: (VocabularyStage) -> Unit,
  modifier: Modifier = Modifier) {
  var choosingStage by rememberSaveable { mutableStateOf(false) }
  Surface(onClick = { choosingStage = true }, enabled = enabled, modifier = modifier,
    shape = MaterialTheme.shapes.medium, color = glassPanelColor(), contentColor = MaterialTheme.colorScheme.onSurface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f))) {
    Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(stage.localizedLabel(), Modifier.weight(1f), maxLines = 1,
        style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp), overflow = TextOverflow.Ellipsis)
      Icon(Icons.Default.KeyboardArrowDown, contentDescription = uiString(UiR.string.ui_choose_learning_stage),
        modifier = Modifier.size(20.dp))
    }
  }
  if (choosingStage) HomeBookPicker(stage, enabled, onStage, onDismiss = { choosingStage = false })
}

@Composable
private fun HomeBookPicker(stage: VocabularyStage, enabled: Boolean, onStage: (VocabularyStage) -> Unit,
  onDismiss: () -> Unit) {
  FlashcardDialog(onDismissRequest = onDismiss,
    title = { Text(uiString(R.string.home_choose_book)) }, text = {
      HomeBookPickerContent(stage, enabled, onStage = { entry -> onDismiss(); onStage(entry) })
    }, confirmButton = {
      TextButton(onClick = onDismiss) { Text(uiString(UiR.string.ui_back)) }
    })
}

@Composable
private fun HomeBookPickerContent(stage: VocabularyStage, enabled: Boolean, onStage: (VocabularyStage) -> Unit,
  modifier: Modifier = Modifier) {
  Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    VocabularyStage.entries.chunked(2).forEach { terms ->
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(uiString(R.string.home_grade, terms.first().grade), style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          terms.forEachIndexed { index, entry ->
            val selected = entry == stage
            val stageLabel = entry.localizedLabel()
            Surface(onClick = { onStage(entry) }, enabled = enabled,
              modifier = Modifier.weight(1f).semantics { this.selected = selected; contentDescription = stageLabel },
              shape = MaterialTheme.shapes.small,
              color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
              border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant)) {
              Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Text(uiString(if (index == 0) R.string.home_first_term else R.string.home_second_term),
                  style = MaterialTheme.typography.titleSmall)
                if (selected) Icon(Icons.Default.Check, contentDescription = null,
                  modifier = Modifier.padding(start = 6.dp).size(18.dp), tint = MaterialTheme.colorScheme.primary)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun HomeWordShowcase(
  word: WordlistItem?, wordError: Boolean, wordStage: VocabularyStage?, hasToday: Boolean,
  loading: Boolean, busy: Boolean, stage: VocabularyStage, onReplay: () -> Unit,
  replayEnabled: Boolean, onShuffle: () -> Unit, replayPreparing: Boolean = false,
  shuffleWaiting: Boolean = false, shuffleFailed: Boolean = false,
) {
  Surface(Modifier.fillMaxWidth().glassPanel(), shape = MaterialTheme.shapes.large,
    color = glassPanelColor()) {
    Column(Modifier.padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(uiString(UiR.string.ui_word_discovery), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onShuffle, enabled = !loading && !busy && !shuffleWaiting && (word != null || wordError),
          shape = MaterialTheme.shapes.small) {
          if (shuffleWaiting) SpeechPreparingIndicator() else Icon(Icons.Default.Refresh, contentDescription = null,
            modifier = Modifier.size(17.dp))
          Text(uiString(UiR.string.ui_shuffle), Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelLarge)
        }
      }
      when {
        loading -> LoadingNotice()
        word != null && word.stage.code == stage.code -> {
          Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Text(word.headword, style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = if (word.headword.length > 18) 22.sp else 28.sp),
                color = MaterialTheme.colorScheme.onSurface)
              if (word.ipa.isNotBlank()) Text(word.ipa, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
              Text(listOf(word.partOfSpeech.localizedPartOfSpeech(), word.chineseMeaning)
                .filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
              IconButton(onClick = onReplay, enabled = replayEnabled && !shuffleWaiting && !replayPreparing,
                modifier = Modifier.size(48.dp)) {
                if (replayPreparing) SpeechPreparingIndicator() else Icon(FlashcardIcons.Speaker,
                  contentDescription = uiString(UiR.string.ui_replay), tint = MaterialTheme.colorScheme.primary)
              }
            }
          }
        }
        wordError -> Text(uiString(UiR.string.ui_couldn_t_load_a_word_tap_shuffle_to_retry), style = MaterialTheme.typography.bodyMedium)
        hasToday && wordStage != stage -> LoadingNotice()
        hasToday -> Text(uiString(UiR.string.ui_no_words_in_this_stage_yet),
          color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
      }
      if (shuffleFailed) Text(uiString(UiR.string.ui_speech_could_not_be_played_you_can_continue_studying),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
  }
}

private fun StudyUiState.homeReplayRequest(sourceId: String): SpeechRequest? {
  val stage = today?.stageProgress?.stage ?: return null
  val word = homeWord?.takeIf { it.stage.code == stage.code } ?: return null
  if (loading || busy || homeShuffleWaiting || homeWordStage != stage) return null
  val request = word.homeSpeechRequest(AppLanguage.EN, sourceId)
  return request.takeIf { settings.speechOptions.requests(it).isNotEmpty() }
}

@Preview(name = "Home · today", widthDp = 360, heightDp = 760)
@Preview(name = "Home · short screen", widthDp = 360, heightDp = 640)
@Preview(name = "Home · compact", widthDp = 320, heightDp = 640, fontScale = 1.3f)
@Composable
private fun HomePreview() { HomePreviewContent(PreviewToday) }

@Preview(name = "Home · goal complete", widthDp = 360, heightDp = 760)
@Composable
private fun HomeCompletePreview() {
  HomePreviewContent(PreviewToday.copy(newCompleted = PreviewToday.newPlanned,
    reviewCompleted = PreviewToday.reviewPlanned, carryoverCompleted = PreviewToday.carryoverPlanned))
}

@Preview(name = "Home · first word", widthDp = 360, heightDp = 760)
@Composable
private fun HomeFirstWordPreview() {
  HomePreviewContent(PreviewToday.copy(stageProgress = PreviewToday.stageProgress.copy(learned = 0)))
}

@Preview(name = "Home · term complete", widthDp = 360, heightDp = 760)
@Composable
private fun HomeTermCompletePreview() {
  HomePreviewContent(PreviewToday.copy(stageProgress = PreviewToday.stageProgress.copy(
    learned = PreviewToday.stageProgress.total)))
}

@Composable
private fun HomePreviewContent(today: TodaySummary, darkTheme: Boolean = false) {
  FlashcardTheme(stage = today.stageProgress.stage, darkTheme = darkTheme) {
    Box(Modifier.fillMaxSize().flashcardBackground()) {
    Scaffold(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground, bottomBar = {
      NavigationBar(Modifier.height(72.dp), containerColor = glassPanelColor()) {
        listOf(UiR.string.ui_home to Icons.Default.Home, UiR.string.ui_wordlist to Icons.AutoMirrored.Filled.List,
          UiR.string.ui_profile to Icons.Default.Person).forEachIndexed { index, (label, icon) ->
          NavigationBarItem(selected = index == 0, onClick = {}, icon = { Icon(icon, null) },
            label = { Text(uiString(label), style = MaterialTheme.typography.labelMedium) },
            colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer,
              selectedIconColor = MaterialTheme.colorScheme.primary,
              selectedTextColor = MaterialTheme.colorScheme.primary,
              unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
              unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant))
        }
      }
    }) { padding ->
      Box(Modifier.fillMaxSize().padding(padding).flashcardBackground()) {
      val word = WordlistItem(PreviewWord.id, PreviewWord.headword, PreviewWord.ipa,
        PreviewWord.chineseMeaning, PreviewWord.chinesePinyin, today.stageProgress.stage,
        WordlistStatus.UNLEARNED, 0, 4, partOfSpeech = PreviewWord.partOfSpeech)
      TodayContent(StudyUiState(loading = false, snapshot = StudySnapshot(today, null),
        settings = com.example.flashcardforteens.core.domain.StudySettings(displayName = "乐乐"),
        homeWord = word, homeWordStage = today.stageProgress.stage),
        onStart = {}, onResume = {}, onLearnMore = {}, onLearnMoreBlocked = {},
        onStage = {}, onRetry = {}, onReplay = {}, onShuffle = {}, onProfile = {}, onWordlist = {})
      }
    }
    }
  }
}

@Preview(name = "Home · grade 4", widthDp = 360, heightDp = 760)
@Composable
private fun HomeGradeFourPreview() = HomePreviewContent(PreviewToday.copy(
  stageProgress = PreviewToday.stageProgress.copy(stage = VocabularyStage.GRADE_4_FIRST)))

@Preview(name = "Home · grade 5", widthDp = 360, heightDp = 760)
@Composable
private fun HomeGradeFivePreview() = HomePreviewContent(PreviewToday.copy(
  stageProgress = PreviewToday.stageProgress.copy(stage = VocabularyStage.GRADE_5_FIRST)))

@Preview(name = "Home · grade 6 dark", widthDp = 360, heightDp = 760)
@Composable
private fun HomeGradeSixDarkPreview() = HomePreviewContent(PreviewToday.copy(
  stageProgress = PreviewToday.stageProgress.copy(stage = VocabularyStage.GRADE_6_FIRST)), darkTheme = true)

@Preview(name = "Home · choose book compact", widthDp = 320, heightDp = 568, fontScale = 1.3f)
@Composable
private fun HomeBookPickerPreview() {
  FlashcardTheme {
    Box(Modifier.fillMaxSize().flashcardBackground().padding(20.dp), contentAlignment = Alignment.Center) {
      // Render the real dialog content inline: standalone layoutlib does not capture popup windows.
      Surface(Modifier.widthIn(max = 560.dp).fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceBright) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
          Text(uiString(R.string.home_choose_book), style = MaterialTheme.typography.titleLarge)
          HomeBookPickerContent(VocabularyStage.GRADE_3_FIRST, enabled = true, onStage = {},
            modifier = Modifier.weight(1f, fill = false))
          TextButton(onClick = {}, modifier = Modifier.align(Alignment.End)) { Text(uiString(UiR.string.ui_back)) }
        }
      }
    }
  }
}
