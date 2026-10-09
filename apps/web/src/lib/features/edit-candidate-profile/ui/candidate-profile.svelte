<script lang="ts">
  import { ActionIcon } from "$lib/shared/ui";
  import type { Profile, ResumeDraft } from "$lib/entities/candidate";
  import { specializations, industries } from "$lib/entities/candidate";
  let {
    profile = $bindable(),
    resumeDraft,
    techStackText = $bindable(),
    softSkillsText = $bindable(),
    targetRolesText = $bindable(),
    busy,
    uploadResume,
    confirmResume,
    saveProfile,
    downloadResume,
    revokeConsent,
  }: {
    profile: Profile;
    resumeDraft: ResumeDraft | null;
    techStackText: string;
    softSkillsText: string;
    targetRolesText: string;
    busy: boolean;
    uploadResume: (event: Event) => Promise<void>;
    confirmResume: () => Promise<void>;
    saveProfile: () => Promise<void>;
    downloadResume: () => Promise<void>;
    revokeConsent: () => Promise<void>;
  } = $props();
</script>

<div class="d-ui-panel">
  <h2 class="d-ui-panel-title">Загрузить резюме PDF</h2>
  <p class="d-ui-muted">
    Файл до 5 МБ. Распознавание создаёт только черновик: проверьте данные перед сохранением;
    подтверждённый email и настройка публикации не меняются.
  </p>
  <input
    type="file"
    accept="application/pdf,.pdf"
    aria-label="Загрузить PDF-резюме"
    onchange={uploadResume}
    disabled={busy}
  />
  {#if resumeDraft}
    <div class="d-ui-note">
      Черновик готов к проверке. Исправьте распознанные поля ниже, затем подтвердите перенос.
    </div>
    <button type="button" class="d-ui-button" disabled={busy} onclick={confirmResume}>
      Подтвердить данные из PDF <ActionIcon name="confirm" />
    </button>
  {/if}
</div>
<form
  class="d-ui-panel"
  onsubmit={(event) => {
    event.preventDefault();
    saveProfile();
  }}
>
  <h2 class="d-ui-panel-title">Профиль специалиста</h2>
  <p class="d-ui-muted">
    Категория подтверждается только после оценки. Указанный грейд — ваше предположение, а не
    результат теста.
  </p>
  <div class="d-ui-grid">
    <div>
      <label for="fullName" class="d-ui-label">Имя и фамилия</label>
      <input id="fullName" required bind:value={profile.fullName} class="d-ui-field" />
    </div>
    <div>
      <label for="phone" class="d-ui-label">Телефон для связи</label>
      <input id="phone" type="tel" bind:value={profile.phone} class="d-ui-field" />
    </div>
    <div>
      <label for="spec" class="d-ui-label">Специализация</label>
      <select id="spec" required bind:value={profile.specialization} class="d-ui-field">
        <option value="">Выберите направление</option>
        {#each specializations as item}
          <option value={item.value}>{item.label}</option>
        {/each}
      </select>
    </div>
    <div>
      <label for="industry" class="d-ui-label">Отрасль / интерес</label>
      <select id="industry" bind:value={profile.industry} class="d-ui-field">
        <option value="">Не указана</option>
        {#each industries as item}
          <option value={item.id}>
            {item.label}
          </option>
        {/each}
        {#if profile.industry && !industries.some((i) => i.id === profile.industry)}
          <option value={profile.industry}>
            {profile.industry}
          </option>
        {/if}
      </select>
    </div>
    <div>
      <label for="grade" class="d-ui-label">Предполагаемый грейд</label>
      <select id="grade" bind:value={profile.desiredGrade} class="d-ui-field">
        <option value="junior">Junior</option>
        <option value="middle">Middle</option>
        <option value="senior">Senior</option>
      </select>
    </div>
    <div>
      <label for="email" class="d-ui-label">Электронная почта</label>
      <input
        id="email"
        type="email"
        readonly
        aria-describedby="email-hint"
        bind:value={profile.email}
        class="d-ui-field"
      />
      <small id="email-hint" class="d-ui-muted">
        Подтверждённый адрес из учётной записи; изменить его можно в настройках Keycloak.
      </small>
    </div>
  </div>
  <div class="d-ui-grid">
    <div>
      <label for="techStack" class="d-ui-label">Технологии · через запятую</label>
      <input
        id="techStack"
        placeholder="Kotlin, PostgreSQL, Ktor"
        bind:value={techStackText}
        class="d-ui-field"
      />
    </div>
    <div>
      <label for="targetRoles" class="d-ui-label">Желаемые роли · через запятую</label>
      <input
        id="targetRoles"
        placeholder="Backend engineer, Team lead"
        bind:value={targetRolesText}
        class="d-ui-field"
      />
    </div>
  </div>
  <label for="softSkills" class="d-ui-label">Гибкие навыки · через запятую</label>
  <input
    id="softSkills"
    placeholder="Коммуникация, работа в команде"
    bind:value={softSkillsText}
    class="d-ui-field"
  />
  <label for="experience" class="d-ui-label">Опыт и достижения</label>
  <textarea id="experience" bind:value={profile.experience} class="d-ui-textarea"></textarea>
  <h3 class="d-ui-panel-title">Опыт работы</h3>
  {#each profile.cvHistory as entry, index}
    <div class="d-ui-panel">
      <div class="d-ui-grid">
        <div>
          <label for={"employer-" + index} class="d-ui-label">Компания</label>
          <input id={"employer-" + index} bind:value={entry.company} class="d-ui-field" />
        </div>
        <div>
          <label for={"role-" + index} class="d-ui-label">Роль</label>
          <input id={"role-" + index} bind:value={entry.role} class="d-ui-field" />
        </div>
        <div>
          <label for={"start-" + index} class="d-ui-label">Начало (ГГГГ-ММ)</label>
          <input
            id={"start-" + index}
            placeholder="2022-09"
            bind:value={entry.startDate}
            class="d-ui-field"
          />
        </div>
        <div>
          <label for={"end-" + index} class="d-ui-label">Окончание / по настоящее время</label>
          <input
            id={"end-" + index}
            placeholder="2024-06"
            bind:value={entry.endDate}
            class="d-ui-field"
          />
        </div>
      </div>
      <label for={"description-" + index} class="d-ui-label">Что делали</label>
      <textarea
        id={"description-" + index}
        bind:value={entry.description}
        class="d-ui-textarea"
      ></textarea>
      <button
        type="button"
        class="d-ui-danger"
        onclick={() =>
          (profile.cvHistory = profile.cvHistory.filter((_, position) => position !== index))}
      >
        Удалить запись <ActionIcon name="remove" />
      </button>
    </div>
  {/each}
  <button
    type="button"
    class="d-ui-outline"
    onclick={() =>
      (profile.cvHistory = [
        ...profile.cvHistory,
        {
          company: "",
          role: "",
          startDate: "",
          endDate: "",
          description: "",
        },
      ])}
  >
    Добавить опыт <ActionIcon name="add" />
  </button>
  <label class="d-ui-answer" style="margin-top: 20px;">
    <input type="checkbox" required bind:checked={profile.processingConsent} />
    Я даю согласие на обработку персональных данных для работы платформы. Без него нельзя сохранять профиль
    и проходить оценку.
  </label>
  <label class="d-ui-answer">
    <input type="checkbox" bind:checked={profile.consentPublication} />
    Я отдельно согласен(на) на публикацию профиля в подборке работодателей. Эту настройку можно выключить,
    сохранив профиль.
  </label>
  <div class="d-ui-inline">
    <button type="submit" disabled={busy} class="d-ui-button">
      Сохранить профиль <ActionIcon name="save" />
    </button>
    <button
      type="button"
      disabled={busy || !profile.processingConsent || !profile.fullName}
      class="d-ui-outline"
      onclick={downloadResume}
    >
      Скачать резюме PDF <ActionIcon name="download" />
    </button>
    <button
      type="button"
      disabled={busy}
      class="d-ui-danger"
      onclick={() => {
        if (
          !window.confirm("Отозвать согласие и безвозвратно удалить профиль, оценки и приглашения?")
        )
          return;
        revokeConsent();
      }}
    >
      Удалить мои данные <ActionIcon name="remove" />
    </button>
  </div>
</form>
