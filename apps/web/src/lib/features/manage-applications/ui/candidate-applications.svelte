<script lang="ts">
  import { onMount } from "svelte";
  import { ActionIcon, EmptyState } from "$lib/shared/ui";
  import { vacancyApi } from "$lib/entities/vacancy";
  import {
    applicationStatusLabels,
    gradeName,
    salaryRange,
    shortDate,
    specializationName,
    workFormatName,
  } from "$lib/entities/vacancy";
  import type { ApplicationStatus, CandidateApplication } from "$lib/entities/vacancy";
  import type { Section } from "$lib/entities/account";
  import type { RunAction } from "$lib/shared/api";

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
    accepted: "Компания приняла отклик и свяжется с вами по контактам из профиля.",
    rejected: "Компания выбрала другого кандидата.",
  };

  async function load() {
    loaded = false;
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

<div class="d-ui-panel">
  <div class="d-ui-row">
    <div>
      <h2 class="d-ui-panel-title">Статусы откликов</h2>
      <p class="d-ui-muted" style="margin-top: 0;">
        Отправлен → Просмотрен → Принят или Отказ. Компания видит ваши контакты только по тем
        вакансиям, на которые вы откликнулись сами.
      </p>
    </div>
    <div class="d-ui-inline">
      <button type="button" disabled={busy} class="d-ui-outline" onclick={() => load()}>
        Обновить <ActionIcon name="refresh" />
      </button>
      <button type="button" class="d-ui-button" onclick={() => openSection("vacancies")}>
        Найти вакансии <ActionIcon name="vacancies" />
      </button>
    </div>
  </div>
</div>

{#each applications as application (application.id)}
  <article class="d-ui-panel" aria-labelledby={"application-" + application.id}>
    <div class="d-ui-row" style="align-items: flex-start;">
      <div style="min-width: 0;">
        <div class="d-ui-kicker">{application.companyName.toUpperCase()}</div>
        <h2
          id={"application-" + application.id}
          class="d-ui-panel-title"
          style="margin-top: 10px; margin-bottom: 4px;"
        >
          {application.vacancyTitle}
        </h2>
        <div class="d-vx-salary">
          {salaryRange(application.salaryMinRub, application.salaryMaxRub)}
          <span class="d-vx-salary-unit">в месяц</span>
        </div>
      </div>
      <span
        class={`d-vx-badge ${application.status === "accepted" ? "d-vx-badge-success" : ""} ${application.status === "rejected" ? "d-vx-badge-danger" : ""}`}
      >
        {applicationStatusLabels[application.status]}
      </span>
    </div>
    <div>
      <span class="d-ui-tag">{specializationName(application.specialization)}</span>
      <span class="d-ui-tag">{gradeName(application.grade)}</span>
      <span class="d-ui-tag d-vx-tag-muted">
        {workFormatName(application.workFormat)}{application.city ? ` · ${application.city}` : ""}
      </span>
      {#if application.vacancyStatus === "closed"}
        <span class="d-ui-tag d-vx-tag-muted">Вакансия закрыта</span>
      {/if}
    </div>
    <p class="d-ui-muted">
      {hints[application.status]} Отклик от {shortDate(application.createdAt)}, статус обновлён {shortDate(
        application.updatedAt,
      )}.
    </p>
    {#if application.coverNote}
      <div class="d-vx-quote">
        {application.coverNote}
      </div>
    {/if}
    {#if application.status === "sent" || application.status === "viewed"}
      <button
        type="button"
        disabled={busy}
        class="d-ui-danger"
        onclick={() => withdraw(application)}
      >
        Отозвать отклик <ActionIcon name="undo" />
      </button>
    {/if}
  </article>
{:else}
  {#if loaded}
    <EmptyState
      icon="applications"
      title="Откликов пока нет"
      description="Вы ещё не откликались на вакансии. Можно дождаться приглашения или найти вакансию самостоятельно."
    >
      {#snippet action()}
        <button class="d-ui-button" onclick={() => openSection("vacancies")}>
          Найти вакансии <ActionIcon name="vacancies" />
        </button>
      {/snippet}
    </EmptyState>
  {/if}
{/each}
