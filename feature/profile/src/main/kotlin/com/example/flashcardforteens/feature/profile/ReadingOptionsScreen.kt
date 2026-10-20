package com.example.flashcardforteens.feature.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.style.TextAlign
import com.example.flashcardforteens.core.ui.FlashcardTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.flashcardforteens.core.domain.SpeechMode
import com.example.flashcardforteens.core.domain.SpeechOptions
import com.example.flashcardforteens.core.domain.SpeechScene
import com.example.flashcardforteens.core.domain.AppLanguage
import com.example.flashcardforteens.core.ui.FlashcardLayout
import com.example.flashcardforteens.core.ui.FlashcardToolbar
import com.example.flashcardforteens.core.ui.flashcardBackground
import com.example.flashcardforteens.core.ui.uiString
import com.example.flashcardforteens.core.ui.R as UiR

@Composable
fun ReadingOptionsScreen(vm: ProfileViewModel, onBack: () -> Unit) {
  val state by vm.state.collectAsStateWithLifecycle()
  OptionsPage(uiString(UiR.string.ui_reading_options), stringResource(R.string.profile_reading_hint), state, onBack, vm::retry) {
    item(key = "explanation-sequence") {
      ExplanationPlaybackCard(state.stored.speechOptions, enabled = !state.loading && !state.saving) {
        vm.save(ProfileUpdate(speechOptions = it))
      }
    }
    items(otherSpeechScenes, key = { it.name }, contentType = { "speech-scene" }) { scene ->
      val defaultLanguage = if (scene == SpeechScene.MEANING_PRONUNCIATION) AppLanguage.CN else AppLanguage.EN
      SpeechSceneCard(scene, state.stored.speechOptions.mode(scene, defaultLanguage),
        enabled = !state.loading && !state.saving, onMode = { mode ->
          vm.save(ProfileUpdate(speechOptions = state.stored.speechOptions.withMode(scene, mode)))
        })
    }
  }
}

private val otherSpeechScenes = SpeechScene.entries.filterNot {
  it == SpeechScene.STUDY_EXPLANATION || it == SpeechScene.STUDY_EXPLANATION_WORD ||
    it == SpeechScene.STUDY_EXPLANATION_EXAMPLE
}

@Composable
private fun ExplanationPlaybackCard(options: SpeechOptions, enabled: Boolean, onChange: (SpeechOptions) -> Unit) {
  Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
      Text(stringResource(R.string.profile_explanation_title), Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.titleMedium)
      Text(stringResource(R.string.profile_explanation_hint), Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      listOf(
        Triple(SpeechScene.STUDY_EXPLANATION_WORD, AppLanguage.EN, R.string.profile_explanation_word),
        Triple(SpeechScene.STUDY_EXPLANATION_WORD, AppLanguage.CN, R.string.profile_explanation_meaning),
        Triple(SpeechScene.STUDY_EXPLANATION_EXAMPLE, AppLanguage.EN, R.string.profile_explanation_sentence),
        Triple(SpeechScene.STUDY_EXPLANATION_EXAMPLE, AppLanguage.CN, R.string.profile_explanation_translation),
      ).forEach { (scene, language, label) ->
        val checked = options.mode(scene, AppLanguage.EN).allows(language)
        Row(Modifier.fillMaxWidth().toggleable(value = checked, enabled = enabled, role = Role.Switch,
          onValueChange = { onChange(options.withLanguage(scene, language, it)) })
          .heightIn(min = 56.dp).padding(horizontal = 16.dp),
          verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(stringResource(label), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
          Switch(checked = checked, onCheckedChange = null, enabled = enabled)
        }
      }
    }
  }
}

@Composable
private fun SpeechSceneCard(scene: SpeechScene, selected: SpeechMode, enabled: Boolean,
  onMode: (SpeechMode) -> Unit) {
  var expanded by rememberSaveable { mutableStateOf(false) }
  Box(Modifier.fillMaxWidth()) {
    Surface(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth(),
      shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
      Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(uiString(scene.labelId()), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(selected.shortLabelId()), style = MaterialTheme.typography.labelLarge,
          textAlign = TextAlign.End, color = MaterialTheme.colorScheme.primary)
        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(20.dp),
          tint = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
    DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
      val modes = if (scene == SpeechScene.STUDY_PROMPT || scene == SpeechScene.STUDY_CONTEXT)
        listOf(SpeechMode.NONE, SpeechMode.ONLY_EN) else SpeechMode.entries
      modes.forEach { mode ->
        DropdownMenuItem(text = { Text(uiString(mode.labelId())) }, onClick = { expanded = false; onMode(mode) },
          leadingIcon = { RadioButton(selected = mode == selected, onClick = null) })
      }
    }
  }
}

