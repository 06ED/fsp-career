package ru.fsp.career.profiles.domain

import ru.fsp.career.shared.domain.*

internal fun validateProfile(profile: CandidateProfile, trustedEmail: String): CandidateProfile {
    if (!profile.processingConsent)
        bad("Grant processingConsent before storing a profile; DELETE /api/candidates/me/consent to revoke")

    grade(profile.desiredGrade)
    validateCareerFields(
        profile.experience,
        profile.techStack,
        profile.softSkills,
        profile.targetRoles,
        profile.cvHistory
    )

    if (profile.email.isNotBlank() && profile.email.trim() != trustedEmail)
        bad("Email must match verified identity email")
    if (trustedEmail.isNotBlank() && !Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(trustedEmail))
        bad("Invalid verified email")
    if (profile.fullName.length > 200 ||
        profile.email.length > 320 ||
        profile.phone.length > 50 ||
        profile.specialization.length > 100 ||
        profile.industry.length > 100
    ) bad("Profile field too long")

    return profile.copy(
        fullName = profile.fullName.trim(),
        email = trustedEmail,
        phone = profile.phone.trim(),
        specialization = profile.specialization.trim(),
        industry = profile.industry.trim(),
        experience = profile.experience.trim()
    )
}

internal fun validateNeed(need: Need): Need {
    grade(need.minGrade)
    val spec = requireText(need.specialization, "Specialization", 100)
    if (need.industry.length > 100 || need.description.length > 2000) bad("Need field too long")
    return need.copy(
        specialization = spec,
        industry = need.industry.trim(),
        description = need.description.trim(),
        techStack = validateNeedStack(need.techStack)
    )
}
