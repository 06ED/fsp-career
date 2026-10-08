package ru.fsp.career.achievements.infrastructure

import org.jetbrains.exposed.sql.ResultRow
import ru.fsp.career.achievements.domain.VerifiedAchievement
import ru.fsp.career.matching.domain.MatchingEngine

internal fun verifiedAchievement(row: ResultRow) = VerifiedAchievement(
    title = row[Achievements.title], issuer = row[Achievements.issuer], points = row[Achievements.points],
    source = row[Achievements.sourceName], eventName = row[Achievements.eventName].ifBlank { null },
    discipline = row[Achievements.discipline].ifBlank { null }, eventDate = row[Achievements.eventDate]?.toString(),
    level = row[Achievements.level].ifBlank { null }, levelLabel = MatchingEngine.levelLabel(row[Achievements.level]),
    place = row[Achievements.place], result = row[Achievements.resultRole].ifBlank { null },
    resultLabel = MatchingEngine.resultLabel(row[Achievements.resultRole]),
)
