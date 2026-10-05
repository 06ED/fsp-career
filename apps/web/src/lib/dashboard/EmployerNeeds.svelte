<script lang="ts">
  import { sx } from "$lib/svelte-stylex";
  import { ui as s } from "$lib/dashboard.stylex";
  import ActionIcon from "$lib/ActionIcon.svelte";
  import type { Need } from "./types";
  import { specializations } from "./specializations";
  let {
    need = $bindable(),
    busy,
    saveNeed,
  }: { need: Need; busy: boolean; saveNeed: () => Promise<void> } = $props();
</script>

<form
  {...sx(s.panel)}
  onsubmit={(event) => {
    event.preventDefault();
    saveNeed();
  }}
>
  <h2 {...sx(s.panelTitle)}>Кого ищет ваша команда?</h2>
  <p {...sx(s.muted)}>
    Описание сохраняется в запросе компании. В подборке учитываются
    специализация, минимальный грейд, оценка и совпадение отрасли.
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
      <label for="minGrade" {...sx(s.label)}>Минимальный грейд</label><select
        id="minGrade"
        bind:value={need.minGrade}
        {...sx(s.field)}
        ><option value="junior">Junior</option><option value="middle"
          >Middle</option
        ><option value="senior">Senior</option></select
      >
    </div>
  </div>
  <label for="needIndustry" {...sx(s.label)}>Отрасль</label><input
    id="needIndustry"
    bind:value={need.industry}
    {...sx(s.field)}
  /><label for="needDescription" {...sx(s.label)}>Команда, стек и задача</label
  ><textarea
    id="needDescription"
    required
    bind:value={need.description}
    {...sx(s.textarea)}
  ></textarea><button type="submit" disabled={busy} {...sx(s.button)}
    >Сохранить потребность <ActionIcon name="save" /></button
  >
</form>
