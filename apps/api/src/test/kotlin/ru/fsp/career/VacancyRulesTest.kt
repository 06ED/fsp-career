package ru.fsp.career

import ru.fsp.career.profiles.domain.*
import ru.fsp.career.recruitment.domain.*
import ru.fsp.career.shared.domain.*
import ru.fsp.career.shared.http.status

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VacancyRulesTest {
    private val vacancy = VacancyRequest(
        title = "  Backend-разработчик  ", description = "Платёжный шлюз", specialization = " Backend ",
        grade = "middle", techStack = listOf("Kotlin", " kotlin ", "PostgreSQL"),
        salaryMinRub = 200_000, salaryMaxRub = 280_000, workFormat = "Hybrid", city = " Москва ",
    )

    private fun status(block: () -> Unit) = assertFailsWith<DomainError> { block() }.status

    @Test fun vacancyIsNormalized() {
        val normalized = validateVacancy(vacancy)
        assertEquals("Backend-разработчик", normalized.title)
        assertEquals("backend", normalized.specialization)
        assertEquals("hybrid", normalized.workFormat)
        assertEquals("Москва", normalized.city)
        assertEquals(listOf("Kotlin", "PostgreSQL"), normalized.techStack)
    }

    @Test fun vacancySalaryRangeIsMandatoryAndConsistent() {
        assertFailsWith<kotlinx.serialization.MissingFieldException> {
            Json.decodeFromString<VacancyRequest>(
                """{"title":"t","description":"d","specialization":"backend","grade":"junior","workFormat":"remote","salaryMinRub":100}"""
            )
        }
        assertEquals(HttpStatusCode.BadRequest, status { validateVacancy(vacancy.copy(salaryMinRub = 0)) })
        assertEquals(HttpStatusCode.BadRequest, status { validateVacancy(vacancy.copy(salaryMaxRub = 199_999)) })
        assertEquals(HttpStatusCode.BadRequest, status { validateVacancy(vacancy.copy(salaryMaxRub = VACANCY_MAX_SALARY_RUB + 1)) })
        assertEquals(200_000, validateVacancy(vacancy.copy(salaryMaxRub = 200_000)).salaryMaxRub)
    }

    @Test fun vacancyDictionariesAreEnforced() {
        assertEquals(HttpStatusCode.BadRequest, status { validateVacancy(vacancy.copy(specialization = "devops")) })
        assertEquals(HttpStatusCode.BadRequest, status { validateVacancy(vacancy.copy(grade = "lead")) })
        assertEquals(HttpStatusCode.BadRequest, status { validateVacancy(vacancy.copy(workFormat = "anywhere")) })
        assertEquals(HttpStatusCode.BadRequest, status { validateVacancy(vacancy.copy(title = "  ")) })
        assertEquals(HttpStatusCode.BadRequest, status { validateVacancy(vacancy.copy(techStack = List(21) { "s$it" })) })
        assertEquals(HttpStatusCode.BadRequest, status { validateVacancy(vacancy.copy(techStack = listOf(""))) })
    }

    @Test fun cityRequiredUnlessRemote() {
        assertEquals(HttpStatusCode.BadRequest, status { validateVacancy(vacancy.copy(city = "")) })
        assertEquals("", validateVacancy(vacancy.copy(workFormat = "remote", city = "")).city)
    }

    @Test fun applicationTransitions() {
        assertTrue(applicationTransitionAllowed("sent", "viewed"))
        assertTrue(applicationTransitionAllowed("sent", "accepted"))
        assertTrue(applicationTransitionAllowed("viewed", "accepted"))
        assertTrue(applicationTransitionAllowed("viewed", "rejected"))
        assertFalse(applicationTransitionAllowed("viewed", "viewed"))
        assertFalse(applicationTransitionAllowed("accepted", "rejected"))
        assertFalse(applicationTransitionAllowed("rejected", "accepted"))
        assertFalse(applicationTransitionAllowed("sent", "sent"))
        assertFalse(applicationTransitionAllowed("sent", "withdrawn"))
        assertTrue(applicationWithdrawable("sent"))
        assertTrue(applicationWithdrawable("viewed"))
        assertFalse(applicationWithdrawable("accepted"))
        assertFalse(applicationWithdrawable("rejected"))
    }

    @Test fun vacancyTransitions() {
        assertTrue(vacancyTransitionAllowed("open", "closed"))
        assertTrue(vacancyTransitionAllowed("closed", "open"))
        assertFalse(vacancyTransitionAllowed("open", "open"))
        assertFalse(vacancyTransitionAllowed("closed", "closed"))
    }

    @Test fun applicationPreconditions() {
        val ready = CandidateProfile(fullName = "Анна", email = "anna@example.org", processingConsent = true,
            specialization = "backend", grade = "middle", score = 70)
        requireApplicationReady(ready)
        requireApplicationReady(ready.copy(email = "", phone = "+70000000000"))
        assertEquals(HttpStatusCode.Forbidden, status { requireApplicationReady(ready.copy(processingConsent = false)) })
        assertEquals(HttpStatusCode.BadRequest, status { requireApplicationReady(ready.copy(fullName = " ")) })
        assertEquals(HttpStatusCode.BadRequest, status { requireApplicationReady(ready.copy(specialization = "")) })
        assertEquals(HttpStatusCode.BadRequest, status { requireApplicationReady(ready.copy(email = "", phone = "")) })
        assertEquals(HttpStatusCode.Conflict, status { requireApplicationReady(ready.copy(grade = null, score = null)) })
    }

    @Test fun vacancyFilterParsesAndRejectsInvalidValues() {
        val params = mapOf("specialization" to "Frontend", "grade" to "SENIOR", "techStack" to "React, ,TypeScript",
            "salaryFrom" to "150000", "workFormat" to "remote", "q" to "  финтех ")
        val filter = vacancyFilter { params[it] }
        assertEquals(VacancyFilter("frontend", "senior", listOf("React", "TypeScript"), 150000, "remote", "финтех"), filter)
        assertEquals(VacancyFilter(), vacancyFilter { "" })
        assertEquals(HttpStatusCode.BadRequest, status { vacancyFilter { if (it == "salaryFrom") "-1" else null } })
        assertEquals(HttpStatusCode.BadRequest, status { vacancyFilter { if (it == "salaryFrom") "много" else null } })
        assertEquals(HttpStatusCode.BadRequest, status { vacancyFilter { if (it == "workFormat") "moon" else null } })
        assertEquals(HttpStatusCode.BadRequest, status { vacancyFilter { if (it == "specialization") "devops" else null } })
        assertEquals(HttpStatusCode.BadRequest, status { vacancyFilter { if (it == "techStack") (1..11).joinToString(",") else null } })
        assertEquals(HttpStatusCode.BadRequest, status { vacancyFilter { if (it == "q") "x".repeat(101) else null } })
    }

    @Test fun textFilterRequiresAllSkillsAndMatchesAnyField() {
        val stack = listOf("Kotlin", "PostgreSQL")
        val fields = listOf("Backend-разработчик", "Платёжный шлюз", "ООО Финтех", "Москва")
        assertTrue(VacancyFilter(stack = listOf("kotlin", "POSTGRESQL")).acceptsText(stack, fields))
        assertFalse(VacancyFilter(stack = listOf("kotlin", "Go")).acceptsText(stack, fields))
        assertTrue(VacancyFilter(query = "финтех").acceptsText(stack, fields))
        assertTrue(VacancyFilter(query = "postgres").acceptsText(stack, fields))
        assertFalse(VacancyFilter(query = "Казань").acceptsText(stack, fields))
    }

    @Test fun fitExplainsConfirmedCategoryNotSelfReportedSkills() {
        val match = vacancyFit("backend", "senior", 85, listOf("kotlin"), "backend", "middle", listOf("Kotlin", "Go"), forCandidate = true)
        assertTrue(match.categoryMatch)
        assertEquals(listOf("Kotlin"), match.overlap)
        assertTrue(match.text.contains("соответствует требованию Middle"), match.text)
        assertTrue(match.text.contains("1 из 2"), match.text)

        val lower = vacancyFit("backend", "junior", 40, emptyList(), "backend", "middle", emptyList(), forCandidate = false)
        assertFalse(lower.categoryMatch)
        assertTrue(lower.text.contains("требуется Middle"), lower.text)
        assertFalse(lower.text.contains("стека"), lower.text)

        val otherTrack = vacancyFit("frontend", "senior", 90, listOf("Kotlin", "Go"), "backend", "junior", listOf("Kotlin", "Go"), forCandidate = true)
        assertFalse(otherTrack.categoryMatch, "Full stack overlap must not compensate for another specialization")
        assertTrue(otherTrack.text.contains("Backend-разработка"), otherTrack.text)

        val unassessed = vacancyFit("backend", null, null, listOf("Kotlin"), "backend", "junior", listOf("Kotlin"), forCandidate = true)
        assertFalse(unassessed.categoryMatch)
        assertTrue(unassessed.text.startsWith("Категория не подтверждена"), unassessed.text)
    }

    @Test fun shortTaskInputValidation() {
        val task = validateShortTask(ShortTaskRequest(" Индекс ", " Как ускорить запрос? ", "DATA", " "))
        assertEquals(ShortTaskRequest("Индекс", "Как ускорить запрос?", "data", null), task)
        assertEquals("senior", validateShortTask(task.copy(grade = "Senior")).grade)
        assertEquals(HttpStatusCode.BadRequest, status { validateShortTask(task.copy(grade = "lead")) })
        assertEquals(HttpStatusCode.BadRequest, status { validateShortTask(task.copy(specialization = "qa")) })
        assertEquals(HttpStatusCode.BadRequest, status { validateShortTask(task.copy(body = "x".repeat(4001))) })

        assertEquals("approach", validateTaskSubmission(TaskSubmissionRequest("id", "Approach", " план ")).kind)
        assertEquals(HttpStatusCode.BadRequest, status { validateTaskSubmission(TaskSubmissionRequest("id", "essay", "text")) })
        assertEquals(HttpStatusCode.BadRequest, status { validateTaskSubmission(TaskSubmissionRequest("id", "solution", "  ")) })

        assertEquals(TaskRatingRequest(4, "Хорошо"), validateTaskRating(TaskRatingRequest(4, " Хорошо ")))
        assertEquals(HttpStatusCode.BadRequest, status { validateTaskRating(TaskRatingRequest(0)) })
        assertEquals(HttpStatusCode.BadRequest, status { validateTaskRating(TaskRatingRequest(6)) })
        assertEquals(HttpStatusCode.BadRequest, status { validateTaskRating(TaskRatingRequest(3, "x".repeat(1001))) })
    }

    @Test fun weeklyPeriodStartsOnMonday() {
        val monday = LocalDate.of(2026, 10, 5)
        assertEquals(monday, taskPeriodStart(monday))
        assertEquals(monday, taskPeriodStart(LocalDate.of(2026, 10, 8)))
        assertEquals(monday, taskPeriodStart(LocalDate.of(2026, 10, 11)))
        assertEquals(monday.plusWeeks(1), taskPeriodStart(LocalDate.of(2026, 10, 12)))
    }

    @Test fun weeklyPickIsDeterministicSpreadAndRotates() {
        val week = LocalDate.of(2026, 10, 5)
        val tasks = List(5) { UUID.nameUUIDFromBytes("task-$it".toByteArray()) }
        val candidate = UUID.nameUUIDFromBytes("candidate".toByteArray())
        assertNull(pickWeeklyTask(candidate, week, emptyList()))
        assertEquals(tasks[0], pickWeeklyTask(candidate, week, tasks.take(1)))

        // Same input -> same task, regardless of the order the pool was read from the DB.
        val pick = pickWeeklyTask(candidate, week, tasks)
        assertEquals(pick, pickWeeklyTask(candidate, week, tasks.reversed()))

        // Different candidates in the same week are spread over every task (leak resistance, fair load).
        val perTask = (0 until 200).mapNotNull { pickWeeklyTask(UUID.nameUUIDFromBytes("c$it".toByteArray()), week, tasks) }
            .groupingBy { it }.eachCount()
        assertEquals(tasks.toSet(), perTask.keys)
        assertTrue(perTask.values.all { it in 20..60 }, "unbalanced distribution: $perTask")

        // The same candidate is offered different tasks across weeks.
        val overWeeks = (0L until 12L).mapNotNull { pickWeeklyTask(candidate, week.plusWeeks(it), tasks) }.toSet()
        assertTrue(overWeeks.size >= 3, "rotation too narrow: $overWeeks")
    }
}
