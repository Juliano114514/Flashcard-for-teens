package com.example.flashcardforteens.core.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

/** One action style across lessons, forms, onboarding and empty states. */
@Composable
fun LearningPrimaryButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
  shape: Shape = MaterialTheme.shapes.medium, colors: ButtonColors = ButtonDefaults.buttonColors(),
  border: BorderStroke? = null, content: @Composable RowScope.() -> Unit) {
  val interactions = remember { MutableInteractionSource() }
  val pressed by interactions.collectIsPressedAsState()
  val drop by animateDpAsState(if (pressed) 2.dp else 0.dp, label = "button press")
  Button(onClick, modifier.padding(bottom = 4.dp).heightIn(min = 56.dp).offset(y = drop)
    .shadow(if (enabled && !pressed) 3.dp else 0.dp, shape, clip = false,
      ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = .20f),
      spotColor = MaterialTheme.colorScheme.primary.copy(alpha = .28f)),
    enabled = enabled, shape = shape, colors = colors, border = border,
    interactionSource = interactions, elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), content = content)
}

@Composable
fun LearningSecondaryButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
  shape: Shape = MaterialTheme.shapes.medium, colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
  border: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  content: @Composable RowScope.() -> Unit) {
  val interactions = remember { MutableInteractionSource() }
  val pressed by interactions.collectIsPressedAsState()
  val drop by animateDpAsState(if (pressed) 2.dp else 0.dp, label = "secondary button press")
  OutlinedButton(onClick, modifier.padding(bottom = 4.dp).heightIn(min = 56.dp).offset(y = drop),
    enabled = enabled, shape = shape, colors = colors, border = border, interactionSource = interactions,
    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp), content = content)
}

@Composable
fun LearningPageTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
  Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(title, style = MaterialTheme.typography.headlineLarge)
      subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
  }
}

/** Original vector book and star, deliberately decorative. */
@Composable
fun LearningIllustration(modifier: Modifier = Modifier) {
  val blue = MaterialTheme.colorScheme.primary
  val page = MaterialTheme.colorScheme.primaryContainer
  val yellow = if (FlashcardStyle.darkTheme) Color(0xFFFFD96A) else Color(0xFFFFC83D)
  Canvas(modifier.clearAndSetSemantics { }) {
    val w = size.width; val h = size.height
    drawRoundRect(blue, Offset(w * .06f, h * .25f), Size(w * .84f, h * .65f), CornerRadius(w * .10f))
    drawRoundRect(page, Offset(w * .14f, h * .20f), Size(w * .68f, h * .60f), CornerRadius(w * .07f))
    drawLine(blue, Offset(w * .48f, h * .27f), Offset(w * .48f, h * .74f), strokeWidth = w * .035f)
    repeat(3) { n ->
      val y = h * (.36f + .12f * n)
      drawLine(blue.copy(alpha = .55f), Offset(w * .22f, y), Offset(w * .38f, y), strokeWidth = w * .025f)
      drawLine(blue.copy(alpha = .55f), Offset(w * .57f, y), Offset(w * .73f, y), strokeWidth = w * .025f)
    }
    val star = Path().apply {
      moveTo(w * .78f, 0f); lineTo(w * .84f, h * .14f); lineTo(w, h * .18f)
      lineTo(w * .87f, h * .28f); lineTo(w * .88f, h * .44f); lineTo(w * .74f, h * .36f)
      lineTo(w * .60f, h * .44f); lineTo(w * .63f, h * .28f); lineTo(w * .51f, h * .17f)
      lineTo(w * .69f, h * .14f); close()
    }
    drawPath(star, yellow)
    drawPath(star, blue.copy(alpha = .12f), style = Stroke(width = w * .02f))
  }
}
