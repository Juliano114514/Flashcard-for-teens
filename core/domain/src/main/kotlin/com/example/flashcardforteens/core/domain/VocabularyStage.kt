package com.example.flashcardforteens.core.domain

enum class VocabularyStage(val code: Int, val csvGrade: String, val grade: Int, val label: String) {
  GRADE_3_FIRST(0, "3.0", 3, "三年级上册"),
  GRADE_3_SECOND(1, "3.5", 3, "三年级下册"),
  GRADE_4_FIRST(2, "4.0", 4, "四年级上册"),
  GRADE_4_SECOND(3, "4.5", 4, "四年级下册"),
  GRADE_5_FIRST(4, "5.0", 5, "五年级上册"),
  GRADE_5_SECOND(5, "5.5", 5, "五年级下册"),
  GRADE_6_FIRST(6, "6.0", 6, "六年级上册"),
  GRADE_6_SECOND(7, "6.5", 6, "六年级下册");

  companion object {
    fun fromCode(code: Int): VocabularyStage? = entries.firstOrNull { it.code == code }
    fun fromCsvGrade(value: String): VocabularyStage? = entries.firstOrNull { it.csvGrade == value }
  }
}

data class StageProgress(val stage: VocabularyStage = VocabularyStage.GRADE_3_FIRST,
  val learned: Int = 0, val total: Int = 0, val lap: Int = 1)
