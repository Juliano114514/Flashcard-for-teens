package com.example.flashcardforteens.feature.study

import com.example.flashcardforteens.core.ui.uiString
import com.example.flashcardforteens.core.ui.R as UiR

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.example.flashcardforteens.core.domain.CardPhase
import com.example.flashcardforteens.core.domain.StudyCard
import com.example.flashcardforteens.core.domain.StudyKind
import com.example.flashcardforteens.core.domain.StudySnapshot
import com.example.flashcardforteens.core.domain.TodaySummary
import com.example.flashcardforteens.core.domain.PracticeProgress
import com.example.flashcardforteens.core.domain.VocabularyStage
import com.example.flashcardforteens.core.domain.AppLanguage
import com.example.flashcardforteens.core.domain.SpeechRequest
import com.example.flashcardforteens.core.ui.AppLanguageProvider
import com.example.flashcardforteens.core.ui.flashcardBackground
import com.example.flashcardforteens.core.ui.FlashcardStyle
import com.example.flashcardforteens.core.ui.FlashcardToolbar
import com.example.flashcardforteens.core.ui.FlashcardTheme
import com.example.flashcardforteens.core.ui.StageThemePreviewCase
import com.example.flashcardforteens.core.ui.WordMeanings
import com.example.flashcardforteens.core.ui.WordExplanation
import com.example.flashcardforteens.core.ui.WordExamplePanel
import com.example.flashcardforteens.core.ui.LearningPrimaryButton
import com.example.flashcardforteens.core.ui.LearningSecondaryButton
import com.example.flashcardforteens.core.ui.LearningIllustration
import com.example.flashcardforteens.core.ui.WordUserActions
import com.example.flashcardforteens.core.ui.FlashcardIcons
import com.example.flashcardforteens.core.ui.glassPanel
import com.example.flashcardforteens.core.ui.glassPanelColor

