package com.example.flashcardforteens.core.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.example.flashcardforteens.core.domain.ExplanationOptions

/** Shared by learning explanations and vocabulary details. */
val LocalExplanationOptions = staticCompositionLocalOf { ExplanationOptions() }
