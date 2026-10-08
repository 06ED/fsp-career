package ru.fsp.career

import io.ktor.server.application.Application
import ru.fsp.career.achievements.achievementDependencies
import ru.fsp.career.assessment.assessmentDependencies
import ru.fsp.career.identity.identityDependencies
import ru.fsp.career.matching.matchingDependencies
import ru.fsp.career.profiles.profileDependencies
import ru.fsp.career.recruitment.recruitmentDependencies

internal fun Application.careerDependencies(issuer: String) {
    platformDependencies(issuer)
    identityDependencies()
    profileDependencies()
    assessmentDependencies()
    matchingDependencies()
    recruitmentDependencies()
    achievementDependencies()
}
