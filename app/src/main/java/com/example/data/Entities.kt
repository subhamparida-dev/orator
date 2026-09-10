package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters

@Entity(tableName = "student_profile")
data class StudentProfile(
    @PrimaryKey val id: Int = 1,
    val name: String = "Alex Rivera",
    val college: String = "Engineering & Technology Institute",
    val major: String = "Computer Science",
    val targetRole: String = "Software Development Engineer (SDE)",
    val preferredCorrectionStyle: String = "Gentle & Encouraging", // Gentle, Detailed, Direct
    val englishProficiency: String = "Intermediate",
    val currentStreak: Int = 3,
    val totalSpeakingMinutes: Int = 42,
    val lastActiveDate: Long = System.currentTimeMillis()
)

@Entity(tableName = "practice_sessions")
data class PracticeSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionType: String, // FLUENCY, ENGLISH, PRESENTATION, INTERVIEW
    val title: String,
    val timestamp: Long = System.currentTimeMillis(),
    val durationSeconds: Int = 0,
    val transcript: String = "",
    val wpm: Int = 0,
    val pauseCount: Int = 0,
    val fillerCount: Int = 0,
    val repetitionCount: Int = 0,
    val confidencePre: Int = 3, // 1-5 scale
    val confidencePost: Int = 4, // 1-5 scale
    val overallScore: Int = 75, // 0-100
    val fluencyScore: Int = 75,
    val englishScore: Int = 75,
    val presentationScore: Int = 75,
    val technicalScore: Int = 75,
    val feedback: String = "",
    val strengths: String = "", // Comma-separated or JSON
    val weaknesses: String = "",
    val nextAction: String = "",
    val providerUsed: String = "Gemini",
    val modelUsed: String = "gemini-3.5-flash"
)

@Entity(tableName = "weak_words")
data class WeakWord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val word: String,
    val phonetic: String = "",
    val category: String = "FLUENCY", // FLUENCY, PRONUNCIATION, VOCABULARY
    val contextSentence: String = "",
    val encounterCount: Int = 1,
    val masteryScore: Int = 30, // 0-100
    val lastPracticed: Long = System.currentTimeMillis()
)

@Entity(tableName = "presentations")
data class Presentation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val topic: String,
    val targetAudience: String = "Academic & Technical Committee",
    val totalSlides: Int = 4,
    val slidesJson: String = "", // Serialized slide data
    val lastPracticedScore: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "interview_records")
data class InterviewRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String, // HR, TECHNICAL, DSA, CORE_CS, PROJECT, BEHAVIORAL, SYSTEM_DESIGN
    val company: String = "General Tech",
    val role: String = "Software Engineer",
    val round: String = "Technical",
    val difficulty: String = "Medium",
    val questionCount: Int = 0,
    val averageScore: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val notes: String = ""
)

@Entity(tableName = "student_memory_facts")
data class MemoryFact(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val domain: String, // SPEAKING, ENGLISH, PRESENTATION, INTERVIEW, LEARNING
    val factKey: String,
    val factValue: String,
    val importance: Int = 1, // 1-5
    val updatedAt: Long = System.currentTimeMillis()
)
