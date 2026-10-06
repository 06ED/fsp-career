<script lang="ts">
  import { onMount } from "svelte";
  import { sx } from "$lib/svelte-stylex";
  import { ui as s } from "$lib/dashboard.stylex";
  import ActionIcon from "$lib/ActionIcon.svelte";
  import { vx } from "./vacancy.stylex";
  import { vacancyApi } from "./service";
  import { specializations } from "./specializations";
  import {
    applicationStatusLabels,
    gradeName,
    grades,
    salaryRange,
    specializationName,
    workFormatName,
    workFormats,
  } from "./vacancyLabels";
  import type {
    Profile,
    RunAction,
    Section,
    VacancyCard,
    VacancyFilters,
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
  {...sx(s.panel)}
  aria-labelledby="vacancy-filters-title"
  onsubmit={(event) => {
    event.preventDefault();
    load();
  }}
>
  <div {...sx(s.row)}>
    <div>
      <h2 id="vacancy-filters-title" {...sx(s.panelTitle)}>
        Открытые вакансии
      </h2>
      <p {...sx(s.muted)} style="margin-top: 0;">
        Условия видны сразу: зарплатная вилка в рублях, формат и компания.
        Сначала показаны вакансии, подходящие вашей подтверждённой категории.
      </p>
    </div>
  </div>
  <div {...sx(vx.filters)}>
    <div>
      <label for="vf-spec" {...sx(s.label)}>Специализация</label><select
        id="vf-spec"
        bind:value={filters.specialization}
        {...sx(s.field)}
        ><option value="">Любая</option>{#each specializations as item}<option
            value={item.value}>{item.label}</option
          >{/each}</select
      >
    </div>
    <div>
      <label for="vf-grade" {...sx(s.label)}>Грейд</label><select
        id="vf-grade"
        bind:value={filters.grade}
        {...sx(s.field)}
        ><option value="">Любой</option>{#each grades as item}<option
            value={item.value}>{item.label}</option
          >{/each}</select
      >
    </div>
    <div>
      <label for="vf-format" {...sx(s.label)}>Формат работы</label><select
        id="vf-format"
        bind:value={filters.workFormat}
        {...sx(s.field)}
        ><option value="">Любой</option>{#each workFormats as item}<option
            value={item.value}>{item.label}</option
          >{/each}</select
      >
    </div>
    <div>
      <label for="vf-salary" {...sx(s.label)}>Зарплата от, ₽ в месяц</label
      ><input
        id="vf-salary"
        type="number"
        min="0"
        step="10000"
        inputmode="numeric"
        placeholder="150000"
        bind:value={filters.salaryFrom}
        {...sx(s.field)}
      />
    </div>
    <div>
      <label for="vf-stack" {...sx(s.label)}>Стек · все через запятую</label
      ><input
        id="vf-stack"
        placeholder="Kotlin, PostgreSQL"
        bind:value={filters.techStack}
        {...sx(s.field)}
      />
    </div>
    <div>
      <label for="vf-q" {...sx(s.label)}>Поиск по тексту</label><input
        id="vf-q"
        type="search"
        maxlength="100"
        placeholder="Компания, город, задача…"
        bind:value={filters.q}
        {...sx(s.field)}
      />
    </div>
  </div>
  <div {...sx(s.inline)}>
    <button type="submit" disabled={busy} {...sx(s.button)}
      >Найти <ActionIcon name="matches" /></button
    ><button
      type="button"
      disabled={busy}
      {...sx(s.outline)}
      onclick={() => {
        filters = emptyFilters();
        load();
      }}>Сбросить фильтры <ActionIcon name="refresh" /></button
    >
  </div>
</form>

{#if blocker}<div {...sx(s.note)}>
    {blocker}
    <div {...sx(s.inline)} style="margin-top: 12px;">
      <button
        type="button"
        {...sx(s.outline)}
        onclick={() =>
          openSection(
            !profile.processingConsent ||
              !profile.fullName ||
              !profile.specialization
              ? "profile"
              : "assessment",
          )}
        >{!profile.processingConsent ||
        !profile.fullName ||
        !profile.specialization
          ? "Открыть профиль"
          : "Перейти к оценке"}
        <ActionIcon name="arrow" /></button
      >
    </div>
  </div>{/if}

{#each vacancies as vacancy (vacancy.id)}<article
    {...sx(s.panel)}
    aria-labelledby={"vacancy-" + vacancy.id}
  >
    <div {...sx(s.row)} style="align-items: flex-start;">
      <div style="min-width: 0;">
        <div {...sx(s.kicker)}>
          {vacancy.companyName.toUpperCase()}
        </div>
        <h2
          id={"vacancy-" + vacancy.id}
          {...sx(s.panelTitle)}
          style="margin-top: 10px; margin-bottom: 4px;"
        >
          {vacancy.title}
        </h2>
        <div {...sx(vx.salary)}>
          {salaryRange(vacancy.salaryMinRub, vacancy.salaryMaxRub)}
          <span {...sx(vx.salaryUnit)}>в месяц</span>
        </div>
      </div>
      {#if vacancy.myApplicationStatus}<span
          {...sx(
            vx.badge,
            vacancy.myApplicationStatus === "accepted" && vx.badgeSuccess,
            vacancy.myApplicationStatus === "rejected" && vx.badgeDanger,
          )}
          >Отклик: {applicationStatusLabels[vacancy.myApplicationStatus]}</span
        >{:else if vacancy.categoryMatch}<span
          {...sx(vx.badge, vx.badgeSuccess)}>Подходит вашей категории</span
        >{/if}
    </div>
    <div>
      <span {...sx(s.tag)}>{specializationName(vacancy.specialization)}</span
      ><span {...sx(s.tag)}>{gradeName(vacancy.grade)}</span><span
        {...sx(s.tag, vx.tagMuted)}
        >{workFormatName(vacancy.workFormat)}{vacancy.city
          ? ` · ${vacancy.city}`
          : ""}</span
      >
    </div>
    {#if vacancy.techStack.length}<div aria-label="Стек вакансии">
        {#each vacancy.techStack as skill}<span
            {...sx(
              s.tag,
              vacancy.stackOverlap.includes(skill) ? vx.tagMatch : vx.tagMuted,
            )}
            title={vacancy.stackOverlap.includes(skill)
              ? "Есть в вашем профиле"
              : undefined}>{skill}</span
          >{/each}
      </div>{/if}
    <p {...sx(s.muted)}>{vacancy.fit}</p>
    <details {...sx(vx.details)}>
      <summary {...sx(vx.summary)}>Описание вакансии и компании</summary>
      <p {...sx(vx.text)}>{vacancy.description}</p>
      {#if vacancy.companyDescription}<p {...sx(s.muted)}>
          {vacancy.companyName}: {vacancy.companyDescription}
        </p>{/if}
      {#if vacancy.companyWebsite}<p {...sx(s.muted)}>
          Сайт: {vacancy.companyWebsite}
        </p>{/if}
    </details>
    {#if !vacancy.myApplicationStatus}
      {#if applyingTo === vacancy.id}<form
          onsubmit={(event) => {
            event.preventDefault();
            apply(vacancy);
          }}
        >
          <label for={"cover-" + vacancy.id} {...sx(s.label)}
            >Сопроводительное письмо · необязательно</label
          ><textarea
            id={"cover-" + vacancy.id}
            maxlength="2000"
            placeholder="Почему вам интересна эта команда и какой опыт будет полезен"
            bind:value={coverNote}
            {...sx(s.textarea)}
          ></textarea>
          <p {...sx(s.muted)} style="margin-top: 0;">
            После отклика компания увидит ваши контакты (email и телефон),
            подтверждённую категорию и профиль. Пока решения нет, отклик можно
            отозвать.
          </p>
          <div {...sx(s.inline)}>
            <button type="submit" disabled={busy} {...sx(s.button)}
              >Отправить отклик <ActionIcon name="send" /></button
            ><button
              type="button"
              {...sx(s.outline)}
              onclick={() => {
                applyingTo = null;
                coverNote = "";
              }}>Отмена <ActionIcon name="close" /></button
            >
          </div>
        </form>{:else}<button
          type="button"
          disabled={busy || !!blocker}
          {...sx(s.button)}
          onclick={() => {
            applyingTo = vacancy.id;
            coverNote = "";
          }}>Откликнуться <ActionIcon name="send" /></button
        >{/if}
    {:else}<button
        type="button"
        {...sx(s.outline)}
        onclick={() => openSection("applications")}
        >Мои отклики <ActionIcon name="applications" /></button
      >{/if}
  </article>{:else}{#if loaded}<div {...sx(s.panel)}>
      <p {...sx(s.muted)}>
        По этим условиям открытых вакансий нет. Измените фильтры — или дождитесь
        приглашения: компании сами находят специалистов по подтверждённой
        категории.
      </p>
    </div>{/if}{/each}
