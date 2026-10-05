<script lang="ts">
  import { sx } from "$lib/svelte-stylex";
  import { ui as s } from "$lib/dashboard.stylex";
  import type { Profile, Assessment, Result } from "./types";
  let {
    profile,
    assessment,
    answers = $bindable(),
    result,
    history,
    busy,
    startAssessment,
    submitAssessment,
  }: {
    profile: Profile;
    assessment: Assessment | null;
    answers: Record<string, string>;
    result: Result | null;
    history: Result[];
    busy: boolean;
    startAssessment: () => Promise<void>;
    submitAssessment: () => Promise<void>;
  } = $props();
</script>

<div {...sx(s.panel)}>
  <h2 {...sx(s.panelTitle)}>Подтвердите свою категорию</h2>
  <p {...sx(s.muted)}>
    Короткий тест по выбранной специализации. Задания выбираются из банка;
    сопоставимость сложности ещё не подтверждена независимой проверкой.
    Повторное прохождение ограничено по времени; неудачная попытка не понижает
    уже подтверждённый грейд.
  </p>
  {#if result}<div {...sx(s.note)}>
      Результат: {result.score} баллов · {result.specialization} / {result.grade}.
      Дата: {new Date(result.submittedAt).toLocaleDateString("ru-RU")}.
    </div>{/if}
  {#if !assessment}<button
      disabled={busy || !profile.specialization}
      {...sx(s.button)}
      onclick={() => startAssessment()}>Начать оценку ↗</button
    >{#if !profile.specialization}<p {...sx(s.muted)}>
        Сначала заполните специализацию в профиле.
      </p>{/if}{/if}
</div>
{#if assessment}<form
    {...sx(s.panel)}
    onsubmit={(event) => {
      event.preventDefault();
      submitAssessment();
    }}
  >
    <h2 {...sx(s.panelTitle)}>Вопросы оценки</h2>
    <p {...sx(s.muted)}>
      Попытка действует до {new Date(assessment.expiresAt).toLocaleString(
        "ru-RU",
      )}.
    </p>
    {#each assessment.questions as question, index}<fieldset
        style="border: 0; padding: 0; margin: 28px 0;"
      >
        <legend style="font-weight: 600; margin-bottom: 13px;"
          >{String(index + 1).padStart(2, "0")}. {question.prompt}</legend
        >{#each question.options as option}<label {...sx(s.answer)}
            ><input
              required
              type="radio"
              name={String(question.id)}
              value={option.id}
              bind:group={answers[question.id]}
            />
            {option.label}</label
          >{/each}
      </fieldset>{/each}<button disabled={busy} type="submit" {...sx(s.button)}
      >Завершить тест ↗</button
    >
  </form>{/if}
<div {...sx(s.panel)}>
  <h2 {...sx(s.panelTitle)}>История оценок</h2>
  {#each history as attempt}<div
      {...sx(s.row)}
      style="border-top: 1px solid #ebebed; padding: 14px 0;"
    >
      <span
        >{attempt.specialization} / {attempt.grade} · {new Date(
          attempt.submittedAt,
        ).toLocaleDateString("ru-RU")}</span
      ><strong>{attempt.score} / 100</strong>
    </div>{:else}<p {...sx(s.muted)}>Завершённых попыток пока нет.</p>{/each}
</div>