@Composable
internal fun StudyContent(
  state: StudyUiState,
  selectedOption: String?,
  answerChosen: Boolean,
  correctAnswerRevealed: Boolean,
  feedbackFinished: Boolean,
  feedbackPlaybackSucceeded: Boolean,
  feedbackCueFinished: Boolean,
  onBack: () -> Unit,
  onSpeak: (SpeechRequest) -> Unit,
  onSelect: (String?) -> Unit,
  onSubmit: (String, String?) -> Unit,
  onExplain: (String) -> Unit,
  onAdvance: () -> Unit,
  onCollection: (String, Boolean) -> Unit,
  onSkip: (String, Boolean) -> Unit,
  onRetry: () -> Unit,
  voiceLoading: Boolean = false,
  onReviewMore: () -> Unit = {},
) {
  val card = state.card
  val practice = state.snapshot?.practice?.takeIf { it.kind == card?.kind }
  val today = state.today
  val (completed, planned) = if (practice != null) practice.completed to practice.planned else when (card?.kind) {
    StudyKind.NEW -> (today?.dailyNewCompleted ?: 0) to (today?.dailyNewPlanned ?: 0)
    StudyKind.REVIEW -> (today?.dueReviewCompleted ?: 0) to (today?.dueReviewPlanned ?: 0)
    StudyKind.CARRYOVER -> (today?.carryoverCompleted ?: 0) to (today?.carryoverPlanned ?: 0)
    else -> 0 to 0
  }
  val progressLabel = when (card?.kind) {
    StudyKind.NEW -> uiString(R.string.progress_daily_learning)
    StudyKind.REVIEW -> uiString(R.string.progress_due_review)
    StudyKind.CARRYOVER -> uiString(R.string.progress_carryover)
    StudyKind.COLLECTION -> uiString(UiR.string.ui_collections)
    else -> uiString(UiR.string.ui_mistakes_3bd9fb)
  }
  val (extraCompleted, extraPlanned) = when (card?.kind) {
    StudyKind.NEW -> (today?.extraNewCompleted ?: 0) to (today?.extraNewPlanned ?: 0)
    StudyKind.REVIEW -> (today?.extraReviewCompleted ?: 0) to (today?.extraReviewPlanned ?: 0)
    else -> 0 to 0
  }
  Box(Modifier.fillMaxSize().flashcardBackground()) {
  Scaffold(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground,
    contentWindowInsets = if (card != null && card.phase != CardPhase.FINISHED) WindowInsets(0, 0, 0, 0)
      else WindowInsets.navigationBars.only(WindowInsetsSides.Bottom), topBar = {
    Column {
    FlashcardToolbar(when (card?.kind) {
        StudyKind.NEW -> uiString(UiR.string.ui_learn_2118cb)
        StudyKind.REVIEW -> uiString(UiR.string.ui_review)
        StudyKind.CARRYOVER -> uiString(UiR.string.ui_continue)
        StudyKind.COLLECTION -> uiString(UiR.string.ui_collections)
        StudyKind.MISTAKES -> uiString(UiR.string.ui_mistakes_3bd9fb)
        null -> uiString(UiR.string.ui_study)
      }, onBack, Modifier.padding(horizontal = 12.dp, vertical = 3.dp), enabled = !state.busy, trailing = {
      if (card != null && card.phase != CardPhase.FINISHED) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          WordUserActions(isCollected = card.isCollected, isSkipped = card.isSkipped,
            enabled = !state.busy && card.phase != CardPhase.FEEDBACK,
            onCollection = { onCollection(card.word.id, it) }, onSkip = { onSkip(card.word.id, it) },
            wordId = card.word.id,
            wordLabel = card.word.headword,
            compact = true)
        }
      }
    }, subtitle = {
      if (voiceLoading && card?.phase in listOf(CardPhase.INTRO, CardPhase.QUESTION, CardPhase.FEEDBACK, CardPhase.EXPLANATION)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.dp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
          Text(uiString(UiR.string.ui_loading_voice), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false))
        }
      }
    })
    if (card != null && card.phase != CardPhase.FINISHED && state.snapshot != null) {
      Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.CenterVertically) {
          if (!card.reviewRecall && card.phase in listOf(CardPhase.INTRO, CardPhase.QUESTION, CardPhase.FEEDBACK)) {
            RoundProgress(card)
          }
          LabeledDailyProgress(progressLabel, completed, planned, Modifier.weight(1f))
        }
        if (extraPlanned > 0) LabeledDailyProgress(uiString(if (card.kind == StudyKind.NEW)
          R.string.progress_extra_learning else R.string.progress_extra_review), extraCompleted, extraPlanned)
      }
    }
    }
  }, bottomBar = {
    if (card != null && card.phase != CardPhase.FINISHED) {
      val barShape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
      Surface(color = glassPanelColor(), tonalElevation = 0.dp, shadowElevation = 0.dp,
        modifier = Modifier.glassPanel(barShape), shape = barShape) {
      Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        when (card.phase) {
          CardPhase.QUESTION -> {
            if (!card.canAnswer) {
              Text(uiString(UiR.string.ui_this_word_has_too_few_distinct_choices_you_can_skip_it_for_now),
                style = MaterialTheme.typography.bodySmall)
              LearningSecondaryButton(onClick = { onSkip(card.word.id, true) }, enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(uiString(UiR.string.ui_skip_word)) }
            } else {
              LearningPrimaryButton(onClick = { onSubmit(card.id, selectedOption) },
                enabled = answerChosen && !state.busy && !state.loading,
                modifier = Modifier.fillMaxWidth()) { Text(uiString(UiR.string.ui_check_answer)) }
            }
          }
          CardPhase.FEEDBACK -> {
            AnswerFeedback(card.correct == true,
              correctMeaning = card.options.firstOrNull { it.id == card.correctWordId }
                ?.chinese?.takeIf { correctAnswerRevealed })
            if (card.correct == true && feedbackFinished && !feedbackPlaybackSucceeded) {
              Text(uiString(UiR.string.ui_audio_unavailable_tap_next), style = MaterialTheme.typography.bodySmall,
                color = FlashcardStyle.colors.gradientSecondaryInk)
            }
            LearningPrimaryButton(onClick = { onExplain(card.id) }, enabled = !state.busy && feedbackCueFinished &&
              (feedbackFinished || (card.correct == true && !state.settings.waitForCorrectAnswerAudio)),
              modifier = Modifier.fillMaxWidth()) { Text(uiString(UiR.string.ui_next)) }
          }
          CardPhase.INTRO, CardPhase.EXPLANATION -> {
            LearningPrimaryButton(onClick = onAdvance, enabled = !state.busy,
              modifier = Modifier.fillMaxWidth()) {
              Text(if (card.phase == CardPhase.INTRO && card.canAnswer) uiString(UiR.string.ui_continue)
                else uiString(UiR.string.ui_next_word))
            }
          }
          else -> Unit
        }
      }
      }
    }
  }) { padding ->
    PageColumn(scrollKey = "${card?.id}/${card?.phase}", modifier = Modifier.padding(padding), studyPage = true) {
      when {
        state.loading || (state.busy && card == null) -> LoadingNotice(studyPage = true)
        card == null -> {
          Text(uiString(UiR.string.ui_no_active_session), style = MaterialTheme.typography.headlineMedium)
          Text(uiString(UiR.string.ui_choose_learn_or_review_from_home), color = FlashcardStyle.colors.gradientSecondaryInk)
          LearningPrimaryButton(onClick = onBack, enabled = !state.busy) { Text(uiString(UiR.string.ui_back_to_home)) }
        }
        card.phase == CardPhase.FINISHED -> {
          val unfinishedDaily = state.today?.let { today -> when (card.kind) {
            StudyKind.NEW -> today.newCompleted < today.newPlanned
            StudyKind.REVIEW -> today.reviewCompleted < today.reviewPlanned
            StudyKind.CARRYOVER -> today.carryoverCompleted < today.carryoverPlanned
            else -> false
          } } == true
          val paused = practice?.paused == true || (practice == null && (card.isSkipped || unfinishedDaily))
          if (card.canAnswer && !paused) {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = FlashcardStyle.colors.successContainer,
              modifier = Modifier.fillMaxWidth()) {
              Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                LearningIllustration(Modifier.size(72.dp))
                Text(uiString(UiR.string.ui_completion_title), style = MaterialTheme.typography.headlineLarge)
                Text(uiString(UiR.string.ui_completion_hint), style = MaterialTheme.typography.bodyLarge,
                  textAlign = androidx.compose.ui.text.style.TextAlign.Center)
              }
            }
          }
          if (!card.canAnswer || paused) Text(if (!card.canAnswer) uiString(UiR.string.ui_viewing_complete)
            else uiString(UiR.string.ui_session_paused), style = MaterialTheme.typography.headlineLarge)
          if (!card.canAnswer) Text(uiString(UiR.string.ui_no_reliable_question_is_available_for_this_word_no_learning_progress_was_recorded),
            color = FlashcardStyle.colors.gradientSecondaryInk)
          if (paused && card.canAnswer) Text(uiString(UiR.string.ui_open_this_session_from_home_to_continue_skipped_words_stay_paused_until_you_unskip_them),
            color = FlashcardStyle.colors.gradientSecondaryInk)
          if (practice != null) {
            CompletionCount(if (practice.kind == StudyKind.COLLECTION) uiString(UiR.string.ui_collections) else uiString(UiR.string.ui_mistakes_3bd9fb), completed, planned)
          } else state.today?.let { today ->
            CompletionCount(uiString(R.string.progress_daily_learning), today.dailyNewCompleted, today.dailyNewPlanned)
            CompletionCount(uiString(R.string.progress_due_review), today.dueReviewCompleted, today.dueReviewPlanned)
            if (today.extraNewPlanned > 0) CompletionCount(uiString(R.string.progress_extra_learning),
              today.extraNewCompleted, today.extraNewPlanned)
            if (today.extraReviewPlanned > 0) CompletionCount(uiString(R.string.progress_extra_review),
              today.extraReviewCompleted, today.extraReviewPlanned)
            if (today.carryoverPlanned > 0) CompletionCount(uiString(R.string.progress_carryover),
              today.carryoverCompleted, today.carryoverPlanned)
            today.nextReviewDate?.let { date ->
              Text(uiString(UiR.string.ui_next_review_1_s, date), style = MaterialTheme.typography.titleMedium,
                color = FlashcardStyle.colors.gradientAction)
            }
          }
          if (card.kind == StudyKind.REVIEW && card.canAnswer && !paused && state.today?.canReviewMore == true) {
            LearningSecondaryButton(onClick = onReviewMore, enabled = !state.busy,
              modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(uiString(UiR.string.ui_continue_review)) }
          }
          LearningPrimaryButton(onClick = onBack, enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(uiString(UiR.string.ui_done)) }
        }
        else -> {
          QuestionHeading(card, enabled = !state.busy && card.phase != CardPhase.FEEDBACK, onSpeak = onSpeak)
          when (card.phase) {
            CardPhase.INTRO, CardPhase.EXPLANATION -> {
              Surface(shape = MaterialTheme.shapes.large, color = glassPanelColor(),
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth().glassPanel(shape = MaterialTheme.shapes.large)) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                  WordMeanings(card.word, onSpeak, enabled = !state.busy)
                }
              }
              if (!card.canAnswer) Text(uiString(UiR.string.ui_no_reliable_question_is_available_for_this_word_viewing_it_does_not_count_as_learned),
                style = MaterialTheme.typography.bodySmall, color = FlashcardStyle.colors.gradientSecondaryInk)
              WordExplanation(card.word, onSpeak, compact = false, enabled = !state.busy, progressive = true)
            }
            CardPhase.QUESTION, CardPhase.FEEDBACK -> {
              val feedback = card.phase == CardPhase.FEEDBACK
              if (feedback) AnswerChoices(card, selectedOption, answerChosen, correctAnswerRevealed,
                enabled = false, onSelect = onSelect)
              val visibleExample = card.contextExample() ?: card.word.examples.firstOrNull()
                ?.takeIf { feedback && correctAnswerRevealed }
              visibleExample?.let { example ->
                if (feedback && correctAnswerRevealed) {
                  WordExamplePanel(example, onSpeak, enabled = !state.busy && feedbackFinished, compact = true)
                } else QuestionExample(example, onSpeak, enabled = !feedback && !state.busy)
              }
              if (!feedback) {
                Text(uiString(UiR.string.ui_question_instruction), style = MaterialTheme.typography.labelLarge,
                  color = FlashcardStyle.colors.gradientSecondaryInk)
                AnswerChoices(card, selectedOption, answerChosen, correctAnswerRevealed,
                  enabled = !state.busy && !state.loading, onSelect = onSelect)
              }
            }
            CardPhase.FINISHED -> Unit
          }
        }
      }
      state.error?.let { ErrorNotice(it, !state.busy, onRetry, studyPage = true) }
    }
  }
  }
}

