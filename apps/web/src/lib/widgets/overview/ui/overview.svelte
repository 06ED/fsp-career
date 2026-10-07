<script lang="ts">
  import { ActionIcon } from "$lib/shared/ui";
  import type { Role, Section } from "$lib/entities/account";
  import type { Profile } from "$lib/entities/candidate";
  import type { Company, Need } from "$lib/entities/company";
  import type { Invitation } from "$lib/entities/invitation";
  let {
    role,
    profile,
    company,
    need,
    invitations,
    openSection,
  }: {
    role: Role;
    profile: Profile;
    company: Company;
    need: Need;
    invitations: Invitation[];
    openSection: (id: Section) => Promise<void>;
  } = $props();
</script>

{#if role === "candidate"}
  <div class="d-ui-cards">
    <div class="d-ui-stat">
      <div class="d-ui-stat-label">ПОДТВЕРЖДЁННАЯ КАТЕГОРИЯ</div>
      <div class="d-ui-stat-number">{profile.grade || "—"}</div>
    </div>
    <div class="d-ui-stat d-ui-stat-light">
      <div class="d-ui-stat-label">СПЕЦИАЛИЗАЦИЯ</div>
      <div
        class="d-ui-stat-number"
        style="font-size: 22px; overflow-wrap: anywhere;"
      >
        {profile.specialization || "Не указана"}
      </div>
    </div>
    <div class="d-ui-stat d-ui-stat-light">
      <div class="d-ui-stat-label">ПРИГЛАШЕНИЯ</div>
      <div class="d-ui-stat-number">{invitations.length}</div>
    </div>
  </div>
  <div class="d-ui-panel">
    <h2 class="d-ui-panel-title">Ваш маршрут</h2>
    <p class="d-ui-muted">
      01 — Заполните профиль и дайте согласие на публикацию. 02 — Выберите
      специализацию и предполагаемый грейд. 03 — Пройдите оценку: категория
      определяется проверкой навыков. 04 — Рассмотрите приглашения.
    </p>
    <div class="d-ui-inline">
      <button class="d-ui-button" onclick={() => openSection("profile")}
        >Заполнить профиль <ActionIcon name="arrow" /></button
      ><button class="d-ui-outline" onclick={() => openSection("assessment")}
        >Перейти к оценке <ActionIcon name="assessment" /></button
      >
    </div>
  </div>
{:else}
  <div class="d-ui-cards">
    <div class="d-ui-stat">
      <div class="d-ui-stat-label">СПЕЦИАЛИЗАЦИЯ ПОИСКА</div>
      <div
        class="d-ui-stat-number"
        style="font-size: 22px; overflow-wrap: anywhere;"
      >
        {need.specialization || "—"}
      </div>
    </div>
    <div class="d-ui-stat d-ui-stat-light">
      <div class="d-ui-stat-label">ПРИГЛАШЕНИЯ</div>
      <div class="d-ui-stat-number">{invitations.length}</div>
    </div>
    <div class="d-ui-stat d-ui-stat-light">
      <div class="d-ui-stat-label">КОМПАНИЯ</div>
      <div
        class="d-ui-stat-number"
        style="font-size: 22px; overflow-wrap: anywhere;"
      >
        {company.name || "—"}
      </div>
    </div>
  </div>
  <div class="d-ui-panel">
    <h2 class="d-ui-panel-title">Подбор по подтверждённым навыкам</h2>
    <p class="d-ui-muted">
      Опишите команду и задачу. Платформа покажет специалистов с проверенной
      категорией, рейтингом и объяснением совпадения. Контакты откроются только
      если кандидат примет приглашение.
    </p>
    <div class="d-ui-inline">
      <button class="d-ui-button" onclick={() => openSection("needs")}
        >Описать потребность <ActionIcon name="need" /></button
      ><button class="d-ui-outline" onclick={() => openSection("matches")}
        >Смотреть подборку <ActionIcon name="matches" /></button
      >
    </div>
  </div>
{/if}
