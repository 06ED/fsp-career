<script lang="ts">
  import { ActionIcon } from "$lib/shared/ui";
  import type { Need } from "$lib/entities/company";
  import { specializations, industries } from "$lib/entities/candidate";
  import { splitTags, stackSuggestions } from "$lib/entities/company";
  let {
    need = $bindable(),
    busy,
    saveNeed,
  }: { need: Need; busy: boolean; saveNeed: () => Promise<void> } = $props();
  let stackText = $state((need.techStack ?? []).join(", "));
  let suggestions = $derived(
    (stackSuggestions[need.specialization] ?? []).filter(
      (skill) =>
        !(need.techStack ?? []).some((known) => known.toLowerCase() === skill.toLowerCase()),
    ),
  );
  function syncStack() {
    need.techStack = splitTags(stackText);
  }
  function addSkill(skill: string) {
    need.techStack = [...(need.techStack ?? []), skill];
    stackText = need.techStack.join(", ");
  }
  function removeSkill(skill: string) {
    need.techStack = (need.techStack ?? []).filter((item) => item !== skill);
    stackText = need.techStack.join(", ");
  }
</script>

<form
  class="d-ui-panel"
  onsubmit={(event) => {
    event.preventDefault();
    syncStack();
    saveNeed();
  }}
>
  <h2 class="d-ui-panel-title">Кого ищет ваша команда?</h2>
  <p class="d-ui-muted">
    На основе потребности формируется подборка: кандидаты отбираются по подтверждённой тестом
    категории и ранжируются по совпадению с описанием, стеком и отраслью — с объяснением по каждому.
  </p>
  <div class="d-ui-grid">
    <div>
      <label for="needSpec" class="d-ui-label">Специализация</label>
      <select id="needSpec" required bind:value={need.specialization} class="d-ui-field">
        <option value="">Выберите направление</option>
        {#each specializations as item (item.value)}
          <option value={item.value}>{item.label}</option>
        {/each}
      </select>
    </div>
    <div>
      <label for="minGrade" class="d-ui-label">Требуемый грейд</label>
      <select id="minGrade" bind:value={need.minGrade} class="d-ui-field">
        <option value="junior">Junior</option>
        <option value="middle">Middle</option>
        <option value="senior">Senior</option>
      </select>
    </div>
  </div>
  <p class="d-ui-muted" style="margin-top: -8px;">
    Точное совпадение грейда — выше в выдаче; кандидаты с грейдом выше показываются с пометкой, ниже
    — только по вашему запросу.
  </p>
  <label for="needIndustry" class="d-ui-label">Отрасль</label>
  <select id="needIndustry" bind:value={need.industry} class="d-ui-field">
    <option value="">Не важно</option>
    {#each industries as item (item.id)}
      <option value={item.id}>
        {item.label}
      </option>
    {/each}
    {#if need.industry && !industries.some((i) => i.id === need.industry)}
      <option value={need.industry}>
        {need.industry}
      </option>
    {/if}
  </select>
  <label for="needStack" class="d-ui-label">Требуемый стек · через запятую (до 15)</label>
  <input
    id="needStack"
    placeholder="Kotlin, PostgreSQL, Kafka"
    bind:value={stackText}
    onchange={syncStack}
    class="d-ui-field"
  />
  {#if need.techStack?.length}
    <div style="margin-top: -8px; margin-bottom: 10px;">
      {#each need.techStack as skill (skill)}
        <button
          type="button"
          class="d-mx-badge d-mx-badge-ok"
          style="border: 0; cursor: pointer;"
          aria-label={`Убрать ${skill}`}
          onclick={() => removeSkill(skill)}
        >
          {skill} ×
        </button>
      {/each}
    </div>
  {/if}
  {#if suggestions.length}
    <div style="margin-bottom: 16px;">
      <span class="d-ui-muted">Часто ищут:</span>
      {#each suggestions as skill (skill)}
        <button
          type="button"
          class="d-mx-badge d-mx-badge-muted"
          style="border: 0; cursor: pointer;"
          onclick={() => addSkill(skill)}
        >
          + {skill}
        </button>
      {/each}
    </div>
  {/if}
  <label for="needDescription" class="d-ui-label">
    Чем занимается команда и какие задачи у специалиста
  </label>
  <textarea
    id="needDescription"
    required
    maxlength="2000"
    placeholder="Например: команда платёжного шлюза, микросервисы на Kotlin, обработка транзакций и антифрод…"
    bind:value={need.description}
    class="d-ui-textarea"
  ></textarea>
  <button type="submit" disabled={busy} class="d-ui-button">
    Сохранить потребность <ActionIcon name="save" />
  </button>
</form>
