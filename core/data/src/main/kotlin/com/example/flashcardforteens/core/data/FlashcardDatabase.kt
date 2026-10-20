package com.example.flashcardforteens.core.data

import androidx.room.Dao
import androidx.room.ColumnInfo
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "words", indices = [Index("sortOrder"), Index("stage")])
internal data class WordEntity(@PrimaryKey val id: String, val headword: String, val ipa: String,
  val chineseMeaning: String, val chinesePinyin: String, val partOfSpeech: String,
  val stage: Int, val examplesJson: String, val distractorsJson: String, val sortOrder: Int,
  val sourcesJson: String, val pronunciationNote: String, val sourceNote: String,
  val presetSourceId: String = "")

internal data class WordStudyRow(val id: String, val stage: Int)
internal data class WordlistRow(val id: String, val headword: String, val ipa: String,
  val chineseMeaning: String, val chinesePinyin: String, val partOfSpeech: String, val stage: Int,
  val firstEncounterShown: Boolean, val firstPassedDay: Long?, val correctRounds: Int,
  val targetRounds: Int, val isCollected: Boolean, val isSkipped: Boolean, val mistakePending: Boolean)

@Entity(tableName = "settings")
internal data class SettingsEntity(@PrimaryKey val id: Int = 1, val dailyWords: Int = 10,
  val rounds: Int = 4, val reviewDaysJson: String = "[1,3,7]", val welcomed: Boolean = false,
  val displayName: String = "", val avatarId: String = "1", val themeMode: String = "SYSTEM",
  val speechModesJson: String = "{}", val explanationMask: Int = 5,
  val speechPreloadWords: Int = 5, val speechCacheMiB: Int = 256,
  val homeShuffleQueueSize: Int = 5, val waitForCorrectAnswerAudio: Boolean = false,
  val reviewBatchSize: Int = 10,
  @ColumnInfo(defaultValue = "1") val learningSoundsEnabled: Boolean = true)

@Entity(tableName = "app_state")
internal data class AppStateEntity(@PrimaryKey val id: Int = 1, val seeded: Boolean = false,
  val day: Long? = null, val selectedKind: String? = null, val currentCardId: String? = null,
  val revision: Long = 0, val presetCsvVersion: String = "", val selectedStage: Int? = null,
  val growthDays: Long = 0, val learnedCompletions: Long = 0, val clearedMistakes: Long = 0,
  val contextVersion: Long = 0)

@Entity(tableName = "daily_plans")
internal data class DailyPlanEntity(@PrimaryKey val day: Long, val dailyGoal: Int,
  val loginRewarded: Boolean = false, val learnRewarded: Boolean = false)

@Entity(tableName = "word_progress")
internal data class WordProgressEntity(@PrimaryKey val wordId: String,
  val firstEncounterShown: Boolean = false, val firstPassedDay: Long? = null,
  val activeCycleId: String? = null, val isCollected: Boolean = false,
  val isSkipped: Boolean = false, val mistakePending: Boolean = false)

@Entity(tableName = "stage_states")
internal data class StageStateEntity(@PrimaryKey val stage: Int, val lap: Int = 1)

@Entity(tableName = "stage_completions", primaryKeys = ["stage", "lap", "wordId"])
internal data class StageCompletionEntity(val stage: Int, val lap: Int, val wordId: String, val completedDay: Long)

@Entity(tableName = "stage_daily_plans", primaryKeys = ["day", "stage"])
internal data class StageDailyPlanEntity(val day: Long, val stage: Int, val dailyGoal: Int)

@Entity(tableName = "learning_cycles", indices = [Index("wordId"), Index("status")])
internal data class CycleEntity(@PrimaryKey val id: String, val wordId: String,
  val targetRounds: Int, val reviewDaysJson: String, val correctRounds: Int = 0,
  val status: String = "ACTIVE", val startedDay: Long, val passedDay: Long? = null,
  val scope: String = "NORMAL", val originStage: Int? = null, val originLap: Int? = null)

