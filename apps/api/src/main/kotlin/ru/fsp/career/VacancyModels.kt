package ru.fsp.career

import kotlinx.serialization.Serializable

// DTOs for vacancies and candidate applications (VacancyRoutes.kt).

/** Employer input for create/edit. The salary range is mandatory (spec 2.2, rule 1). */
@Serializable data class VacancyRequest(
    val title: String,
    val description: String,
    val specialization: String,
    val grade: String,
    val techStack: List<String> = emptyList(),
    val salaryMinRub: Int,
    val salaryMaxRub: Int,
    val workFormat: String,
    val city: String = "",
)

@Serializable data class ApplicationCounts(val total: Int = 0, val sent: Int = 0, val viewed: Int = 0, val accepted: Int = 0, val rejected: Int = 0)

/** Employer's own vacancy with incoming application counters. */
@Serializable data class EmployerVacancy(
    val id: String,
    val title: String,
    val description: String,
    val specialization: String,
    val grade: String,
    val techStack: List<String>,
    val salaryMinRub: Int,
    val salaryMaxRub: Int,
    val workFormat: String,
    val city: String,
    val status: String,
    val createdAt: String,
    val updatedAt: String,
    val applications: ApplicationCounts,
)
@Serializable data class EmployerVacancies(val vacancies: List<EmployerVacancy>)

/**
 * Published vacancy as a candidate sees it: conditions (salary, format, company) are
 * visible before any conversation. [fit] explains how it relates to the candidate's
 * confirmed category; [myApplicationStatus] is null when the candidate has not applied.
 */
@Serializable data class VacancyCard(
    val id: String,
    val title: String,
    val description: String,
    val specialization: String,
    val grade: String,
    val techStack: List<String>,
    val salaryMinRub: Int,
    val salaryMaxRub: Int,
    val workFormat: String,
    val city: String,
    val companyName: String,
    val companyDescription: String,
    val companyWebsite: String,
    val createdAt: String,
    val categoryMatch: Boolean,
    val stackOverlap: List<String>,
    val fit: String,
    val myApplicationId: String? = null,
    val myApplicationStatus: String? = null,
)
@Serializable data class VacancyCards(val vacancies: List<VacancyCard>)

@Serializable data class ApplyRequest(val coverNote: String = "")

/** Candidate's own application with the vacancy conditions it refers to. */
@Serializable data class CandidateApplication(
    val id: String,
    val vacancyId: String,
    val vacancyTitle: String,
    val vacancyStatus: String,
    val companyName: String,
    val specialization: String,
    val grade: String,
    val salaryMinRub: Int,
    val salaryMaxRub: Int,
    val workFormat: String,
    val city: String,
    val coverNote: String,
    val status: String,
    val createdAt: String,
    val updatedAt: String,
)
@Serializable data class CandidateApplications(val applications: List<CandidateApplication>)

/**
 * Applicant as the employer sees it. Contacts are included because the candidate applied
 * to this employer's vacancy (spec 2.2, rule 2). Category = specialization + grade confirmed
 * by the assessment, never the self-declared desired grade.
 */
@Serializable data class Applicant(
    val id: String,
    val vacancyId: String,
    val candidateId: String,
    val fullName: String,
    val specialization: String,
    val assessedGrade: String? = null,
    val score: Int? = null,
    val techStack: List<String>,
    val experience: String,
    val coverNote: String,
    val status: String,
    val createdAt: String,
    val updatedAt: String,
    val contactEmail: String,
    val contactPhone: String,
    val categoryMatch: Boolean,
    val stackOverlap: List<String>,
    val fit: String,
    val taskSubmissions: Int = 0,
    val taskAverageRating: Double? = null,
    val lastTaskActivityAt: String? = null,
)
@Serializable data class Applicants(val vacancy: EmployerVacancy, val applications: List<Applicant>)

@Serializable data class ApplicationStatusRequest(val status: String)
