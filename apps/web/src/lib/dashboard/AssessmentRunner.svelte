<script lang="ts">
  import { onDestroy } from "svelte";
  import { sx } from "$lib/svelte-stylex";
  import { ui as s } from "$lib/dashboard.stylex";
  import ActionIcon from "$lib/ActionIcon.svelte";
  import { a } from "./assessment.stylex";
  import { gradeLabel } from "./assessment";
  import type { AssessmentAttempt } from "./types";

  let {
    attempt,
    busy,
    onAnswer,
    onExpire,
  }: {
    attempt: AssessmentAttempt;
    busy: boolean;
    onAnswer: (answer: string) => Promise<void>;
    onExpire: () => Promise<void>;
  } = $props();

  let value = $state("");
  let now = $state(Date.now());
  let expiredNotified = false;
  const tick = setInterval(() => (now = Date.now()), 1000);
  onDestroy(() => clearInterval(tick));

  let remaining = $derived(
    Math.max(
      0,
      Math.floor((new Date(attempt.expiresAt).getTime() - now) / 1000),
    ),
  );
  let clock = $derived(
    `${String(Math.floor(remaining / 60)).padStart(2, "0")}:${String(remaining % 60).padStart(2, "0")}`,
  );
  let progress = $derived(Math.round((attempt.position / attempt.total) * 100));
  let item = $derived(attempt.item);

  // A new item arrives with a new position: clear the previous answer.
  $effect(() => {
    void attempt.position;
    value = "";
  });

  $effect(() => {
    if (remaining === 0 && !expiredNotified) {
      expiredNotified = true;
      onExpire();
    }
  });

  async function submit(event: SubmitEvent) {
    event.preventDefault();
    if (!value.trim() || busy) return;
    await onAnswer(value.trim());
  }
</script>

<form {...sx(s.panel)} onsubmit={submit}>
  <div {...sx(s.row)}>
    <div>
      <div {...sx(s.kicker)}>
        ТЕСТ НА {gradeLabel(
          attempt.targetGrade,
        ).toUpperCase()}{attempt.vacancyTitle ? ` · ПОД ВАКАНСИЮ` : ""}
      </div>
      <h2 {...sx(s.panelTitle)} style="margin-top: 8px;">
        Задание {attempt.position + 1} из {attempt.total}
      </h2>
    </div>
    <div style="text-align: right;">
      <div {...sx(a.timer, remaining < 120 && a.timerLow)} aria-live="polite">
        {clock}
      </div>
      <div {...sx(a.small)}>осталось времени</div>
    </div>
  </div>
  <div {...sx(a.progressTrack)}>
    <div {...sx(a.progressFill)} style="width: {progress}%;"></div>
  </div>
  {#if item}
    <span {...sx(s.tag)}>{item.topicLabel}</span>
    <p {...sx(a.prompt)}>{item.prompt}</p>
    {#if item.code}<pre {...sx(a.code)}><code>{item.code}</code></pre>{/if}
    {#if item.format === "choice"}
      <fieldset style="border: 0; padding: 0; margin: 14px 0 18px;">
        <legend {...sx(s.label)}>Выберите один вариант</legend>
        {#each item.options as option (option.id)}
          <label {...sx(s.answer)}>
            <input
              type="radio"
              name="answer"
              value={option.id}
              bind:group={value}
              disabled={busy}
            />
            <span style="white-space: pre-line;">{option.label}</span>
          </label>
        {/each}
      </fieldset>
    {:else}
      <label for="numeric-answer" {...sx(s.label)}
        >Ваш ответ{item.unit ? `, ${item.unit}` : ""}</label
      >
      <input
        id="numeric-answer"
        inputmode="decimal"
        autocomplete="off"
        placeholder="Например, 42 или 3,5"
        bind:value
        disabled={busy}
        {...sx(s.field, a.numberInput)}
      />
    {/if}
  {:else}
    <p {...sx(s.muted)}>Загружаем задание…</p>
  {/if}
  <div {...sx(s.row)}>
    <p {...sx(a.small)} style="margin: 0; max-width: 520px;">
      Вернуться к предыдущему заданию нельзя, правильность ответов покажем
      только в итоговой оценке. Если время закончится, попытка засчитается по
      данным ответам.
    </p>
    <button type="submit" disabled={busy || !value.trim()} {...sx(s.button)}
      >{attempt.position + 1 === attempt.total ? "Завершить тест" : "Ответить"}
      <ActionIcon
        name={attempt.position + 1 === attempt.total ? "accept" : "arrow"}
      /></button
    >
  </div>
</form>
