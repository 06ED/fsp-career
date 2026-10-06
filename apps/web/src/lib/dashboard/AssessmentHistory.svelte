<script lang="ts">
  import { sx } from "$lib/svelte-stylex";
  import { ui as s } from "$lib/dashboard.stylex";
  import { a } from "./assessment.stylex";
  import {
    entryLabel,
    formatDate,
    formatNumber,
    gradeLabel,
  } from "./assessment";
  import type { AssessmentCatalog, AssessmentResult } from "./types";

  let {
    history,
    catalog,
  }: { history: AssessmentResult[]; catalog: AssessmentCatalog | null } =
    $props();
</script>

<div {...sx(s.panel)}>
  <h2 {...sx(s.panelTitle)}>История опросов и тестов</h2>
  {#each history as attempt (attempt.attemptId)}
    <div {...sx(a.historyItem)}>
      <div {...sx(s.row)}>
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
              {...sx(attempt.passed ? a.ok : a.fail)}
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
          <div {...sx(a.small)}>
            {formatDate(attempt.submittedAt)}{attempt.vacancyTitle
              ? ` · под вакансию «${attempt.vacancyTitle}»`
              : ""}
          </div>
        </div>
        <div style="text-align: right;">
          <div {...sx(s.status)}>грейд после теста</div>
          <strong style="font-size: 18px;">{gradeLabel(attempt.grade)}</strong>
          <div {...sx(a.small)}>
            {attempt.score} / 100{attempt.theta !== null &&
            attempt.theta !== undefined
              ? ` · θ ${formatNumber(attempt.theta)} ± ${formatNumber(attempt.thetaSe)}`
              : ""}
          </div>
        </div>
      </div>
      <details {...sx(a.details)}>
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
    <p {...sx(s.muted)}>Завершённых попыток пока нет.</p>
  {/each}
</div>
