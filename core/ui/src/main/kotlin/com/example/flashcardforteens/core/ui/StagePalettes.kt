package com.example.flashcardforteens.core.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.example.flashcardforteens.core.domain.VocabularyStage

/** Four grade hue families, each with a light and dark reading surface. */
@Immutable
internal data class StagePalette(
  val lightAccent: Color, val darkAccent: Color,
  val lightStart: Color, val lightMiddle: Color, val lightEnd: Color,
  val darkStart: Color, val darkMiddle: Color, val darkEnd: Color,
) {
  fun scheme(base: ColorScheme, dark: Boolean): ColorScheme {
    val accent = if (dark) darkAccent else lightAccent
    val background = if (dark) darkMiddle else lightMiddle
    val tint = lerp(background, accent, if (dark) .16f else .12f)
    val ink = if (dark) Color(0xFFF0F4FC) else Color(0xFF253047)
    val secondaryInk = if (dark) Color(0xFFC0CCDE) else Color(0xFF58667B)
    val surface = if (dark) lerp(background, Color.White, .065f) else Color.White
    val container = if (dark) lerp(background, accent, .20f) else tint
    val onAccent = if (dark) background else Color.White
    return base.copy(
      primary = accent, onPrimary = onAccent,
      primaryContainer = container, onPrimaryContainer = if (dark) darkAccent else lerp(lightAccent, Color.Black, .36f),
      inversePrimary = if (dark) lightAccent else darkAccent,
      secondary = if (dark) lerp(darkAccent, ink, .24f) else lerp(lightAccent, secondaryInk, .24f),
      onSecondary = onAccent,
      secondaryContainer = if (dark) lerp(surface, accent, .10f) else lerp(surface, accent, .06f),
      onSecondaryContainer = ink,
      background = background, onBackground = ink,
      surface = surface, onSurface = ink, surfaceTint = accent,
      surfaceVariant = lerp(surface, accent, if (dark) .12f else .09f),
      onSurfaceVariant = secondaryInk,
      inverseSurface = if (dark) lightMiddle else darkMiddle,
      inverseOnSurface = if (dark) Color(0xFF253047) else Color(0xFFF0F4FC),
      surfaceBright = if (dark) lerp(background, ink, .12f) else surface,
      surfaceDim = if (dark) background else lerp(surface, tint, .80f),
      surfaceContainerLowest = if (dark) lerp(background, Color.Black, .20f) else Color.White,
      surfaceContainerLow = surface,
      surfaceContainer = lerp(surface, accent, if (dark) .055f else .035f),
      surfaceContainerHigh = lerp(surface, accent, if (dark) .09f else .065f),
      surfaceContainerHighest = lerp(surface, accent, if (dark) .13f else .10f),
      outline = if (dark) Color(0xFF97A7BC) else Color(0xFF7A879A),
      outlineVariant = lerp(surface, accent, if (dark) .25f else .17f),
    )
  }
}

internal val StagePalettes = mapOf(
  3 to StagePalette(
    Color(0xFF087F68), Color(0xFF71E3BA),
    Color(0xFFE6FBF0), Color(0xFFFAFDFB), Color(0xFFEAF5FF),
    Color(0xFF15372E), Color(0xFF101F1D), Color(0xFF142C36)),
  4 to StagePalette(
    Color(0xFF2864DC), Color(0xFF9AC6FF),
    Color(0xFFE9F2FF), Color(0xFFFBFCFF), Color(0xFFEEECFF),
    Color(0xFF172F53), Color(0xFF131E30), Color(0xFF242443)),
  5 to StagePalette(
    Color(0xFF7D4FD2), Color(0xFFCCAEFF),
    Color(0xFFF2EBFF), Color(0xFFFDFCFF), Color(0xFFFFEEF7),
    Color(0xFF30204B), Color(0xFF21192F), Color(0xFF3C253C)),
  6 to StagePalette(
    Color(0xFFC03C68), Color(0xFFFFA8C2),
    Color(0xFFFFECF2), Color(0xFFFFFCFC), Color(0xFFFFF2E2),
    Color(0xFF472435), Color(0xFF2B1C27), Color(0xFF3C2C28)),
)

/** Both semesters share their grade's accent; answer feedback keeps its own semantic colors. */
@androidx.compose.runtime.Composable
@androidx.compose.runtime.ReadOnlyComposable
fun stageAccent(stage: VocabularyStage): Color {
  val palette = StagePalettes.getValue(stage.grade)
  return if (FlashcardStyle.darkTheme) palette.darkAccent else palette.lightAccent
}
