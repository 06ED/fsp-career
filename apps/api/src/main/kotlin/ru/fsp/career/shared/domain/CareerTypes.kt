package ru.fsp.career.shared.domain

import kotlinx.serialization.Serializable

@Serializable
data class Option(val id: String, val label: String)

fun grade(value: String): String = value.takeIf { it in listOf("junior", "middle", "senior") }
    ?: bad("grade must be junior, middle or senior")

internal fun gradeRank(value: String): Int = when (value) {
    "senior" -> 3; "middle" -> 2; else -> 1
}

private val specializationLabels = mapOf(
    "backend" to "Backend-разработка", "frontend" to "Frontend-разработка",
    "data" to "Данные и аналитика", "product" to "Продукт", "design" to "Дизайн",
)

internal fun specializationLabel(spec: String): String =
    specializationLabels[spec.lowercase()] ?: spec.replaceFirstChar { it.uppercase() }

internal fun categoryLabel(spec: String, grade: String): String =
    "${specializationLabel(spec)} · ${grade.replaceFirstChar { it.uppercase() }}"

internal fun anonymousName(id: java.util.UUID) = "Кандидат №" + id.toString().take(6).uppercase()
