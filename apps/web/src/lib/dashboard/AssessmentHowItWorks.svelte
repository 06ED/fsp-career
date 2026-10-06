<script lang="ts">
  import { sx } from "$lib/svelte-stylex";
  import { ui as s } from "$lib/dashboard.stylex";
  import ActionIcon from "$lib/ActionIcon.svelte";
  import { a } from "./assessment.stylex";
  import { assessmentApi, entryLabel, gradeLabel } from "./assessment";
  import type { AssessmentCatalog, BlueprintPreview } from "./types";

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

<div {...sx(s.panel)}>
  <h2 {...sx(s.panelTitle)}>Как формируются задания</h2>
  <p {...sx(s.muted)}>
    В банке {catalog.familyCount} семейств заданий. Семейство — это один навык фиксированной,
    заранее откалиброванной сложности; каждый кандидат получает собственный экземпляр
    (новые числа, таблицы, фрагменты кода или равноценный вариант вопроса). Поэтому
    пересказ чужого теста не даёт готовых ответов, а сложность и шкала оценки у всех
    одинаковы. Ниже видно, как описание вакансии превращается в план теста.
  </p>
  <div {...sx(s.grid)}>
    <div>
      <label for="blueprint-source" {...sx(s.label)}>Вакансия</label>
      <select id="blueprint-source" bind:value={source} {...sx(s.field)}>
        {#each catalog.vacancies as vacancy}
          <option value={vacancy.id}>{vacancy.title}</option>
        {/each}
        <option value="custom">Свой текст вакансии…</option>
      </select>
    </div>
    <div style="display: flex; align-items: flex-end; padding-bottom: 18px;">
      <button
        {...sx(s.button)}
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
      {...sx(s.textarea)}
    ></textarea>
  {:else if selected}
    <details {...sx(a.details)} style="margin-bottom: 16px;">
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
  {#if error}<div role="alert" {...sx(s.error)}>{error}</div>{/if}

  {#if preview}
    <div {...sx(s.separator)}></div>
    <h3 {...sx(s.panelTitle)} style="font-size: 17px;">
      1. Навыки, найденные в тексте
    </h3>
    <p {...sx(a.small)}>
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
        <span {...sx(a.chip)} title={`Совпадения: ${skill.matched.join(", ")}`}
          >{skill.label} <strong>×{skill.hits}</strong></span
        >
      {:else}
        <span {...sx(a.small)}
          >Навыки не распознаны — тест строится по профилю специализации.</span
        >
      {/each}
    </div>
    {#if preview.uncoveredSkills.length}
      <p {...sx(a.small)}>
        Пока не покрыто банком для этой специализации: {preview.uncoveredSkills
          .map((skill) => skill.label)
          .join(", ")}.
      </p>
    {/if}

    <h3 {...sx(s.panelTitle)} style="font-size: 17px; margin-top: 20px;">
      2. План теста: темы и число заданий
    </h3>
    <p {...sx(a.small)}>
      60 % веса — темы из вакансии, 40 % — базовый профиль специализации. Всего
      {preview.testLength} заданий из пула в {preview.poolSize} семейств; сложность
      подбирается адаптивно под ответы.
    </p>
    {#each preview.topics as topic}
      <div {...sx(a.barRow)}>
        <span>{topic.label}</span>
        <div {...sx(a.barTrack)}>
          <div
            {...sx(a.barFill)}
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

    <h3 {...sx(s.panelTitle)} style="font-size: 17px; margin-top: 20px;">
      3. Примеры заданий для уровня {gradeLabel(preview.targetGrade)}
    </h3>
    <p {...sx(a.small)}>
      Сгенерированы только что; при повторном построении получатся другие
      экземпляры той же сложности. Ответы в браузер не передаются — их знает
      только сервер.
    </p>
    {#each preview.sampleItems as item, index}
      <div {...sx(a.sample)}>
        <span {...sx(s.tag)}>{item.topicLabel}</span>
        <p {...sx(a.prompt)}>{index + 1}. {item.prompt}</p>
        {#if item.code}<pre {...sx(a.code)}><code>{item.code}</code></pre>{/if}
        {#if item.format === "choice"}
          <ul
            style="margin: 0; padding-left: 20px; font-size: 13px; line-height: 1.6;"
          >
            {#each item.options as option}<li>{option.label}</li>{/each}
          </ul>
        {:else}
          <p {...sx(a.small)}>
            Ответ — число{item.unit ? `, ${item.unit}` : ""}.
          </p>
        {/if}
      </div>
    {/each}
  {/if}
</div>
