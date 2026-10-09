<script lang="ts">
  import { onMount, tick } from "svelte";
  import { ActionIcon, EmptyState } from "$lib/shared/ui";
  import { vacancyApi } from "$lib/entities/vacancy";
  import { specializations } from "$lib/entities/candidate";
  import {
    applicationStatusLabels,
    gradeName,
    grades,
    salaryRange,
    shortDate,
    specializationName,
    splitList,
    workFormatName,
    workFormats,
  } from "$lib/entities/vacancy";
  import type { Applicant, EmployerVacancy, VacancyInput } from "$lib/entities/vacancy";
  import type { RunAction } from "$lib/shared/api";

  let { busy, run }: { busy: boolean; run: RunAction } = $props();

  const blank = (): VacancyInput => ({
    title: "",
    description: "",
    specialization: "",
    grade: "middle",
    techStack: [],
    salaryMinRub: 150000,
    salaryMaxRub: 220000,
    workFormat: "hybrid",
    city: "",
  });
  let vacancies = $state<EmployerVacancy[]>([]);
  let loaded = $state(false);
  let editing = $state<{ id: string | null; draft: VacancyInput } | null>(null);
  let stackText = $state("");
  let selected = $state<EmployerVacancy | null>(null);
  let applicants = $state<Applicant[]>([]);
  let salaryInvalid = $derived(
    !!editing &&
      (!(editing.draft.salaryMinRub > 0) ||
        !(editing.draft.salaryMaxRub >= editing.draft.salaryMinRub)),
  );

  // Form and applicants open above the list; bring them into view when started from a card below.
  let workspace = $state<HTMLElement>();
  async function reveal() {
    await tick();
    workspace?.scrollIntoView({ block: "start" });
  }

  async function load() {
    loaded = false;
    await run(async () => {
      vacancies = await vacancyApi.employerVacancies();
      loaded = true;
    }, "");
  }
  function startEdit(vacancy: EmployerVacancy | null) {
    selected = null;
    editing = vacancy
      ? {
          id: vacancy.id,
          draft: {
            title: vacancy.title,
            description: vacancy.description,
            specialization: vacancy.specialization,
            grade: vacancy.grade,
            techStack: [...vacancy.techStack],
            salaryMinRub: vacancy.salaryMinRub,
            salaryMaxRub: vacancy.salaryMaxRub,
            workFormat: vacancy.workFormat,
            city: vacancy.city,
          },
        }
      : { id: null, draft: blank() };
    stackText = editing.draft.techStack.join(", ");
    reveal();
  }
  async function saveVacancy() {
    if (!editing) return;
    const { id, draft } = editing;
    const payload: VacancyInput = {
      ...draft,
      techStack: splitList(stackText),
      salaryMinRub: Number(draft.salaryMinRub),
      salaryMaxRub: Number(draft.salaryMaxRub),
    };
    await run(
      async () => {
        if (id) await vacancyApi.updateVacancy(id, payload);
        else await vacancyApi.createVacancy(payload);
        editing = null;
        vacancies = await vacancyApi.employerVacancies();
      },
      id
        ? "Вакансия обновлена."
        : "Вакансия опубликована. Кандидаты видят её вместе с зарплатной вилкой.",
    );
  }
  async function toggle(vacancy: EmployerVacancy) {
    const reopen = vacancy.status === "closed";
    await run(
      async () => {
        await vacancyApi.setVacancyOpen(vacancy.id, reopen);
        vacancies = await vacancyApi.employerVacancies();
        if (selected?.id === vacancy.id)
          selected = vacancies.find((v) => v.id === vacancy.id) ?? null;
      },
      reopen ? "Вакансия снова открыта." : "Вакансия закрыта. Отклики сохранены.",
    );
  }
  // Opening the applications marks new ones as "viewed": the candidate then sees that
  // the company has looked at the application (spec 2.2 rule 3).
  async function openApplicants(vacancy: EmployerVacancy) {
    editing = null;
    await run(async () => {
      let result = await vacancyApi.applicants(vacancy.id);
      const fresh = result.applications.filter((a) => a.status === "sent");
      if (fresh.length) {
        await Promise.all(fresh.map((a) => vacancyApi.setApplicationStatus(a.id, "viewed")));
        result = await vacancyApi.applicants(vacancy.id);
        vacancies = await vacancyApi.employerVacancies();
      }
      selected = result.vacancy;
      applicants = result.applications;
    }, "");
    if (selected) reveal();
  }
  async function decide(applicant: Applicant, status: "accepted" | "rejected") {
    await run(
      async () => {
        await vacancyApi.setApplicationStatus(applicant.id, status);
        if (selected) {
          const result = await vacancyApi.applicants(selected.id);
          selected = result.vacancy;
          applicants = result.applications;
        }
        vacancies = await vacancyApi.employerVacancies();
      },
      status === "accepted"
        ? `Отклик ${applicant.fullName} принят. Свяжитесь с кандидатом по контактам из карточки.`
        : "Кандидат получит статус «Отказ».",
    );
  }
  onMount(() => {
    load();
  });
