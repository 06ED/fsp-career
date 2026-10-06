<script lang="ts">
  import { onMount } from "svelte";
  import { sx } from "$lib/svelte-stylex";
  import { ui as s } from "$lib/dashboard.stylex";
  import { mx } from "./matching.stylex";
  import ActionIcon from "$lib/ActionIcon.svelte";
  import FactorBreakdown from "./FactorBreakdown.svelte";
  import CandidateCardModal from "./CandidateCardModal.svelte";
  import { dashboardApi } from "./service";
  import { specializations } from "./specializations";
  import {
    achievementLabel,
    companyContactLine,
    fitLabels,
    gradeLabel,
    invitationStatusLabels,
    splitTags,
  } from "./matching";
  import type {
    CandidateCard,
    Company,
    Match,
    MatchCategory,
    MatchResponse,
    Need,
    Offer,
    RunAction,
  } from "./types";

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
            m.industry,
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
      needResult = await dashboardApi.matches({
        q: refineQuery.trim(),
        allowLowerGrade,
      });
    }, "");
  }

  async function loadBank() {
    await run(async () => {
      bankResult = await dashboardApi.bank({
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
      card = await dashboardApi.candidateCard(candidateId);
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

  const flagStyle = (flag: string) =>
    /ниже|давно|не подтверждена/.test(flag)
      ? mx.badgeWarn
      : /выше/.test(flag)
        ? mx.badgeFsp
        : mx.badgeMuted;

  onMount(() => {
    loadNeed();
  });
</script>

<div {...sx(s.panel)}>
  <div {...sx(s.row)}>
    <div>
      <h2 {...sx(s.panelTitle)}>Подбор кандидатов</h2>
      <p {...sx(s.muted)} style="margin: 0;">
        Выдача строится по подтверждённым категориям (специализация + грейд по
        тесту). У каждого кандидата — объяснение, почему он в подборке.
      </p>
    </div>
    <div {...sx(mx.segmented)} role="tablist" aria-label="Режим подбора">
      <button
        role="tab"
        aria-selected={mode === "need"}
        {...sx(mx.segment, mode === "need" && mx.segmentActive)}
        onclick={() => switchMode("need")}>Подборка по потребности</button
      >
      <button
        role="tab"
        aria-selected={mode === "bank"}
        {...sx(mx.segment, mode === "bank" && mx.segmentActive)}
        onclick={() => switchMode("bank")}>Банк кандидатов</button
      >
    </div>
  </div>

  {#if mode === "need"}
    {#if !need.specialization}
      <div {...sx(s.note)} style="margin-top: 18px;">
        Сначала опишите потребность: специализацию, грейд, стек и задачи команды
        — на её основе формируется подборка.
      </div>
      <button {...sx(s.button)} onclick={openNeeds}
        >Описать потребность <ActionIcon name="need" /></button
      >
    {:else}
      <p {...sx(mx.summaryLine)} style="margin-top: 18px;">
        <strong
          >{specializations.find((i) => i.value === need.specialization)
            ?.label ?? need.specialization} · {gradeLabel(
            need.minGrade,
          )}</strong
        >{need.industry ? ` · ${need.industry}` : ""}{need.techStack?.length
          ? ` · стек: ${need.techStack.join(", ")}`
          : ""}
        <button
          type="button"
          {...sx(s.outline)}
          style="padding: 6px 10px; margin-left: 8px;"
          onclick={openNeeds}>Изменить</button
        >
      </p>
      <form
        {...sx(mx.needToolbar)}
        onsubmit={(event) => {
          event.preventDefault();
          loadNeed();
        }}
      >
        <div>
          <label for="refineQuery" {...sx(s.label)}
            >Уточнить запрос (добавляется к описанию потребности)</label
          >
          <input
            id="refineQuery"
            placeholder="Например: антифрод, высокие нагрузки"
            bind:value={refineQuery}
            {...sx(s.field)}
          />
        </div>
        <label {...sx(mx.checkboxRow)}>
          <input type="checkbox" bind:checked={allowLowerGrade} /> Показывать грейд
          ниже
        </label>
        <button
          type="submit"
          disabled={busy}
          {...sx(s.button)}
          style="margin-bottom: 18px;"
          >Обновить подборку <ActionIcon name="refresh" /></button
        >
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
      <div {...sx(mx.toolbar)}>
        <div>
          <label for="bankQuery" {...sx(s.label)}>Поиск по опыту и стеку</label>
          <input
            id="bankQuery"
            placeholder="Kafka, платежи, дизайн-система…"
            bind:value={bankQuery}
            {...sx(s.field)}
          />
        </div>
        <div>
          <label for="bankSpec" {...sx(s.label)}>Специализация</label>
          <select id="bankSpec" bind:value={bankSpec} {...sx(s.field)}>
            <option value="">Все</option>
            {#each specializations as item}<option value={item.value}
                >{item.label}</option
              >{/each}
          </select>
        </div>
        <div>
          <label for="bankGrade" {...sx(s.label)}>Грейд (по тесту)</label>
          <select id="bankGrade" bind:value={bankGrade} {...sx(s.field)}>
            <option value="">Любой</option>
            <option value="junior">Junior</option>
            <option value="middle">Middle</option>
            <option value="senior">Senior</option>
          </select>
        </div>
        <div>
          <label for="bankStack" {...sx(s.label)}
            >Стек (все, через запятую)</label
          >
          <input
            id="bankStack"
            placeholder="Kotlin, PostgreSQL"
            bind:value={bankStack}
            {...sx(s.field)}
          />
        </div>
      </div>
      <div {...sx(s.inline)}>
        <label {...sx(mx.checkboxRow)} style="margin: 0;">
          <input type="checkbox" bind:checked={bankFsp} /> Только с подтверждёнными
          достижениями ФСП
        </label>
        <button type="submit" disabled={busy} {...sx(s.button)}
          >Найти <ActionIcon name="matches" /></button
        >
      </div>
    </form>
  {/if}

  {#if current}
    <div {...sx(s.separator)}></div>
    <div {...sx(s.row)}>
      <h3 {...sx(s.panelTitle)} style="margin: 0;">
        {mode === "need" ? "Рекомендованные категории" : "Категории банка"}
      </h3>
      <button
        type="button"
        {...sx(s.outline)}
        onclick={() => (showFormula = !showFormula)}
        aria-expanded={showFormula}
        >{showFormula ? "Скрыть" : "Как считается рейтинг"}</button
      >
    </div>
    {#if showFormula}
      <ul {...sx(mx.formula)} style="margin-top: 12px;">
        {#each current.formula as line}<li>{line}</li>{/each}
      </ul>
    {/if}
    <div {...sx(mx.chips)}>
      {#each current.categories as category (category.key)}
        <button
          type="button"
          title={category.reason}
          {...sx(
            mx.chip,
            category.recommended && mx.chipRecommended,
            mode === "bank" &&
              bankSpec === category.specialization &&
              bankGrade === category.grade &&
              mx.chipActive,
          )}
          onclick={() => openCategory(category)}
        >
          <span {...sx(mx.chipTitle)}>{category.label}</span>
          <span {...sx(mx.chipMeta)}
            >{category.candidates} канд. · ср. тест {category.avgTestScore} · с ФСП
            {category.withFsp}</span
          >
          {#if mode === "need"}<span {...sx(mx.chipMeta)}
              >{fitLabels[category.fit] ?? category.fit}{category.recommended
                ? " · рекомендуем"
                : ""}</span
            >{/if}
        </button>
      {:else}
        <p {...sx(s.muted)}>Пока нет кандидатов с подтверждённой категорией.</p>
      {/each}
    </div>
  {/if}
</div>

{#if current}
  <div {...sx(s.panel)}>
    <div {...sx(mx.toolbar)}>
      <div>
        <label for="search" {...sx(s.label)}
          >Уточнить внутри подборки (без нового запроса)</label
        >
        <input
          id="search"
          placeholder="Имя, стек, событие ФСП, отрасль…"
          bind:value={search}
          {...sx(s.field)}
        />
      </div>
      <div>
        <label for="gradeFilter" {...sx(s.label)}>Грейд</label>
        <select id="gradeFilter" bind:value={gradeFilter} {...sx(s.field)}>
          <option value="">Любой</option>
          <option value="junior">Junior</option>
          <option value="middle">Middle</option>
          <option value="senior">Senior</option>
        </select>
      </div>
      <div>
        <label for="sort" {...sx(s.label)}>Сортировка</label>
        <select id="sort" bind:value={sort} {...sx(s.field)}>
          <option value="score"
            >{mode === "need" ? "По релевантности" : "По силе профиля"}</option
          >
          <option value="test">По баллу теста</option>
          <option value="fsp">По достижениям ФСП</option>
          <option value="fresh">По свежести профиля</option>
        </select>
      </div>
      <button
        type="button"
        {...sx(s.outline)}
        style="margin-bottom: 18px;"
        onclick={resetRefinement}>Сбросить уточнения</button
      >
    </div>
    <div {...sx(s.inline)}>
      <label {...sx(mx.checkboxRow)} style="margin: 0;">
        <input type="checkbox" bind:checked={fspOnly} /> С достижениями ФСП
      </label>
      <label {...sx(mx.checkboxRow)} style="margin: 0;">
        <input type="checkbox" bind:checked={hideInvited} /> Скрыть уже приглашённых
      </label>
      <span {...sx(s.muted)}
        >Показано {visible.length} из {current.matches.length}{current.total >
        current.matches.length
          ? ` (всего найдено ${current.total})`
          : ""} — исходная подборка сохранена.</span
      >
    </div>
  </div>

  {#each visible as match, index (match.candidateId)}
    <article {...sx(s.panel)}>
      <div {...sx(s.row)} style="align-items: flex-start;">
        <div style="flex: 1 1 380px;">
          <div {...sx(s.kicker)}>
            {match.category.toUpperCase()} · №{index + 1}
          </div>
          <h2 {...sx(s.panelTitle)} style="margin-top: 12px;">
            {match.fullName}
          </h2>
          <p {...sx(mx.summaryLine)} style="margin: 0;">{match.explanation}</p>
        </div>
        <div style="text-align: right;">
          <div {...sx(s.score)}>
            {match.totalScore}<span style="font-size: 14px;"> / 100</span>
          </div>
          <div {...sx(s.muted)}>
            {mode === "need" ? "релевантность" : "сила профиля"}
          </div>
        </div>
      </div>
      <div style="margin-top: 12px;">
        {#if match.invitationStatus}<span {...sx(mx.badge, mx.badgeOk)}
            >Приглашение: {invitationStatusLabels[match.invitationStatus] ??
              match.invitationStatus}</span
          >{/if}
        {#each match.achievements.slice(0, 3) as achievement}<span
            {...sx(mx.badge, mx.badgeFsp)}
            title={achievement.discipline ?? ""}
            >ФСП: {achievementLabel(achievement)}</span
          >{/each}
        {#if match.achievements.length > 3}<span {...sx(mx.badge, mx.badgeFsp)}
            >ещё {match.achievements.length - 3}</span
          >{/if}
        {#each match.flags as flag}<span {...sx(mx.badge, flagStyle(flag))}
            >{flag}</span
          >{/each}
      </div>
      <details open={index < 3}>
        <summary style="cursor: pointer; font-size: 13px; font-weight: 600;"
          >Почему кандидат в подборке</summary
        >
        <FactorBreakdown factors={match.factors} />
      </details>
      <div style="margin-top: 8px;">
        {#each match.techStack as skill}<span {...sx(s.tag)}>{skill}</span
          >{/each}
      </div>
      <div {...sx(s.separator)}></div>
      <div {...sx(s.inline)}>
        <button
          type="button"
          {...sx(s.outline)}
          disabled={busy}
          onclick={() => openCard(match.candidateId)}
          >Карточка кандидата <ActionIcon name="view" /></button
        >
        <button
          type="button"
          {...sx(s.button)}
          onclick={() => startInvite(match.candidateId, match.fullName)}
          >Пригласить <ActionIcon name="send" /></button
        >
      </div>
    </article>
  {:else}
    <div {...sx(s.panel)}>
      <p {...sx(s.muted)}>
        {current.matches.length
          ? "Под уточнения никто не подходит — сбросьте их, подборка сохранена."
          : mode === "need"
            ? "Пока нет кандидатов с подтверждённой категорией под эту потребность. Попробуйте «Показывать грейд ниже» или откройте банк кандидатов."
            : "По этим фильтрам кандидатов нет."}
      </p>
    </div>
  {/each}
{/if}

{#if card}
  <CandidateCardModal
    {card}
    onClose={() => (card = null)}
    onInvite={startInvite}
  />
{/if}

{#if inviteFor}
  <div {...sx(mx.backdrop)}>
    <form
      {...sx(mx.modal)}
      role="dialog"
      aria-modal="true"
      aria-labelledby="invite-title"
      onsubmit={(event) => {
        event.preventDefault();
        submitInvite();
      }}
    >
      <div {...sx(s.row)}>
        <h2 id="invite-title" {...sx(s.panelTitle)}>
          Приглашение кандидату: {inviteFor.name}
        </h2>
        <button
          type="button"
          {...sx(s.outline)}
          onclick={() => (inviteFor = null)}
          >Закрыть <ActionIcon name="close" /></button
        >
      </div>
      <label for="offerDescription" {...sx(s.label)}
        >Описание предложения (роль, задачи, формат работы)</label
      ><textarea
        id="offerDescription"
        required
        maxlength="4000"
        bind:value={offer.description}
        {...sx(s.textarea)}
      ></textarea>
      <div {...sx(s.grid)}>
        <div>
          <label for="salaryMin" {...sx(s.label)}>Зарплата от, ₽</label><input
            id="salaryMin"
            required
            type="number"
            min="1"
            bind:value={offer.salaryMinRub}
            {...sx(s.field)}
          />
        </div>
        <div>
          <label for="salaryMax" {...sx(s.label)}>Зарплата до, ₽</label><input
            id="salaryMax"
            required
            type="number"
            min={offer.salaryMinRub}
            bind:value={offer.salaryMaxRub}
            {...sx(s.field)}
          />
        </div>
      </div>
      <label for="contactMethod" {...sx(s.label)}
        >Способ связи для кандидата</label
      ><input
        id="contactMethod"
        required
        maxlength="300"
        placeholder="Email, телефон или Telegram рекрутера"
        bind:value={offer.contactMethod}
        {...sx(s.field)}
      />
      {#if !companyContactLine(company)}
        <p {...sx(s.muted)}>
          Совет: укажите контакты в профиле компании — они будут подставляться
          автоматически.
        </p>
      {/if}
      <p {...sx(s.muted)}>
        Кандидат увидит название компании «{company.name || "—"}», условия и
        способ связи. Его контакты станут доступны вам только после принятия
        приглашения.
      </p>
      <button
        type="submit"
        disabled={busy ||
          offer.salaryMaxRub < offer.salaryMinRub ||
          !offer.contactMethod.trim()}
        {...sx(s.button)}
        >Отправить приглашение <ActionIcon name="send" /></button
      >
    </form>
  </div>
{/if}
