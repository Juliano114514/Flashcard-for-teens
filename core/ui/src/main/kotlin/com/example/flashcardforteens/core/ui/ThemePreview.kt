package com.example.flashcardforteens.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp

@Preview(name = "Grade themes · light & dark", widthDp = 360, heightDp = 720)
@Composable
private fun ThemePreview(@PreviewParameter(StageThemePreviewProvider::class) theme: StageThemePreviewCase) {
  FlashcardTheme(darkTheme = theme.darkTheme, stage = theme.stage) {
    val scheme = MaterialTheme.colorScheme
    val colors = FlashcardStyle.colors
    FlashcardBackground {
      Surface(color = Color.Transparent, contentColor = scheme.onBackground) {
      Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("${theme.stage.label}", style = MaterialTheme.typography.titleLarge)
        Text("每天进步一点点", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        Surface(color = glassPanelColor(), modifier = Modifier.fillMaxWidth().glassPanel(),
          shape = MaterialTheme.shapes.large) {
          Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("sunshine", style = MaterialTheme.typography.headlineLarge, color = scheme.primary)
            Text("/ˈsʌnʃaɪn/", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            Text("n. 阳光；晴天", style = MaterialTheme.typography.bodyLarge)
          }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf(scheme.surfaceContainerLowest, scheme.surfaceContainerLow, scheme.surfaceContainer,
            scheme.surfaceContainerHigh, scheme.surfaceContainerHighest).forEach { color ->
            Surface(Modifier.size(40.dp), color = color, shape = MaterialTheme.shapes.small) {}
          }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          Button(onClick = {}, modifier = Modifier.weight(1f)) { Text("继续学习") }
          OutlinedButton(onClick = {}) { Text("听一听") }
        }
        Surface(color = colors.successContainer, contentColor = colors.onSuccessContainer,
          shape = MaterialTheme.shapes.medium) {
          Text("答对了，继续加油", Modifier.fillMaxWidth().padding(16.dp))
        }
        Surface(color = scheme.errorContainer, contentColor = scheme.onErrorContainer,
          shape = MaterialTheme.shapes.medium) {
          Text("再试一次", Modifier.fillMaxWidth().padding(16.dp))
        }
        Text("第 1 / 4 轮", color = colors.gradientSecondaryInk, style = MaterialTheme.typography.bodySmall)
      }
      }
    }
  }
}
