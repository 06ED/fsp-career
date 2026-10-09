<script lang="ts">
  import { ActionIcon } from "$lib/shared/ui";
  import { companyContactLine } from "$lib/entities/company";
  import type { Company } from "$lib/entities/company";
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
  class="d-ui-panel"
  onsubmit={(event) => {
    event.preventDefault();
    saveCompany();
  }}
>
  <h2 class="d-ui-panel-title">Профиль компании</h2>
  <div class="d-ui-grid">
    <div>
      <label for="companyName" class="d-ui-label">Название компании</label>
      <input
        id="companyName"
        required
        maxlength="200"
        bind:value={company.name}
        class="d-ui-field"
      />
    </div>
    <div>
      <label for="companyIndustry" class="d-ui-label"
        >Направление деятельности</label
      >
      <input
        id="companyIndustry"
        maxlength="100"
        placeholder="Финтех, e-commerce, госсектор…"
        bind:value={company.industry}
        class="d-ui-field"
      />
    </div>
  </div>
  <label for="website" class="d-ui-label">Сайт</label>
  <input
    id="website"
    type="url"
    placeholder="https://"
    bind:value={company.website}
    class="d-ui-field"
  />
  <label for="companyDescription" class="d-ui-label">О компании</label>
  <textarea
    id="companyDescription"
    maxlength="2000"
    bind:value={company.description}
    class="d-ui-textarea"
  ></textarea>

  <h3 class="d-ui-panel-title">Контактные данные</h3>
  <p class="d-ui-muted">
    Из них по умолчанию собирается способ связи в каждом приглашении: кандидат
    видит название компании, условия и как с вами связаться ещё до начала
    общения.
  </p>
  <div class="d-ui-grid">
    <div>
      <label for="contactEmail" class="d-ui-label">Email для кандидатов</label>
      <input
        id="contactEmail"
        type="email"
        placeholder="hr@company.ru"
        bind:value={company.contactEmail}
        class="d-ui-field"
      />
    </div>
    <div>
      <label for="contactPhone" class="d-ui-label">Телефон</label>
      <input
        id="contactPhone"
        type="tel"
        placeholder="+7 900 000-00-00"
        bind:value={company.contactPhone}
        class="d-ui-field"
      />
    </div>
    <div>
      <label for="contactTelegram" class="d-ui-label">Telegram</label>
      <input
        id="contactTelegram"
        placeholder="@company_hr"
        bind:value={company.contactTelegram}
        class="d-ui-field"
      />
    </div>
    <div>
      <label for="contactNote" class="d-ui-label"
        >Комментарий к способу связи</label
      >
      <input
        id="contactNote"
        maxlength="300"
        placeholder="Пишите рекрутеру Анне, отвечаем в течение дня"
        bind:value={company.contactNote}
        class="d-ui-field"
      />
    </div>
  </div>
  <div class="d-ui-note">
    Кандидат увидит: <strong>
      {contactPreview || "контакты не указаны — добавьте хотя бы один"}
    </strong>
  </div>
  <button type="submit" disabled={busy} class="d-ui-button">
    Сохранить <ActionIcon name="save" />
  </button>
</form>
