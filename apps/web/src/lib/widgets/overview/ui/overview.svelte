<script lang="ts">
  import { ActionIcon } from "$lib/shared/ui";
  import type { Role } from "$lib/entities/account";
  import type { Profile } from "$lib/entities/candidate";
  import type { Company, Need } from "$lib/entities/company";
  import type { Invitation } from "$lib/entities/invitation";
  import { routeProgress } from "../model/route";

  let {
    role,
    profile,
    company,
    need,
    invitations,
  }: {
    role: Role;
    profile: Profile;
    company: Company;
    need: Need;
    invitations: Invitation[];
  } = $props();

  const steps = [
    {
      description: "Заполните профиль и дайте согласие на публикацию.",
      href: "/app/profile",
    },
    {
      description: "Выберите специализацию и предполагаемый грейд.",
      href: "/app/profile",
    },
    {
      description: "Пройдите оценку: категория определяется проверкой навыков.",
      href: "/app/assessment",
    },
    {
      description: "Рассмотрите приглашения.",
      href: "/app/invitations",
    },
  ] satisfies {
    description: string;
    href: string;
  }[];

  const statusLabels = {
    done: "Пройдено",
    current: "Текущий шаг",
    upcoming: "Не пройдено",
  };
  let progress = $derived(routeProgress(profile, invitations));
</script>

{#if role === "candidate"}
  <div class="d-ui-cards">
    <div class="d-ui-stat">
      <div class="d-ui-stat-label">ПОДТВЕРЖДЁННАЯ КАТЕГОРИЯ</div>
      <div class="d-ui-stat-number">{profile.grade || "—"}</div>
    </div>
    <div class="d-ui-stat d-ui-stat-light">
      <div class="d-ui-stat-label">СПЕЦИАЛИЗАЦИЯ</div>
      <div class="d-ui-stat-number" style="overflow-wrap: anywhere;">
        {profile.specialization || "Не указана"}
      </div>
    </div>
    <div class="d-ui-stat d-ui-stat-light">
      <div class="d-ui-stat-label">ПРИГЛАШЕНИЯ</div>
      <div class="d-ui-stat-number">{invitations.length}</div>
    </div>
  </div>
  <section class="d-ui-panel" aria-labelledby="route-title">
    <h2 id="route-title" class="d-ui-panel-title">Ваш маршрут</h2>
    <ol class="route-steps" role="list">
      {#each steps as step, index (step)}
        {@const status = progress[index]}
        <li
          class="route-step"
          data-status={status}
          aria-current={status === "current" ? "step" : undefined}
        >
          <a
            href={step.href}
            data-interactive
            class="route-action"
            aria-label={`${String(index + 1).padStart(2, "0")}. ${step.description} ${statusLabels[status]}`}
          >
            <span class="route-marker" aria-hidden="true">
              {#if status === "done"}
                <ActionIcon name="accept" size={20} placement="solo" />
              {:else}
                {String(index + 1).padStart(2, "0")}
              {/if}
            </span>
            <span class="route-description">
              {step.description}<ActionIcon name="arrow" size={15} />
            </span>
          </a>
        </li>
      {/each}
    </ol>
  </section>
{:else}
  <div class="d-ui-cards">
    <div class="d-ui-stat">
      <div class="d-ui-stat-label">СПЕЦИАЛИЗАЦИЯ ПОИСКА</div>
      <div class="d-ui-stat-number" style="overflow-wrap: anywhere;">
        {need.specialization || "—"}
      </div>
    </div>
    <div class="d-ui-stat d-ui-stat-light">
      <div class="d-ui-stat-label">ПРИГЛАШЕНИЯ</div>
      <div class="d-ui-stat-number">{invitations.length}</div>
    </div>
    <div class="d-ui-stat d-ui-stat-light">
      <div class="d-ui-stat-label">КОМПАНИЯ</div>
      <div class="d-ui-stat-number" style="font-size: 22px; overflow-wrap: anywhere;">
        {company.name || "—"}
      </div>
    </div>
  </div>
  <div class="d-ui-panel">
    <h2 class="d-ui-panel-title">Подбор по подтверждённым навыкам</h2>
    <p class="d-ui-muted">
      Опишите команду и задачу. Платформа покажет специалистов с проверенной категорией, рейтингом и
      объяснением совпадения. Контакты откроются только если кандидат примет приглашение.
    </p>
    <div class="d-ui-inline">
      <a href="/app/needs" data-interactive class="d-ui-button">
        Описать потребность <ActionIcon name="need" />
      </a>
      <a href="/app/matches" data-interactive class="d-ui-outline">
        Смотреть подборку <ActionIcon name="matches" />
      </a>
    </div>
  </div>
{/if}

<style>
  .route-steps {
    list-style: none;
    padding: 0;
    margin: 20px 0 0;
  }
  .route-step {
    position: relative;
    padding-bottom: 16px;
  }
  .route-step:not(:last-child)::after {
    content: "";
    position: absolute;
    top: 44px;
    bottom: 0;
    left: 21px;
    width: 2px;
    background: var(--border-strong);
  }
  .route-step:last-child {
    padding-bottom: 0;
  }
  .route-step[data-status="done"]::after {
    background: var(--accent-lilac);
  }
  .route-marker {
    display: grid;
    place-items: center;
    width: 44px;
    height: 44px;
    border: 2px solid var(--border-strong);
    border-radius: 50%;
    background: var(--surface-muted);
    color: var(--text-muted);
    font-size: 13px;
    font-weight: 700;
    z-index: 1;
  }
  .route-step[data-status="done"] .route-marker {
    background: var(--accent);
    border-color: var(--accent);
    color: var(--on-accent);
  }
  .route-step[data-status="current"] .route-marker {
    background: var(--surface);
    border-color: var(--accent-highlight);
    color: var(--accent);
    box-shadow: 0 0 0 5px var(--accent-wash);
  }
  .route-description {
    padding: 12px 0;
    color: var(--accent);
    font-size: 13px;
    line-height: 1.55;
    text-decoration: underline;
    text-decoration-color: var(--accent-lilac);
    text-underline-offset: 3px;
  }
  .route-step[data-status="current"] .route-description {
    font-weight: 600;
  }
  .route-action:hover .route-description,
  .route-action:focus-visible .route-description {
    text-decoration-color: var(--accent-highlight);
  }
  .route-action {
    display: grid;
    grid-template-columns: 44px minmax(0, 1fr);
    align-items: start;
    gap: 14px;
    width: 100%;
    padding: 0;
    background: transparent;
    text-align: left;
  }
</style>
