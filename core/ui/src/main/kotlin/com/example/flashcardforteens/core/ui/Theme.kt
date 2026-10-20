package com.example.flashcardforteens.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flashcardforteens.core.domain.VocabularyStage
import com.example.flashcardforteens.core.domain.ThemeMode

private val Light = lightColorScheme(
  primary = Color(0xFF237553), onPrimary = Color.White,
  primaryContainer = Color(0xFFE1F2E7), onPrimaryContainer = Color(0xFF174D37),
  inversePrimary = Color(0xFF9BD5B5),
  secondary = Color(0xFF35658C), onSecondary = Color.White,
  secondaryContainer = Color(0xFFE8F1FA), onSecondaryContainer = Color(0xFF234E72),
  tertiary = Color(0xFF856014), onTertiary = Color.White,
  tertiaryContainer = Color(0xFFFFF0C7), onTertiaryContainer = Color(0xFF68480A),
  background = Color(0xFFF7F9F8), onBackground = Color(0xFF20352D),
  surface = Color.White, onSurface = Color(0xFF20352D),
  surfaceVariant = Color(0xFFEDF2EF), onSurfaceVariant = Color(0xFF627168),
  surfaceTint = Color(0xFF237553),
  inverseSurface = Color(0xFF20352D), inverseOnSurface = Color(0xFFF7F9F8),
  surfaceBright = Color.White, surfaceDim = Color(0xFFE3EBE6),
  surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFAFCFA),
  surfaceContainer = Color(0xFFF0F5F1), surfaceContainerHigh = Color(0xFFE8EFEA),
  surfaceContainerHighest = Color(0xFFDEE9E1),
  outline = Color(0xFF77887D), outlineVariant = Color(0xFFDAE5DD),
  error = Color(0xFFB83229), onError = Color.White,
  errorContainer = Color(0xFFFFE3DC), onErrorContainer = Color(0xFF7C2119),
  scrim = Color.Black,
)
private val Dark = darkColorScheme(
  primary = Color(0xFF9BD5B5), onPrimary = Color(0xFF103B29),
  primaryContainer = Color(0xFF234C38), onPrimaryContainer = Color(0xFFD2EFDD),
  inversePrimary = Color(0xFF237553),
  secondary = Color(0xFFA9CDEB), onSecondary = Color(0xFF17364E),
  secondaryContainer = Color(0xFF28445D), onSecondaryContainer = Color(0xFFD9ECFC),
  tertiary = Color(0xFFEACA7F), onTertiary = Color(0xFF443108),
  tertiaryContainer = Color(0xFF544321), onTertiaryContainer = Color(0xFFFFF0C7),
  background = Color(0xFF142019), onBackground = Color(0xFFE4EEE7),
  surface = Color(0xFF1D2B23), onSurface = Color(0xFFE4EEE7),
  surfaceVariant = Color(0xFF2F4035), onSurfaceVariant = Color(0xFFB8C9BE),
  surfaceTint = Color(0xFF9BD5B5),
  inverseSurface = Color(0xFFE4EEE7), inverseOnSurface = Color(0xFF20352D),
  surfaceBright = Color(0xFF35483C), surfaceDim = Color(0xFF142019),
  surfaceContainerLowest = Color(0xFF0E1712), surfaceContainerLow = Color(0xFF18261D),
  surfaceContainer = Color(0xFF1D2B23), surfaceContainerHigh = Color(0xFF28392E),
  surfaceContainerHighest = Color(0xFF324438),
  outline = Color(0xFF92AA9A), outlineVariant = Color(0xFF405447),
  error = Color(0xFFFFB4A5), onError = Color(0xFF63221A),
  errorContainer = Color(0xFF642E27), onErrorContainer = Color(0xFFFFE3DC),
  scrim = Color.Black,
)

/** App-specific semantic colors for learning surfaces and answer feedback. */
@Immutable
data class FlashcardColors(
  val success: Color,
  val onSuccess: Color,
  val successContainer: Color,
  val onSuccessContainer: Color,
  val gradientStart: Color,
  val gradientMiddle: Color,
  val gradientEnd: Color,
  val gradientAction: Color,
  val gradientSecondaryInk: Color,
  val gradientSuccess: Color,
  val gradientError: Color,
)

@Immutable
data class FlashcardOpacity(
  val explanationPanel: Float,
  val choicePanel: Float,
  val divider: Float = .65f,
  val disabledContent: Float = .38f,
  val disabledContainer: Float = .12f,
)