@Composable
private fun AnswerFeedback(correct: Boolean, correctMeaning: String? = null) {
  Surface(shape = MaterialTheme.shapes.medium,
    modifier = Modifier.fillMaxWidth(),
    color = if (correct) FlashcardStyle.colors.successContainer else MaterialTheme.colorScheme.errorContainer,
    contentColor = if (correct) FlashcardStyle.colors.onSuccessContainer else MaterialTheme.colorScheme.onErrorContainer) {
  Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Icon(if (correct) FlashcardIcons.Check else FlashcardIcons.Close, contentDescription = null,
        modifier = Modifier.size(24.dp))
      Text(uiString(if (correct) UiR.string.ui_feedback_success
        else if (correctMeaning != null) UiR.string.ui_feedback_retry else UiR.string.ui_incorrect_round_resets_to_1),
        modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
    if (!correct && correctMeaning != null) {
      Text(correctMeaning, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
      Text(uiString(UiR.string.ui_incorrect_round_resets_to_1), style = MaterialTheme.typography.bodySmall)
    }
  }
  }
}

@Composable
private fun CompletionCount(label: String, completed: Int, planned: Int) {
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
    Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
      color = FlashcardStyle.colors.gradientSecondaryInk)
    Text(uiString(UiR.string.ui_fraction, completed, planned), style = MaterialTheme.typography.titleMedium)
  }
}

