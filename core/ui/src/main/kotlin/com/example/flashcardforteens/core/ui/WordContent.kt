package com.example.flashcardforteens.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flashcardforteens.core.domain.*
import com.example.flashcardforteens.core.ui.R as UiR

/** English headword and the IPA supplied by the vocabulary record. */
@Composable
fun WordHeading(word: WordEntry, enabled: Boolean, onSpeak: () -> Unit, onLongClick: (() -> Unit)? = null,
  trailing: (@Composable () -> Unit)? = null, trailingOnNewLine: Boolean = false, trailingSpacing: Dp = 4.dp,
  playBesideWord: Boolean = false, sourceId: String? = null) {
  val speakEnabled = enabled && !isSpeechPreparing(sourceId)
  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(trailingSpacing)) {
      BoxWithConstraints(Modifier.weight(1f).combinedClickable(enabled = speakEnabled, onClick = onSpeak,
        onLongClick = onLongClick, onLongClickLabel = if (onLongClick != null) uiString(UiR.string.ui_word_actions) else null)) {
        val measurer = rememberTextMeasurer()
        val baseStyle = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.SemiBold, hyphens = Hyphens.None)
        val fontSize = listOf(36, 32, 28, 24).firstOrNull { size ->
          !measurer.measure(AnnotatedString(word.headword),
            style = baseStyle.copy(fontSize = size.sp), softWrap = false, maxLines = 1,
            constraints = Constraints(maxWidth = constraints.maxWidth)).didOverflowWidth
        } ?: 24
        Text(word.headword, style = baseStyle.copy(fontSize = fontSize.sp, lineHeight = (fontSize + 8).sp))
      }
      if (playBesideWord) WordPlayButton(speakEnabled, onSpeak, sourceId)
      if (!trailingOnNewLine) trailing?.invoke()
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(word.ipa, modifier = Modifier.weight(1f), fontSize = 18.sp, lineHeight = 28.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
      if (!playBesideWord) WordPlayButton(speakEnabled, onSpeak, sourceId)
    }
    if (trailingOnNewLine) trailing?.invoke()
  }
}

@Composable
private fun WordPlayButton(enabled: Boolean, onSpeak: () -> Unit, sourceId: String?) {
  IconButton(onClick = onSpeak, enabled = enabled,
    modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape)) {
    SpeechPlayIcon(sourceId, contentDescription = uiString(UiR.string.ui_listen_to_word), modifier = Modifier.size(20.dp))
  }
}

@Composable
fun WordMeanings(word: WordEntry, onSpeak: (SpeechRequest) -> Unit = {}, enabled: Boolean = true) {
  val sourceId = rememberSpeechSourceId(word.id + "/meaning")
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      if (word.partOfSpeech.isNotBlank()) Text(word.partOfSpeech.localizedPartOfSpeech(),
        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
      Text(word.chineseMeaning, style = MaterialTheme.typography.titleLarge)
    }
    IconButton(onClick = { onSpeak(SpeechRequest(word.chineseMeaning, AppLanguage.CN,
      scene = SpeechScene.MEANING_PRONUNCIATION, counterpart = word.headword,
      chinesePronunciation = ChinesePronunciation(word.chineseMeaning, word.chinesePinyin), sourceId = sourceId)) },
      enabled = enabled && !isSpeechPreparing(sourceId)) {
      SpeechPlayIcon(sourceId, contentDescription = uiString(UiR.string.ui_listen_to_chinese_meaning))
    }
  }
}

@Composable
fun WordExplanation(word: WordEntry, onSpeak: (SpeechRequest) -> Unit, compact: Boolean = false,
  enabled: Boolean = true, progressive: Boolean = false) {
  var selected by rememberSaveable(word.id) { mutableIntStateOf(0) }
  if (word.examples.isEmpty()) return
  if (progressive && word.examples.size > 1) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      word.examples.indices.forEach { index ->
        FilterChip(selected = selected == index, enabled = enabled, onClick = { selected = index },
          label = { Text(uiString(UiR.string.ui_example_number, index + 1)) })
      }
    }
  }
  val examples = if (progressive) listOf(word.examples[selected.coerceIn(word.examples.indices)]) else word.examples
  examples.forEach { WordExamplePanel(it, onSpeak, enabled, compact) }
}

@Composable
fun WordExamplePanel(example: ExampleSentence, onSpeak: (SpeechRequest) -> Unit,
  enabled: Boolean = true, compact: Boolean = false, header: (@Composable () -> Unit)? = null) {
  val enSource = rememberSpeechSourceId(example.english to "example/en")
  val cnSource = rememberSpeechSourceId(example.chinese to "example/cn")
  Card(modifier = Modifier.fillMaxWidth().glassPanel(shape = MaterialTheme.shapes.large),
    shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = glassPanelColor())) {
    Column(Modifier.padding(horizontal = if (compact) 12.dp else 16.dp)
      .padding(top = 8.dp, bottom = if (compact) 12.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      header?.invoke()
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(uiString(UiR.string.ui_example), Modifier.weight(1f),
          style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        TextButton(onClick = { onSpeak(SpeechRequest(example.english, AppLanguage.EN,
          SpeechScene.EXAMPLE_PRONUNCIATION, counterpart = example.chinese,
          chinesePronunciation = ChinesePronunciation(example.chinese, example.chinesePinyin), sourceId = enSource)) },
          enabled = enabled && !isSpeechPreparing(enSource), contentPadding = PaddingValues(horizontal = 8.dp)) {
          SpeechPlayIcon(enSource, contentDescription = uiString(UiR.string.ui_listen_to_english_example), modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(4.dp))
          Text(uiString(UiR.string.ui_english), style = MaterialTheme.typography.labelMedium)
        }
        TextButton(onClick = { onSpeak(SpeechRequest(example.chinese, AppLanguage.CN,
          SpeechScene.EXAMPLE_PRONUNCIATION, counterpart = example.english,
          chinesePronunciation = ChinesePronunciation(example.chinese, example.chinesePinyin), sourceId = cnSource)) },
          enabled = enabled && !isSpeechPreparing(cnSource), contentPadding = PaddingValues(horizontal = 8.dp)) {
          SpeechPlayIcon(cnSource, contentDescription = uiString(UiR.string.ui_listen_to_chinese_example), modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(4.dp))
          Text(uiString(UiR.string.ui_chinese), style = MaterialTheme.typography.labelMedium)
        }
      }
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(example.chunks.takeIf { it.isNotEmpty() }?.joinToString(" / ") { it.english } ?: example.english,
          Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge)
        val segmentedChinese = example.chunks.takeIf { it.isNotEmpty() }?.joinToString(" / ") { it.chinese }
          ?: example.literalChinese.takeIf { it.isNotBlank() && it != example.chinese }
        segmentedChinese?.let { Text(it, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text(example.chinese, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      example.wordIpa?.takeIf { it.isNotBlank() }?.let { ipa ->
        Text(uiString(UiR.string.ui_example_word_ipa, ipa), style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }
}