private val LightColors = FlashcardColors(
  success = Color(0xFF237553), onSuccess = Color.White,
  successContainer = Color(0xFFE1F2E7), onSuccessContainer = Color(0xFF174D37),
  gradientStart = Light.background, gradientMiddle = Light.background, gradientEnd = Light.background,
  gradientAction = Light.primary, gradientSecondaryInk = Light.onSurfaceVariant,
  gradientSuccess = Color(0xFF237553), gradientError = Light.error,
)
private val DarkColors = FlashcardColors(
  success = Color(0xFF9BD5B5), onSuccess = Color(0xFF103B29),
  successContainer = Color(0xFF234C38), onSuccessContainer = Color(0xFFD2EFDD),
  gradientStart = Dark.background, gradientMiddle = Dark.background, gradientEnd = Dark.background,
  gradientAction = Dark.primary, gradientSecondaryInk = Dark.onSurfaceVariant,
  gradientSuccess = Color(0xFF9BD5B5), gradientError = Dark.error,
)
private val LightOpacity = FlashcardOpacity(explanationPanel = .88f, choicePanel = .94f)
private val DarkOpacity = FlashcardOpacity(explanationPanel = .90f, choicePanel = .96f)
private val LocalColors = staticCompositionLocalOf { LightColors }
private val LocalOpacity = staticCompositionLocalOf { LightOpacity }
private val LocalStage = staticCompositionLocalOf { VocabularyStage.GRADE_3_FIRST }
private val LocalDarkTheme = staticCompositionLocalOf { false }

object FlashcardStyle {
  val stage: VocabularyStage
    @Composable @ReadOnlyComposable get() = LocalStage.current
  val darkTheme: Boolean
    @Composable @ReadOnlyComposable get() = LocalDarkTheme.current
  val colors: FlashcardColors
    @Composable @ReadOnlyComposable get() = LocalColors.current
  val opacity: FlashcardOpacity
    @Composable @ReadOnlyComposable get() = LocalOpacity.current
}
private val Type = Typography(
  displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 56.sp, lineHeight = 68.sp, fontWeight = FontWeight.Medium),
  headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
  headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 23.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
  headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
  titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
  titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 18.sp, lineHeight = 25.sp, fontWeight = FontWeight.Medium),
  titleSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
  labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
  labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
  labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
  bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 18.sp, lineHeight = 28.sp),
  bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp),
  bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp))
private val Corners = Shapes(
  extraSmall = RoundedCornerShape(8.dp),
  small = RoundedCornerShape(12.dp),
  medium = RoundedCornerShape(16.dp),
  large = RoundedCornerShape(20.dp),
  extraLarge = RoundedCornerShape(24.dp))

@Composable
fun FlashcardTheme(darkTheme: Boolean = isSystemInDarkTheme(),
  stage: VocabularyStage = VocabularyStage.GRADE_3_FIRST, themeMode: ThemeMode = ThemeMode.SYSTEM,
  content: @Composable () -> Unit) {
  val dark = when (themeMode) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.SYSTEM -> darkTheme
  }
  // Theme by grade, so switching semesters preserves the same visual identity.
  val palette = StagePalettes.getValue(stage.grade)
  val scheme = remember(palette, dark) { palette.scheme(if (dark) Dark else Light, dark) }
  val colors = remember(palette, scheme, dark) {
    (if (dark) DarkColors else LightColors).copy(
      gradientStart = if (dark) palette.darkStart else palette.lightStart,
      gradientMiddle = if (dark) palette.darkMiddle else palette.lightMiddle,
      gradientEnd = if (dark) palette.darkEnd else palette.lightEnd,
      gradientAction = scheme.primary,
      gradientSecondaryInk = scheme.onSurfaceVariant,
    )
  }
  CompositionLocalProvider(
    LocalColors provides colors,
    LocalOpacity provides if (dark) DarkOpacity else LightOpacity,
    LocalStage provides stage,
    LocalDarkTheme provides dark,
  ) {
    MaterialTheme(colorScheme = scheme,
      typography = Type, shapes = Corners, content = content)
  }
}

/** Learning surfaces share the same theme selection as all Material components. */
@Composable
fun studyBackgroundBrush(): Brush {
  val colors = FlashcardStyle.colors
  return remember(colors) {
    Brush.verticalGradient(0f to colors.gradientStart, .4f to colors.gradientMiddle, 1f to colors.gradientEnd)
  }
}

fun Throwable.flashcardMessage(): com.example.flashcardforteens.core.domain.UiMessageKey = when (this) {
  is com.example.flashcardforteens.core.domain.UiMessageException -> code
  is com.example.flashcardforteens.core.domain.NewStudyDayException ->
    com.example.flashcardforteens.core.domain.UiMessageKey.A_NEW_DAY_HAS_STARTED_YOUR_DAILY_PLAN_HAS_BEEN_REFRESHED
  else -> com.example.flashcardforteens.core.domain.UiMessageKey.LOCAL_DATA_COULD_NOT_BE_READ_OR_SAVED_PLEASE_RETRY_YOUR_SAVED_PROGRESS_HAS_NOT_BEEN_CLEARED
}
