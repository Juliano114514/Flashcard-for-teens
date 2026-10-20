package com.example.flashcardforteens.feature.study

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.example.flashcardforteens.core.domain.CardPhase
import com.example.flashcardforteens.core.domain.SpeechRequest
import com.example.flashcardforteens.core.domain.StudyKind
import com.example.flashcardforteens.core.domain.SpeechPlaybackPhase
import com.example.flashcardforteens.core.ui.LocalSpeechPlaybackState
import kotlinx.coroutines.flow.first

@Composable
fun StudyScreen(vm: StudyViewModel, onBack: () -> Unit,
  onSpeakAndWait: suspend (SpeechRequest) -> Boolean,
  playbackPreparing: Boolean = false) {
  val state by vm.state.collectAsStateWithLifecycle()
  val card = state.card
  val generation = state.directionGeneration
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  LaunchedEffect(vm, lifecycle) {
    lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
      vm.setStudySpeechActive(true)
      try { awaitCancellation() }
      finally { vm.setStudySpeechActive(false) }
    }
  }
  val attemptKey = "$generation/${card?.contextVersion}/${card?.id}"
  var selectedOption by rememberSaveable(attemptKey) { mutableStateOf<String?>(null) }
  var answerChosen by rememberSaveable(attemptKey) { mutableStateOf(false) }
  var correctAnswerRevealed by rememberSaveable(attemptKey) { mutableStateOf(false) }
  var feedbackFinished by rememberSaveable(attemptKey) { mutableStateOf(false) }
  var feedbackPlaybackSucceeded by rememberSaveable(attemptKey) { mutableStateOf(false) }
  var feedbackSoundClaimed by rememberSaveable(attemptKey) { mutableStateOf(false) }
  var feedbackCueFinished by rememberSaveable(attemptKey) { mutableStateOf(false) }
  var seenActiveSession by rememberSaveable(generation) { mutableStateOf(false) }
  val sounds = rememberLearningSounds()
  LaunchedEffect(answerChosen, selectedOption, card?.options) {
    if (answerChosen && selectedOption != null && card?.options?.none { it.id == selectedOption } == true) {
      selectedOption = null
      answerChosen = false
    }
  }
  LaunchedEffect(state.settings.learningSoundsEnabled) {
    if (!state.settings.learningSoundsEnabled) sounds.stop()
  }
  // Re-entering this screen must replay its explanation; recomposition and resume must not.
  var spokenPage by remember { mutableStateOf<String?>(null) }
  val manualSpeech = remember { ManualSpeechPlayback() }
  val latestSpeakAndWait by rememberUpdatedState(onSpeakAndWait)
  val latestSpeechOptions by rememberUpdatedState(state.settings.speechOptions)
  val playback = remember { StudyPlayback(requests = { latestSpeechOptions.requests(it) }) {
    latestSpeakAndWait(it)
  } }
  val speechScope = rememberCoroutineScope()
  val manualSpeak: (SpeechRequest) -> Unit = { request ->
    val current = vm.state.value
    if (current.directionGeneration == generation && current.card?.id == card?.id && current.card?.phase == card?.phase &&
      current.card?.contextVersion == card?.contextVersion &&
      (current.card?.phase != CardPhase.FEEDBACK || feedbackFinished)) {
      manualSpeech.revision++
      sounds.stop()
      manualSpeech.job?.cancel()
      playback.cancel()
      manualSpeech.job = speechScope.launch { playback.sequence { speak(request, immediate = true) } }
    }
  }
  DisposableEffect(card?.id, card?.phase, card?.contextVersion, generation, lifecycle, playback) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
        manualSpeech.job?.cancel()
        playback.cancel()
      }
    }
    lifecycle.addObserver(observer)
    onDispose { lifecycle.removeObserver(observer); manualSpeech.job?.cancel(); playback.cancel(); sounds.stop() }
  }
  val speechPage = card?.takeIf { it.phase in listOf(CardPhase.INTRO, CardPhase.QUESTION, CardPhase.EXPLANATION) }
    ?.let { "$generation/${it.contextVersion}/${it.id}/${it.phase}" }
  LaunchedEffect(speechPage, lifecycle) {
    val entryRevision = manualSpeech.revision
    if (speechPage != null) {
      lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        vm.state.first { !it.busy && !it.loading }
        val activeCard = vm.state.value.card
        if (spokenPage != speechPage) {
          if (activeCard == null || "${vm.state.value.directionGeneration}/${activeCard.contextVersion}/${activeCard.id}/${activeCard.phase}" != speechPage) return@repeatOnLifecycle
          spokenPage = speechPage
          if (manualSpeech.revision != entryRevision) return@repeatOnLifecycle
          playback.sequence {
            speakCard(activeCard) {
              val current = vm.state.value.card
              vm.state.value.directionGeneration == generation && manualSpeech.revision == entryRevision &&
                current?.id == activeCard.id && current.contextVersion == activeCard.contextVersion &&
                current.phase == activeCard.phase
            }
          }
        }
        awaitCancellation()
      }
    }
  }
  LaunchedEffect(card?.id, card?.phase, generation, state.loading, lifecycle) {
    if (card?.phase == CardPhase.FEEDBACK && !state.loading) {
      lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        if (!feedbackFinished) {
          playback.sequence {
            if (!feedbackSoundClaimed) {
              // Claim before suspension so rotation or a background/resume cannot repeat the cue.
              feedbackSoundClaimed = true
              // Visual feedback starts immediately; speech still follows the cue.
              feedbackCueFinished = true
              if (vm.state.value.settings.learningSoundsEnabled) sounds.playAnswer(card.correct == true)
            } else feedbackCueFinished = true
            currentCoroutineContext().ensureActive()
            if (!correctAnswerRevealed) {
              if (card.correct == false) {
                withFrameNanos { }; withFrameNanos { }
                card.options.firstOrNull { it.id == card.selectedOptionId }?.let { speak(card.optionSpeech(it)) }
                currentCoroutineContext().ensureActive()
              }
              correctAnswerRevealed = true
            }
            withFrameNanos { }; withFrameNanos { }
            val active = vm.state.value.card
            if (vm.state.value.directionGeneration != generation || active == null || active.id != card.id || active.phase != CardPhase.FEEDBACK) return@sequence
            val played = speakCard(card) {
              val current = vm.state.value.card
              vm.state.value.directionGeneration == generation && current?.id == card.id &&
                current.contextVersion == card.contextVersion && current.phase == CardPhase.FEEDBACK
            }
            currentCoroutineContext().ensureActive()
            val current = vm.state.value.card
            if (vm.state.value.directionGeneration == generation && current != null && current.id == card.id && current.phase == CardPhase.FEEDBACK) {
              feedbackPlaybackSucceeded = played
              feedbackFinished = true
            }
          }
        }
        awaitCancellation()
      }
    }
  }
  val dailyComplete = state.today?.let { today -> when (card?.kind) {
    StudyKind.NEW -> today.newCompleted >= today.newPlanned
    StudyKind.REVIEW -> today.reviewCompleted >= today.reviewPlanned
    StudyKind.CARRYOVER -> today.carryoverCompleted >= today.carryoverPlanned
    else -> true
  } } ?: true
  LaunchedEffect(card?.id, card?.phase, state.loading) {
    if (!state.loading && card != null && card.phase != CardPhase.FINISHED) seenActiveSession = true
  }
  val completed = card?.phase == CardPhase.FINISHED && card.canAnswer && !card.isSkipped && dailyComplete &&
    state.snapshot?.practice?.takeIf { it.kind == card.kind }?.paused != true && !state.loading && state.error == null
  val submitDraft: (String, String?) -> Unit = { id, option ->
    val current = vm.state.value
    val active = current.card
    if (active != null && card != null && answerChosen && selectedOption == option && !current.busy && !current.loading &&
      current.directionGeneration == generation && active.id == id && id == card.id &&
      active.contextVersion == card.contextVersion && active.phase == CardPhase.QUESTION && active.canAnswer &&
      (option == null || active.options.any { it.id == option })) {
      vm.onAction(StudyAction.Submit(id, option, generation))
    }
  }
  Box(Modifier.fillMaxSize()) {
  StudyContent(state, selectedOption, answerChosen, correctAnswerRevealed, feedbackFinished,
    feedbackPlaybackSucceeded, feedbackCueFinished, onBack, manualSpeak,
    onSelect = { option ->
      val current = vm.state.value
      val active = current.card
      if (active != null && card != null && !current.busy && !current.loading &&
        current.directionGeneration == generation && active.id == card.id && active.contextVersion == card.contextVersion &&
        active.phase == CardPhase.QUESTION && active.canAnswer &&
        (option == null || active.options.any { it.id == option })) {
        selectedOption = option
        answerChosen = true
      }
    },
    onSubmit = submitDraft,
    onExplain = { id -> card?.let { vm.onAction(StudyAction.Explain(id, generation)) } },
    onAdvance = { card?.let { vm.onAction(StudyAction.Advance(it.id, generation)) } },
    onCollection = vm::setCollected,
    onSkip = { id, skipped -> card?.let { vm.setSkipped(id, skipped) } },
    onRetry = {
      val active = vm.state.value.card
      if (active?.phase == CardPhase.QUESTION && answerChosen) submitDraft(active.id, selectedOption)
      else vm.retry()
    }, onReviewMore = vm::reviewMore,
    voiceLoading = state.speechPreparing || playbackPreparing ||
      LocalSpeechPlaybackState.current.phase == SpeechPlaybackPhase.PREPARING)
  StudyCompletionEffects(card?.id, generation,
    card?.phase == CardPhase.FEEDBACK && card.correct == true && feedbackCueFinished && !state.loading,
    sounds, Modifier.fillMaxSize(), soundEnabled = false, small = true)
  StudyCompletionEffects(card?.id, generation, completed && seenActiveSession, sounds, Modifier.fillMaxSize(),
    soundEnabled = state.settings.learningSoundsEnabled)
  }
}

/** Playback handles do not describe a visible state and must not invalidate the study page. */
private class ManualSpeechPlayback {
  var revision = 0L
  var job: Job? = null
}
