package com.example.flashcardforteens.feature.study

import com.example.flashcardforteens.core.domain.*

internal val PreviewWord = WordEntry(
  id = "thank-you", headword = "thank you", ipa = "/θæŋk juː/",
  chineseMeaning = "谢谢你", chinesePinyin = "xièxie nǐ", partOfSpeech = "交际用语",
  stage = VocabularyStage.GRADE_3_FIRST,
  examples = listOf(ExampleSentence("Thank you for your help.", "谢谢你 / 因为 / 你的帮助。", "谢谢你的帮助。",
    "Xièxie nǐ de bāngzhù.", listOf(ExampleChunk("Thank you", "谢谢你", "xièxie nǐ"),
      ExampleChunk("for", "因为", "yīnwèi"), ExampleChunk("your help.", "你的帮助。", "nǐ de bāngzhù.")))),
  distractorWordIds = listOf("morning", "welcome", "tomorrow"),
)

internal val PreviewCard = StudyCard("preview", PreviewWord, StudyKind.NEW, CardPhase.QUESTION, 2, 4, false,
  listOf(AnswerOption("thank-you", "谢谢你", "xièxie nǐ"), AnswerOption("morning", "早上好", "zǎoshang hǎo"),
    AnswerOption("welcome", "不客气", "bú kèqi"), AnswerOption("tomorrow", "明天见", "míngtiān jiàn")))

internal val PreviewToday = TodaySummary(date = "2026-10-18", dailyGoal = 10, totalWords = 3200,
  remainingWords = 2960, learnedWords = 240, newPlanned = 10, newCompleted = 6,
  reviewPlanned = 3, reviewCompleted = 1, carryoverPlanned = 2, carryoverCompleted = 0,
  availableNewWords = 50, collectionsAvailable = 12, mistakesAvailable = 3,
  stageProgress = StageProgress(VocabularyStage.GRADE_3_FIRST, learned = 240, total = 372))

internal val PreviewDueToday = PreviewToday.copy(newCompleted = 0, reviewPlanned = 44, reviewCompleted = 0,
  carryoverPlanned = 0, carryoverCompleted = 0, dailyNewCompleted = 0,
  dueReviewPlanned = 44, dueReviewCompleted = 0)

internal val PreviewExtraToday = PreviewDueToday.copy(newPlanned = 20, newCompleted = 12,
  reviewPlanned = 54, reviewCompleted = 46, dailyNewCompleted = 10, dueReviewCompleted = 44)
