<script lang="ts">
  import { onMount } from "svelte";
  import { sx } from "$lib/svelte-stylex";
  import { ui as s } from "$lib/dashboard.stylex";
  import ActionIcon from "$lib/ActionIcon.svelte";
  import { vx } from "./vacancy.stylex";
  import { vacancyApi } from "./service";
  import {
    applicationStatusLabels,
    gradeName,
    salaryRange,
    shortDate,
    specializationName,
    workFormatName,
  } from "./vacancyLabels";
  import type {
    ApplicationStatus,
    CandidateApplication,
    RunAction,
    Section,
  } from "./types";

  let {
    busy,
    run,
    openSection,
  }: {
    busy: boolean;
    run: RunAction;
    openSection: (id: Section) => Promise<void>;
  } = $props();

  let applications = $state<CandidateApplication[]>([]);
  let loaded = $state(false);

  const hints: Record<ApplicationStatus, string> = {
    sent: "Компания ещё не открывала отклик.",
    viewed: "Компания посмотрела отклик и ваш профиль.",
    accepted:
      "Компания приняла отклик и свяжется с вами по контактам из профиля.",
    rejected: "Компания выбрала другого кандидата.",
  };

  async function load() {
    await run(async () => {
      applications = await vacancyApi.myApplications();
      loaded = true;
    }, "");
  }
  async function withdraw(application: CandidateApplication) {
    if (
      !window.confirm(
        `Отозвать отклик на «${application.vacancyTitle}»? Компания перестанет видеть его и ваши контакты.`,
      )
    )
      return;
    await run(async () => {
      await vacancyApi.withdraw(application.id);
      applications = await vacancyApi.myApplications();
    }, "Отклик отозван.");
  }
  onMount(() => {
    load();
  });
</script>

<div {...sx(s.panel)}>
  <div {...sx(s.row)}>
    <div>
      <h2 {...sx(s.panelTitle)}>Статусы откликов</h2>
      <p {...sx(s.muted)} style="margin-top: 0;">
        Отправлен → Просмотрен → Принят или Отказ. Компания видит ваши контакты
        только по тем вакансиям, на которые вы откликнулись сами.
      </p>
    </div>
    <div {...sx(s.inline)}>
      <button
        type="button"
        disabled={busy}
        {...sx(s.outline)}
        onclick={() => load()}>Обновить <ActionIcon name="refresh" /></button
      ><button
        type="button"
        {...sx(s.button)}
        onclick={() => openSection("vacancies")}
        >Найти вакансии <ActionIcon name="vacancies" /></button
      >
    </div>
  </div>
</div>

{#each applications as application (application.id)}<article
    {...sx(s.panel)}
    aria-labelledby={"application-" + application.id}
  >
    <div {...sx(s.row)} style="align-items: flex-start;">
      <div style="min-width: 0;">
        <div {...sx(s.kicker)}>{application.companyName.toUpperCase()}</div>
        <h2
          id={"application-" + application.id}
          {...sx(s.panelTitle)}
          style="margin-top: 10px; margin-bottom: 4px;"
        >
          {application.vacancyTitle}
        </h2>
        <div {...sx(vx.salary)}>
          {salaryRange(application.salaryMinRub, application.salaryMaxRub)}
          <span {...sx(vx.salaryUnit)}>в месяц</span>
        </div>
      </div>
      <span
        {...sx(
          vx.badge,
          application.status === "accepted" && vx.badgeSuccess,
          application.status === "rejected" && vx.badgeDanger,
        )}>{applicationStatusLabels[application.status]}</span
      >
    </div>
    <div>
      <span {...sx(s.tag)}
        >{specializationName(application.specialization)}</span
      ><span {...sx(s.tag)}>{gradeName(application.grade)}</span><span
        {...sx(s.tag, vx.tagMuted)}
        >{workFormatName(application.workFormat)}{application.city
          ? ` · ${application.city}`
          : ""}</span
      >{#if application.vacancyStatus === "closed"}<span
          {...sx(s.tag, vx.tagMuted)}>Вакансия закрыта</span
        >{/if}
    </div>
    <p {...sx(s.muted)}>
      {hints[application.status]} Отклик от {shortDate(application.createdAt)},
      статус обновлён {shortDate(application.updatedAt)}.
    </p>
    {#if application.coverNote}<div {...sx(vx.quote)}>
        {application.coverNote}
      </div>{/if}
    {#if application.status === "sent" || application.status === "viewed"}<button
        type="button"
        disabled={busy}
        {...sx(s.danger)}
        onclick={() => withdraw(application)}
        >Отозвать отклик <ActionIcon name="undo" /></button
      >{/if}
  </article>{:else}{#if loaded}<div {...sx(s.panel)}>
      <p {...sx(s.muted)}>
        Вы ещё не откликались на вакансии. Основной путь — приглашения от
        компаний, но вы можете проявить инициативу в разделе «Вакансии».
      </p>
    </div>{/if}{/each}
