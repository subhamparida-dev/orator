package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentProfileDao {
    @Query("SELECT * FROM student_profile WHERE id = 1 LIMIT 1")
    fun getProfileFlow(): Flow<StudentProfile?>

    @Query("SELECT * FROM student_profile WHERE id = 1 LIMIT 1")
    suspend fun getProfile(): StudentProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProfile(profile: StudentProfile)
}

@Dao
interface PracticeSessionDao {
    @Query("SELECT * FROM practice_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<PracticeSession>>

    @Query("SELECT * FROM practice_sessions WHERE sessionType = :type ORDER BY timestamp DESC")
    fun getSessionsByType(type: String): Flow<List<PracticeSession>>

    @Query("SELECT * FROM practice_sessions ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentSessions(limit: Int = 10): Flow<List<PracticeSession>>

    @Query("SELECT * FROM practice_sessions WHERE id = :id LIMIT 1")
    suspend fun getSessionById(id: Long): PracticeSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: PracticeSession): Long

    @Query("SELECT COUNT(*) FROM practice_sessions")
    fun getTotalSessionsCount(): Flow<Int>

    @Query("SELECT SUM(durationSeconds) FROM practice_sessions")
    fun getTotalDurationSeconds(): Flow<Int?>

    @Query("DELETE FROM practice_sessions")
    suspend fun clearAll()
}

@Dao
interface WeakWordDao {
    @Query("SELECT * FROM weak_words ORDER BY masteryScore ASC, encounterCount DESC")
    fun getAllWeakWords(): Flow<List<WeakWord>>

    @Query("SELECT * FROM weak_words WHERE word = :word LIMIT 1")
    suspend fun findWord(word: String): WeakWord?

    @Query("SELECT * FROM weak_words WHERE id = :id LIMIT 1")
    suspend fun getWordById(id: Long): WeakWord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(word: WeakWord)

    @Delete
    suspend fun deleteWord(word: WeakWord)
}

@Dao
interface PresentationDao {
    @Query("SELECT * FROM presentations ORDER BY createdAt DESC")
    fun getAllPresentations(): Flow<List<Presentation>>

    @Query("SELECT * FROM presentations WHERE id = :id LIMIT 1")
    suspend fun getPresentationById(id: Long): Presentation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPresentation(presentation: Presentation): Long

    @Update
    suspend fun updatePresentation(presentation: Presentation)

    @Delete
    suspend fun deletePresentation(presentation: Presentation)
}

@Dao
interface InterviewRecordDao {
    @Query("SELECT * FROM interview_records ORDER BY timestamp DESC")
    fun getAllInterviews(): Flow<List<InterviewRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInterview(record: InterviewRecord): Long
}

@Dao
interface MemoryFactDao {
    @Query("SELECT * FROM student_memory_facts WHERE domain = :domain ORDER BY importance DESC, updatedAt DESC LIMIT :limit")
    suspend fun getFactsByDomain(domain: String, limit: Int = 5): List<MemoryFact>

    @Query("SELECT * FROM student_memory_facts ORDER BY updatedAt DESC LIMIT 20")
    suspend fun getRecentFacts(): List<MemoryFact>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFact(fact: MemoryFact)

    @Query("DELETE FROM student_memory_facts")
    suspend fun clearAll()
}
