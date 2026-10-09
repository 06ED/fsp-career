<script lang="ts">
  import { onMount } from "svelte";
  import { ActionIcon } from "$lib/shared/ui";
  import { assessmentApi, entryLabel, formatDate, gradeLabel } from "$lib/entities/assessment";
  import AssessmentSetup from "./assessment-setup.svelte";
  import AssessmentRunner from "./assessment-runner.svelte";
  import AssessmentResultPanel from "./assessment-result-panel.svelte";
  import AssessmentHistory from "./assessment-history.svelte";
  import AssessmentHowItWorks from "./assessment-how-it-works.svelte";
  import type {
    AssessmentAttempt,
    AssessmentCatalog,
    AssessmentEligibility,
    AssessmentResult,
    AssessmentSurvey,
    GradeId,
  } from "$lib/entities/assessment";
  import type { Profile } from "$lib/entities/candidate";

  let { profile, refreshProfile }: { profile: Profile; refreshProfile: () => Promise<void> } =
    $props();

  type View = "status" | "setup" | "test" | "result";
  let view = $state<View>("status");
  let catalog = $state<AssessmentCatalog | null>(null);
  let history = $state<AssessmentResult[]>([]);
  let eligibility = $state<AssessmentEligibility | null>(null);
  let attempt = $state<AssessmentAttempt | null>(null);
  let result = $state<AssessmentResult | null>(null);
  let busy = $state(false);
  let error = $state("");
  let survey = $state<AssessmentSurvey>({
    industry: "",
    specialization: "",
    experienceYears: 1,
    teamRoles: [],
    workFormat: "",
  });

  let lastSurvey = $derived(history.find((h) => h.survey)?.survey ?? null);

  onMount(() => {
    loadInitial();
  });

  function loadInitial() {
    run(async () => {
      const [c, current, h] = await Promise.all([
        assessmentApi.catalog(),
        assessmentApi.current(),
        assessmentApi.history(),
      ]);
      catalog = c;
      history = h;
      // Pre-fill the survey from the last attempt, then from the profile.
      const previous = h.find((entry) => entry.survey)?.survey;
      survey = previous
        ? { ...previous, teamRoles: [...previous.teamRoles] }
        : {
            ...survey,
            specialization: c.specializations.some((x) => x.id === profile.specialization)
              ? profile.specialization
              : "",
            industry: c.industries.some((x) => x.id === profile.industry) ? profile.industry : "",
          };
      if (current.attempt) {
        attempt = current.attempt;
        view = "test";
      } else if (current.closed) {
        result = current.closed;
        view = "result";
      }
      await loadEligibility(survey.specialization);
    });
  }

  async function run(job: () => Promise<void>) {
    error = "";
    busy = true;
    try {
      await job();
    } catch (e) {
      error = e instanceof Error ? e.message : "Произошла ошибка";
    } finally {
      busy = false;
    }
  }

  async function loadEligibility(specialization: string) {
    eligibility = specialization ? await assessmentApi.eligibility(specialization) : null;
  }

  async function afterAttempt(finished: AssessmentResult) {
    result = finished;
    attempt = null;
    view = "result";
    history = await assessmentApi.history();
    await loadEligibility(finished.specialization);
    await refreshProfile();
  }

  async function start(grade: GradeId, vacancyId: string | null) {
    await run(async () => {
      attempt = await assessmentApi.start({
        survey,
        targetGrade: grade,
        ...(vacancyId ? { vacancyId } : {}),
      });
      result = null;
      view = "test";
      await refreshProfile();
    });
  }

  async function answer(value: string) {
    if (!attempt) return;
    const current = attempt;
    await run(async () => {
      const outcome = await assessmentApi.answer(current.attemptId, current.position, value);
      if (outcome.done && outcome.result) await afterAttempt(outcome.result);
      else if (outcome.attempt) attempt = outcome.attempt;
    });
  }

  async function expire() {
    await run(async () => {
      const current = await assessmentApi.current();
      if (current.closed) await afterAttempt(current.closed);
      else if (current.attempt) attempt = current.attempt;
    });
  }
