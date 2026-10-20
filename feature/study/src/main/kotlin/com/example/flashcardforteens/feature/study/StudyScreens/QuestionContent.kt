package com.example.flashcardforteens.feature.study

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flashcardforteens.core.domain.*
import com.example.flashcardforteens.core.ui.*
import com.example.flashcardforteens.core.ui.R as UiR

/** The question exposes English and CSV IPA only, never its Chinese counterpart. */
@Composable
internal fun QuestionHeading(card: StudyCard, enabled: Boolean, onSpeak: (SpeechRequest) -> Unit) {
  val sourceId = rememberSpeechSourceId(Triple(card.id, card.phase, card.contextVersion))
  StudyWordHeading(card.word, enabled, onSpeak = { onSpeak(card.promptSpeech().copy(sourceId = sourceId)) },
    sourceId = sourceId)
}

@Composable
internal fun QuestionExample(example: ExampleSentence, onSpeak: (SpeechRequest) -> Unit, enabled: Boolean) {
  val sourceId = rememberSpeechSourceId(example.english to "question")
  Surface(shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
    color = MaterialTheme.colorScheme.surfaceContainerLow) {
    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
      verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(example.english, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 24.sp))
      IconButton(onClick = { onSpeak(SpeechRequest(example.english, AppLanguage.EN,
        scene = SpeechScene.STUDY_CONTEXT, sourceId = sourceId)) }, enabled = enabled && !isSpeechPreparing(sourceId)) {
        SpeechPlayIcon(sourceId, contentDescription = uiString(UiR.string.ui_listen_to_english_example))
      }
    }
  }
}

/** Each answer keeps a full-width row at every screen size and text scale. */
@Composable
internal fun AnswerChoices(card: StudyCard, selectedOption: String?, answerChosen: Boolean,
  correctAnswerRevealed: Boolean, enabled: Boolean, onSelect: (String?) -> Unit) {
  val feedback = card.phase == CardPhase.FEEDBACK
  val choices = if (feedback) card.options.filter { option ->
    option.id == card.selectedOptionId || (correctAnswerRevealed && option.id == card.correctWordId)
  } else card.options
  Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    choices.forEach { option ->
      val index = card.options.indexOf(option)
      val selected = if (feedback) card.selectedOptionId == option.id else answerChosen && selectedOption == option.id
      val correct = card.correctWordId == option.id
      val revealed = feedback && (selected || (card.correct == false && correct && correctAnswerRevealed))
      LanguageOption(index, option, selected, enabled = enabled && !feedback && card.canAnswer,
        onClick = { onSelect(option.id) },
        result = when {
          feedback && selected && !correct -> false
          feedback && correct && revealed -> true
          else -> null
        }, dimmed = feedback && !revealed)
    }
    if (card.canAnswer && !feedback) {
        val unknownSelected = answerChosen && selectedOption == null
        Surface(onClick = { onSelect(null) }, enabled = enabled,
          modifier = Modifier.fillMaxWidth().semantics { selected = unknownSelected; role = Role.RadioButton },
          shape = MaterialTheme.shapes.medium,
          color = if (unknownSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
          border = BorderStroke(if (unknownSelected) 2.dp else 1.dp,
            if (unknownSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
          Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (unknownSelected) Icon(FlashcardIcons.Check, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(uiString(UiR.string.ui_i_am_still_learning), style = MaterialTheme.typography.labelLarge,
              color = if (unknownSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
    }
  }
}

@Composable
internal fun LanguageOption(index: Int, option: AnswerOption, selected: Boolean,
  enabled: Boolean, onClick: () -> Unit, result: Boolean? = null,
  dimmed: Boolean = false, modifier: Modifier = Modifier) {
  val colors = FlashcardStyle.colors
  val contentColor = when (result) {
    true -> colors.onSuccessContainer
    false -> MaterialTheme.colorScheme.onErrorContainer
    null -> if (selected) MaterialTheme.colorScheme.onPrimaryContainer
      else if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
  }
  val resultDescription = result?.let {
    uiString(if (it) UiR.string.ui_correct_answer else UiR.string.ui_incorrect_round_resets_to_1)
  }
  Surface(modifier = modifier.fillMaxWidth()
    .semantics { this.selected = selected; role = Role.RadioButton
      resultDescription?.let { stateDescription = it }
    }, shape = MaterialTheme.shapes.medium, color = when (result) {
      true -> colors.successContainer
      false -> MaterialTheme.colorScheme.errorContainer
      null -> if (selected) MaterialTheme.colorScheme.primaryContainer
        else if (dimmed) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface
    }, contentColor = contentColor, border = BorderStroke(if (selected || result != null) 2.dp else 1.dp, when (result) {
      true -> colors.success
      false -> MaterialTheme.colorScheme.error
      null -> if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    }), onClick = onClick, enabled = enabled) {
    Row(Modifier.heightIn(min = 56.dp).padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically) {
      Surface(shape = MaterialTheme.shapes.extraSmall,
        color = if (selected || result != null) contentColor.copy(alpha = .10f) else MaterialTheme.colorScheme.surfaceContainerHigh) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
          if (result != null || selected) Icon(if (result == false) FlashcardIcons.Close else FlashcardIcons.Check,
            contentDescription = null, modifier = Modifier.size(20.dp))
          else Text(('A' + index).toString(), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
      Text(option.chinese, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium)
    }
  }
}
