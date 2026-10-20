package com.example.flashcardforteens

import android.app.Application
import com.example.flashcardforteens.core.data.FlashcardRepositories
import com.example.flashcardforteens.core.media.OfflineSpeech

class FlashcardApplication : Application() {
  val repositories: FlashcardRepositories by lazy { FlashcardRepositories(this) }
  val speech: OfflineSpeech by lazy { OfflineSpeech(this) }
}
