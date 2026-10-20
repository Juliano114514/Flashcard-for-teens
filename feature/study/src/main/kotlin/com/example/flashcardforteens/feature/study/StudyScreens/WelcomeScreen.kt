package com.example.flashcardforteens.feature.study

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flashcardforteens.core.domain.DailyWordChoices
import com.example.flashcardforteens.core.domain.StudySettings
import com.example.flashcardforteens.core.domain.StudySettingsRules
import com.example.flashcardforteens.core.domain.UiMessageKey
import com.example.flashcardforteens.core.ui.AvatarPickerDialog
import com.example.flashcardforteens.core.ui.EditableAvatar
import com.example.flashcardforteens.core.ui.FlashcardTheme
import com.example.flashcardforteens.core.ui.LearningIllustration
import com.example.flashcardforteens.core.ui.LearningPrimaryButton
import com.example.flashcardforteens.core.ui.flashcardBackground
import com.example.flashcardforteens.core.ui.uiString
import com.example.flashcardforteens.core.ui.R as UiR

@Composable
fun WelcomeScreen(
  settings: StudySettings,
  saving: Boolean,
  error: UiMessageKey?,
  onSave: (StudySettings) -> Unit,
) {
  var name by rememberSaveable { mutableStateOf(settings.displayName) }
  var avatarId by rememberSaveable { mutableStateOf(settings.avatarId) }
  var choosingAvatar by rememberSaveable { mutableStateOf(false) }
  var dailyWords by rememberSaveable { mutableIntStateOf(DailyWordChoices.normalize(settings.dailyWords)) }
  var reviewDays by rememberSaveable { mutableStateOf(settings.reviewDays) }
  val focus = LocalFocusManager.current
  val canSave = name.isNotBlank() && DailyWordChoices.isAllowed(dailyWords) && reviewDays.isNotEmpty() && !saving
  val save = {
    if (canSave) {
      focus.clearFocus()
      onSave(settings.copy(displayName = name.trim(), avatarId = avatarId,
        dailyWords = dailyWords, reviewDays = reviewDays, welcomed = true))
    }
  }
  BackHandler(enabled = saving) { }
  Column(Modifier.fillMaxSize().flashcardBackground().imePadding().padding(horizontal = 16.dp),
    horizontalAlignment = Alignment.CenterHorizontally) {
    // A single setup page makes the choices and the final action visible together.
    Column(Modifier.weight(1f).widthIn(max = 440.dp).fillMaxWidth()
      .verticalScroll(rememberScrollState()).padding(top = 20.dp, bottom = 12.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)) {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(uiString(R.string.welcome_title), style = MaterialTheme.typography.headlineSmall)
          Text(uiString(R.string.welcome_subtitle), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LearningIllustration(Modifier.size(52.dp))
      }
      Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          EditableAvatar(avatarId, size = 56.dp, enabled = !saving,
            onClick = { focus.clearFocus(); choosingAvatar = true })
          OutlinedTextField(value = name, onValueChange = { value ->
            if (value.length <= 40 && value.none { it.isISOControl() }) name = value
          }, modifier = Modifier.weight(1f), enabled = !saving, singleLine = true,
            label = { Text(uiString(R.string.welcome_name)) },
            placeholder = { Text(uiString(R.string.welcome_name_placeholder)) },
            shape = MaterialTheme.shapes.small,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }))
        }
      }
      Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          WelcomeSectionTitle(uiString(R.string.welcome_daily_title), uiString(R.string.welcome_daily_hint))
          (DailyWordChoices.MIN..DailyWordChoices.MAX step DailyWordChoices.STEP).toList().chunked(5).forEach { choices ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
              choices.forEach { count ->
                WelcomeChoice(count.toString(), count == dailyWords, !saving, Modifier.weight(1f)) {
                  focus.clearFocus()
                  dailyWords = count
                }
              }
            }
          }
        }
      }
      Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          WelcomeSectionTitle(uiString(R.string.welcome_review_title), uiString(R.string.welcome_review_hint))
          StudySettingsRules.reviewDays.chunked(3).forEach { choices ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              choices.forEach { day ->
                WelcomeChoice(uiString(R.string.welcome_review_day, day), day in reviewDays,
                  !saving, Modifier.weight(1f), multiple = true) {
                  focus.clearFocus()
                  reviewDays = if (day in reviewDays) reviewDays - day else (reviewDays + day).sorted()
                }
              }
            }
          }
          if (reviewDays.isEmpty()) Text(uiString(UiR.string.ui_select_at_least_one_day),
            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
      }
    }
    Column(Modifier.widthIn(max = 440.dp).fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
      horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
      error?.let { ErrorNotice(it, canSave, save) }
      Text(uiString(R.string.welcome_settings_hint), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
      LearningPrimaryButton(onClick = save, enabled = canSave, modifier = Modifier.fillMaxWidth()) {
        Text(uiString(if (saving) UiR.string.ui_saving_ff509c else UiR.string.ui_let_s_start))
      }
    }
  }
  if (choosingAvatar) AvatarPickerDialog(selectedId = avatarId,
    onDismiss = { choosingAvatar = false }, onConfirm = { avatarId = it; choosingAvatar = false })
}

@Composable
private fun WelcomeSectionTitle(title: String, subtitle: String) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
private fun WelcomeChoice(label: String, selected: Boolean, enabled: Boolean, modifier: Modifier,
  multiple: Boolean = false, onClick: () -> Unit) {
  Surface(onClick = onClick, enabled = enabled,
    modifier = modifier.semantics {
      if (multiple) {
        role = Role.Checkbox
        toggleableState = if (selected) ToggleableState.On else ToggleableState.Off
      } else {
        role = Role.RadioButton
        this.selected = selected
      }
    },
    shape = MaterialTheme.shapes.small,
    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
    contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    border = BorderStroke(if (selected) 2.dp else 1.dp,
      if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
    Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 4.dp),
      verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
      Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium), textAlign = TextAlign.Center,
        maxLines = 1)
    }
  }
}

@Preview(name = "Welcome · single page", widthDp = 360, heightDp = 760)
@Preview(name = "Welcome · compact", widthDp = 320, heightDp = 760, fontScale = 1.3f)
@Composable
private fun WelcomePreview() {
  FlashcardTheme {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
      WelcomeScreen(StudySettings(displayName = "乐乐"), saving = false, error = null, onSave = {})
    }
  }
}
