package com.example.flashcardforteens.feature.study

import android.content.Context
import android.content.pm.ApplicationInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal enum class LearningSound(val resource: Int, val durationMs: Long) {
  CORRECT(R.raw.gentle_correct, 400),
  TRY_AGAIN(R.raw.gentle_retry, 320),
  COMPLETE(R.raw.gentle_complete, 720),
}

/** Original soft musical cues, generated offline by tools/generate_learning_cues.py. */
internal class LearningSounds(context: Context) : AutoCloseable {
  private val debug = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
  private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
  private val lock = Any()
  private val readiness = mutableMapOf<Int, CompletableDeferred<Boolean>>()
  private var closed = false
  private var revision = 0L
  private var activeStream = 0
  private val poolDelegate = lazy { runCatching {
    SoundPool.Builder().setMaxStreams(1)
      .setAudioAttributes(AudioAttributes.Builder()
        // Learning cues follow the same media volume and output route as spoken words.
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
      .build().also { player ->
        player.setOnLoadCompleteListener { _, sample, status ->
          synchronized(lock) { readiness.getOrPut(sample) { CompletableDeferred() }.complete(status == 0) }
        }
      }
  }.getOrNull() }
  private val pool by poolDelegate
  private val samples by lazy { LearningSound.entries.associateWith { sound ->
    runCatching { pool?.load(context.applicationContext, sound.resource, 1) ?: 0 }.getOrDefault(0)
  } }

  /** Decode the bundled clips before the learner submits an answer. Never plays audio. */
  suspend fun prewarm() = withContext(Dispatchers.IO) {
    synchronized(lock) { if (!closed) samples.size }
  }

  suspend fun playAnswer(correct: Boolean) = play(if (correct) LearningSound.CORRECT else LearningSound.TRY_AGAIN)

  /** A failed, muted or cancelled cue must never prevent the next learning action. */
  suspend fun play(sound: LearningSound): Unit = withContext(Dispatchers.IO) {
    if (synchronized(lock) { closed } || !audible()) return@withContext
    val (player, sample, operation) = synchronized(lock) {
      if (closed) return@withContext
      val player = pool ?: return@withContext
      val sample = samples[sound]?.takeIf { it != 0 } ?: return@withContext
      stopLocked()
      Triple(player, sample, revision)
    }
    val ready = synchronized(lock) { readiness.getOrPut(sample) { CompletableDeferred() } }
    // A late clip is skipped instead of delaying feedback on a cold start.
    if (!ready.isCompleted || !ready.await()) return@withContext
    currentCoroutineContext().ensureActive()
    val stream = synchronized(lock) {
      if (closed || revision != operation || !audible()) return@withContext
      runCatching { player.play(sample, .45f, .45f, 1, 0, 1f) }.getOrDefault(0)
        .also { activeStream = it }
    }
    if (stream == 0) return@withContext
    if (debug) Log.d("LearningSounds", "cue=$sound state=started")
    var waitFinished = false
    try {
      delay(sound.durationMs + 35L)
      waitFinished = true
    }
    finally {
      synchronized(lock) {
        if (!waitFinished && revision == operation && activeStream == stream) {
          runCatching { player.stop(stream) }
          activeStream = 0
        }
      }
      // A one-shot clip ends itself. Keep its handle until the next cue/stop so lifecycle cancellation
      // can still stop a delayed device tail, without cutting a normal tail on a wall-clock timer.
      if (debug) Log.d("LearningSounds", "cue=$sound state=${if (waitFinished) "wait_elapsed" else "cancelled"}")
    }
  }

  private fun audible(): Boolean = runCatching {
    audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) > 0
  }.getOrDefault(false)

  fun stop() = synchronized(lock) { stopLocked() }

  private fun stopLocked() {
    revision++
    if (activeStream != 0) runCatching { pool?.stop(activeStream) }
    activeStream = 0
  }

  override fun close() = synchronized(lock) {
    if (!closed) {
      closed = true
      stopLocked()
      readiness.values.forEach { it.complete(false) }
      if (poolDelegate.isInitialized()) runCatching { pool?.release() }
    }
  }
}
