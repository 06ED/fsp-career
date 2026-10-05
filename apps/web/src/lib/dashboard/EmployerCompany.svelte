<script lang="ts">
  import { sx } from "$lib/svelte-stylex";
  import { ui as s } from "$lib/dashboard.stylex";
  import type { Company } from "./types";
  let {
    company = $bindable(),
    busy,
    saveCompany,
  }: {
    company: Company;
    busy: boolean;
    saveCompany: () => Promise<void>;
  } = $props();
</script>

<form
  {...sx(s.panel)}
  onsubmit={(event) => {
    event.preventDefault();
    saveCompany();
  }}
>
  <h2 {...sx(s.panelTitle)}>Профиль компании</h2>
  <label for="companyName" {...sx(s.label)}>Название компании</label><input
    id="companyName"
    required
    bind:value={company.name}
    {...sx(s.field)}
  /><label for="website" {...sx(s.label)}>Сайт</label><input
    id="website"
    type="url"
    placeholder="https://"
    bind:value={company.website}
    {...sx(s.field)}
  /><label for="companyDescription" {...sx(s.label)}>О компании</label><textarea
    id="companyDescription"
    bind:value={company.description}
    {...sx(s.textarea)}
  ></textarea><button type="submit" disabled={busy} {...sx(s.button)}
    >Сохранить ↗</button
  >
</form>
