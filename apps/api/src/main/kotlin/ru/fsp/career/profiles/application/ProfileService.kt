package ru.fsp.career.profiles.application

import ru.fsp.career.profiles.domain.*

internal class ProfileService(private val profiles: ProfileRepository) {
    suspend fun privacy(subject: String) = profiles.privacy(subject)

    suspend fun updatePrivacy(subject: String, privacy: CandidatePrivacy) =
        profiles.savePrivacy(subject, privacy)

    suspend fun candidate(subject: String) = profiles.candidate(subject)

    suspend fun updateCandidate(subject: String, input: CandidateProfile, verifiedEmail: String): CandidateProfile {
        val profile = validateProfile(input, verifiedEmail)
        return profiles.saveCandidate(subject, profile)
    }

    suspend fun revokeConsent(subject: String) = profiles.revokeConsent(subject)

    suspend fun company(subject: String) = profiles.company(subject)

    suspend fun updateCompany(subject: String, input: Company): Company {
        val company = validateCompany(input)
        return profiles.saveCompany(subject, company)
    }

    suspend fun need(subject: String) = profiles.need(subject)

    suspend fun updateNeed(subject: String, input: Need): Need {
        val need = validateNeed(input)
        return profiles.saveNeed(subject, need)
    }
}
