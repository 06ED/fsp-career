<script lang="ts">
  import { ActionIcon } from "$lib/shared/ui";
  import {
    assessmentApi,
    entryLabel,
    gradeLabel,
  } from "$lib/entities/assessment";
  import type {
    AssessmentCatalog,
    BlueprintPreview,
  } from "$lib/entities/assessment";

  let { catalog }: { catalog: AssessmentCatalog } = $props();

  // Initial choice only; later catalog updates must not override what the user picked.
  const firstVacancy = () => catalog.vacancies[0]?.id ?? "custom";
  let source = $state<string>(firstVacancy());
  let customText = $state("");
  let preview = $state<BlueprintPreview | null>(null);
  let loading = $state(false);
  let error = $state("");

  let selected = $derived(catalog.vacancies.find((v) => v.id === source));
  let maxWeight = $derived(
    Math.max(0.01, ...(preview?.topics ?? []).map((t) => t.weight)),
  );

  async function build() {
    error = "";
    loading = true;
    try {
      preview = await assessmentApi.blueprint(
        source === "custom"
          ? { vacancyText: customText }
          : { vacancyId: source },
      );
    } catch (e) {
      error =
        e instanceof Error ? e.message : "Не удалось построить план теста";
    } finally {
      loading = false;
    }
  }
</script>

<div class="d-ui-panel">
  <h2 class="d-ui-panel-title">Как формируются задания</h2>
  <p class="d-ui-muted">
    В банке {catalog.familyCount} семейств заданий. Семейство — это один навык фиксированной,
    заранее откалиброванной сложности; каждый кандидат получает собственный экземпляр
    (новые числа, таблицы, фрагменты кода или равноценный вариант вопроса). Поэтому
    пересказ чужого теста не даёт готовых ответов, а сложность и шкала оценки у всех
    одинаковы. Ниже видно, как описание вакансии превращается в план теста.
  </p>
  <div class="d-ui-grid">
    <div>
      <label for="blueprint-source" class="d-ui-label">Вакансия</label>
      <select id="blueprint-source" bind:value={source} class="d-ui-field">
        {#each catalog.vacancies as vacancy}
          <option value={vacancy.id}>{vacancy.title}</option>
        {/each}
        <option value="custom">Свой текст вакансии…</option>
      </select>
    </div>
    <div style="display: flex; align-items: flex-end; padding-bottom: 18px;">
      <button
        class="d-ui-button"
        disabled={loading ||
          (source === "custom" && customText.trim().length < 20)}
        onclick={build}>Построить план теста <ActionIcon name="arrow" /></button
      >
    </div>
  </div>
  {#if source === "custom"}
    <textarea
      aria-label="Текст вакансии"
      placeholder="Вставьте описание вакансии: задачи, стек, требования…"
      maxlength="6000"
      bind:value={customText}
      class="d-ui-textarea"
    ></textarea>
  {:else if selected}
    <details class="d-a-details" style="margin-bottom: 16px;">
      <summary>
        {selected.company} · {entryLabel(
          catalog.specializations,
          selected.specialization,
        )}
        · {gradeLabel(selected.grade)} — текст вакансии
      </summary>
      <p style="white-space: pre-line;">{selected.text}</p>
    </details>
  {/if}
  {#if error}<div role="alert" class="d-ui-error">{error}</div>{/if}

  {#if preview}
    <div class="d-ui-separator"></div>
    <h3 class="d-ui-panel-title" style="font-size: 17px;">
      1. Навыки, найденные в тексте
    </h3>
    <p class="d-a-small">
      Словарь русских и английских терминов с упрощённым стеммингом.
      Специализация: {entryLabel(
        catalog.specializations,
        preview.specialization,
      )}{preview.detectedSpecialization &&
      preview.detectedSpecialization !== preview.specialization
        ? ` (по тексту похоже на «${entryLabel(catalog.specializations, preview.detectedSpecialization)}»)`
        : ""}.
    </p>
    <div style="margin-bottom: 10px;">
      {#each preview.skills as skill}
        <span class="d-a-chip" title={`Совпадения: ${skill.matched.join(", ")}`}
          >{skill.label} <strong>×{skill.hits}</strong></span
        >
      {:else}
        <span class="d-a-small"
          >Навыки не распознаны — тест строится по профилю специализации.</span
        >
      {/each}
    </div>
    {#if preview.uncoveredSkills.length}
      <p class="d-a-small">
        Пока не покрыто банком для этой специализации: {preview.uncoveredSkills
          .map((skill) => skill.label)
          .join(", ")}.
      </p>
    {/if}

    <h3 class="d-ui-panel-title" style="font-size: 17px; margin-top: 20px;">
      2. План теста: темы и число заданий
    </h3>
    <p class="d-a-small">
      60 % веса — темы из вакансии, 40 % — базовый профиль специализации. Всего
      {preview.testLength} заданий из пула в {preview.poolSize} семейств; сложность
      подбирается адаптивно под ответы.
    </p>
    {#each preview.topics as topic}
      <div class="d-a-bar-row">
        <span>{topic.label}</span>
        <div class="d-a-bar-track">
          <div
            class="d-a-bar-fill"
            style="width: {Math.round((topic.weight / maxWeight) * 100)}%;"
          ></div>
        </div>
        <span
          style="text-align: right;"
          title={`Вес темы ${Math.round(topic.weight * 100)} %`}
          >{topic.items}
          {topic.items === 1
            ? "задание"
            : topic.items < 5
              ? "задания"
              : "заданий"}</span
        >
      </div>
    {/each}

    <h3 class="d-ui-panel-title" style="font-size: 17px; margin-top: 20px;">
      3. Примеры заданий для уровня {gradeLabel(preview.targetGrade)}
    </h3>
    <p class="d-a-small">
      Сгенерированы только что; при повторном построении получатся другие
      экземпляры той же сложности. Ответы в браузер не передаются — их знает
      только сервер.
    </p>
    {#each preview.sampleItems as item, index}
      <div class="d-a-sample">
        <span class="d-ui-tag">{item.topicLabel}</span>
        <p class="d-a-prompt">{index + 1}. {item.prompt}</p>
        {#if item.code}<pre class="d-a-code"><code>{item.code}</code></pre>{/if}
        {#if item.format === "choice"}
          <ul
            style="margin: 0; padding-left: 20px; font-size: 13px; line-height: 1.6;"
          >
            {#each item.options as option}<li>{option.label}</li>{/each}
          </ul>
        {:else}
          <p class="d-a-small">
            Ответ — число{item.unit ? `, ${item.unit}` : ""}.
          </p>
        {/if}
      </div>
    {/each}
  {/if}
</div>