@Entity(tableName = "daily_items", indices = [Index(value = ["day", "kind"]), Index("cycleId")])
internal data class DailyItemEntity(val day: Long, val wordId: String, val kind: String,
  val cycleId: String, val batch: Int, val queueOrder: Long, val completed: Boolean = false,
  val reviewRecall: Boolean = false, @PrimaryKey val id: String = "$day:$kind:$cycleId:$wordId",
  val originStage: Int? = null, val originLap: Int? = null)

@Entity(tableName = "practice_sessions", indices = [Index(value = ["kind", "status"])])
internal data class PracticeSessionEntity(@PrimaryKey val id: String, val kind: String,
  val status: String = "ACTIVE", val lap: Int = 1, val createdAt: Long)

@Entity(tableName = "practice_items", primaryKeys = ["sessionId", "wordId"], indices = [Index("cycleId")])
internal data class PracticeItemEntity(val sessionId: String, val wordId: String,
  val cycleId: String, val queueOrder: Long, val completed: Boolean = false)

@Entity(tableName = "review_nodes", indices = [Index(value = ["status", "dueDay"]), Index("cycleId"), Index("wordId")])
internal data class ReviewNodeEntity(@PrimaryKey val id: String, val wordId: String,
  val cycleId: String, val dueDay: Long, val status: String = "PENDING")

@Entity(tableName = "study_cards", indices = [Index(value = ["day", "kind", "phase"]), Index("wordId"), Index("sessionId")])
internal data class CardEntity(@PrimaryKey val id: String, val day: Long, val wordId: String,
  val cycleId: String, val kind: String, val phase: String, val round: Int,
  val targetRounds: Int, val reviewRecall: Boolean, val optionsJson: String,
  val selectedOptionId: String? = null, val correct: Boolean? = null, val createdAt: Long,
  val itemId: String? = null, val sessionId: String? = null, val correctWordId: String = wordId)

@Dao
internal interface FlashcardDao {
  @Query("""
    SELECT w.id, w.headword, w.ipa, w.chineseMeaning, w.chinesePinyin, w.partOfSpeech, w.stage,
      COALESCE(p.firstEncounterShown, 0) AS firstEncounterShown, p.firstPassedDay,
      COALESCE(c.correctRounds, 0) AS correctRounds, COALESCE(c.targetRounds, 0) AS targetRounds,
      COALESCE(p.isCollected, 0) AS isCollected, COALESCE(p.isSkipped, 0) AS isSkipped,
      COALESCE(p.mistakePending, 0) AS mistakePending
    FROM words w
    LEFT JOIN word_progress p ON p.wordId = w.id
    LEFT JOIN learning_cycles c ON c.id = p.activeCycleId AND c.status = 'ACTIVE'
    ORDER BY w.stage, w.sortOrder, w.id
  """)
  fun observeWordlist(): Flow<List<WordlistRow>>
  @Query("SELECT * FROM word_progress WHERE wordId = :wordId")
  fun observeProgress(wordId: String): Flow<WordProgressEntity?>
  @Query("SELECT * FROM words ORDER BY stage, sortOrder, id") suspend fun words(): List<WordEntity>
  @Query("SELECT id, stage FROM words ORDER BY stage, sortOrder, id") suspend fun studyWords(): List<WordStudyRow>
  @Query("UPDATE words SET sortOrder = :rank WHERE id = :id AND sortOrder != :rank")
  suspend fun updateWordOrder(id: String, rank: Int)
  @Query("UPDATE words SET stage = :stage WHERE id IN (:ids) AND stage != :stage")
  suspend fun updateDefaultStage(ids: List<String>, stage: Int): Int
  @Query("SELECT * FROM words WHERE id = :id") suspend fun word(id: String): WordEntity?
  @Query("SELECT COUNT(*) FROM words") suspend fun wordCount(): Int
  @Upsert suspend fun putWords(values: List<WordEntity>)
  @Query("SELECT * FROM study_cards WHERE phase != 'FINISHED'") suspend fun unfinishedCardsAll(): List<CardEntity>

