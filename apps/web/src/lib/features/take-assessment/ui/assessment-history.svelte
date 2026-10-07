<script lang="ts">
  import { EmptyState } from "$lib/shared/ui";
  import {
    entryLabel,
    formatDate,
    formatNumber,
    gradeLabel,
  } from "$lib/entities/assessment";
  import type {
    AssessmentCatalog,
    AssessmentResult,
  } from "$lib/entities/assessment";

  let {
    history,
    catalog,
  }: { history: AssessmentResult[]; catalog: AssessmentCatalog | null } =
    $props();
</script>

<div class="d-ui-panel">
  <h2 class="d-ui-panel-title">История опросов и тестов</h2>
  {#each history as attempt (attempt.attemptId)}
    <div class="d-a-history-item">
      <div class="d-ui-row">
        <div>
          <strong
            >{entryLabel(
              catalog?.specializations,
              attempt.specialization,
            )}</strong
          >
          {#if attempt.targetGrade}
            · тест на {gradeLabel(attempt.targetGrade)}
            <span
              class={attempt.passed ? "d-a-ok" : "d-a-fail"}
              style="margin-left: 6px;"
              >{attempt.passed
                ? "подтверждён"
                : attempt.expired
                  ? "время истекло"
                  : "не подтверждён"}</span
            >
          {:else}
            · тест прежнего формата
          {/if}
          <div class="d-a-small">
            {formatDate(attempt.submittedAt)}{attempt.vacancyTitle
              ? ` · под вакансию «${attempt.vacancyTitle}»`
              : ""}
          </div>
        </div>
        <div style="text-align: right;">
          <div class="d-ui-status">грейд после теста</div>
          <strong style="font-size: 18px;">{gradeLabel(attempt.grade)}</strong>
          <div class="d-a-small">
            {attempt.score} / 100{attempt.theta !== null &&
            attempt.theta !== undefined
              ? ` · θ ${formatNumber(attempt.theta)} ± ${formatNumber(attempt.thetaSe)}`
              : ""}
          </div>
        </div>
      </div>
      <details class="d-a-details">
        <summary>Подробнее</summary>
        <p>{attempt.summary}</p>
        <p>
          Верных ответов: {attempt.correct} из {attempt.answered} (план — {attempt.total}).
          Грейд до теста: {gradeLabel(attempt.previousGrade)}.
        </p>
        {#if attempt.survey}
          <p>
            Опрос: отрасль — {entryLabel(
              catalog?.industries,
              attempt.survey.industry,
            )}; опыт — {attempt.survey.experienceYears} лет; формат — {entryLabel(
              catalog?.workFormats,
              attempt.survey.workFormat,
            )}{attempt.survey.teamRoles.length
              ? `; роли — ${attempt.survey.teamRoles
                  .map((role) => entryLabel(catalog?.teamRoles, role))
                  .join(", ")}`
              : ""}.
          </p>
        {/if}
      </details>
    </div>
  {:else}
    <EmptyState
      icon="assessment"
      title="История пока пуста"
      description="После завершения первой оценки результаты появятся здесь."
    />
  {/each}
</div>