</script>

{#if error}
  <div role="alert" class="d-ui-error">{error}</div>
{/if}

{#if !catalog}
  <div class="d-ui-panel">
    {#if error}
      <button class="d-ui-button" disabled={busy} onclick={loadInitial}>
        Повторить загрузку <ActionIcon name="refresh" />
      </button>
    {:else}
      <p class="d-ui-muted">Загружаем тестирование…</p>
    {/if}
  </div>
{:else if view === "test" && attempt}
  <AssessmentRunner {attempt} {busy} onAnswer={answer} onExpire={expire} />
{:else if view === "setup"}
  <AssessmentSetup
    {catalog}
    bind:survey
    {eligibility}
    {busy}
    onSpecializationChange={(spec) => run(() => loadEligibility(spec))}
    onStart={start}
    onCancel={() => (view = "status")}
  />
{:else}
  {#if view === "result" && result}
    <AssessmentResultPanel
      {result}
      {busy}
      onRetake={(grade) => start(grade, null)}
      onClose={() => (view = "status")}
    />
  {/if}
  <div class="d-ui-panel">
    <div class="d-ui-row">
      <div>
        <h2 class="d-ui-panel-title">Категория и грейд</h2>
        <p class="d-ui-muted" style="margin-top: 0;">
          Категорию, которую видит работодатель, определяет не резюме, а путь: опрос → заявленный
          грейд → адаптивный тест на этот грейд.
        </p>
      </div>
      <button
        class="d-ui-button"
        disabled={busy || !!eligibility?.openAttemptId}
        onclick={() => (view = "setup")}
      >
        {history.length ? "Пройти тестирование" : "Начать: опрос и тест"}
        <ActionIcon name="assessment" />
      </button>
    </div>
    <div class="d-a-stats">
      <div class="d-a-stat">
        <div class="d-a-small">Специализация</div>
        <div class="d-a-stat-value" style="font-size: 17px;">
          {entryLabel(
            catalog.specializations,
            profile.specialization || lastSurvey?.specialization,
          )}
        </div>
      </div>
      <div class="d-a-stat">
        <div class="d-a-small">Отрасль</div>
        <div class="d-a-stat-value" style="font-size: 17px;">
          {entryLabel(catalog.industries, profile.industry || lastSurvey?.industry)}
        </div>
      </div>
      <div class="d-a-stat">
        <div class="d-a-small">Подтверждённый грейд</div>
        <div class="d-a-stat-value">
          {gradeLabel(eligibility?.confirmedGrade ?? profile.grade)}
        </div>
      </div>
      <div class="d-a-stat">
        <div class="d-a-small">Балл</div>
        <div class="d-a-stat-value">
          {eligibility?.score ?? profile.score ?? "—"}
        </div>
      </div>
    </div>
    {#if eligibility}
      <span class="d-ui-label">Куда можно пройти тест сейчас</span>
      <div>
        {#each eligibility.options as option (option.grade)}
          <span class={`d-a-chip ${option.allowed ? "d-a-chip-on" : ""}`} title={option.message}>
            {gradeLabel(option.grade)}: {option.allowed
              ? "можно сейчас"
              : option.availableAt
                ? `с ${formatDate(option.availableAt)}`
                : "уже подтверждён более высокий грейд"}
          </span>
        {/each}
      </div>
      <p class="d-a-small">
        Грейд меняется не чаще раза в {eligibility.cooldownDays} дней. Исключения: после неудачи можно
        сразу пройти тест на уровень ниже, после уверенного результата — на уровень выше. Подтверждённый
        грейд не понижается.
      </p>
    {/if}
  </div>
  <AssessmentHistory {history} {catalog} />
  <AssessmentHowItWorks {catalog} />
{/if}
