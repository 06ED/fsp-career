package ru.fsp.career.profiles.domain

internal interface ProfileRepository {
    suspend fun privacy(subject: String): CandidatePrivacy
    suspend fun savePrivacy(subject: String, privacy: CandidatePrivacy): CandidatePrivacy
    suspend fun candidate(subject: String): CandidateProfile
    suspend fun saveCandidate(subject: String, profile: CandidateProfile): CandidateProfile
    suspend fun revokeConsent(subject: String): CandidateProfile
    suspend fun company(subject: String): Company
    suspend fun saveCompany(subject: String, company: Company): Company
    suspend fun need(subject: String): Need
    suspend fun saveNeed(subject: String, need: Need): Need
    suspend fun saveResumeDraft(subject: String, parsed: ResumeFields): ResumeDraft
    suspend fun confirmResume(subject: String, reviewed: ConfirmResume): CandidateProfile
}
