package com.example.flashcardforteens.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

private val LocalBackgroundDrawn = staticCompositionLocalOf { false }

@Composable
fun FlashcardBackground(content: @Composable () -> Unit) {
  Box(Modifier.fillMaxSize().flashcardBackground()) {
    CompositionLocalProvider(LocalBackgroundDrawn provides true, content = content)
  }
}

/** Soft color at the edges leaves the reading area calm and uncluttered. */
@Composable
fun Modifier.flashcardBackground(): Modifier {
  if (LocalBackgroundDrawn.current) return this
  val accent = MaterialTheme.colorScheme.primary
  val dark = FlashcardStyle.darkTheme
  return background(studyBackgroundBrush()).drawWithCache {
    val glow = Brush.radialGradient(
      colors = listOf(accent.copy(alpha = if (dark) .10f else .07f), Color.Transparent),
      center = Offset(size.width * 1.05f, size.height * .12f),
      radius = size.width * .85f,
    )
    onDrawBehind { drawRect(glow) }
  }
}
