<script lang="ts">
  import { ActionIcon } from "$lib/shared/ui";
  import { formatDate, gradeLabel } from "$lib/entities/assessment";
  import type {
    AssessmentCatalog,
    AssessmentEligibility,
    AssessmentSurvey,
    GradeId,
  } from "$lib/entities/assessment";

  let {
    catalog,
    survey = $bindable(),
    eligibility,
    busy,
    onSpecializationChange,
    onStart,
    onCancel,
  }: {
    catalog: AssessmentCatalog;
    survey: AssessmentSurvey;
    eligibility: AssessmentEligibility | null;
    busy: boolean;
    onSpecializationChange: (specialization: string) => Promise<void>;
    onStart: (grade: GradeId, vacancyId: string | null) => Promise<void>;
    onCancel: () => void;
  } = $props();

  let step = $state<1 | 2 | 3>(1);
  let grade = $state<GradeId | null>(null);
  let vacancyId = $state<string | null>(null);

  const stepTitles = ["01 · ОПРОС", "02 · ГРЕЙД", "03 · ФОРМАТ ТЕСТА"];
  let surveyValid = $derived(
    !!survey.specialization &&
      !!survey.industry &&
      !!survey.workFormat &&
      survey.experienceYears >= 0 &&
      survey.experienceYears <= 50,
  );
  let options = $derived(
    eligibility?.specialization === survey.specialization ? eligibility.options : [],
  );
  let vacancies = $derived(
    catalog.vacancies.filter((v) => v.specialization === survey.specialization),
  );
  // Typical experience per grade — a soft hint only; the test decides.
  let experienceHint = $derived(
    grade === "senior" && survey.experienceYears < 4
      ? "Senior обычно подразумевает 5+ лет опыта. Тест всё равно можно пройти — грейд определит результат."
      : grade === "junior" && survey.experienceYears >= 5
        ? "С таким опытом стоит попробовать Middle или Senior: при уверенном результате сразу откроется тест на уровень выше."
        : "",
  );

  function option(id: string) {
    return options.find((o) => o.grade === id);
  }
  function toggleRole(id: string) {
    survey.teamRoles = survey.teamRoles.includes(id)
      ? survey.teamRoles.filter((role) => role !== id)
      : [...survey.teamRoles, id];
  }
  async function toGrades() {
    await onSpecializationChange(survey.specialization);
    if (grade && !option(grade)?.allowed) grade = null;
    step = 2;
  }
</script>

