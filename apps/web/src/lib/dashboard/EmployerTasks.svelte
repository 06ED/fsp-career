<script lang="ts">
  import { onMount, tick } from "svelte";
  import { sx } from "$lib/svelte-stylex";
  import { ui as s } from "$lib/dashboard.stylex";
  import ActionIcon from "$lib/ActionIcon.svelte";
  import { vx } from "./vacancy.stylex";
  import { taskApi } from "./service";
  import { specializations } from "./specializations";
  import {
    gradeName,
    grades,
    shortDate,
    specializationName,
    submissionKindLabels,
  } from "./vacancyLabels";
  import type {
    EmployerShortTask,
    EmployerTaskSubmission,
    RunAction,
    ShortTaskInput,
  } from "./types";

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
      task.active
        ? "Задание снято с ротации. Ответы сохранены."
        : "Задание снова в ротации.",
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

<div {...sx(s.panel)}>
  <div {...sx(s.row)}>
    <div>
      <h2 {...sx(s.panelTitle)}>Регулярные короткие задания</h2>
      <p {...sx(s.muted)} style="margin-top: 0;">
        Раз в неделю платформа предлагает каждому кандидату направления одно
        активное задание — разным кандидатам достаются разные задачи. Кандидат
        присылает решение или подход; вы оцениваете ответ от 1 до 5. Это свежий
        сигнал о кандидате рядом с результатом оценки. Контакты в ответах не
        раскрываются.
      </p>
    </div>
    <button
      type="button"
      disabled={busy}
      {...sx(s.button)}
      onclick={() => {
        selected = null;
        draft = blank();
      }}>Новое задание <ActionIcon name="add" /></button
    >
  </div>
</div>

