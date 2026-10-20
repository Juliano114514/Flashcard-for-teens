package com.example.flashcardforteens.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

// Translucency over the soft page gradient gives a frosted appearance without blurring text.
@Composable
@ReadOnlyComposable
fun glassPanelColor(): Color = MaterialTheme.colorScheme.surface.copy(
  alpha = FlashcardStyle.opacity.explanationPanel,
)

@Composable
fun Modifier.glassPanel(shape: Shape = MaterialTheme.shapes.large): Modifier {
  val edge = MaterialTheme.colorScheme.outlineVariant
  val highlight = Color.White.copy(alpha = if (FlashcardStyle.darkTheme) .18f else .90f)
  return drawWithCache {
      val outline = shape.createOutline(size, layoutDirection, this)
      val stroke = Stroke(width = 1.dp.toPx())
      val rim = Brush.linearGradient(listOf(highlight, edge.copy(alpha = .50f), edge.copy(alpha = .80f)))
      onDrawWithContent {
        drawContent()
        drawOutline(outline, brush = rim, style = stroke)
      }
    }
}
