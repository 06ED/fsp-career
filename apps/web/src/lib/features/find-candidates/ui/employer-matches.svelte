<script lang="ts">
  import { achievementLabel, industryLabel, specializations } from "$lib/entities/candidate";
  import { onMount } from "svelte";
  import { ActionIcon, EmptyState, modal } from "$lib/shared/ui";
  import FactorBreakdown from "./factor-breakdown.svelte";
  import CandidateCardModal from "./candidate-card-modal.svelte";
  import { matchApi } from "../api/matches";
  import { companyContactLine, splitTags } from "$lib/entities/company";
  import { invitationStatusLabels } from "$lib/entities/invitation";
  import { fitLabels, gradeLabel } from "../model/matching";
  import type { CandidateCard, Match, MatchCategory, MatchResponse } from "../model/types";
  import type { Company, Need } from "$lib/entities/company";
  import type { Offer } from "$lib/entities/invitation";
  import type { RunAction } from "$lib/shared/api";

  let {
    need,
    company,
    busy,
    run,
    sendInvitation,
    openNeeds,
  }: {
    need: Need;
    company: Company;
    busy: boolean;
    run: RunAction;
    sendInvitation: (candidateId: string, offer: Offer) => Promise<boolean>;
    openNeeds: () => void;
  } = $props();

  type Mode = "need" | "bank";
  type Sort = "score" | "test" | "fsp" | "fresh";
  let mode = $state<Mode>("need");
  // Each mode keeps its own fetched selection: switching modes or refining never loses it.
  let needResult = $state<MatchResponse | null>(null);
  let bankResult = $state<MatchResponse | null>(null);
  // Server-side refinement (new request).
  let refineQuery = $state("");
  let allowLowerGrade = $state(false);
  let bankSpec = $state("");
  let bankGrade = $state("");
  let bankStack = $state("");
  let bankFsp = $state(false);
  let bankQuery = $state("");
  // Client-side refinement of the fetched list.
  let search = $state("");
  let gradeFilter = $state("");
  let fspOnly = $state(false);
  let hideInvited = $state(false);
  let sort = $state<Sort>("score");
  let showFormula = $state(false);
  let card = $state<CandidateCard | null>(null);
  let inviteFor = $state<{ id: string; name: string } | null>(null);
  let offer = $state<Offer>({
    description: "",
    salaryMinRub: 150000,
    salaryMaxRub: 250000,
    contactMethod: "",
  });

  let current = $derived(mode === "need" ? needResult : bankResult);
  let visible = $derived(refine(current?.matches ?? []));

  function refine(list: Match[]): Match[] {
    const needle = search.trim().toLowerCase();
    const filtered = list.filter(
      (m) =>
        (!needle ||
          [
            m.fullName,
            m.category,
            industryLabel(m.industry),
            m.explanation,
            ...m.techStack,
            ...m.achievements.map((a) => a.eventName ?? a.title),
          ]
            .join(" ")
            .toLowerCase()
            .includes(needle)) &&
        (!gradeFilter || m.grade === gradeFilter) &&
        (!fspOnly || m.achievements.length > 0) &&
        (!hideInvited || !m.invitationStatus),
    );
    if (sort === "score") return filtered;
    const key = (m: Match) =>
      sort === "test"
        ? m.score
        : sort === "fsp"
          ? m.achievementScore
          : m.lastActiveAt
            ? Date.parse(m.lastActiveAt)
            : 0;
    return [...filtered].sort((a, b) => key(b) - key(a));
  }

  async function loadNeed() {
    if (!need.specialization) return;
    await run(async () => {
      needResult = await matchApi.matches({
        q: refineQuery.trim(),
        allowLowerGrade,
      });
    }, "");
  }

  async function loadBank() {
    await run(async () => {
      bankResult = await matchApi.bank({
        specialization: bankSpec,
        grade: bankGrade,
        techStack: splitTags(bankStack),
        verifiedAchievements: bankFsp,
        q: bankQuery.trim(),
      });
    }, "");
  }

  function switchMode(next: Mode) {
    mode = next;
    if (next === "need" && !needResult) loadNeed();
    if (next === "bank" && !bankResult) loadBank();
  }

  function openCategory(category: MatchCategory) {
    bankSpec = category.specialization;
    bankGrade = category.grade;
    mode = "bank";
    loadBank();
  }

  async function openCard(candidateId: string) {
    await run(async () => {
      card = await matchApi.candidateCard(candidateId);
    }, "");
  }

  function startInvite(candidateId: string, name: string) {
    card = null;
    inviteFor = { id: candidateId, name };
    offer = {
      description: "",
      salaryMinRub: 150000,
      salaryMaxRub: 250000,
      contactMethod: companyContactLine(company),
    };
  }

  async function submitInvite() {
    if (!inviteFor) return;
    const id = inviteFor.id;
    if (await sendInvitation(id, offer)) {
      // Track the new status in both cached selections without refetching them.
      for (const result of [needResult, bankResult])
        result?.matches.forEach((m) => {
          if (m.candidateId === id) m.invitationStatus = "sent";
        });
      inviteFor = null;
    }
  }

  function resetRefinement() {
    search = "";
    gradeFilter = "";
    fspOnly = false;
    hideInvited = false;
    sort = "score";
  }

  const flagClass = (flag: string) =>
    /ниже|давно|не подтверждена/.test(flag)
      ? "d-mx-badge-warn"
      : /выше/.test(flag)
        ? "d-mx-badge-fsp"
        : "d-mx-badge-muted";

  onMount(() => {
    loadNeed();
  });
