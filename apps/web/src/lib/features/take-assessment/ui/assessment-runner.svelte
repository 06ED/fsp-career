<script lang="ts">
  import { onDestroy } from "svelte";
  import { ActionIcon } from "$lib/shared/ui";
  import { gradeLabel } from "$lib/entities/assessment";
  import type { AssessmentAttempt } from "$lib/entities/assessment";

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

<form class="d-ui-panel" onsubmit={submit}>
  <div class="d-ui-row">
    <div>
      <div class="d-ui-kicker">
        ТЕСТ НА {gradeLabel(
          attempt.targetGrade,
        ).toUpperCase()}{attempt.vacancyTitle ? ` · ПОД ВАКАНСИЮ` : ""}
      </div>
      <h2 class="d-ui-panel-title" style="margin-top: 8px;">
        Задание {attempt.position + 1} из {attempt.total}
      </h2>
    </div>
    <div style="text-align: right;">
      <div
        class={`d-a-timer ${remaining < 120 ? "d-a-timer-low" : ""}`}
        aria-live="polite"
      >
        {clock}
      </div>
      <div class="d-a-small">осталось времени</div>
    </div>
  </div>
  <div class="d-a-progress-track">
    <div class="d-a-progress-fill" style="width: {progress}%;"></div>
  </div>
  {#if item}
    <span class="d-ui-tag">{item.topicLabel}</span>
    <p class="d-a-prompt">{item.prompt}</p>
    {#if item.code}<pre class="d-a-code"><code>{item.code}</code></pre>{/if}
    {#if item.format === "choice"}
      <fieldset style="border: 0; padding: 0; margin: 14px 0 18px;">
        <legend class="d-ui-label">Выберите один вариант</legend>
        {#each item.options as option (option.id)}
          <label class="d-ui-answer">
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
      <label for="numeric-answer" class="d-ui-label"
        >Ваш ответ{item.unit ? `, ${item.unit}` : ""}</label
      >
      <input
        id="numeric-answer"
        inputmode="decimal"
        autocomplete="off"
        placeholder="Например, 42 или 3,5"
        bind:value
        disabled={busy}
        class="d-ui-field d-a-number-input"
      />
    {/if}
  {:else}
    <p class="d-ui-muted">Загружаем задание…</p>
  {/if}
  <div class="d-ui-row">
    <p class="d-a-small" style="margin: 0; max-width: 520px;">
      Вернуться к предыдущему заданию нельзя, правильность ответов покажем
      только в итоговой оценке. Если время закончится, попытка засчитается по
      данным ответам.
    </p>
    <button type="submit" disabled={busy || !value.trim()} class="d-ui-button"
      >{attempt.position + 1 === attempt.total ? "Завершить тест" : "Ответить"}
      <ActionIcon
        name={attempt.position + 1 === attempt.total ? "accept" : "arrow"}
      /></button
    >
  </div>
</form>
