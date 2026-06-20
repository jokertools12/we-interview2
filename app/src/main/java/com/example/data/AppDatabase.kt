package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "saved_exams")
data class SavedExamEntity(
    @PrimaryKey val id: String,
    val title: String,
    val category: String,
    val score: Int,
    val totalQuestions: Int,
    val timestamp: Long,
    val isCompleted: Boolean,
    val questionsJson: String,
    val generalFeedback: String
)

@Entity(tableName = "repetition_states")
data class RepetitionStateEntity(
    @PrimaryKey val questionId: String,
    val easeFactor: Double,
    val repetitions: Int,
    val intervalDays: Int,
    val nextReviewTimestamp: Long,
    val lastReviewedTimestamp: Long,
    val category: String,
    val answeredCorrectlyLastTime: Boolean
)

@Dao
interface ExamDao {
    @Query("SELECT * FROM saved_exams ORDER BY timestamp DESC")
    fun getAllSavedExams(): Flow<List<SavedExamEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExam(exam: SavedExamEntity)

    @Query("DELETE FROM saved_exams WHERE id = :id")
    suspend fun deleteExamById(id: String)
}

@Dao
interface RepetitionDao {
    @Query("SELECT * FROM repetition_states")
    fun getAllRepetitionStates(): Flow<List<RepetitionStateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRepetitionState(state: RepetitionStateEntity)

    @Query("SELECT * FROM repetition_states WHERE questionId = :questionId LIMIT 1")
    suspend fun getStateByQuestionId(questionId: String): RepetitionStateEntity?
}

@Database(entities = [SavedExamEntity::class, RepetitionStateEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun examDao(): ExamDao
    abstract fun repetitionDao(): RepetitionDao
}
