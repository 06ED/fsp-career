<script lang="ts">
  import { sx } from "$lib/svelte-stylex";
  import { ui as s } from "$lib/dashboard.stylex";
  import ActionIcon from "$lib/ActionIcon.svelte";
  import { a } from "./assessment.stylex";
  import { formatDate, formatNumber, gradeLabel } from "./assessment";
  import type { AssessmentResult, GradeId } from "./types";

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
  let blocked = $derived(
    result.nextOptions.find((o) => !o.allowed && o.availableAt)?.availableAt,
  );
</script>

<div {...sx(s.panel)}>
  <div {...sx(s.kicker)}>
    РЕЗУЛЬТАТ ТЕСТА НА {gradeLabel(result.targetGrade).toUpperCase()}
  </div>
  <div {...sx(s.row)} style="margin-top: 14px;">
    <div>
      <div {...sx(a.small)}>Подтверждённый грейд</div>
      <div {...sx(a.bigGrade)}>{gradeLabel(result.grade)}</div>
    </div>
    <span {...sx(result.passed ? a.ok : a.fail)}
      >{result.passed
        ? "УРОВЕНЬ ПОДТВЕРЖДЁН"
        : result.expired
          ? "ВРЕМЯ ИСТЕКЛО"
          : "УРОВЕНЬ НЕ ПОДТВЕРЖДЁН"}</span
    >
  </div>
  <div {...sx(a.stats)}>
    <div {...sx(a.stat)}>
      <div {...sx(a.small)}>Балл (0–100)</div>
      <div {...sx(a.statValue)}>{result.score}</div>
    </div>
    <div {...sx(a.stat)}>
      <div {...sx(a.small)}>Оценка уровня θ</div>
      <div {...sx(a.statValue)}>
        {formatNumber(result.theta)} ± {formatNumber(result.thetaSe)}
      </div>
    </div>
    <div {...sx(a.stat)}>
      <div {...sx(a.small)}>Верных ответов</div>
      <div {...sx(a.statValue)}>{result.correct} из {result.answered}</div>
    </div>
    <div {...sx(a.stat)}>
      <div {...sx(a.small)}>Было до теста</div>
      <div {...sx(a.statValue)}>{gradeLabel(result.previousGrade)}</div>
    </div>
  </div>
  <div {...sx(s.note)}>{result.summary}</div>
  <p {...sx(a.small)}>
    Балл — процентиль оценки уровня среди всех кандидатов шкалы. Задания разной
    сложности, поэтому учитывается не только число верных ответов, но и то,
    какие именно задания решены.
  </p>
  <div {...sx(s.inline)}>
    {#each next as option}
      <button {...sx(s.button)} disabled={busy} onclick={() => onRetake(option.grade)}
        >{option.reason === "lower-after-fail"
          ? `Пройти тест на ${gradeLabel(option.grade)} сейчас`
          : `Повысить грейд: тест на ${gradeLabel(option.grade)}`}
        <ActionIcon name="assessment" /></button
      >
    {/each}
    <button {...sx(s.outline)} onclick={onClose}>К категории и истории</button>
  </div>
  {#if !next.length && blocked}
    <p {...sx(a.small)}>
      Следующая попытка сменить грейд — с {formatDate(blocked)}.
    </p>
  {/if}
</div>
