<script lang="ts">
  import { onMount } from "svelte";
  import { ActionIcon, EmptyState } from "$lib/shared/ui";
  import { taskApi } from "$lib/entities/task";
  import {
    gradeName,
    shortDate,
    specializationName,
  } from "$lib/entities/vacancy";
  import { submissionKindLabels } from "$lib/entities/task";
  import type {
    CandidateTaskSubmission,
    CurrentShortTask,
    SubmissionKind,
  } from "$lib/entities/task";
  import type { Profile } from "$lib/entities/candidate";
  import type { Section } from "$lib/entities/account";
  import type { RunAction } from "$lib/shared/api";

  let {
    profile,
    busy,
    run,
    openSection,
  }: {
    profile: Profile;
    busy: boolean;
    run: RunAction;
    openSection: (id: Section) => Promise<void>;
  } = $props();

  let current = $state<CurrentShortTask | null>(null);
  let history = $state<CandidateTaskSubmission[]>([]);
  let loaded = $state(false);
  let kind = $state<SubmissionKind>("approach");
  let answer = $state("");
  let ready = $derived(profile.processingConsent && !!profile.specialization);

  async function load() {
    if (!ready) return;
    loaded = false;
    await run(async () => {
      [current, history] = await Promise.all([
        taskApi.current(),
        taskApi.mySubmissions(),
      ]);
      loaded = true;
    }, "");
  }

  async function submit() {
    const task = current?.task;
    if (!task) return;
    await run(async () => {
      await taskApi.submit(task.id, kind, answer.trim());
      answer = "";
      [current, history] = await Promise.all([
        taskApi.current(),
        taskApi.mySubmissions(),
      ]);
    }, "Ответ отправлен компании. Профиль отмечен как активный на этой неделе.");
  }

  function stars(rating: number) {
    return "★".repeat(rating) + "☆".repeat(5 - rating);
  }

  onMount(() => {
    load();
  });
</script>

<div class="d-ui-panel">
  <h2 class="d-ui-panel-title">Задание недели</h2>
  <p class="d-ui-muted" style="margin-top: 0;">
    Раз в неделю (с понедельника) платформа предлагает короткую задачу от
    компании вашего направления. Решите её или опишите подход: ответ держит
    профиль актуальным и даёт работодателям свежий сигнал. Ответ видит только
    компания-автор: имя, категорию и текст ответа, без контактов.
  </p>
  {#if !ready}
    <div class="d-ui-note">
      Чтобы получать задания, сохраните профиль со специализацией и согласием на
      обработку данных.
      <div class="d-ui-inline" style="margin-top: 12px;">
        <button
          type="button"
          class="d-ui-outline"
          onclick={() => openSection("profile")}
        >
          Открыть профиль <ActionIcon name="arrow" />
        </button>
      </div>
    </div>
  {:else if current}
    <div class="d-vx-meta">
      <span>Неделя с {shortDate(current.periodStart)}</span>
      <span>· следующее задание {shortDate(current.nextRotationAt)}</span>
      {#if current.lastActivityAt}
        <span>· последняя активность {shortDate(current.lastActivityAt)}</span>
      {/if}
    </div>
  {/if}
</div>

{#if ready && current}
  {#if current.state === "offered" && current.task}
    <form
      class="d-ui-panel"
      aria-labelledby="weekly-task-title"
      onsubmit={(event) => {
        event.preventDefault();
        submit();
      }}
    >
      <div class="d-ui-kicker">
        {[
          current.task.companyName,
          specializationName(current.task.specialization),
          current.task.grade ? gradeName(current.task.grade) : "",
        ]
          .filter(Boolean)
          .join(" · ")
          .toUpperCase()}
      </div>
      <h2
        id="weekly-task-title"
        class="d-ui-panel-title"
        style="margin-top: 10px;"
      >
        {current.task.title}
      </h2>
      <p class="d-vx-text">{current.task.body}</p>
      <fieldset class="d-vx-radio-row">
        <legend class="d-vx-legend">Формат ответа</legend>
        {#each Object.entries(submissionKindLabels) as [value, label]}
          <label class="d-ui-answer" style="margin-bottom: 0;">
            <input type="radio" name="task-kind" {value} bind:group={kind} />
            {label}
          </label>
        {/each}
      </fieldset>
      <label for="task-answer" class="d-ui-label">
        {kind === "solution" ? "Ваше решение" : "Как бы вы решали задачу"}
      </label>
      <textarea
        id="task-answer"
        required
        maxlength="8000"
        rows="8"
        placeholder={kind === "solution"
          ? "Код, запрос или конкретный ответ"
          : "Шаги, допущения, риски и как проверите результат"}
        bind:value={answer}
        class="d-ui-textarea"
      ></textarea>
      <button
        type="submit"
        disabled={busy || !answer.trim()}
        class="d-ui-button"
      >
        Отправить ответ <ActionIcon name="send" />
      </button>
    </form>
  {:else if current.state === "answered"}
    <div class="d-ui-panel">
      <div class="d-ui-success" role="status">
        Задание этой недели выполнено. Следующее появится {shortDate(
          current.nextRotationAt,
        )}.
      </div>
      {#if current.submission}
        <h2 class="d-ui-panel-title">
          {current.submission.taskTitle}
        </h2>
        <p class="d-ui-muted">
          {current.submission.companyName} · {submissionKindLabels[
            current.submission.kind
          ]} · {shortDate(current.submission.submittedAt)}
        </p>
        <div class="d-vx-quote">{current.submission.answer}</div>
      {/if}
    </div>
  {:else}
    <EmptyState
      icon="tasks"
      title="Активных заданий пока нет"
      description={`Для направления «${specializationName(profile.specialization)}» сейчас нет заданий. Загляните позже.`}
    />
  {/if}
{/if}

{#if ready}
  <div class="d-ui-panel">
    <h2 class="d-ui-panel-title">История ответов</h2>
    {#each history as item (item.id)}
      <div style="border-top: 1px solid var(--border); padding: 16px 0;">
        <div class="d-ui-row">
          <div>
            <strong>{item.taskTitle}</strong>
            <div class="d-ui-muted">
              {item.companyName} · {submissionKindLabels[item.kind]} · {shortDate(
                item.submittedAt,
              )}
            </div>
          </div>
          {#if item.rating}
            <span
              class="d-vx-stars"
              aria-label={`Оценка компании: ${item.rating} из 5`}
            >
              {stars(item.rating)}
            </span>
          {:else}
            <span class="d-vx-badge d-vx-badge-muted">Ждёт оценки</span>
          {/if}
        </div>
        {#if item.ratingComment}
          <p class="d-ui-muted">
            Комментарий компании: {item.ratingComment}
          </p>
        {/if}
        <details class="d-vx-details">
          <summary class="d-vx-summary">Мой ответ</summary>
          <div class="d-vx-quote">{item.answer}</div>
        </details>
      </div>
    {:else}
      {#if loaded}
        <EmptyState
          icon="tasks"
          title="История пока пуста"
          description="Ответ на задание недели появится здесь вместе с оценкой компании."
        />
      {/if}
    {/each}
  </div>
{/if}
