<script lang="ts">
  import { onMount } from "svelte";
  import { ActionIcon, EmptyState } from "$lib/shared/ui";
  import { vacancyApi } from "$lib/entities/vacancy";
  import { specializations } from "$lib/entities/candidate";
  import {
    applicationStatusLabels,
    gradeName,
    grades,
    salaryRange,
    specializationName,
    workFormatName,
    workFormats,
  } from "$lib/entities/vacancy";
  import type { Profile } from "$lib/entities/candidate";
  import type { VacancyCard, VacancyFilters } from "$lib/entities/vacancy";
  import type { RunAction } from "$lib/shared/api";

  let {
    profile,
    busy,
    run,
  }: {
    profile: Profile;
    busy: boolean;
    run: RunAction;
  } = $props();

  const emptyFilters = (): VacancyFilters => ({
    specialization: "",
    grade: "",
    techStack: "",
    salaryFrom: "",
    workFormat: "",
    q: "",
  });
  let filters = $state<VacancyFilters>(emptyFilters());
  let vacancies = $state<VacancyCard[]>([]);
  let loaded = $state(false);
  let applyingTo = $state<string | null>(null);
  let coverNote = $state("");

  // Applying needs consent, a filled profile and a confirmed category (checked again by the API).
  let blocker = $derived(
    !profile.processingConsent
      ? "Чтобы откликаться, сохраните профиль с согласием на обработку данных."
      : !profile.fullName || !profile.specialization
        ? "Чтобы откликаться, укажите имя и специализацию в профиле."
        : !profile.grade
          ? "Чтобы откликаться, пройдите оценку навыков: компания увидит вашу подтверждённую категорию, а не самооценку."
          : "",
  );

  async function load(confirmation = "") {
    loaded = false;
    await run(async () => {
      vacancies = await vacancyApi.vacancies(filters);
      loaded = true;
    }, confirmation);
  }

  async function apply(vacancy: VacancyCard) {
    await run(async () => {
      await vacancyApi.apply(vacancy.id, coverNote.trim());
      applyingTo = null;
      coverNote = "";
      vacancies = await vacancyApi.vacancies(filters);
    }, `Отклик на «${vacancy.title}» отправлен. Компания видит ваши контакты и подтверждённую категорию.`);
  }

  onMount(() => {
    load();
  });
</script>

<form
  class="d-ui-panel"
  aria-labelledby="vacancy-filters-title"
  onsubmit={(event) => {
    event.preventDefault();
    load();
  }}
