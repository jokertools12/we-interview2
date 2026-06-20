package com.example.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class InterviewRepository(private val db: AppDatabase) {

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val examQuestionListType = Types.newParameterizedType(List::class.java, ExamQuestion::class.java)
    private val adapter = moshi.adapter<List<ExamQuestion>>(examQuestionListType)

    val savedExams: Flow<List<Exam>> = db.examDao().getAllSavedExams().map { list ->
        list.map { entity ->
            Exam(
                id = entity.id,
                title = entity.title,
                category = entity.category,
                questions = deserializeQuestions(entity.questionsJson),
                score = entity.score,
                totalQuestions = entity.totalQuestions,
                timestamp = entity.timestamp,
                isCompleted = entity.isCompleted,
                generalFeedback = entity.generalFeedback
            )
        }
    }

    val repetitionStates: Flow<List<RepetitionStateEntity>> = db.repetitionDao().getAllRepetitionStates()

    suspend fun saveExam(exam: Exam) {
        val json = serializeQuestions(exam.questions)
        val entity = SavedExamEntity(
            id = exam.id,
            title = exam.title,
            category = exam.category,
            score = exam.score,
            totalQuestions = exam.totalQuestions,
            timestamp = exam.timestamp,
            isCompleted = exam.isCompleted,
            questionsJson = json,
            generalFeedback = exam.generalFeedback
        )
        db.examDao().insertExam(entity)
    }

    suspend fun deleteExam(examId: String) {
        db.examDao().deleteExamById(examId)
    }

    suspend fun getRepetitionState(questionId: String): RepetitionStateEntity? {
        return db.repetitionDao().getStateByQuestionId(questionId)
    }

    suspend fun saveRepetitionState(state: RepetitionStateEntity) {
        db.repetitionDao().insertRepetitionState(state)
    }

    private fun serializeQuestions(questions: List<ExamQuestion>): String {
        return try {
            adapter.toJson(questions) ?: "[]"
        } catch (e: Exception) {
            "[]"
        }
    }

    private fun deserializeQuestions(json: String): List<ExamQuestion> {
        return try {
            adapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
