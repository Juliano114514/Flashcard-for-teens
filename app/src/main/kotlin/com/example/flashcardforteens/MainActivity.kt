package com.example.flashcardforteens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.flashcardforteens.core.ui.FlashcardTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    val repositories = (application as FlashcardApplication).repositories
    setContent { FlashcardTheme { FlashcardApp(repositories) } }
  }
}