>
  <div class="d-ui-row">
    <div>
      <h2 id="vacancy-filters-title" class="d-ui-panel-title">Открытые вакансии</h2>
      <p class="d-ui-muted" style="margin-top: 0;">
        Условия видны сразу: зарплатная вилка в рублях, формат и компания. Сначала показаны
        вакансии, подходящие вашей подтверждённой категории.
      </p>
    </div>
  </div>
  <div class="d-vx-filters">
    <div>
      <label for="vf-spec" class="d-ui-label">Специализация</label>
      <select id="vf-spec" bind:value={filters.specialization} class="d-ui-field">
        <option value="">Любая</option>
        {#each specializations as item (item.value)}
          <option value={item.value}>{item.label}</option>
        {/each}
      </select>
    </div>
    <div>
      <label for="vf-grade" class="d-ui-label">Грейд</label>
      <select id="vf-grade" bind:value={filters.grade} class="d-ui-field">
        <option value="">Любой</option>
        {#each grades as item (item.value)}
          <option value={item.value}>{item.label}</option>
        {/each}
      </select>
    </div>
    <div>
      <label for="vf-format" class="d-ui-label">Формат работы</label>
      <select id="vf-format" bind:value={filters.workFormat} class="d-ui-field">
        <option value="">Любой</option>
        {#each workFormats as item (item.value)}
          <option value={item.value}>{item.label}</option>
        {/each}
      </select>
    </div>
    <div>
      <label for="vf-salary" class="d-ui-label">Зарплата от, ₽ в месяц</label>
      <input
        id="vf-salary"
        type="number"
        min="0"
        step="10000"
        inputmode="numeric"
        placeholder="150000"
        bind:value={filters.salaryFrom}
        class="d-ui-field"
      />
    </div>
    <div>
      <label for="vf-stack" class="d-ui-label">Стек · все через запятую</label>
      <input
        id="vf-stack"
        placeholder="Kotlin, PostgreSQL"
        bind:value={filters.techStack}
        class="d-ui-field"
      />
    </div>
    <div>
      <label for="vf-q" class="d-ui-label">Поиск по тексту</label>
      <input
        id="vf-q"
        type="search"
        maxlength="100"
        placeholder="Компания, город, задача…"
        bind:value={filters.q}
        class="d-ui-field"
      />
    </div>
  </div>
  <div class="d-ui-inline">
    <button type="submit" disabled={busy} class="d-ui-button">
      Найти <ActionIcon name="matches" />
    </button>
    <button
      type="button"
      disabled={busy}
      class="d-ui-outline"
      onclick={() => {
        filters = emptyFilters();
        load();
      }}
    >
      Сбросить фильтры <ActionIcon name="refresh" />
    </button>
  </div>
</form>

{#if blocker}
  <div class="d-ui-note">
    {blocker}
    <div class="d-ui-inline" style="margin-top: 12px;">
      <a
        data-interactive
        class="d-ui-outline"
        href={!profile.processingConsent || !profile.fullName || !profile.specialization
          ? "/app/profile"
          : "/app/assessment"}
      >
        {!profile.processingConsent || !profile.fullName || !profile.specialization
          ? "Открыть профиль"
          : "Перейти к оценке"}
        <ActionIcon name="arrow" />
      </a>
    </div>
  </div>
{/if}

{#each vacancies as vacancy (vacancy.id)}
  <article class="d-ui-panel" aria-labelledby={"vacancy-" + vacancy.id}>
    <div class="d-ui-row" style="align-items: flex-start;">
      <div style="min-width: 0;">
        <div class="d-ui-kicker">
          {vacancy.companyName.toUpperCase()}
        </div>
        <h2
          id={"vacancy-" + vacancy.id}
          class="d-ui-panel-title"
          style="margin-top: 10px; margin-bottom: 4px;"
        >
          {vacancy.title}
        </h2>
        <div class="d-vx-salary">
          {salaryRange(vacancy.salaryMinRub, vacancy.salaryMaxRub)}
          <span class="d-vx-salary-unit">в месяц</span>
        </div>
      </div>
      {#if vacancy.myApplicationStatus}
        <span
          class={`d-vx-badge ${vacancy.myApplicationStatus === "accepted" ? "d-vx-badge-success" : ""} ${vacancy.myApplicationStatus === "rejected" ? "d-vx-badge-danger" : ""}`}
        >
          Отклик: {applicationStatusLabels[vacancy.myApplicationStatus]}
        </span>
      {:else if vacancy.categoryMatch}
        <span class="d-vx-badge d-vx-badge-success">Подходит вашей категории</span>
      {/if}
    </div>
    <div>
      <span class="d-ui-tag">{specializationName(vacancy.specialization)}</span>
      <span class="d-ui-tag">{gradeName(vacancy.grade)}</span>
      <span class="d-ui-tag d-vx-tag-muted">
        {workFormatName(vacancy.workFormat)}{vacancy.city ? ` · ${vacancy.city}` : ""}
      </span>
    </div>
    {#if vacancy.techStack.length}
      <div aria-label="Стек вакансии">
        {#each vacancy.techStack as skill (skill)}
          <span
            class={`d-ui-tag ${vacancy.stackOverlap.includes(skill) ? "d-vx-tag-match" : "d-vx-tag-muted"}`}
            title={vacancy.stackOverlap.includes(skill) ? "Есть в вашем профиле" : undefined}
          >
            {skill}
          </span>
        {/each}
      </div>
    {/if}
    <p class="d-ui-muted">{vacancy.fit}</p>
    <details class="d-vx-details">
      <summary class="d-vx-summary">Описание вакансии и компании</summary>
      <p class="d-vx-text">{vacancy.description}</p>
      {#if vacancy.companyDescription}
        <p class="d-ui-muted">
          {vacancy.companyName}: {vacancy.companyDescription}
        </p>
      {/if}
      {#if vacancy.companyWebsite}
        <p class="d-ui-muted">
          Сайт: {vacancy.companyWebsite}
        </p>
      {/if}
    </details>
    {#if !vacancy.myApplicationStatus}
      {#if applyingTo === vacancy.id}
        <form
          onsubmit={(event) => {
            event.preventDefault();
            apply(vacancy);
          }}
        >
          <label for={"cover-" + vacancy.id} class="d-ui-label">
            Сопроводительное письмо · необязательно
          </label>
          <textarea
            id={"cover-" + vacancy.id}
            maxlength="2000"
            placeholder="Почему вам интересна эта команда и какой опыт будет полезен"
            bind:value={coverNote}
            class="d-ui-textarea"
          ></textarea>
          <p class="d-ui-muted" style="margin-top: 0;">
            После отклика компания увидит ваши контакты (email и телефон), подтверждённую категорию
            и профиль. Пока решения нет, отклик можно отозвать.
          </p>
          <div class="d-ui-inline">
            <button type="submit" disabled={busy} class="d-ui-button">
              Отправить отклик <ActionIcon name="send" />
            </button>
            <button
              type="button"
              class="d-ui-outline"
              onclick={() => {
                applyingTo = null;
                coverNote = "";
              }}
            >
              Отмена <ActionIcon name="close" />
            </button>
          </div>
        </form>
      {:else}
        <button
          type="button"
          disabled={busy || !!blocker}
          class="d-ui-button"
          onclick={() => {
            applyingTo = vacancy.id;
            coverNote = "";
          }}
        >
          Откликнуться <ActionIcon name="send" />
        </button>
      {/if}
    {:else}
      <a href="/app/applications" data-interactive class="d-ui-outline">
        Мои отклики <ActionIcon name="applications" />
      </a>
    {/if}
  </article>
{:else}
  {#if loaded}
    <EmptyState
      icon="vacancies"
      title="Вакансии не найдены"
      description="По этим условиям открытых вакансий нет. Измените фильтры или дождитесь приглашения от компании."
    >
      {#snippet action()}
        <button
          class="d-ui-outline"
          onclick={() => {
            filters = emptyFilters();
            load();
          }}
        >
          Сбросить фильтры <ActionIcon name="refresh" />
        </button>
      {/snippet}
    </EmptyState>
  {/if}
{/each}
