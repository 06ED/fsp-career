<script lang="ts">
  import { ActionIcon } from "$lib/shared/ui";
  import { formatDate, formatNumber, gradeLabel } from "$lib/entities/assessment";
  import type { AssessmentResult, GradeId } from "$lib/entities/assessment";

  let {
    result,
    busy,
    onRetake,
    onClose,
  }: {
    result: AssessmentResult;
    busy: boolean;
    onRetake: (grade: GradeId) => Promise<void>;
    onClose: () => void;
  } = $props();

  let next = $derived(result.nextOptions.filter((o) => o.allowed));
  let blocked = $derived(result.nextOptions.find((o) => !o.allowed && o.availableAt)?.availableAt);
</script>

<div class="d-ui-panel">
  <div class="d-ui-kicker">
    РЕЗУЛЬТАТ ТЕСТА НА {gradeLabel(result.targetGrade).toUpperCase()}
  </div>
  <div class="d-ui-row" style="margin-top: 14px;">
    <div>
      <div class="d-a-small">Подтверждённый грейд</div>
      <div class="d-a-big-grade">{gradeLabel(result.grade)}</div>
    </div>
    <span class={result.passed ? "d-a-ok" : "d-a-fail"}>
      {result.passed
        ? "УРОВЕНЬ ПОДТВЕРЖДЁН"
        : result.expired
          ? "ВРЕМЯ ИСТЕКЛО"
          : "УРОВЕНЬ НЕ ПОДТВЕРЖДЁН"}
    </span>
  </div>
  <div class="d-a-stats">
    <div class="d-a-stat">
      <div class="d-a-small">Балл (0–100)</div>
      <div class="d-a-stat-value">{result.score}</div>
    </div>
    <div class="d-a-stat">
      <div class="d-a-small">Оценка уровня θ</div>
      <div class="d-a-stat-value">
        {formatNumber(result.theta)} ± {formatNumber(result.thetaSe)}
      </div>
    </div>
    <div class="d-a-stat">
      <div class="d-a-small">Верных ответов</div>
      <div class="d-a-stat-value">{result.correct} из {result.answered}</div>
    </div>
    <div class="d-a-stat">
      <div class="d-a-small">Было до теста</div>
      <div class="d-a-stat-value">{gradeLabel(result.previousGrade)}</div>
    </div>
  </div>
  <div class="d-ui-note">{result.summary}</div>
  <p class="d-a-small">
    Балл — процентиль оценки уровня среди всех кандидатов шкалы. Задания разной сложности, поэтому
    учитывается не только число верных ответов, но и то, какие именно задания решены.
  </p>
  <div class="d-ui-inline">
    {#each next as option (option.grade)}
      <button class="d-ui-button" disabled={busy} onclick={() => onRetake(option.grade)}>
        {option.reason === "lower-after-fail"
          ? `Пройти тест на ${gradeLabel(option.grade)} сейчас`
          : `Повысить грейд: тест на ${gradeLabel(option.grade)}`}
        <ActionIcon name="assessment" />
      </button>
    {/each}
    <button class="d-ui-outline" onclick={onClose}>К категории и истории</button>
  </div>
  {#if !next.length && blocked}
    <p class="d-a-small">
      Следующая попытка сменить грейд — с {formatDate(blocked)}.
    </p>
  {/if}
</div>
