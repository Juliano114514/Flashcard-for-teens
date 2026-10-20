package com.example.flashcardforteens.feature.study

import com.example.flashcardforteens.core.ui.uiString
import com.example.flashcardforteens.core.ui.R as UiR

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.flashcardforteens.core.ui.FlashcardDialog
import com.example.flashcardforteens.core.ui.flashcardBackground
import com.example.flashcardforteens.core.ui.FlashcardStyle
import com.example.flashcardforteens.core.domain.AppLanguage
import com.example.flashcardforteens.core.domain.UiMessageKey

@Composable
internal fun DailyProgress(completed: Int, planned: Int, modifier: Modifier = Modifier) {
  LinearProgressIndicator(progress = {
    if (planned <= 0) 0f else (completed.toFloat() / planned).coerceIn(0f, 1f)
  }, modifier = modifier.fillMaxWidth().height(8.dp).clip(MaterialTheme.shapes.small),
    color = FlashcardStyle.colors.success, trackColor = MaterialTheme.colorScheme.surfaceContainerHighest)
}

@Composable
internal fun StudyActionPair(stacked: Boolean, content: @Composable (Modifier) -> Unit) {
  if (stacked) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    content(Modifier.fillMaxWidth())
  } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    content(Modifier.weight(1f))
  }
}

@Composable
internal fun PageColumn(modifier: Modifier = Modifier, scrollKey: Any? = null, studyPage: Boolean = false,
  content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
  val scroll = rememberScrollState()
  LaunchedEffect(scrollKey) { scroll.scrollTo(0) }
  Column(modifier.fillMaxSize().then(if (studyPage) Modifier else Modifier.flashcardBackground())
    .verticalScroll(scroll).padding(horizontal = if (studyPage) 20.dp else 24.dp,
      vertical = if (studyPage) 16.dp else 24.dp),
    verticalArrangement = Arrangement.spacedBy(if (studyPage) 16.dp else 20.dp), content = content)
}

@Composable
internal fun SectionLabel(value: String) {
  Text(value, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun LoadingNotice(studyPage: Boolean = false) {
  Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp)) {
    CircularProgressIndicator(Modifier.size(28.dp))
    Text(uiString(UiR.string.ui_loading), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
      color = if (studyPage) FlashcardStyle.colors.gradientSecondaryInk else MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
internal fun ErrorNotice(message: UiMessageKey, enabled: Boolean, onRetry: () -> Unit, studyPage: Boolean = false) {
  Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(uiString(message), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
      color = if (studyPage) FlashcardStyle.colors.gradientError else MaterialTheme.colorScheme.error,
      style = MaterialTheme.typography.bodyMedium)
    TextButton(shape = MaterialTheme.shapes.small, onClick = onRetry, enabled = enabled, colors = ButtonDefaults.textButtonColors(
      contentColor = if (studyPage) FlashcardStyle.colors.gradientAction else MaterialTheme.colorScheme.primary)) { Text(uiString(UiR.string.ui_try_again)) }
  }
}
