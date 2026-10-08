package ru.fsp.career.profiles.infrastructure

import org.jetbrains.exposed.sql.ResultRow
import ru.fsp.career.profiles.domain.*

internal fun companyFrom(row: ResultRow) = Company(
    name = row[EmployerCompanies.name], description = row[EmployerCompanies.description],
    website = row[EmployerCompanies.website], industry = row[EmployerCompanies.industry],
    contactEmail = row[EmployerCompanies.contactEmail], contactPhone = row[EmployerCompanies.contactPhone],
    contactTelegram = row[EmployerCompanies.contactTelegram], contactNote = row[EmployerCompanies.contactNote],
)
internal fun needFrom(row: ResultRow) = Need(
    specialization = row[EmployerNeeds.specialization], industry = row[EmployerNeeds.industry],
    minGrade = row[EmployerNeeds.minGrade], description = row[EmployerNeeds.description],
    techStack = row[EmployerNeeds.techStack],
)