  @Query("SELECT * FROM settings WHERE id = 1") suspend fun settings(): SettingsEntity?
  @Query("SELECT * FROM settings WHERE id = 1") fun observeSettings(): Flow<SettingsEntity?>
  @Upsert suspend fun putSettings(value: SettingsEntity)
  @Query("SELECT * FROM app_state WHERE id = 1") suspend fun appState(): AppStateEntity?
  @Query("SELECT revision FROM app_state WHERE id = 1") fun observeRevision(): Flow<Long?>
  @Upsert suspend fun putAppState(value: AppStateEntity)

  @Query("SELECT * FROM daily_plans WHERE day = :day") suspend fun plan(day: Long): DailyPlanEntity?
  @Upsert suspend fun putPlan(value: DailyPlanEntity)
  @Query("SELECT * FROM word_progress ") suspend fun progress(): List<WordProgressEntity>
  @Query("SELECT * FROM word_progress WHERE wordId = :wordId") suspend fun progress(wordId: String): WordProgressEntity?
  @Query("SELECT COUNT(*) FROM word_progress WHERE firstPassedDay IS NOT NULL") suspend fun learnedCount(): Int
  @Query("SELECT COUNT(*) FROM word_progress WHERE firstEncounterShown = 1") suspend fun startedCount(): Int
  @Upsert suspend fun putProgress(value: WordProgressEntity)
  @Query("SELECT * FROM learning_cycles WHERE id = :id") suspend fun cycle(id: String): CycleEntity?
  @Query("SELECT * FROM learning_cycles WHERE status = 'ACTIVE' ORDER BY startedDay, id") suspend fun activeCycles(): List<CycleEntity>
  @Query("""SELECT * FROM learning_cycles WHERE rowid IN
    (SELECT MAX(rowid) FROM learning_cycles
      WHERE scope = 'NORMAL' AND status = 'COMPLETED' GROUP BY wordId)""")
  suspend fun learnedReviewCycles(): List<CycleEntity>
  @Upsert suspend fun putCycle(value: CycleEntity)
  @Query("SELECT * FROM daily_items WHERE day = :day ORDER BY kind, batch, queueOrder") suspend fun dailyItems(day: Long): List<DailyItemEntity>
  @Query("SELECT * FROM daily_items WHERE day = :day AND wordId = :wordId ORDER BY queueOrder LIMIT 1") suspend fun dailyItem(day: Long, wordId: String): DailyItemEntity?
  @Query("SELECT * FROM daily_items WHERE id = :id") suspend fun dailyItemById(id: String): DailyItemEntity?
  @Query("SELECT * FROM daily_items WHERE cycleId = :cycleId ORDER BY day DESC, queueOrder DESC LIMIT 1")
  suspend fun latestDailyItemForCycle(cycleId: String): DailyItemEntity?
  @Upsert suspend fun putDailyItem(value: DailyItemEntity)

  @Query("SELECT * FROM stage_states WHERE stage = :stage") suspend fun stageState(stage: Int): StageStateEntity?
  @Query("SELECT * FROM stage_states  ORDER BY stage") suspend fun stageStates(): List<StageStateEntity>
  @Upsert suspend fun putStageState(value: StageStateEntity)
  @Query("SELECT * FROM stage_completions WHERE stage = :stage AND lap = :lap ORDER BY completedDay, wordId")
  suspend fun stageCompletions(stage: Int, lap: Int): List<StageCompletionEntity>
  @Upsert suspend fun putStageCompletion(value: StageCompletionEntity)
  @Query("SELECT * FROM stage_daily_plans WHERE day = :day AND stage = :stage") suspend fun stagePlan(day: Long, stage: Int): StageDailyPlanEntity?
  @Upsert suspend fun putStagePlan(value: StageDailyPlanEntity)

