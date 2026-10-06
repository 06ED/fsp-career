package ru.fsp.career.assessment

import java.time.Instant
import java.time.temporal.ChronoUnit

/** A completed attempt as seen by the retake rules (legacy attempts have no target: their grade is used). */
data class PastAttempt(
    val target: Grade?,
    val passed: Boolean?,
    val confident: Boolean?,
    val confirmed: Grade,
    val submittedAt: Instant,
)

enum class RetakeReason(val message: String) {
    FIRST_ATTEMPT("Первая попытка в этой специализации"),
    COOLDOWN_OVER("С прошлой попытки прошло 90 дней"),
    LOWER_AFTER_FAIL("Уровень не подтверждён — можно сразу пройти тест на уровень ниже"),
    UPGRADE_AFTER_CONFIDENT_PASS("Уверенный результат — можно сразу пройти тест на уровень выше"),
    COOLDOWN("Смена грейда — не чаще одного раза в 90 дней"),
    BELOW_CONFIRMED("Этот уровень ниже уже подтверждённого грейда"),
}

data class GradeOption(val grade: Grade, val allowed: Boolean, val reason: RetakeReason, val availableAt: Instant?)

/**
 * Retake rules, per specialization (grade changes are time-limited; a grade is never forcibly lowered):
 *  - no attempts yet, or ≥ 90 days since the last one → any target not below the confirmed grade;
 *  - inside the 90 days, only two immediate follow-ups of the LAST attempt are allowed:
 *      · it failed → one attempt one level lower (the spec's "не прошёл — может пройти тест на уровень ниже");
 *      · it passed confidently → one attempt one level higher, unless that level was failed in the window;
 *  - everything else waits until last attempt + 90 days.
 * "Once per failure" follows from using only the last attempt: after the follow-up it becomes the last one.
 * Abandoned/expired attempts are completed as failures, so walking away cannot reset the clock.
 */
object RetakePolicy {
    const val COOLDOWN_DAYS = 90L

    fun options(history: List<PastAttempt>, now: Instant): List<GradeOption> {
        val confirmed = history.maxOfOrNull { it.confirmed.ordinal }?.let { Grade.entries[it] }
        val last = history.maxByOrNull { it.submittedAt }
        return Grade.entries.map { grade ->
            when {
                confirmed != null && grade < confirmed -> GradeOption(grade, false, RetakeReason.BELOW_CONFIRMED, null)
                last == null -> GradeOption(grade, true, RetakeReason.FIRST_ATTEMPT, null)
                else -> {
                    val unlock = last.submittedAt.plus(COOLDOWN_DAYS, ChronoUnit.DAYS)
                    val lastTarget = last.target ?: last.confirmed
                    val windowStart = now.minus(COOLDOWN_DAYS, ChronoUnit.DAYS)
                    val failedRecently = history.any { it.passed == false && it.target == grade && it.submittedAt.isAfter(windowStart) }
                    when {
                        !now.isBefore(unlock) -> GradeOption(grade, true, RetakeReason.COOLDOWN_OVER, null)
                        last.passed == false && grade == lastTarget.previous() ->
                            GradeOption(grade, true, RetakeReason.LOWER_AFTER_FAIL, null)
                        last.confident == true && grade == lastTarget.next() && !failedRecently ->
                            GradeOption(grade, true, RetakeReason.UPGRADE_AFTER_CONFIDENT_PASS, null)
                        else -> GradeOption(grade, false, RetakeReason.COOLDOWN, unlock)
                    }
                }
            }
        }
    }

    fun option(history: List<PastAttempt>, target: Grade, now: Instant): GradeOption = options(history, now).first { it.grade == target }
}