@Composable
private fun OptionsPage(title: String, description: String, state: ProfileUiState,
  onBack: () -> Unit, onRetry: () -> Unit, content: LazyListScope.() -> Unit) {
  LazyColumn(Modifier.fillMaxSize().flashcardBackground(), contentPadding = PaddingValues(FlashcardLayout.pageInset),
    verticalArrangement = Arrangement.spacedBy(10.dp)) {
    item(key = "header", contentType = "header") {
      Column(verticalArrangement = Arrangement.spacedBy(FlashcardLayout.sectionGap)) {
        FlashcardToolbar(title, onBack, enabled = !state.saving, trailing = {
          if (state.saving) CircularProgressIndicator(Modifier.size(24.dp))
        })
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
    if (state.loading) item(key = "loading", contentType = "loading") { CircularProgressIndicator() }
    state.error?.let { error ->
      item(key = "error", contentType = "error") {
        Column {
          Text(uiString(error), color = MaterialTheme.colorScheme.error)
          TextButton(onClick = onRetry, enabled = !state.saving) { Text(uiString(UiR.string.ui_retry)) }
        }
      }
    }
    if (!state.loading) content()
  }
}

private fun SpeechMode.labelId(): Int = when (this) {
  SpeechMode.NONE -> UiR.string.ui_reading_none
  SpeechMode.ONLY_EN -> UiR.string.ui_reading_only_en
  SpeechMode.ONLY_CN -> UiR.string.ui_reading_only_cn
  SpeechMode.BOTH -> UiR.string.ui_reading_both
}

private fun SpeechScene.labelId(): Int = when (this) {
  SpeechScene.HOME_WORD -> UiR.string.ui_reading_home
  SpeechScene.STUDY_PROMPT -> UiR.string.ui_reading_study_prompt
  SpeechScene.STUDY_CONTEXT -> UiR.string.ui_reading_study_context
  SpeechScene.STUDY_EXPLANATION -> UiR.string.ui_reading_study_explanation
  SpeechScene.STUDY_EXPLANATION_WORD -> R.string.profile_explanation_word
  SpeechScene.STUDY_EXPLANATION_EXAMPLE -> R.string.profile_explanation_sentence
  SpeechScene.ANSWER_FEEDBACK -> R.string.profile_reading_wrong_answer
  SpeechScene.WORD_DETAIL -> UiR.string.ui_reading_word_detail
  SpeechScene.WORD_PRONUNCIATION -> UiR.string.ui_reading_word
  SpeechScene.MEANING_PRONUNCIATION -> UiR.string.ui_reading_meaning
  SpeechScene.EXAMPLE_PRONUNCIATION -> UiR.string.ui_reading_example
}

private fun SpeechMode.shortLabelId(): Int = when (this) {
  SpeechMode.NONE -> R.string.profile_reading_none
  SpeechMode.ONLY_EN -> R.string.profile_reading_en
  SpeechMode.ONLY_CN -> R.string.profile_reading_cn
  SpeechMode.BOTH -> R.string.profile_reading_both
}

@Preview(name = "Reading settings", widthDp = 360, heightDp = 760)
@Preview(name = "Reading settings · compact", widthDp = 320, heightDp = 760, fontScale = 1.3f)
@Composable
private fun ReadingOptionsPreview() {
  FlashcardTheme {
    OptionsPage(uiString(UiR.string.ui_reading_options), stringResource(R.string.profile_reading_hint),
      ProfileUiState(loading = false), onBack = {}, onRetry = {}) {
      item { ExplanationPlaybackCard(SpeechOptions(), enabled = true, onChange = {}) }
      items(otherSpeechScenes) { scene ->
        val language = if (scene == SpeechScene.MEANING_PRONUNCIATION) AppLanguage.CN else AppLanguage.EN
        SpeechSceneCard(scene, SpeechOptions().mode(scene, language),
          enabled = true, onMode = {})
      }
    }
  }
}