<div class="d-ui-panel">
  <div class="d-a-steps">
    {#each stepTitles as title, index (title)}
      <span
        class={`d-a-step ${index + 1 === step ? "d-a-step-active" : ""} ${index + 1 < step ? "d-a-step-done" : ""}`}
      >
        {title}
      </span>
    {/each}
  </div>

  {#if step === 1}
    <h2 class="d-ui-panel-title">Расскажите о себе</h2>
    <p class="d-ui-muted">
      Опрос определяет направление и отрасль вашей категории. Ответы сохраняются вместе с попыткой и
      попадают в профиль.
    </p>
    <div class="d-ui-grid">
      <div>
        <label for="survey-spec" class="d-ui-label">Специализация</label>
        <select id="survey-spec" bind:value={survey.specialization} class="d-ui-field">
          <option value="">Выберите направление</option>
          {#each catalog.specializations as spec (spec.id)}
            <option value={spec.id}>{spec.label}</option>
          {/each}
        </select>
      </div>
      <div>
        <label for="survey-industry" class="d-ui-label">Отрасль</label>
        <select id="survey-industry" bind:value={survey.industry} class="d-ui-field">
          <option value="">Выберите отрасль</option>
          {#each catalog.industries as industry (industry.id)}
            <option value={industry.id}>{industry.label}</option>
          {/each}
        </select>
      </div>
      <div>
        <label for="survey-years" class="d-ui-label">Опыт в профессии, лет</label>
        <input
          id="survey-years"
          type="number"
          min="0"
          max="50"
          bind:value={survey.experienceYears}
          class="d-ui-field"
        />
      </div>
      <div>
        <span class="d-ui-label">Формат работы</span>
        <div class="d-ui-inline" style="margin-bottom: 18px;">
          {#each catalog.workFormats as format (format.id)}
            <label
              class={`d-a-chip d-a-chip-toggle ${survey.workFormat === format.id ? "d-a-chip-on" : ""}`}
            >
              <input
                type="radio"
                name="work-format"
                value={format.id}
                bind:group={survey.workFormat}
                style="margin: 0;"
              />
              {format.label}
            </label>
          {/each}
        </div>
      </div>
    </div>
    <span class="d-ui-label">Роли в команде, которые вы выполняли</span>
    <div style="margin-bottom: 18px;">
      {#each catalog.teamRoles as role (role.id)}
        <label
          class={`d-a-chip d-a-chip-toggle ${survey.teamRoles.includes(role.id) ? "d-a-chip-on" : ""}`}
        >
          <input
            type="checkbox"
            checked={survey.teamRoles.includes(role.id)}
            onchange={() => toggleRole(role.id)}
            style="margin: 0;"
          />
          {role.label}
        </label>
      {/each}
    </div>
    <div class="d-ui-inline">
      <button class="d-ui-button" disabled={!surveyValid || busy} onclick={toGrades}>
        Далее: выбор грейда <ActionIcon name="arrow" />
      </button>
      <button class="d-ui-outline" onclick={onCancel}>Отмена</button>
    </div>
  {:else if step === 2}
    <h2 class="d-ui-panel-title">На какой грейд вы претендуете?</h2>
    <p class="d-ui-muted">
      Тест строится под заявленный уровень. Если не получится — сразу можно пройти тест на уровень
      ниже; уверенный результат открывает тест на уровень выше. Подтверждённый грейд не понижается.
    </p>
    <div class="d-a-cards">
      {#each catalog.grades as g (g.id)}
        {@const o = option(g.id)}
        <button
          type="button"
          disabled={!o?.allowed}
          aria-pressed={grade === g.id}
          class={`d-a-card ${grade === g.id ? "d-a-card-selected" : ""} ${!o?.allowed ? "d-a-card-disabled" : ""}`}
          onclick={() => (grade = g.id as GradeId)}
        >
          <span class="d-a-card-title">{g.label}</span>
          <span class="d-a-small">{g.description}</span>
          <span class="d-a-small">Порог подтверждения: {g.scoreCut} из 100</span>
          {#if o && !o.allowed}
            <span class="d-a-small" style="color: var(--danger);">
              {o.message}{o.availableAt ? ` · доступно с ${formatDate(o.availableAt)}` : ""}
            </span>
          {:else if o && o.reason !== "first-attempt" && o.reason !== "cooldown-over"}
            <span class="d-a-small" style="color: var(--accent);">{o.message}</span>
          {/if}
        </button>
      {/each}
    </div>
    {#if experienceHint}
      <div class="d-ui-note">{experienceHint}</div>
    {/if}
    <div class="d-ui-inline">
      <button class="d-ui-button" disabled={!grade} onclick={() => (step = 3)}>
        Далее <ActionIcon name="arrow" />
      </button>
      <button class="d-ui-outline" onclick={() => (step = 1)}>Назад</button>
    </div>
  {:else}
    <h2 class="d-ui-panel-title">Формат теста</h2>
    <p class="d-ui-muted">
      Можно пройти общий тест по специализации или тест «под вакансию»: тогда темы заданий берутся
      из описания вакансии. Сложность и шкала оценки одинаковы — результаты сопоставимы.
    </p>
    <label class="d-ui-answer">
      <input type="radio" name="vacancy" value={null} bind:group={vacancyId} />
      <span>
        <strong>Общий тест</strong>
        — темы по профилю специализации
      </span>
    </label>
    {#each vacancies as vacancy (vacancy.id)}
      <label class="d-ui-answer">
        <input type="radio" name="vacancy" value={vacancy.id} bind:group={vacancyId} />
        <span>
          <strong>{vacancy.title}</strong>
          <br />
          <span class="d-a-small">
            {vacancy.company} · уровень вакансии {gradeLabel(vacancy.grade)}
          </span>
        </span>
      </label>
    {/each}
    <div class="d-ui-note" style="margin-top: 16px;">
      Тест на {gradeLabel(grade)}: {catalog.testLength} заданий, {catalog.timeLimitMinutes}
      минут. Каждое задание сгенерировано лично для вас: новые числа, таблицы и фрагменты кода. Задания
      нельзя пропустить или вернуть. Если закрыть вкладку, таймер продолжит идти; незавершённая попытка
      засчитывается как неудачная. Повторная смена грейда — не чаще раза в {catalog.cooldownDays}
      дней.
    </div>
    <div class="d-ui-inline">
      <button
        class="d-ui-button"
        disabled={busy || !grade}
        onclick={() => grade && onStart(grade, vacancyId)}
      >
        Начать тест <ActionIcon name="assessment" />
      </button>
      <button class="d-ui-outline" onclick={() => (step = 2)}>Назад</button>
    </div>
  {/if}
</div>
