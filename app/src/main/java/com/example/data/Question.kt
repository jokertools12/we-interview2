package com.example.data

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Question(
    val id: String,
    val text: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String,
    val category: String, // "Fiber", "CCNA", "Power"
    val englishText: String? = null,
    val englishOptions: List<String>? = null,
    val englishExplanation: String? = null
) {
    fun getLocalizedText(isEnglish: Boolean): String {
        return if (isEnglish) (englishText ?: text) else text
    }
    fun getLocalizedOptions(isEnglish: Boolean): List<String> {
        return if (isEnglish) (englishOptions ?: options) else options
    }
    fun getLocalizedExplanation(isEnglish: Boolean): String {
        return if (isEnglish) (englishExplanation ?: explanation) else explanation
    }
}

@JsonClass(generateAdapter = true)
data class ExamQuestion(
    val question: Question,
    val selectedIndex: Int? = null,
    val isAnswered: Boolean = false
)

@JsonClass(generateAdapter = true)
data class Exam(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val category: String, // "Fiber", "CCNA", "Power", "Mixed"
    val questions: List<ExamQuestion>,
    val score: Int = 0,
    val totalQuestions: Int = questions.size,
    val timestamp: Long = System.currentTimeMillis(),
    val isCompleted: Boolean = false,
    val generalFeedback: String = ""
)

data class InterviewEvaluation(
    val score: Int,
    val modelAnswerAr: String,
    val modelAnswerEn: String,
    val analysisAr: String,
    val analysisEn: String,
    val tipsAr: String,
    val tipsEn: String
)

