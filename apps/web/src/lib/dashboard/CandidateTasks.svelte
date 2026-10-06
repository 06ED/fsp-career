<script lang="ts">
  import { onMount } from "svelte";
  import { sx } from "$lib/svelte-stylex";
  import { ui as s } from "$lib/dashboard.stylex";
  import ActionIcon from "$lib/ActionIcon.svelte";
  import { vx } from "./vacancy.stylex";
  import { taskApi } from "./service";
  import {
    gradeName,
    shortDate,
    specializationName,
    submissionKindLabels,
  } from "./vacancyLabels";
  import type {
    CandidateTaskSubmission,
    CurrentShortTask,
    Profile,
    RunAction,
    Section,
    SubmissionKind,
  } from "./types";

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
  let kind = $state<SubmissionKind>("approach");
  let answer = $state("");
  let ready = $derived(profile.processingConsent && !!profile.specialization);

  async function load() {
    if (!ready) return;
    await run(async () => {
      [current, history] = await Promise.all([
        taskApi.current(),
        taskApi.mySubmissions(),
      ]);
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

<div {...sx(s.panel)}>
  <h2 {...sx(s.panelTitle)}>Задание недели</h2>
  <p {...sx(s.muted)} style="margin-top: 0;">
    Раз в неделю (с понедельника) платформа предлагает короткую задачу от
    компании вашего направления. Решите её или опишите подход: ответ держит
    профиль актуальным и даёт работодателям свежий сигнал. Ответ видит только
    компания-автор: имя, категорию и текст ответа, без контактов.
  </p>
  {#if !ready}<div {...sx(s.note)}>
      Чтобы получать задания, сохраните профиль со специализацией и согласием на
      обработку данных.
      <div {...sx(s.inline)} style="margin-top: 12px;">
        <button
          type="button"
          {...sx(s.outline)}
          onclick={() => openSection("profile")}
          >Открыть профиль <ActionIcon name="arrow" /></button
        >
      </div>
    </div>{:else if current}
    <div {...sx(vx.meta)}>
      <span>Неделя с {shortDate(current.periodStart)}</span>
      <span>· следующее задание {shortDate(current.nextRotationAt)}</span>
      {#if current.lastActivityAt}<span
          >· последняя активность {shortDate(current.lastActivityAt)}</span
        >{/if}
    </div>
  {/if}
</div>

{#if ready && current}
  {#if current.state === "offered" && current.task}<form
      {...sx(s.panel)}
      aria-labelledby="weekly-task-title"
      onsubmit={(event) => {
        event.preventDefault();
        submit();
      }}
    >
      <div {...sx(s.kicker)}>
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
        {...sx(s.panelTitle)}
        style="margin-top: 10px;"
      >
        {current.task.title}
      </h2>
      <p {...sx(vx.text)}>{current.task.body}</p>
      <fieldset {...sx(vx.radioRow)}>
        <legend {...sx(vx.legend)}>Формат ответа</legend>
        {#each Object.entries(submissionKindLabels) as [value, label]}<label
            {...sx(s.answer)}
            style="margin-bottom: 0;"
            ><input
              type="radio"
              name="task-kind"
              {value}
              bind:group={kind}
            />{label}</label
          >{/each}
      </fieldset>
      <label for="task-answer" {...sx(s.label)}
        >{kind === "solution"
          ? "Ваше решение"
          : "Как бы вы решали задачу"}</label
      ><textarea
        id="task-answer"
        required
        maxlength="8000"
        rows="8"
        placeholder={kind === "solution"
          ? "Код, запрос или конкретный ответ"
          : "Шаги, допущения, риски и как проверите результат"}
        bind:value={answer}
        {...sx(s.textarea)}
      ></textarea>
      <button type="submit" disabled={busy || !answer.trim()} {...sx(s.button)}
        >Отправить ответ <ActionIcon name="send" /></button
      >
    </form>{:else if current.state === "answered"}<div {...sx(s.panel)}>
      <div {...sx(s.success)} role="status">
        Задание этой недели выполнено. Следующее появится {shortDate(
          current.nextRotationAt,
        )}.
      </div>
      {#if current.submission}<h2 {...sx(s.panelTitle)}>
          {current.submission.taskTitle}
        </h2>
        <p {...sx(s.muted)}>
          {current.submission.companyName} · {submissionKindLabels[
            current.submission.kind
          ]} · {shortDate(current.submission.submittedAt)}
        </p>
        <div {...sx(vx.quote)}>{current.submission.answer}</div>{/if}
    </div>{:else}<div {...sx(s.panel)}>
      <p {...sx(s.muted)}>
        Для направления «{specializationName(profile.specialization)}» сейчас
        нет активных заданий. Загляните позже — компании публикуют новые задачи
        регулярно.
      </p>
    </div>{/if}
{/if}

{#if ready}<div {...sx(s.panel)}>
    <h2 {...sx(s.panelTitle)}>История ответов</h2>
    {#each history as item (item.id)}<div
        style="border-top: 1px solid #ebebed; padding: 16px 0;"
      >
        <div {...sx(s.row)}>
          <div>
            <strong>{item.taskTitle}</strong>
            <div {...sx(s.muted)}>
              {item.companyName} · {submissionKindLabels[item.kind]} · {shortDate(
                item.submittedAt,
              )}
            </div>
          </div>
          {#if item.rating}<span
              {...sx(vx.stars)}
              aria-label={`Оценка компании: ${item.rating} из 5`}
              >{stars(item.rating)}</span
            >{:else}<span {...sx(vx.badge, vx.badgeMuted)}>Ждёт оценки</span
            >{/if}
        </div>
        {#if item.ratingComment}<p {...sx(s.muted)}>
            Комментарий компании: {item.ratingComment}
          </p>{/if}
        <details {...sx(vx.details)}>
          <summary {...sx(vx.summary)}>Мой ответ</summary>
          <div {...sx(vx.quote)}>{item.answer}</div>
        </details>
      </div>{:else}<p {...sx(s.muted)}>
        Вы ещё не отвечали на задания. Ответ на задание недели появится здесь
        вместе с оценкой компании.
      </p>{/each}
  </div>{/if}