<div bind:this={workspace} style="scroll-margin-top: 16px;"></div>
{#if draft}<form
    {...sx(s.panel)}
    aria-labelledby="task-form-title"
    onsubmit={(event) => {
      event.preventDefault();
      create();
    }}
  >
    <div {...sx(s.row)}>
      <h2 id="task-form-title" {...sx(s.panelTitle)}>Новое задание</h2>
      <button type="button" {...sx(s.outline)} onclick={() => (draft = null)}
        >Закрыть <ActionIcon name="close" /></button
      >
    </div>
    <label for="task-title" {...sx(s.label)}>Название</label><input
      id="task-title"
      required
      maxlength="200"
      placeholder="Идемпотентный платёжный endpoint"
      bind:value={draft.title}
      {...sx(s.field)}
    />
    <div {...sx(s.grid)}>
      <div>
        <label for="task-spec" {...sx(s.label)}>Специализация</label><select
          id="task-spec"
          required
          bind:value={draft.specialization}
          {...sx(s.field)}
          ><option value="">Выберите направление</option
          >{#each specializations as item}<option value={item.value}
              >{item.label}</option
            >{/each}</select
        >
      </div>
      <div>
        <label for="task-grade" {...sx(s.label)}>Грейд</label><select
          id="task-grade"
          bind:value={draft.grade}
          {...sx(s.field)}
          ><option value={null}>Любой грейд</option
          >{#each grades as item}<option value={item.value}>{item.label}</option
            >{/each}</select
        >
      </div>
    </div>
    <label for="task-body" {...sx(s.label)}>Условие задачи</label><textarea
      id="task-body"
      required
      maxlength="4000"
      rows="7"
      placeholder="Короткая задача на 15–30 минут: что дано, что нужно получить, ограничения"
      bind:value={draft.body}
      {...sx(s.textarea)}
    ></textarea>
    <button type="submit" disabled={busy} {...sx(s.button)}
      >Опубликовать задание <ActionIcon name="save" /></button
    >
  </form>{/if}

{#if selected}<section {...sx(s.panel)} aria-labelledby="task-answers-title">
    <div {...sx(s.row)}>
      <div>
        <div {...sx(s.kicker)}>ОТВЕТЫ НА ЗАДАНИЕ</div>
        <h2
          id="task-answers-title"
          {...sx(s.panelTitle)}
          style="margin-top: 10px;"
        >
          {selected.title}
        </h2>
      </div>
      <button type="button" {...sx(s.outline)} onclick={() => (selected = null)}
        >Закрыть <ActionIcon name="close" /></button
      >
    </div>
    {#each submissions as item (item.id)}<article
        style="border-top: 1px solid #ebebed; padding: 20px 0;"
        aria-labelledby={"answer-" + item.id}
      >
        <div {...sx(s.kicker)}>
          {item.assessedGrade
            ? `ПОДТВЕРЖДЁННАЯ КАТЕГОРИЯ / ${specializationName(item.specialization)} · ${gradeName(item.assessedGrade)}${item.score != null ? ` · ${item.score}/100` : ""}`.toUpperCase()
            : `${specializationName(item.specialization).toUpperCase()} · КАТЕГОРИЯ НЕ ПОДТВЕРЖДЕНА`}
        </div>
        <h3
          id={"answer-" + item.id}
          {...sx(s.panelTitle)}
          style="margin-top: 10px;"
        >
          {item.candidateName}
        </h3>
        <p {...sx(s.muted)} style="margin-top: 0;">
          {submissionKindLabels[item.kind]} · {shortDate(
            item.submittedAt,
          )}{item.ratedAt ? ` · оценено ${shortDate(item.ratedAt)}` : ""}
        </p>
        <div {...sx(vx.quote)}>{item.answer}</div>
        {#if ratings[item.id]}<form
            onsubmit={(event) => {
              event.preventDefault();
              rate(item);
            }}
          >
            <fieldset {...sx(vx.radioRow)}>
              <legend {...sx(vx.legend)}>Оценка ответа</legend>
              {#each [1, 2, 3, 4, 5] as value}<label
                  {...sx(s.answer)}
                  style="margin-bottom: 0;"
                  ><input
                    type="radio"
                    name={"rating-" + item.id}
                    {value}
                    required
                    bind:group={ratings[item.id].rating}
                  />{value}</label
                >{/each}
            </fieldset>
            <label for={"comment-" + item.id} {...sx(s.label)}
              >Комментарий кандидату · необязательно</label
            ><input
              id={"comment-" + item.id}
              maxlength="1000"
              bind:value={ratings[item.id].comment}
              {...sx(s.field)}
            />
            <button
              type="submit"
              disabled={busy || !ratings[item.id].rating}
              {...sx(s.outline)}
              >{item.rating ? "Изменить оценку" : "Сохранить оценку"}
              <ActionIcon name="star" /></button
            >
          </form>{/if}
      </article>{:else}<p {...sx(s.muted)}>
        Ответов пока нет: задание предлагается кандидатам по одному в неделю.
      </p>{/each}
  </section>{/if}

{#each tasks as task (task.id)}<article
    {...sx(s.panel)}
    aria-labelledby={"task-" + task.id}
  >
    <div {...sx(s.row)} style="align-items: flex-start;">
      <div style="min-width: 0;">
        <div {...sx(s.kicker)}>
          {specializationName(task.specialization).toUpperCase()} · {task.grade
            ? gradeName(task.grade).toUpperCase()
            : "ЛЮБОЙ ГРЕЙД"}
        </div>
        <h2
          id={"task-" + task.id}
          {...sx(s.panelTitle)}
          style="margin-top: 10px;"
        >
          {task.title}
        </h2>
      </div>
      <span {...sx(vx.badge, task.active ? vx.badgeSuccess : vx.badgeMuted)}
        >{task.active ? "В ротации" : "Снято"}</span
      >
    </div>
    <details {...sx(vx.details)}>
      <summary {...sx(vx.summary)}>Условие</summary>
      <p {...sx(vx.text)}>{task.body}</p>
    </details>
    <p {...sx(s.muted)}>
      Ответов: {task.submissions} · без оценки: {task.unrated} · создано {shortDate(
        task.createdAt,
      )}
    </p>
    <div {...sx(s.inline)}>
      <button
        type="button"
        disabled={busy}
        {...sx(s.button)}
        onclick={() => openSubmissions(task)}
        >Ответы ({task.submissions}) <ActionIcon name="applications" /></button
      ><button
        type="button"
        disabled={busy}
        {...sx(task.active ? s.danger : s.outline)}
        onclick={() => toggle(task)}
        >{task.active ? "Снять с ротации" : "Вернуть в ротацию"}
        <ActionIcon name={task.active ? "archive" : "undo"} /></button
      >
    </div>
  </article>{:else}{#if loaded && !draft}<div {...sx(s.panel)}>
      <p {...sx(s.muted)}>
        Заданий пока нет. Опубликуйте короткую задачу из реальной работы команды
        — так вы увидите, как кандидаты рассуждают.
      </p>
    </div>{/if}{/each}