</script>

<div class="d-ui-panel">
  <div class="d-ui-row">
    <div>
      <h2 class="d-ui-panel-title">Вакансии компании</h2>
      <p class="d-ui-muted" style="margin-top: 0;">
        Классический путь в дополнение к подбору: опубликуйте вакансию с обязательной вилкой «от–до»
        в рублях, кандидаты откликнутся сами. По отклику вы видите контакты, подтверждённую
        категорию и балл оценки.
      </p>
    </div>
    <button type="button" disabled={busy} class="d-ui-button" onclick={() => startEdit(null)}>
      Новая вакансия <ActionIcon name="add" />
    </button>
  </div>
</div>

<div bind:this={workspace} style="scroll-margin-top: 16px;"></div>
{#if editing}
  <form
    class="d-ui-panel"
    aria-labelledby="vacancy-form-title"
    onsubmit={(event) => {
      event.preventDefault();
      saveVacancy();
    }}
  >
    <div class="d-ui-row">
      <h2 id="vacancy-form-title" class="d-ui-panel-title">
        {editing.id ? "Редактирование вакансии" : "Новая вакансия"}
      </h2>
      <button type="button" class="d-ui-outline" onclick={() => (editing = null)}>
        Закрыть <ActionIcon name="close" />
      </button>
    </div>
    <label for="vac-title" class="d-ui-label">Название должности</label>
    <input
      id="vac-title"
      required
      maxlength="200"
      placeholder="Backend-разработчик (Kotlin)"
      bind:value={editing.draft.title}
      class="d-ui-field"
    />
    <div class="d-vx-filters">
      <div>
        <label for="vac-spec" class="d-ui-label">Специализация</label>
        <select id="vac-spec" required bind:value={editing.draft.specialization} class="d-ui-field">
          <option value="">Выберите направление</option>
          {#each specializations as item}
            <option value={item.value}>{item.label}</option>
          {/each}
        </select>
      </div>
      <div>
        <label for="vac-grade" class="d-ui-label">Требуемый грейд</label>
        <select id="vac-grade" required bind:value={editing.draft.grade} class="d-ui-field">
          {#each grades as item}
            <option value={item.value}>{item.label}</option>
          {/each}
        </select>
      </div>
      <div>
        <label for="vac-format" class="d-ui-label">Формат работы</label>
        <select id="vac-format" required bind:value={editing.draft.workFormat} class="d-ui-field">
          {#each workFormats as item}
            <option value={item.value}>{item.label}</option>
          {/each}
        </select>
      </div>
      <div>
        <label for="vac-min" class="d-ui-label">Зарплата от, ₽ в месяц</label>
        <input
          id="vac-min"
          type="number"
          required
          min="1"
          max="10000000"
          step="1000"
          aria-describedby="vac-salary-hint"
          bind:value={editing.draft.salaryMinRub}
          class="d-ui-field"
        />
      </div>
      <div>
        <label for="vac-max" class="d-ui-label">Зарплата до, ₽ в месяц</label>
        <input
          id="vac-max"
          type="number"
          required
          min={editing.draft.salaryMinRub || 1}
          max="10000000"
          step="1000"
          aria-describedby="vac-salary-hint"
          aria-invalid={salaryInvalid}
          bind:value={editing.draft.salaryMaxRub}
          class="d-ui-field"
        />
      </div>
      <div>
        <label for="vac-city" class="d-ui-label">
          Город{editing.draft.workFormat === "remote" ? " · необязательно" : ""}
        </label>
        <input
          id="vac-city"
          maxlength="100"
          required={editing.draft.workFormat !== "remote"}
          placeholder="Москва"
          bind:value={editing.draft.city}
          class="d-ui-field"
        />
      </div>
    </div>
    <p
      id="vac-salary-hint"
      class={salaryInvalid ? "d-ui-error" : "d-ui-muted"}
      style="margin-top: 0;"
    >
      Вилка обязательна: кандидат видит условия до начала общения. «До» не может быть меньше «от».
    </p>
    <label for="vac-stack" class="d-ui-label">Стек · через запятую</label>
    <input
      id="vac-stack"
      placeholder="Kotlin, PostgreSQL, Kafka"
      bind:value={stackText}
      class="d-ui-field"
    />
    <label for="vac-description" class="d-ui-label">Задачи, команда и условия</label>
    <textarea
      id="vac-description"
      required
      maxlength="6000"
      rows="8"
      bind:value={editing.draft.description}
      class="d-ui-textarea"
    ></textarea>
    <button type="submit" disabled={busy || salaryInvalid} class="d-ui-button">
      {editing.id ? "Сохранить изменения" : "Опубликовать вакансию"}
      <ActionIcon name="save" />
    </button>
  </form>
{/if}

{#if selected}
  <section class="d-ui-panel" aria-labelledby="applicants-title">
    <div class="d-ui-row">
      <div>
        <div class="d-ui-kicker">ОТКЛИКИ НА ВАКАНСИЮ</div>
        <h2 id="applicants-title" class="d-ui-panel-title" style="margin-top: 10px;">
          {selected.title}
        </h2>
      </div>
      <button type="button" class="d-ui-outline" onclick={() => (selected = null)}>
        Закрыть <ActionIcon name="close" />
      </button>
    </div>
    <p class="d-ui-muted" style="margin-top: 0;">
      Сначала — кандидаты, чья подтверждённая тестом категория соответствует вакансии, затем по
      грейду и баллу оценки. Контакты открыты, потому что кандидат откликнулся сам.
    </p>
    {#each applicants as applicant (applicant.id)}
      <article
        style="border-top: 1px solid var(--border); padding: 20px 0;"
        aria-labelledby={"applicant-" + applicant.id}
      >
        <div class="d-ui-row" style="align-items: flex-start;">
          <div style="min-width: 0;">
            <div class="d-ui-kicker">
              {applicant.assessedGrade
                ? `ПОДТВЕРЖДЁННАЯ КАТЕГОРИЯ / ${specializationName(applicant.specialization)} · ${gradeName(applicant.assessedGrade)}`.toUpperCase()
                : "КАТЕГОРИЯ НЕ ПОДТВЕРЖДЕНА"}
            </div>
            <h3 id={"applicant-" + applicant.id} class="d-ui-panel-title" style="margin-top: 10px;">
              {applicant.fullName}
            </h3>
          </div>
          <div style="text-align: right;">
            {#if applicant.score != null}
              <div class="d-ui-score">
                {applicant.score}
                <span style="font-size: 14px;">/ 100</span>
              </div>
            {/if}
            <span
              class={`d-vx-badge ${applicant.status === "accepted" ? "d-vx-badge-success" : ""} ${applicant.status === "rejected" ? "d-vx-badge-danger" : ""}`}
            >
              {applicationStatusLabels[applicant.status]}
            </span>
          </div>
        </div>
        <p class="d-ui-muted">
          {#if applicant.categoryMatch}
            <span class="d-ui-tag d-vx-tag-match">Категория подходит</span>
          {/if}
          {applicant.fit}
        </p>
        {#if applicant.techStack.length}
          <div aria-label="Стек кандидата">
            {#each applicant.techStack as skill}
              <span
                class={`d-ui-tag ${
                  applicant.stackOverlap.some((item) => item.toLowerCase() === skill.toLowerCase())
                    ? "d-vx-tag-match"
                    : "d-vx-tag-muted"
                }`}
              >
                {skill}
              </span>
            {/each}
          </div>
        {/if}
        <!-- Keep contact links green against the success panel. -->
        <div class="d-vx-contacts">
          <strong>Контакты кандидата</strong>
          {#if applicant.contactEmail}
            <div>
              Email: <a class="d-vx-contact-link" href={"mailto:" + applicant.contactEmail}>
                {applicant.contactEmail}
              </a>
            </div>
          {/if}
          {#if applicant.contactPhone}
            <div>
              Телефон: <a
                class="d-vx-contact-link"
                href={"tel:" + applicant.contactPhone.replace(/[^\d+]/g, "")}
              >
                {applicant.contactPhone}
              </a>
            </div>
          {/if}
        </div>
        {#if applicant.coverNote}
          <div class="d-vx-quote">
            {applicant.coverNote}
          </div>
        {/if}
        {#if applicant.experience}
          <details class="d-vx-details">
            <summary class="d-vx-summary">Опыт из профиля</summary>
            <p class="d-vx-text">{applicant.experience}</p>
          </details>
        {/if}
        <p class="d-ui-muted" style="margin-top: 0;">
          Отклик от {shortDate(applicant.createdAt)}{applicant.lastTaskActivityAt
            ? ` · последнее регулярное задание ${shortDate(applicant.lastTaskActivityAt)}`
            : ""}
        </p>
        {#if applicant.status === "sent" || applicant.status === "viewed"}
          <div class="d-ui-inline">
            <button
              type="button"
              disabled={busy}
              class="d-ui-button"
              onclick={() => decide(applicant, "accepted")}
            >
              Принять отклик <ActionIcon name="accept" />
            </button>
            <button
              type="button"
              disabled={busy}
              class="d-ui-danger"
              onclick={() => decide(applicant, "rejected")}
            >
              Отказать <ActionIcon name="reject" />
            </button>
          </div>
        {/if}
      </article>
    {:else}
      <EmptyState
        icon="applications"
        title="Откликов пока нет"
        description="Кандидатов можно найти и пригласить в разделе «Подбор кандидатов»."
      />
    {/each}
  </section>
{/if}

{#each vacancies as vacancy (vacancy.id)}
  <article class="d-ui-panel" aria-labelledby={"own-vacancy-" + vacancy.id}>
    <div class="d-ui-row" style="align-items: flex-start;">
      <div style="min-width: 0;">
        <div class="d-ui-kicker">
          {specializationName(vacancy.specialization).toUpperCase()} · {gradeName(
            vacancy.grade,
          ).toUpperCase()}
        </div>
        <h2
          id={"own-vacancy-" + vacancy.id}
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
      <span
        class={`d-vx-badge ${vacancy.status === "open" ? "d-vx-badge-success" : "d-vx-badge-muted"}`}
      >
        {vacancy.status === "open" ? "Открыта" : "Закрыта"}
      </span>
    </div>
    <div>
      <span class="d-ui-tag d-vx-tag-muted">
        {workFormatName(vacancy.workFormat)}{vacancy.city ? ` · ${vacancy.city}` : ""}
      </span>
      {#each vacancy.techStack as skill}
        <span class="d-ui-tag">{skill}</span>
      {/each}
    </div>
    <p class="d-ui-muted">
      Откликов: {vacancy.applications.total} · новых: {vacancy.applications.sent} · просмотрено: {vacancy
        .applications.viewed} · принято: {vacancy.applications.accepted} · отказ: {vacancy
        .applications.rejected}. Обновлена
      {shortDate(vacancy.updatedAt)}.
    </p>
    <div class="d-ui-inline">
      <button
        type="button"
        disabled={busy}
        class="d-ui-button"
        onclick={() => openApplicants(vacancy)}
      >
        Отклики ({vacancy.applications.total}) <ActionIcon name="applications" />
      </button>
      <button type="button" disabled={busy} class="d-ui-outline" onclick={() => startEdit(vacancy)}>
        Редактировать <ActionIcon name="edit" />
      </button>
      <button
        type="button"
        disabled={busy}
        class={vacancy.status === "open" ? "d-ui-danger" : "d-ui-outline"}
        onclick={() => toggle(vacancy)}
      >
        {vacancy.status === "open" ? "Закрыть вакансию" : "Открыть снова"}
        <ActionIcon name={vacancy.status === "open" ? "archive" : "undo"} />
      </button>
    </div>
  </article>
{:else}
  {#if loaded && !editing}
    <EmptyState
      icon="vacancies"
      title="Вакансий пока нет"
      description="Опубликуйте первую вакансию или найдите кандидатов напрямую через подборку."
    >
      {#snippet action()}
        <button class="d-ui-button" onclick={() => startEdit(null)}>
          Новая вакансия <ActionIcon name="add" />
        </button>
      {/snippet}
    </EmptyState>
  {/if}
{/each}
