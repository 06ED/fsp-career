<script lang="ts">
  import { onMount, tick } from "svelte";
  import { ActionIcon, EmptyState } from "$lib/shared/ui";
  import { taskApi } from "$lib/entities/task";
  import { specializations } from "$lib/entities/candidate";
  import { gradeName, grades, shortDate, specializationName } from "$lib/entities/vacancy";
  import { submissionKindLabels } from "$lib/entities/task";
  import type {
    EmployerShortTask,
    EmployerTaskSubmission,
    ShortTaskInput,
  } from "$lib/entities/task";
  import type { RunAction } from "$lib/shared/api";

  let { busy, run }: { busy: boolean; run: RunAction } = $props();

  const blank = (): ShortTaskInput => ({
    title: "",
    body: "",
    specialization: "",
    grade: null,
  });
  let tasks = $state<EmployerShortTask[]>([]);
  let loaded = $state(false);
  let draft = $state<ShortTaskInput | null>(null);
  let selected = $state<EmployerShortTask | null>(null);
  let submissions = $state<EmployerTaskSubmission[]>([]);
  // Per-answer rating form state, keyed by submission id.
  let ratings = $state<Record<string, { rating: number; comment: string }>>({});
  let workspace = $state<HTMLElement>();

  async function load() {
    loaded = false;
    await run(async () => {
      tasks = await taskApi.employerTasks();
      loaded = true;
    }, "");
  }
  async function create() {
    if (!draft) return;
    const payload = { ...draft, grade: draft.grade || null };
    await run(async () => {
      await taskApi.createTask(payload);
      draft = null;
      tasks = await taskApi.employerTasks();
    }, "Задание опубликовано и попадёт в еженедельную ротацию кандидатов этого направления.");
  }
  async function toggle(task: EmployerShortTask) {
    await run(
      async () => {
        await taskApi.setTaskActive(task.id, !task.active);
        tasks = await taskApi.employerTasks();
      },
      task.active ? "Задание снято с ротации. Ответы сохранены." : "Задание снова в ротации.",
    );
  }
  async function openSubmissions(task: EmployerShortTask) {
    draft = null;
    await run(async () => {
      const result = await taskApi.submissions(task.id);
      selected = result.task;
      submissions = result.submissions;
      ratings = Object.fromEntries(
        result.submissions.map((item) => [
          item.id,
          { rating: item.rating ?? 0, comment: item.ratingComment },
        ]),
      );
    }, "");
    if (selected) {
      // The answers panel opens above the list; bring it into view.
      await tick();
      workspace?.scrollIntoView({ block: "start" });
    }
  }
  async function rate(item: EmployerTaskSubmission) {
    const form = ratings[item.id];
    if (!form?.rating || !selected) return;
    const taskId = selected.id;
    await run(async () => {
      await taskApi.rate(item.id, Number(form.rating), form.comment.trim());
      const result = await taskApi.submissions(taskId);
      selected = result.task;
      submissions = result.submissions;
      tasks = await taskApi.employerTasks();
    }, `Оценка ответа ${item.candidateName} сохранена.`);
  }
  onMount(() => {
    load();
  });
</script>

<div class="d-ui-panel">
  <div class="d-ui-row">
    <div>
      <h2 class="d-ui-panel-title">Регулярные короткие задания</h2>
      <p class="d-ui-muted" style="margin-top: 0;">
        Раз в неделю платформа предлагает каждому кандидату направления одно активное задание —
        разным кандидатам достаются разные задачи. Кандидат присылает решение или подход; вы
        оцениваете ответ от 1 до 5. Это свежий сигнал о кандидате рядом с результатом оценки.
        Контакты в ответах не раскрываются.
      </p>
    </div>
    <button
      type="button"
      disabled={busy}
      class="d-ui-button"
      onclick={() => {
        selected = null;
        draft = blank();
      }}
    >
      Новое задание <ActionIcon name="add" />
    </button>
  </div>
</div>

