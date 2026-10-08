package ru.fsp.career.recruitment.application

import ru.fsp.career.recruitment.domain.*
import ru.fsp.career.shared.domain.uuid
import java.util.UUID

internal class TaskService(private val tasks: TaskRepository) {
    suspend fun own(subject: String) = tasks.own(subject)

    suspend fun create(subject: String, request: ShortTaskRequest): EmployerShortTask {
        val input = validateShortTask(request)
        return tasks.create(subject, input)
    }

    suspend fun changeActive(subject: String, id: UUID, active: Boolean) =
        tasks.changeActive(subject, id, active)

    suspend fun submissions(subject: String, id: UUID) = tasks.submissions(subject, id)
    suspend fun rate(subject: String, id: UUID, request: TaskRatingRequest): EmployerTaskSubmission {
        val input = validateTaskRating(request)
        return tasks.rate(subject, id, input)
    }

    suspend fun current(subject: String) = tasks.current(subject)

    suspend fun submit(subject: String, request: TaskSubmissionRequest): CandidateTaskSubmission {
        val input = validateTaskSubmission(request)
        val taskId = uuid(input.taskId)
        return tasks.submit(subject, input, taskId)
    }

    suspend fun history(subject: String) =
        tasks.history(subject)
}
