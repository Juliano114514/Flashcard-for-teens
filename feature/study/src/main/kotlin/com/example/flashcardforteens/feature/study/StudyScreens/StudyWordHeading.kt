package com.example.flashcardforteens.feature.study

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flashcardforteens.core.domain.StudyCard
import com.example.flashcardforteens.core.domain.WordEntry
import com.example.flashcardforteens.core.ui.*
import com.example.flashcardforteens.core.ui.R as UiR

@Composable
internal fun RoundProgress(card: StudyCard) {
  val description = uiString(UiR.string.ui_round_of, card.round, card.targetRounds)
  Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.semantics {
    contentDescription = description
  }) {
    repeat(card.targetRounds) { index ->
      Spacer(Modifier.size(8.dp).clip(CircleShape).background(
        if (index < card.round) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant))
    }
  }
}

@Composable
internal fun StudyWordHeading(word: WordEntry, enabled: Boolean, onSpeak: () -> Unit,
  sourceId: String? = null) {
  Surface(shape = MaterialTheme.shapes.large, color = glassPanelColor(),
    contentColor = MaterialTheme.colorScheme.onSurface,
    modifier = Modifier.fillMaxWidth().glassPanel(shape = MaterialTheme.shapes.large)) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
      val measurer = rememberTextMeasurer()
      val density = LocalDensity.current
      val inlineWidth = with(density) { (maxWidth - 56.dp).toPx() }
      val inlinePlayback = measurer.measure(AnnotatedString(word.headword), softWrap = false,
        style = MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp, fontWeight = FontWeight.SemiBold))
        .size.width <= inlineWidth
      WordHeading(word, enabled, onSpeak, trailingSpacing = 8.dp, playBesideWord = inlinePlayback, sourceId = sourceId)
    }
  }
}
