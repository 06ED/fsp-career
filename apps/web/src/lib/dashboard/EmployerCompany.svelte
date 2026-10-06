<script lang="ts">
  import { sx } from "$lib/svelte-stylex";
  import { ui as s } from "$lib/dashboard.stylex";
  import ActionIcon from "$lib/ActionIcon.svelte";
  import { companyContactLine } from "./matching";
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
  let contactPreview = $derived(companyContactLine(company));
</script>

<form
  {...sx(s.panel)}
  onsubmit={(event) => {
    event.preventDefault();
    saveCompany();
  }}
>
  <h2 {...sx(s.panelTitle)}>Профиль компании</h2>
  <div {...sx(s.grid)}>
    <div>
      <label for="companyName" {...sx(s.label)}>Название компании</label><input
        id="companyName"
        required
        maxlength="200"
        bind:value={company.name}
        {...sx(s.field)}
      />
    </div>
    <div>
      <label for="companyIndustry" {...sx(s.label)}
        >Направление деятельности</label
      ><input
        id="companyIndustry"
        maxlength="100"
        placeholder="Финтех, e-commerce, госсектор…"
        bind:value={company.industry}
        {...sx(s.field)}
      />
    </div>
  </div>
  <label for="website" {...sx(s.label)}>Сайт</label><input
    id="website"
    type="url"
    placeholder="https://"
    bind:value={company.website}
    {...sx(s.field)}
  /><label for="companyDescription" {...sx(s.label)}>О компании</label><textarea
    id="companyDescription"
    maxlength="2000"
    bind:value={company.description}
    {...sx(s.textarea)}
  ></textarea>

  <h3 {...sx(s.panelTitle)}>Контактные данные</h3>
  <p {...sx(s.muted)}>
    Из них по умолчанию собирается способ связи в каждом приглашении: кандидат
    видит название компании, условия и как с вами связаться ещё до начала
    общения.
  </p>
  <div {...sx(s.grid)}>
    <div>
      <label for="contactEmail" {...sx(s.label)}>Email для кандидатов</label
      ><input
        id="contactEmail"
        type="email"
        placeholder="hr@company.ru"
        bind:value={company.contactEmail}
        {...sx(s.field)}
      />
    </div>
    <div>
      <label for="contactPhone" {...sx(s.label)}>Телефон</label><input
        id="contactPhone"
        type="tel"
        placeholder="+7 900 000-00-00"
        bind:value={company.contactPhone}
        {...sx(s.field)}
      />
    </div>
    <div>
      <label for="contactTelegram" {...sx(s.label)}>Telegram</label><input
        id="contactTelegram"
        placeholder="@company_hr"
        bind:value={company.contactTelegram}
        {...sx(s.field)}
      />
    </div>
    <div>
      <label for="contactNote" {...sx(s.label)}
        >Комментарий к способу связи</label
      ><input
        id="contactNote"
        maxlength="300"
        placeholder="Пишите рекрутеру Анне, отвечаем в течение дня"
        bind:value={company.contactNote}
        {...sx(s.field)}
      />
    </div>
  </div>
  <div {...sx(s.note)}>
    Кандидат увидит: <strong
      >{contactPreview || "контакты не указаны — добавьте хотя бы один"}</strong
    >
  </div>
  <button type="submit" disabled={busy} {...sx(s.button)}
    >Сохранить <ActionIcon name="save" /></button
  >
</form>
