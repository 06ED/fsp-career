<script lang="ts">
  import { onMount, tick } from "svelte";
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
    shortDate,
    specializationName,
    splitList,
    workFormatName,
    workFormats,
  } from "./vacancyLabels";
  import type {
    Applicant,
    EmployerVacancy,
    RunAction,
    VacancyInput,
  } from "./types";

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

  const contactLinkStyle =
    "color: #1f7a3d; font-weight: 600; text-decoration: underline;";
  // Form and applicants open above the list; bring them into view when started from a card below.
  let workspace = $state<HTMLElement>();
  async function reveal() {
    await tick();
    workspace?.scrollIntoView({ block: "start" });
  }

  async function load() {
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
      reopen
        ? "Вакансия снова открыта."
        : "Вакансия закрыта. Отклики сохранены.",
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
        await Promise.all(
          fresh.map((a) => vacancyApi.setApplicationStatus(a.id, "viewed")),
        );
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

<div {...sx(s.panel)}>
  <div {...sx(s.row)}>
    <div>
      <h2 {...sx(s.panelTitle)}>Вакансии компании</h2>
      <p {...sx(s.muted)} style="margin-top: 0;">
        Классический путь в дополнение к подбору: опубликуйте вакансию с
        обязательной вилкой «от–до» в рублях, кандидаты откликнутся сами. По
        отклику вы видите контакты, подтверждённую категорию и балл оценки.
      </p>
    </div>
    <button
      type="button"
      disabled={busy}
      {...sx(s.button)}
      onclick={() => startEdit(null)}
      >Новая вакансия <ActionIcon name="add" /></button
    >
  </div>
</div>

<div bind:this={workspace} style="scroll-margin-top: 16px;"></div>
{#if editing}<form
    {...sx(s.panel)}
    aria-labelledby="vacancy-form-title"
    onsubmit={(event) => {
      event.preventDefault();
      saveVacancy();
    }}
  >
    <div {...sx(s.row)}>
      <h2 id="vacancy-form-title" {...sx(s.panelTitle)}>
        {editing.id ? "Редактирование вакансии" : "Новая вакансия"}
      </h2>
      <button type="button" {...sx(s.outline)} onclick={() => (editing = null)}
        >Закрыть <ActionIcon name="close" /></button
      >
    </div>
    <label for="vac-title" {...sx(s.label)}>Название должности</label><input
      id="vac-title"
      required
      maxlength="200"
      placeholder="Backend-разработчик (Kotlin)"
      bind:value={editing.draft.title}
      {...sx(s.field)}
    />
    <div {...sx(vx.filters)}>
      <div>
        <label for="vac-spec" {...sx(s.label)}>Специализация</label><select
          id="vac-spec"
          required
          bind:value={editing.draft.specialization}
          {...sx(s.field)}
          ><option value="">Выберите направление</option
          >{#each specializations as item}<option value={item.value}
              >{item.label}</option
            >{/each}</select
        >
      </div>
      <div>
        <label for="vac-grade" {...sx(s.label)}>Требуемый грейд</label><select
          id="vac-grade"
          required
          bind:value={editing.draft.grade}
          {...sx(s.field)}
          >{#each grades as item}<option value={item.value}>{item.label}</option
            >{/each}</select
        >
      </div>
      <div>
        <label for="vac-format" {...sx(s.label)}>Формат работы</label><select
          id="vac-format"
          required
          bind:value={editing.draft.workFormat}
          {...sx(s.field)}
          >{#each workFormats as item}<option value={item.value}
              >{item.label}</option
            >{/each}</select
        >
      </div>
      <div>
        <label for="vac-min" {...sx(s.label)}>Зарплата от, ₽ в месяц</label
        ><input
          id="vac-min"
          type="number"
          required
          min="1"
          max="10000000"
          step="1000"
          aria-describedby="vac-salary-hint"
          bind:value={editing.draft.salaryMinRub}
          {...sx(s.field)}
        />
      </div>
      <div>
        <label for="vac-max" {...sx(s.label)}>Зарплата до, ₽ в месяц</label
        ><input
          id="vac-max"
          type="number"
          required
          min={editing.draft.salaryMinRub || 1}
          max="10000000"
          step="1000"
          aria-describedby="vac-salary-hint"
          aria-invalid={salaryInvalid}
          bind:value={editing.draft.salaryMaxRub}
          {...sx(s.field)}
        />
      </div>
      <div>
        <label for="vac-city" {...sx(s.label)}
          >Город{editing.draft.workFormat === "remote"
            ? " · необязательно"
            : ""}</label
        ><input
          id="vac-city"
          maxlength="100"
          required={editing.draft.workFormat !== "remote"}
          placeholder="Москва"
          bind:value={editing.draft.city}
          {...sx(s.field)}
        />
      </div>
    </div>
    <p
      id="vac-salary-hint"
      {...sx(salaryInvalid ? s.error : s.muted)}
      style="margin-top: 0;"
    >
      Вилка обязательна: кандидат видит условия до начала общения. «До» не может
      быть меньше «от».
    </p>
    <label for="vac-stack" {...sx(s.label)}>Стек · через запятую</label><input
      id="vac-stack"
      placeholder="Kotlin, PostgreSQL, Kafka"
      bind:value={stackText}
      {...sx(s.field)}
    />
    <label for="vac-description" {...sx(s.label)}
      >Задачи, команда и условия</label
    ><textarea
      id="vac-description"
      required
      maxlength="6000"
      rows="8"
      bind:value={editing.draft.description}
      {...sx(s.textarea)}
    ></textarea>
    <button type="submit" disabled={busy || salaryInvalid} {...sx(s.button)}
      >{editing.id ? "Сохранить изменения" : "Опубликовать вакансию"}
      <ActionIcon name="save" /></button
    >
  </form>{/if}

{#if selected}<section {...sx(s.panel)} aria-labelledby="applicants-title">
    <div {...sx(s.row)}>
      <div>
        <div {...sx(s.kicker)}>ОТКЛИКИ НА ВАКАНСИЮ</div>
        <h2
          id="applicants-title"
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
    <p {...sx(s.muted)} style="margin-top: 0;">
      Сначала — кандидаты, чья подтверждённая тестом категория соответствует
      вакансии, затем по грейду и баллу оценки. Контакты открыты, потому что
      кандидат откликнулся сам.
    </p>
    {#each applicants as applicant (applicant.id)}<article
        style="border-top: 1px solid #ebebed; padding: 20px 0;"
        aria-labelledby={"applicant-" + applicant.id}
      >
        <div {...sx(s.row)} style="align-items: flex-start;">
          <div style="min-width: 0;">
            <div {...sx(s.kicker)}>
              {applicant.assessedGrade
                ? `ПОДТВЕРЖДЁННАЯ КАТЕГОРИЯ / ${specializationName(applicant.specialization)} · ${gradeName(applicant.assessedGrade)}`.toUpperCase()
                : "КАТЕГОРИЯ НЕ ПОДТВЕРЖДЕНА"}
            </div>
            <h3
              id={"applicant-" + applicant.id}
              {...sx(s.panelTitle)}
              style="margin-top: 10px;"
            >
              {applicant.fullName}
            </h3>
          </div>
          <div style="text-align: right;">
            {#if applicant.score != null}<div {...sx(s.score)}>
                {applicant.score}<span style="font-size: 14px;"> / 100</span>
              </div>{/if}
            <span
              {...sx(
                vx.badge,
                applicant.status === "accepted" && vx.badgeSuccess,
                applicant.status === "rejected" && vx.badgeDanger,
              )}>{applicationStatusLabels[applicant.status]}</span
            >
          </div>
        </div>
        <p {...sx(s.muted)}>
          {#if applicant.categoryMatch}<span {...sx(s.tag, vx.tagMatch)}
              >Категория подходит</span
            >{/if}{applicant.fit}
        </p>
        {#if applicant.techStack.length}<div aria-label="Стек кандидата">
            {#each applicant.techStack as skill}<span
                {...sx(
                  s.tag,
                  applicant.stackOverlap.some(
                    (item) => item.toLowerCase() === skill.toLowerCase(),
                  )
                    ? vx.tagMatch
                    : vx.tagMuted,
                )}>{skill}</span
              >{/each}
          </div>{/if}
        <!-- Global `a { color: inherit }` is unlayered and beats layered StyleX, hence inline link styles. -->
        <div {...sx(vx.contacts)}>
          <strong>Контакты кандидата</strong>
          {#if applicant.contactEmail}<div>
              Email: <a
                style={contactLinkStyle}
                href={"mailto:" + applicant.contactEmail}
                >{applicant.contactEmail}</a
              >
            </div>{/if}
          {#if applicant.contactPhone}<div>
              Телефон: <a
                style={contactLinkStyle}
                href={"tel:" + applicant.contactPhone.replace(/[^\d+]/g, "")}
                >{applicant.contactPhone}</a
              >
            </div>{/if}
        </div>
        {#if applicant.coverNote}<div {...sx(vx.quote)}>
            {applicant.coverNote}
          </div>{/if}
        {#if applicant.experience}<details {...sx(vx.details)}>
            <summary {...sx(vx.summary)}>Опыт из профиля</summary>
            <p {...sx(vx.text)}>{applicant.experience}</p>
          </details>{/if}
        <p {...sx(s.muted)} style="margin-top: 0;">
          Отклик от {shortDate(
            applicant.createdAt,
          )}{applicant.lastTaskActivityAt
            ? ` · последнее регулярное задание ${shortDate(applicant.lastTaskActivityAt)}`
            : ""}
        </p>
        {#if applicant.status === "sent" || applicant.status === "viewed"}<div
            {...sx(s.inline)}
          >
            <button
              type="button"
              disabled={busy}
              {...sx(s.button)}
              onclick={() => decide(applicant, "accepted")}
              >Принять отклик <ActionIcon name="accept" /></button
            ><button
              type="button"
              disabled={busy}
              {...sx(s.danger)}
              onclick={() => decide(applicant, "rejected")}
              >Отказать <ActionIcon name="reject" /></button
            >
          </div>{/if}
      </article>{:else}<p {...sx(s.muted)}>
        Откликов пока нет. Кандидатов можно найти и пригласить в разделе «Подбор
        кандидатов».
      </p>{/each}
  </section>{/if}

{#each vacancies as vacancy (vacancy.id)}<article
    {...sx(s.panel)}
    aria-labelledby={"own-vacancy-" + vacancy.id}
  >
    <div {...sx(s.row)} style="align-items: flex-start;">
      <div style="min-width: 0;">
        <div {...sx(s.kicker)}>
          {specializationName(vacancy.specialization).toUpperCase()} · {gradeName(
            vacancy.grade,
          ).toUpperCase()}
        </div>
        <h2
          id={"own-vacancy-" + vacancy.id}
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
      <span
        {...sx(
          vx.badge,
          vacancy.status === "open" ? vx.badgeSuccess : vx.badgeMuted,
        )}>{vacancy.status === "open" ? "Открыта" : "Закрыта"}</span
      >
    </div>
    <div>
      <span {...sx(s.tag, vx.tagMuted)}
        >{workFormatName(vacancy.workFormat)}{vacancy.city
          ? ` · ${vacancy.city}`
          : ""}</span
      >{#each vacancy.techStack as skill}<span {...sx(s.tag)}>{skill}</span
        >{/each}
    </div>
    <p {...sx(s.muted)}>
      Откликов: {vacancy.applications.total} · новых: {vacancy.applications
        .sent} · просмотрено: {vacancy.applications.viewed} · принято: {vacancy
        .applications.accepted} · отказ: {vacancy.applications.rejected}.
      Обновлена
      {shortDate(vacancy.updatedAt)}.
    </p>
    <div {...sx(s.inline)}>
      <button
        type="button"
        disabled={busy}
        {...sx(s.button)}
        onclick={() => openApplicants(vacancy)}
        >Отклики ({vacancy.applications.total}) <ActionIcon
          name="applications"
        /></button
      ><button
        type="button"
        disabled={busy}
        {...sx(s.outline)}
        onclick={() => startEdit(vacancy)}
        >Редактировать <ActionIcon name="edit" /></button
      ><button
        type="button"
        disabled={busy}
        {...sx(vacancy.status === "open" ? s.danger : s.outline)}
        onclick={() => toggle(vacancy)}
        >{vacancy.status === "open" ? "Закрыть вакансию" : "Открыть снова"}
        <ActionIcon
          name={vacancy.status === "open" ? "archive" : "undo"}
        /></button
      >
    </div>
  </article>{:else}{#if loaded && !editing}<div {...sx(s.panel)}>
      <p {...sx(s.muted)}>
        Вакансий пока нет. Опубликуйте первую — или выйдите на кандидатов
        напрямую через подборку.
      </p>
    </div>{/if}{/each}