  @Query("SELECT * FROM practice_sessions WHERE id = :id") suspend fun practiceSession(id: String): PracticeSessionEntity?
  @Query("SELECT * FROM practice_sessions WHERE kind = :kind AND status = 'ACTIVE' ORDER BY createdAt DESC, id DESC LIMIT 1")
  suspend fun activePracticeSession(kind: String): PracticeSessionEntity?
  @Query("SELECT * FROM practice_sessions WHERE kind = :kind ORDER BY lap, createdAt, id")
  suspend fun practiceSessions(kind: String): List<PracticeSessionEntity>
  @Upsert suspend fun putPracticeSession(value: PracticeSessionEntity)
  @Query("SELECT * FROM practice_items WHERE sessionId = :sessionId ORDER BY queueOrder, wordId")
  suspend fun practiceItems(sessionId: String): List<PracticeItemEntity>
  @Query("SELECT * FROM practice_items WHERE sessionId = :sessionId AND wordId = :wordId")
  suspend fun practiceItem(sessionId: String, wordId: String): PracticeItemEntity?
  @Upsert suspend fun putPracticeItem(value: PracticeItemEntity)

  @Query("SELECT * FROM review_nodes WHERE status = 'PENDING' AND dueDay <= :day ORDER BY dueDay, id") suspend fun dueNodes(day: Long): List<ReviewNodeEntity>
  @Query("SELECT * FROM review_nodes WHERE cycleId = :cycleId AND status = 'PENDING'") suspend fun pendingNodes(cycleId: String): List<ReviewNodeEntity>
  @Query("""
    SELECT MIN(n.dueDay) FROM review_nodes n
    LEFT JOIN word_progress p ON p.wordId = n.wordId
    WHERE n.status = 'PENDING' AND n.dueDay > :day AND COALESCE(p.isSkipped, 0) = 0
  """) suspend fun nextReviewDay(day: Long): Long?
  @Upsert suspend fun putReviewNodes(values: List<ReviewNodeEntity>)
  @Query("SELECT * FROM study_cards WHERE id = :id") suspend fun card(id: String): CardEntity?
  @Query("SELECT * FROM study_cards WHERE day = :day AND kind = :kind AND phase != 'FINISHED' ORDER BY createdAt LIMIT 1") suspend fun pendingCard(day: Long, kind: String): CardEntity?
  @Query("SELECT * FROM study_cards WHERE day = :day AND kind = :kind AND phase = 'FINISHED' ORDER BY createdAt DESC LIMIT 1") suspend fun finishedCard(day: Long, kind: String): CardEntity?
  @Query("SELECT * FROM study_cards WHERE day != :day AND phase != 'FINISHED'") suspend fun oldCards(day: Long): List<CardEntity>
  @Query("SELECT * FROM study_cards WHERE phase != 'FINISHED'") suspend fun unfinishedCards(): List<CardEntity>
  @Query("SELECT * FROM study_cards WHERE sessionId = :sessionId AND phase != 'FINISHED' ORDER BY createdAt, id LIMIT 1")
  suspend fun pendingPracticeCard(sessionId: String): CardEntity?
  @Query("SELECT * FROM study_cards WHERE sessionId = :sessionId ORDER BY createdAt DESC, id DESC LIMIT 1")
  suspend fun latestPracticeCard(sessionId: String): CardEntity?
  @Query("SELECT DISTINCT wordId FROM study_cards WHERE day = :day AND kind = :kind AND sessionId IS NULL AND optionsJson = '[]' AND phase = 'FINISHED'")
  suspend fun viewOnlyDailyWords(day: Long, kind: String): List<String>
  @Query("SELECT DISTINCT wordId FROM study_cards WHERE sessionId = :sessionId AND optionsJson = '[]' AND phase = 'FINISHED'")
  suspend fun viewOnlyPracticeWords(sessionId: String): List<String>
  @Upsert suspend fun putCard(value: CardEntity)


}

@Database(entities = [WordEntity::class, SettingsEntity::class, AppStateEntity::class,
  DailyPlanEntity::class, WordProgressEntity::class, CycleEntity::class, DailyItemEntity::class,
  ReviewNodeEntity::class, CardEntity::class, StageStateEntity::class, StageCompletionEntity::class,
  StageDailyPlanEntity::class, PracticeSessionEntity::class, PracticeItemEntity::class],
  version = 2, exportSchema = true)
internal abstract class FlashcardDatabase : RoomDatabase() {
  abstract fun flashcards(): FlashcardDao
}
