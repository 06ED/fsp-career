package ru.fsp.career.profiles.infrastructure

import ru.fsp.career.identity.infrastructure.account
import ru.fsp.career.profiles.domain.*
import ru.fsp.career.shared.infrastructure.Database

internal class ExposedProfileRepository(private val db: Database) : ProfileRepository {
    override suspend fun privacy(subject: String) = db.tx { privacy(account(subject, "candidate")) }
    override suspend fun savePrivacy(subject: String, privacy: CandidatePrivacy) = db.tx { savePrivacy(account(subject, "candidate"), privacy) }
    override suspend fun candidate(subject: String) = db.tx { profile(account(subject, "candidate")) }
    override suspend fun saveCandidate(subject: String, profile: CandidateProfile) = db.tx { saveProfile(account(subject, "candidate"), profile) }
    override suspend fun revokeConsent(subject: String) = db.tx { revokeConsent(account(subject, "candidate")) }
    override suspend fun company(subject: String) = db.tx { company(account(subject, "employer")) }
    override suspend fun saveCompany(subject: String, company: Company) = db.tx { saveCompany(account(subject, "employer"), company) }
    override suspend fun need(subject: String) = db.tx { need(account(subject, "employer")) }
    override suspend fun saveNeed(subject: String, need: Need) = db.tx { saveNeed(account(subject, "employer"), need) }
    override suspend fun saveResumeDraft(subject: String, parsed: ResumeFields) = db.tx { saveResumeDraft(account(subject, "candidate"), parsed) }
    override suspend fun confirmResume(subject: String, reviewed: ConfirmResume) = db.tx { confirmResume(account(subject, "candidate"), reviewed) }
}