<div bind:this={workspace} style="scroll-margin-top: 16px;"></div>
{#if draft}
  <form
    class="d-ui-panel"
    aria-labelledby="task-form-title"
    onsubmit={(event) => {
      event.preventDefault();
      create();
    }}
  >
    <div class="d-ui-row">
      <h2 id="task-form-title" class="d-ui-panel-title">Новое задание</h2>
      <button type="button" class="d-ui-outline" onclick={() => (draft = null)}>
        Закрыть <ActionIcon name="close" />
      </button>
    </div>
    <label for="task-title" class="d-ui-label">Название</label>
    <input
      id="task-title"
      required
      maxlength="200"
      placeholder="Идемпотентный платёжный endpoint"
      bind:value={draft.title}
      class="d-ui-field"
    />
    <div class="d-ui-grid">
      <div>
        <label for="task-spec" class="d-ui-label">Специализация</label>
        <select id="task-spec" required bind:value={draft.specialization} class="d-ui-field">
          <option value="">Выберите направление</option>
          {#each specializations as item (item.value)}
            <option value={item.value}>{item.label}</option>
          {/each}
        </select>
      </div>
      <div>
        <label for="task-grade" class="d-ui-label">Грейд</label>
        <select id="task-grade" bind:value={draft.grade} class="d-ui-field">
          <option value={null}>Любой грейд</option>
          {#each grades as item (item.value)}
            <option value={item.value}>{item.label}</option>
          {/each}
        </select>
      </div>
    </div>
    <label for="task-body" class="d-ui-label">Условие задачи</label>
    <textarea
      id="task-body"
      required
      maxlength="4000"
      rows="7"
      placeholder="Короткая задача на 15–30 минут: что дано, что нужно получить, ограничения"
      bind:value={draft.body}
      class="d-ui-textarea"
    ></textarea>
    <button type="submit" disabled={busy} class="d-ui-button">
      Опубликовать задание <ActionIcon name="save" />
    </button>
  </form>
{/if}

{#if selected}
  <section class="d-ui-panel" aria-labelledby="task-answers-title">
    <div class="d-ui-row">
      <div>
        <div class="d-ui-kicker">ОТВЕТЫ НА ЗАДАНИЕ</div>
        <h2 id="task-answers-title" class="d-ui-panel-title" style="margin-top: 10px;">
          {selected.title}
        </h2>
      </div>
      <button type="button" class="d-ui-outline" onclick={() => (selected = null)}>
        Закрыть <ActionIcon name="close" />
      </button>
    </div>
    {#each submissions as item (item.id)}
      <article
        style="border-top: 1px solid var(--border); padding: 20px 0;"
        aria-labelledby={"answer-" + item.id}
      >
        <div class="d-ui-kicker">
          {item.assessedGrade
            ? `ПОДТВЕРЖДЁННАЯ КАТЕГОРИЯ / ${specializationName(item.specialization)} · ${gradeName(item.assessedGrade)}${item.score != null ? ` · ${item.score}/100` : ""}`.toUpperCase()
            : `${specializationName(item.specialization).toUpperCase()} · КАТЕГОРИЯ НЕ ПОДТВЕРЖДЕНА`}
        </div>
        <h3 id={"answer-" + item.id} class="d-ui-panel-title" style="margin-top: 10px;">
          {item.candidateName}
        </h3>
        <p class="d-ui-muted" style="margin-top: 0;">
          {submissionKindLabels[item.kind]} · {shortDate(item.submittedAt)}{item.ratedAt
            ? ` · оценено ${shortDate(item.ratedAt)}`
            : ""}
        </p>
        <div class="d-vx-quote">{item.answer}</div>
        {#if ratings[item.id]}
          <form
            onsubmit={(event) => {
              event.preventDefault();
              rate(item);
            }}
          >
            <fieldset class="d-vx-radio-row">
              <legend class="d-vx-legend">Оценка ответа</legend>
              {#each [1, 2, 3, 4, 5] as value (value)}
                <label class="d-ui-answer" style="margin-bottom: 0;">
                  <input
                    type="radio"
                    name={"rating-" + item.id}
                    {value}
                    required
                    bind:group={ratings[item.id].rating}
                  />
                  {value}
                </label>
              {/each}
            </fieldset>
            <label for={"comment-" + item.id} class="d-ui-label">
              Комментарий кандидату · необязательно
            </label>
            <input
              id={"comment-" + item.id}
              maxlength="1000"
              bind:value={ratings[item.id].comment}
              class="d-ui-field"
            />
            <button type="submit" disabled={busy || !ratings[item.id].rating} class="d-ui-outline">
              {item.rating ? "Изменить оценку" : "Сохранить оценку"}
              <ActionIcon name="star" />
            </button>
          </form>
        {/if}
      </article>
    {:else}
      <EmptyState
        icon="tasks"
        title="Ответов пока нет"
        description="Задание предлагается кандидатам по одному в неделю."
      />
    {/each}
  </section>
{/if}

{#each tasks as task (task.id)}
  <article class="d-ui-panel" aria-labelledby={"task-" + task.id}>
    <div class="d-ui-row" style="align-items: flex-start;">
      <div style="min-width: 0;">
        <div class="d-ui-kicker">
          {specializationName(task.specialization).toUpperCase()} · {task.grade
            ? gradeName(task.grade).toUpperCase()
            : "ЛЮБОЙ ГРЕЙД"}
        </div>
        <h2 id={"task-" + task.id} class="d-ui-panel-title" style="margin-top: 10px;">
          {task.title}
        </h2>
      </div>
      <span class={`d-vx-badge ${task.active ? "d-vx-badge-success" : "d-vx-badge-muted"}`}>
        {task.active ? "В ротации" : "Снято"}
      </span>
    </div>
    <details class="d-vx-details">
      <summary class="d-vx-summary">Условие</summary>
      <p class="d-vx-text">{task.body}</p>
    </details>
    <p class="d-ui-muted">
      Ответов: {task.submissions} · без оценки: {task.unrated} · создано {shortDate(task.createdAt)}
    </p>
    <div class="d-ui-inline">
      <button
        type="button"
        disabled={busy}
        class="d-ui-button"
        onclick={() => openSubmissions(task)}
      >
        Ответы ({task.submissions}) <ActionIcon name="applications" />
      </button>
      <button
        type="button"
        disabled={busy}
        class={task.active ? "d-ui-danger" : "d-ui-outline"}
        onclick={() => toggle(task)}
      >
        {task.active ? "Снять с ротации" : "Вернуть в ротацию"}
        <ActionIcon name={task.active ? "archive" : "undo"} />
      </button>
    </div>
  </article>
{:else}
  {#if loaded && !draft}
    <EmptyState
      icon="tasks"
      title="Заданий пока нет"
      description="Опубликуйте короткую задачу из работы команды, чтобы увидеть, как кандидаты рассуждают."
    >
      {#snippet action()}
        <button
          class="d-ui-button"
          onclick={() => {
            selected = null;
            draft = blank();
          }}
        >
          Новое задание <ActionIcon name="add" />
        </button>
      {/snippet}
    </EmptyState>
  {/if}
{/each}
