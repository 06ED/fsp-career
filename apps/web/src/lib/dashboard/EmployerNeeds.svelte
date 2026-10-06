<script lang="ts">
  import { sx } from "$lib/svelte-stylex";
  import { ui as s } from "$lib/dashboard.stylex";
  import { mx } from "./matching.stylex";
  import ActionIcon from "$lib/ActionIcon.svelte";
  import type { Need } from "./types";
  import { specializations } from "./specializations";
  import { industries } from "./industries";
  import { splitTags, stackSuggestions } from "./matching";
  let {
    need = $bindable(),
    busy,
    saveNeed,
  }: { need: Need; busy: boolean; saveNeed: () => Promise<void> } = $props();
  let stackText = $state((need.techStack ?? []).join(", "));
  let suggestions = $derived(
    (stackSuggestions[need.specialization] ?? []).filter(
      (skill) =>
        !(need.techStack ?? []).some(
          (known) => known.toLowerCase() === skill.toLowerCase(),
        ),
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
  {...sx(s.panel)}
  onsubmit={(event) => {
    event.preventDefault();
    syncStack();
    saveNeed();
  }}
>
  <h2 {...sx(s.panelTitle)}>Кого ищет ваша команда?</h2>
  <p {...sx(s.muted)}>
    На основе потребности формируется подборка: кандидаты отбираются по
    подтверждённой тестом категории и ранжируются по совпадению с описанием,
    стеком и отраслью — с объяснением по каждому.
  </p>
  <div {...sx(s.grid)}>
    <div>
      <label for="needSpec" {...sx(s.label)}>Специализация</label><select
        id="needSpec"
        required
        bind:value={need.specialization}
        {...sx(s.field)}
        ><option value="">Выберите направление</option
        >{#each specializations as item}<option value={item.value}
            >{item.label}</option
          >{/each}</select
      >
    </div>
    <div>
      <label for="minGrade" {...sx(s.label)}>Требуемый грейд</label><select
        id="minGrade"
        bind:value={need.minGrade}
        {...sx(s.field)}
        ><option value="junior">Junior</option><option value="middle"
          >Middle</option
        ><option value="senior">Senior</option></select
      >
    </div>
  </div>
  <p {...sx(s.muted)} style="margin-top: -8px;">
    Точное совпадение грейда — выше в выдаче; кандидаты с грейдом выше
    показываются с пометкой, ниже — только по вашему запросу.
  </p>
  <label for="needIndustry" {...sx(s.label)}>Отрасль</label><select
    id="needIndustry"
    bind:value={need.industry}
    {...sx(s.field)}
    ><option value="">Не важно</option>{#each industries as item}<option
        value={item.id}>{item.label}</option
      >{/each}{#if need.industry && !industries.some((i) => i.id === need.industry)}<option
        value={need.industry}>{need.industry}</option
      >{/if}</select
  >
  <label for="needStack" {...sx(s.label)}
    >Требуемый стек · через запятую (до 15)</label
  ><input
    id="needStack"
    placeholder="Kotlin, PostgreSQL, Kafka"
    bind:value={stackText}
    onchange={syncStack}
    {...sx(s.field)}
  />
  {#if need.techStack?.length}
    <div style="margin-top: -8px; margin-bottom: 10px;">
      {#each need.techStack as skill}<button
          type="button"
          {...sx(mx.badge, mx.badgeOk)}
          style="border: 0; cursor: pointer;"
          aria-label={`Убрать ${skill}`}
          onclick={() => removeSkill(skill)}>{skill} ×</button
        >{/each}
    </div>
  {/if}
  {#if suggestions.length}
    <div style="margin-bottom: 16px;">
      <span {...sx(s.muted)}>Часто ищут: </span>
      {#each suggestions as skill}<button
          type="button"
          {...sx(mx.badge, mx.badgeMuted)}
          style="border: 0; cursor: pointer;"
          onclick={() => addSkill(skill)}>+ {skill}</button
        >{/each}
    </div>
  {/if}
  <label for="needDescription" {...sx(s.label)}
    >Чем занимается команда и какие задачи у специалиста</label
  ><textarea
    id="needDescription"
    required
    maxlength="2000"
    placeholder="Например: команда платёжного шлюза, микросервисы на Kotlin, обработка транзакций и антифрод…"
    bind:value={need.description}
    {...sx(s.textarea)}
  ></textarea><button type="submit" disabled={busy} {...sx(s.button)}
    >Сохранить потребность <ActionIcon name="save" /></button
  >
</form>