@Composable
private fun StudyPagePreview(theme: StageThemePreviewCase, card: StudyCard, selectedOption: String? = null,
  revealCorrect: Boolean = false, feedbackFinished: Boolean = false, answerChosen: Boolean = selectedOption != null,
  today: TodaySummary = PreviewToday) {
  FlashcardTheme(darkTheme = theme.darkTheme, stage = theme.stage) {
    val practice = if (card.kind in listOf(StudyKind.COLLECTION, StudyKind.MISTAKES)) PracticeProgress(card.kind, 2, 5) else null
    StudyContent(StudyUiState(loading = false, snapshot = StudySnapshot(today, card, practice)),
      selectedOption = selectedOption, answerChosen = answerChosen,
      correctAnswerRevealed = revealCorrect, feedbackFinished = feedbackFinished,
      feedbackPlaybackSucceeded = feedbackFinished, feedbackCueFinished = feedbackFinished,
      onBack = {}, onSpeak = {}, onSelect = {},
      onSubmit = { _, _ -> }, onExplain = {}, onAdvance = {},
      onCollection = { _, _ -> }, onSkip = { _, _ -> }, onRetry = {})
  }
}

@Preview(name = "Question", widthDp = 360, heightDp = 640)
@Composable
private fun QuestionPreview() {
  StudyPagePreview(StageThemePreviewCase(VocabularyStage.GRADE_3_FIRST, false), PreviewCard)
}

@Preview(name = "Question · compact 320", widthDp = 320, heightDp = 568)
@Composable
private fun CompactQuestionPreview() {
  StudyPagePreview(StageThemePreviewCase(VocabularyStage.GRADE_3_FIRST, false), PreviewCard)
}