</script>

<div class="d-ui-panel">
  <div class="d-ui-row">
    <div>
      <h2 class="d-ui-panel-title">Подбор кандидатов</h2>
      <p class="d-ui-muted" style="margin: 0;">
        Выдача строится по подтверждённым категориям (специализация + грейд по тесту). У каждого
        кандидата — объяснение, почему он в подборке.
      </p>
    </div>
    <div class="d-mx-segmented" role="tablist" aria-label="Режим подбора">
      <button
        role="tab"
        aria-selected={mode === "need"}
        class={`d-mx-segment ${mode === "need" ? "d-mx-segment-active" : ""}`}
        onclick={() => switchMode("need")}
      >
        Подборка по потребности
      </button>
      <button
        role="tab"
        aria-selected={mode === "bank"}
        class={`d-mx-segment ${mode === "bank" ? "d-mx-segment-active" : ""}`}
        onclick={() => switchMode("bank")}
      >
        Банк кандидатов
      </button>
    </div>
  </div>

  {#if mode === "need"}
    {#if !need.specialization}
      <div class="d-ui-note" style="margin-top: 18px;">
        Сначала опишите потребность: специализацию, грейд, стек и задачи команды — на её основе
        формируется подборка.
      </div>
      <button class="d-ui-button" onclick={openNeeds}>
        Описать потребность <ActionIcon name="need" />
      </button>
    {:else}
      <p class="d-mx-summary-line" style="margin-top: 18px;">
        <strong>
          {specializations.find((i) => i.value === need.specialization)?.label ??
            need.specialization} · {gradeLabel(need.minGrade)}
        </strong>
        {need.industry ? ` · ${industryLabel(need.industry)}` : ""}{need.techStack?.length
          ? ` · стек: ${need.techStack.join(", ")}`
          : ""}
        <button
          type="button"
          class="d-ui-outline"
          style="padding: 6px 10px; margin-left: 8px;"
          onclick={openNeeds}
        >
          Изменить
        </button>
      </p>
      <form
        class="d-mx-need-toolbar"
        onsubmit={(event) => {
          event.preventDefault();
          loadNeed();
        }}
      >
        <div>
          <label for="refineQuery" class="d-ui-label">
            Уточнить запрос (добавляется к описанию потребности)
          </label>
          <input
            id="refineQuery"
            placeholder="Например: антифрод, высокие нагрузки"
            bind:value={refineQuery}
            class="d-ui-field"
          />
        </div>
        <label class="d-mx-checkbox-row">
          <input type="checkbox" bind:checked={allowLowerGrade} />
          Показывать грейд ниже
        </label>
        <button type="submit" disabled={busy} class="d-ui-button" style="margin-bottom: 18px;">
          Обновить подборку <ActionIcon name="refresh" />
        </button>
      </form>
    {/if}
  {:else}
    <form
      onsubmit={(event) => {
        event.preventDefault();
        loadBank();
      }}
      style="margin-top: 18px;"
    >
      <div class="d-mx-toolbar">
        <div>
          <label for="bankQuery" class="d-ui-label">Поиск по опыту и стеку</label>
          <input
            id="bankQuery"
            placeholder="Kafka, платежи, дизайн-система…"
            bind:value={bankQuery}
            class="d-ui-field"
          />
        </div>
        <div>
          <label for="bankSpec" class="d-ui-label">Специализация</label>
          <select id="bankSpec" bind:value={bankSpec} class="d-ui-field">
            <option value="">Все</option>
            {#each specializations as item}
              <option value={item.value}>{item.label}</option>
            {/each}
          </select>
        </div>
        <div>
          <label for="bankGrade" class="d-ui-label">Грейд (по тесту)</label>
          <select id="bankGrade" bind:value={bankGrade} class="d-ui-field">
            <option value="">Любой</option>
            <option value="junior">Junior</option>
            <option value="middle">Middle</option>
            <option value="senior">Senior</option>
          </select>
        </div>
        <div>
          <label for="bankStack" class="d-ui-label">Стек (все, через запятую)</label>
          <input
            id="bankStack"
            placeholder="Kotlin, PostgreSQL"
            bind:value={bankStack}
            class="d-ui-field"
          />
        </div>
      </div>
      <div class="d-ui-inline">
        <label class="d-mx-checkbox-row" style="margin: 0;">
          <input type="checkbox" bind:checked={bankFsp} />
          Только с подтверждёнными достижениями ФСП
        </label>
        <button type="submit" disabled={busy} class="d-ui-button">
          Найти <ActionIcon name="matches" />
        </button>
      </div>
    </form>
  {/if}

  {#if current}
    <div class="d-ui-separator"></div>
    <div class="d-ui-row">
      <h3 class="d-ui-panel-title" style="margin: 0;">
        {mode === "need" ? "Рекомендованные категории" : "Категории банка"}
      </h3>
      <button
        type="button"
        class="d-ui-outline"
        onclick={() => (showFormula = !showFormula)}
        aria-expanded={showFormula}
      >
        {showFormula ? "Скрыть" : "Как считается рейтинг"}
      </button>
    </div>
    {#if showFormula}
      <ul class="d-mx-formula" style="margin-top: 12px;">
        {#each current.formula as line}
          <li>{line}</li>
        {/each}
      </ul>
    {/if}
    <div class="d-mx-chips">
      {#each current.categories as category (category.key)}
        <button
          type="button"
          title={category.reason}
          class={`d-mx-chip ${category.recommended ? "d-mx-chip-recommended" : ""} ${
            mode === "bank" && bankSpec === category.specialization && bankGrade === category.grade
              ? "d-mx-chip-active"
              : ""
          }`}
          onclick={() => openCategory(category)}
        >
          <span class="d-mx-chip-title">{category.label}</span>
          <span class="d-mx-chip-meta">
            {category.candidates} канд. · ср. тест {category.avgTestScore} · с ФСП
            {category.withFsp}
          </span>
          {#if mode === "need"}
            <span class="d-mx-chip-meta">
              {fitLabels[category.fit] ?? category.fit}{category.recommended
                ? " · рекомендуем"
                : ""}
            </span>
          {/if}
        </button>
      {:else}
        <EmptyState
          icon="matches"
          title="Категории пока пусты"
          description="Кандидатов с подтверждённой категорией пока нет."
        />
      {/each}
    </div>
  {/if}
</div>

{#if current}
  <div class="d-ui-panel">
    <div class="d-mx-toolbar">
      <div>
        <label for="search" class="d-ui-label">Уточнить внутри подборки (без нового запроса)</label>
        <input
          id="search"
          placeholder="Имя, стек, событие ФСП, отрасль…"
          bind:value={search}
          class="d-ui-field"
        />
      </div>
      <div>
        <label for="gradeFilter" class="d-ui-label">Грейд</label>
        <select id="gradeFilter" bind:value={gradeFilter} class="d-ui-field">
          <option value="">Любой</option>
          <option value="junior">Junior</option>
          <option value="middle">Middle</option>
          <option value="senior">Senior</option>
        </select>
      </div>
      <div>
        <label for="sort" class="d-ui-label">Сортировка</label>
        <select id="sort" bind:value={sort} class="d-ui-field">
          <option value="score">{mode === "need" ? "По релевантности" : "По силе профиля"}</option>
          <option value="test">По баллу теста</option>
          <option value="fsp">По достижениям ФСП</option>
          <option value="fresh">По свежести профиля</option>
        </select>
      </div>
      <button
        type="button"
        class="d-ui-outline"
        style="margin-bottom: 18px;"
        onclick={resetRefinement}
      >
        Сбросить уточнения
      </button>
    </div>
    <div class="d-ui-inline">
      <label class="d-mx-checkbox-row" style="margin: 0;">
        <input type="checkbox" bind:checked={fspOnly} />
        С достижениями ФСП
      </label>
      <label class="d-mx-checkbox-row" style="margin: 0;">
        <input type="checkbox" bind:checked={hideInvited} />
        Скрыть уже приглашённых
      </label>
      <span class="d-ui-muted">
        Показано {visible.length} из {current.matches.length}{current.total > current.matches.length
          ? ` (всего найдено ${current.total})`
          : ""} — исходная подборка сохранена.
      </span>
    </div>
  </div>

  {#each visible as match, index (match.candidateId)}
    <article class="d-ui-panel">
      <div class="d-ui-row" style="align-items: flex-start;">
        <div style="flex: 1 1 380px;">
          <div class="d-ui-kicker">
            {match.category.toUpperCase()} · №{index + 1}
          </div>
          <h2 class="d-ui-panel-title" style="margin-top: 12px;">
            {match.fullName}
          </h2>
          <p class="d-mx-summary-line" style="margin: 0;">
            {match.explanation}
          </p>
        </div>
        <div style="text-align: right;">
          <div class="d-ui-score">
            {match.totalScore}
            <span style="font-size: 14px;">/ 100</span>
          </div>
          <div class="d-ui-muted">
            {mode === "need" ? "релевантность" : "сила профиля"}
          </div>
        </div>
      </div>
      <div style="margin-top: 12px;">
        {#if match.invitationStatus}
          <span class="d-mx-badge d-mx-badge-ok">
            Приглашение: {invitationStatusLabels[match.invitationStatus] ?? match.invitationStatus}
          </span>
        {/if}
        {#each match.achievements.slice(0, 3) as achievement}
          <span class="d-mx-badge d-mx-badge-fsp" title={achievement.discipline ?? ""}>
            ФСП: {achievementLabel(achievement)}
          </span>
        {/each}
        {#if match.achievements.length > 3}
          <span class="d-mx-badge d-mx-badge-fsp">
            ещё {match.achievements.length - 3}
          </span>
        {/if}
        {#each match.flags as flag}
          <span class={`d-mx-badge ${flagClass(flag)}`}>
            {flag}
          </span>
        {/each}
      </div>
      <details open={index < 3}>
        <summary style="cursor: pointer; font-size: 13px; font-weight: 600;">
          Почему кандидат в подборке
        </summary>
        <FactorBreakdown factors={match.factors} />
      </details>
      <div style="margin-top: 8px;">
        {#each match.techStack as skill}
          <span class="d-ui-tag">{skill}</span>
        {/each}
      </div>
      <div class="d-ui-separator"></div>
      <div class="d-ui-inline">
        <button
          type="button"
          class="d-ui-outline"
          disabled={busy}
          onclick={() => openCard(match.candidateId)}
        >
          Карточка кандидата <ActionIcon name="view" />
        </button>
        <button
          type="button"
          class="d-ui-button"
          onclick={() => startInvite(match.candidateId, match.fullName)}
        >
          Пригласить <ActionIcon name="send" />
        </button>
      </div>
    </article>
  {:else}
    <EmptyState
      icon="matches"
      title="Кандидаты не найдены"
      description={current.matches.length
        ? "Под уточнения никто не подходит — подборка сохранена."
        : mode === "need"
          ? "Под эту потребность пока нет кандидатов с подтверждённой категорией. Попробуйте другой грейд или банк кандидатов."
          : "По этим фильтрам кандидатов нет."}
    >
      {#snippet action()}
        {#if current.matches.length}
          <button class="d-ui-outline" onclick={resetRefinement}>
            Сбросить уточнения <ActionIcon name="refresh" />
          </button>
        {:else if mode === "need"}
          <button class="d-ui-outline" onclick={() => switchMode("bank")}>
            Открыть банк кандидатов <ActionIcon name="matches" />
          </button>
        {:else}
          <button
            class="d-ui-outline"
            onclick={() => {
              bankSpec = "";
              bankGrade = "";
              bankStack = "";
              bankFsp = false;
              bankQuery = "";
              loadBank();
            }}
          >
            Сбросить фильтры <ActionIcon name="refresh" />
          </button>
        {/if}
      {/snippet}
    </EmptyState>
  {/each}
{/if}

{#if card}
  <CandidateCardModal {card} onClose={() => (card = null)} onInvite={startInvite} />
{/if}

{#if inviteFor}
  <dialog class="d-mx-modal" aria-labelledby="invite-title" use:modal={() => (inviteFor = null)}>
    <form
      onsubmit={(event) => {
        event.preventDefault();
        submitInvite();
      }}
    >
      <div class="d-ui-row">
        <h2 id="invite-title" class="d-ui-panel-title">
          Приглашение кандидату: {inviteFor.name}
        </h2>
        <button type="button" class="d-ui-outline" onclick={() => (inviteFor = null)}>
          Закрыть <ActionIcon name="close" />
        </button>
      </div>
      <label for="offerDescription" class="d-ui-label">
        Описание предложения (роль, задачи, формат работы)
      </label>
      <textarea
        id="offerDescription"
        required
        maxlength="4000"
        bind:value={offer.description}
        class="d-ui-textarea"
      ></textarea>
      <div class="d-ui-grid">
        <div>
          <label for="salaryMin" class="d-ui-label">Зарплата от, ₽</label>
          <input
            id="salaryMin"
            required
            type="number"
            min="1"
            bind:value={offer.salaryMinRub}
            class="d-ui-field"
          />
        </div>
        <div>
          <label for="salaryMax" class="d-ui-label">Зарплата до, ₽</label>
          <input
            id="salaryMax"
            required
            type="number"
            min={offer.salaryMinRub}
            bind:value={offer.salaryMaxRub}
            class="d-ui-field"
          />
        </div>
      </div>
      <label for="contactMethod" class="d-ui-label">Способ связи для кандидата</label>
      <input
        id="contactMethod"
        required
        maxlength="300"
        placeholder="Email, телефон или Telegram рекрутера"
        bind:value={offer.contactMethod}
        class="d-ui-field"
      />
      {#if !companyContactLine(company)}
        <p class="d-ui-muted">
          Совет: укажите контакты в профиле компании — они будут подставляться автоматически.
        </p>
      {/if}
      <p class="d-ui-muted">
        Кандидат увидит название компании «{company.name || "—"}», условия и способ связи. Его
        контакты станут доступны вам только после принятия приглашения.
      </p>
      <button
        type="submit"
        disabled={busy || offer.salaryMaxRub < offer.salaryMinRub || !offer.contactMethod.trim()}
        class="d-ui-button"
      >
        Отправить приглашение <ActionIcon name="send" />
      </button>
    </form>
  </dialog>
{/if}
