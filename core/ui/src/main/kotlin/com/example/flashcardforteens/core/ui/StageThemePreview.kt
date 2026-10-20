package com.example.flashcardforteens.core.ui

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.example.flashcardforteens.core.domain.VocabularyStage

data class StageThemePreviewCase(val stage: VocabularyStage, val darkTheme: Boolean) {
  override fun toString() = "Stage ${stage.code} · ${if (darkTheme) "dark" else "light"}"
}

/** Four grade families, each in light and dark; semesters deliberately share a palette. */
class StageThemePreviewProvider : PreviewParameterProvider<StageThemePreviewCase> {
  override val values = VocabularyStage.entries.distinctBy { it.grade }.asSequence().flatMap { stage ->
    sequenceOf(StageThemePreviewCase(stage, false), StageThemePreviewCase(stage, true))
  }
}