@Preview(name = "Question · large text", widthDp = 320, heightDp = 640, fontScale = 1.3f)
@Composable
private fun LargeTextQuestionPreview() {
  StudyPagePreview(StageThemePreviewCase(VocabularyStage.GRADE_3_FIRST, false),
    PreviewCard.copy(word = PreviewWord.copy(headword = "Thank you very much!", ipa = "/θæŋk juː ˈveri mʌtʃ/",
      chineseMeaning = "非常感谢你的帮助。"),
      options = PreviewCard.options.mapIndexed { index, option ->
        if (index == 0) option.copy(chinese = "非常感谢你的帮助。") else option
      }))
}

@Preview(name = "Question · I am still learning", widthDp = 360, heightDp = 640)
@Composable
private fun UnknownAnswerPreview() {
  StudyPagePreview(StageThemePreviewCase(VocabularyStage.GRADE_3_FIRST, false), PreviewCard,
    answerChosen = true)
}

@Preview(name = "New word", widthDp = 360, heightDp = 640)
@Composable
private fun IntroPreview() {
  StudyPagePreview(StageThemePreviewCase(VocabularyStage.GRADE_3_FIRST, false),
    PreviewCard.copy(phase = CardPhase.INTRO))
}

@Preview(name = "Correct answer · dark", widthDp = 360, heightDp = 640)
@Composable
private fun CorrectAnswerPreview() {
  StudyPagePreview(StageThemePreviewCase(VocabularyStage.GRADE_3_FIRST, true),
    PreviewCard.copy(phase = CardPhase.FEEDBACK, selectedOptionId = "thank-you", correct = true),
    selectedOption = "thank-you", revealCorrect = true, feedbackFinished = true)
}

@Preview(name = "Complete", widthDp = 360, heightDp = 640)
@Composable
private fun CompletionPreview() {
  StudyPagePreview(StageThemePreviewCase(VocabularyStage.GRADE_3_FIRST, false),
    PreviewCard.copy(phase = CardPhase.FINISHED),
    today = PreviewToday.copy(newCompleted = PreviewToday.newPlanned,
      reviewCompleted = PreviewToday.reviewPlanned, carryoverCompleted = PreviewToday.carryoverPlanned,
      dailyNewCompleted = PreviewToday.dailyNewPlanned, dueReviewCompleted = PreviewToday.dueReviewPlanned))
}

@Preview(name = "Learning · daily plan 10", widthDp = 360, heightDp = 640)
@Composable
private fun LearningSplitProgressPreview() {
  StudyPagePreview(StageThemePreviewCase(VocabularyStage.GRADE_3_FIRST, false), PreviewCard, today = PreviewDueToday)
}

@Preview(name = "Review · due words 44", widthDp = 360, heightDp = 640)
@Composable
private fun ReviewSplitProgressPreview() {
  StudyPagePreview(StageThemePreviewCase(VocabularyStage.GRADE_3_FIRST, false),
    PreviewCard.copy(kind = StudyKind.REVIEW, reviewRecall = true, round = 1, targetRounds = 1), today = PreviewDueToday)
}

@Preview(name = "Review · optional batch", widthDp = 320, heightDp = 640, fontScale = 1.3f)
@Composable
private fun ReviewExtraProgressPreview() {
  StudyPagePreview(StageThemePreviewCase(VocabularyStage.GRADE_3_FIRST, false),
    PreviewCard.copy(kind = StudyKind.REVIEW, reviewRecall = true, round = 1, targetRounds = 1), today = PreviewExtraToday)
}

@Preview(name = "Wrong answer revealed", widthDp = 360, heightDp = 640)
@Composable
private fun WrongAnswerPreview() {
  StudyPagePreview(StageThemePreviewCase(VocabularyStage.GRADE_3_FIRST, false),
    PreviewCard.copy(phase = CardPhase.FEEDBACK, selectedOptionId = "morning", correct = false),
    selectedOption = "morning", revealCorrect = true, feedbackFinished = true)
}

@Preview(name = "English question · Chinese interface", widthDp = 360, heightDp = 640)
@Composable
private fun EnglishQuestionPreview() {
  AppLanguageProvider(AppLanguage.CN) {
    StudyPagePreview(StageThemePreviewCase(VocabularyStage.GRADE_3_FIRST, false),
      PreviewCard)
  }
}

@Preview(name = "Explanation", widthDp = 360, heightDp = 640)
@Composable
private fun ExplanationPreview() {
  StudyPagePreview(StageThemePreviewCase(VocabularyStage.GRADE_3_FIRST, false),
    PreviewCard.copy(phase = CardPhase.EXPLANATION, correct = true,
      selectedOptionId = "thank-you", isCollected = true))
}
