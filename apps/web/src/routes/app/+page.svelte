<script lang="ts">
  import { onMount } from "svelte";
  import { auth } from "$lib/client";
  import { sx } from "$lib/svelte-stylex";
  import ActionIcon from "$lib/ActionIcon.svelte";
  import { ui as s } from "$lib/dashboard.stylex";
  import { dashboardApi } from "$lib/dashboard/service";
  import type {
    Company,
    Invitation,
    InvitationStatusAction,
    Match,
    Need,
    Offer,
    Profile,
    ResumeDraft,
    Role,
    Section,
  } from "$lib/dashboard/types";
  import DashboardNavigation from "$lib/dashboard/DashboardNavigation.svelte";
  import Overview from "$lib/dashboard/Overview.svelte";
  import CandidateProfile from "$lib/dashboard/CandidateProfile.svelte";
  import CandidateAssessment from "$lib/dashboard/CandidateAssessment.svelte";
  import EmployerCompany from "$lib/dashboard/EmployerCompany.svelte";
  import EmployerNeeds from "$lib/dashboard/EmployerNeeds.svelte";
  import EmployerMatches from "$lib/dashboard/EmployerMatches.svelte";
  import Invitations from "$lib/dashboard/Invitations.svelte";

  let ready = $state(false);
  let authenticated = $state(false);
  let role = $state<Role>("candidate");
  let section = $state<Section>("overview");
  let error = $state("");
  let success = $state("");
  let busy = $state(false);
  let identity = $state("");
  let desiredRole = $state<Role>("candidate");
  let profile = $state<Profile>({
    fullName: "",
    email: "",
    phone: "",
    processingConsent: false,
    consentPublication: false,
    specialization: "",
    industry: "",
    desiredGrade: "junior",
    techStack: [],
    experience: "",
    softSkills: [],
    targetRoles: [],
    cvHistory: [],
  });
  let company = $state<Company>({ name: "", description: "", website: "" });
  let need = $state<Need>({
    specialization: "",
    industry: "",
    minGrade: "junior",
    description: "",
  });
  let matches = $state<Match[]>([]);
  let invitations = $state<Invitation[]>([]);
  let resumeDraft = $state<ResumeDraft | null>(null);
  let techStackText = $state("");
  let softSkillsText = $state("");
  let targetRolesText = $state("");

  onMount(() => {
    const url = new URL(window.location.href);
    desiredRole =
      url.searchParams.get("role") === "employer" ? "employer" : "candidate";
    auth
      .init({
        onLoad: "check-sso",
        checkLoginIframe: false,
        pkceMethod: "S256",
      })
      .then(async (isAuthenticated) => {
        authenticated = isAuthenticated;
        if (isAuthenticated) {
          const roles = auth.realmAccess?.roles ?? [];
          role = roles.includes("employer") ? "employer" : "candidate";
          identity =
            auth.tokenParsed?.email?.toString() ??
            auth.tokenParsed?.preferred_username?.toString() ??
            "";
          await refresh();
        }
        ready = true;
      })
      .catch((e: unknown) => {
        error =
          e instanceof Error ? e.message : "Не удалось подключить авторизацию";
        ready = true;
      });
  });

  async function refresh() {
    try {
      if (role === "candidate") {
        const [p, i] = await Promise.all([
          dashboardApi.profile(),
          dashboardApi.invitations(),
        ]);
        profile = {
          ...profile,
          ...p,
          email: p.email || auth.tokenParsed?.email?.toString() || "",
        };
        invitations = i;
        syncTags();
      } else {
        const [c, n, i] = await Promise.all([
          dashboardApi.company(),
          dashboardApi.need(),
          dashboardApi.invitations(),
        ]);
        company = { ...company, ...c };
        need = { ...need, ...n };
        invitations = i;
      }
    } catch (e) {
      error = message(e);
    }
  }

  function message(e: unknown) {
    return e instanceof Error ? e.message : "Произошла ошибка";
  }
  async function action(job: () => Promise<void>, confirmation: string) {
    error = "";
    success = "";
    busy = true;
    try {
      await job();
      success = confirmation;
    } catch (e) {
      error = message(e);
    } finally {
      busy = false;
    }
  }
  async function openSection(id: Section) {
    section = id;
    error = "";
    success = "";
    if (id === "matches") await findMatches();
    if (id === "invitations")
      await action(async () => {
        invitations = await dashboardApi.invitations();
      }, "");
  }
  function splitTags(value: string): string[] {
    return [
      ...new Set(
        value
          .split(",")
          .map((tag) => tag.trim())
          .filter(Boolean),
      ),
    ];
  }
  function syncTags() {
    techStackText = (profile.techStack ?? []).join(", ");
    softSkillsText = (profile.softSkills ?? []).join(", ");
    targetRolesText = (profile.targetRoles ?? []).join(", ");
  }
  function applyTags() {
    profile.techStack = splitTags(techStackText);
    profile.softSkills = splitTags(softSkillsText);
    profile.targetRoles = splitTags(targetRolesText);
  }
  async function saveProfile() {
    await action(async () => {
      applyTags();
      profile = await dashboardApi.saveProfile(profile);
    }, "Профиль сохранён.");
  }
  async function downloadResume() {
    await action(async () => {
      const pdf = await dashboardApi.exportResume();
      const url = URL.createObjectURL(pdf);
      const link = document.createElement("a");
      link.href = url;
      link.download = "fsp-resume.pdf";
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.setTimeout(() => URL.revokeObjectURL(url), 30_000);
    }, "Резюме готово к скачиванию.");
  }
  async function revokeConsent() {
    await action(async () => {
      profile = await dashboardApi.revokeConsent();
      invitations = [];
      resumeDraft = null;
      techStackText = "";
      softSkillsText = "";
      targetRolesText = "";
    }, "Данные удалены. Для продолжения потребуется новое согласие.");
  }
  async function uploadResume(event: Event) {
    const input = event.currentTarget as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    if (!profile.processingConsent) {
      error = "Сначала сохраните профиль с согласием на обработку данных.";
      return;
    }
    if (file.type !== "application/pdf" || file.size > 5 * 1024 * 1024) {
      error = "Выберите PDF-файл размером не более 5 МБ.";
      return;
    }
    await action(async () => {
      resumeDraft = await dashboardApi.uploadResume(file);
      profile = {
        ...profile,
        ...resumeDraft.parsedFields,
        email: profile.email,
        processingConsent: profile.processingConsent,
        consentPublication: profile.consentPublication,
      };
      syncTags();
    }, "Черновик распознан. Проверьте каждое поле и подтвердите перенос данных.");
  }
  async function confirmResume() {
    if (!resumeDraft) return;
    await action(async () => {
      applyTags();
      profile = await dashboardApi.confirmResume(resumeDraft!.draftId, profile);
      resumeDraft = null;
    }, "Проверенные данные из резюме сохранены. Публикация профиля не менялась.");
  }
  async function saveCompany() {
    await action(async () => {
      company = await dashboardApi.saveCompany(company);
    }, "Данные компании сохранены.");
  }
  async function saveNeed() {
    await action(async () => {
      need = await dashboardApi.saveNeed(need);
    }, "Потребность сохранена. Теперь можно открыть подборку.");
  }
  async function findMatches() {
    await action(async () => {
      matches = await dashboardApi.matches();
    }, "");
  }
  async function sendInvitation(candidate: Match, offer: Offer) {
    await action(async () => {
      await dashboardApi.sendInvitation(candidate.candidateId, offer);
      await refresh();
    }, "Приглашение отправлено кандидату. Контакты появятся после принятия.");
  }
  async function updateInvitation(
    invitation: Invitation,
    status: InvitationStatusAction,
  ) {
    await action(
      async () => {
        await dashboardApi.updateInvitation(invitation.id, status);
        await refresh();
      },
      status === "accept"
        ? "Приглашение принято. Компания получит ваши контакты."
        : "Статус приглашения обновлён.",
    );
  }
</script>

<svelte:head><title>Личный кабинет — ФСП Карьера</title></svelte:head>

{#if !ready}
  <div {...sx(s.main)}><p {...sx(s.muted)}>Подключаем платформу…</p></div>
{:else if !authenticated}
  <div {...sx(s.shell)}>
    <aside {...sx(s.sidebar)}>
      <a href="/" {...sx(s.brand)}
        ><img src="/fsp-mark.svg" alt="" {...sx(s.emblem)} /> ФСП / КАРЬЕРА</a
      >
    </aside>
    <main {...sx(s.main)}>
      <div {...sx(s.kicker)}>ДОБРО ПОЖАЛОВАТЬ / 01</div>
      <h1 {...sx(s.title)}>
        {desiredRole === "employer"
          ? "Найдите специалистов,\nкоторые подходят."
          : "Откройте свой следующий\nкарьерный шаг."}
      </h1>
      <p {...sx(s.subtitle)}>
        Для продолжения войдите или создайте аккаунт. Адрес электронной почты
        нужно подтвердить.
      </p>
      {#if error}<p role="alert" {...sx(s.error)}>{error}</p>{/if}
      <div {...sx(s.panel)} style="max-width: 540px; margin-top: 35px;">
        <h2 {...sx(s.panelTitle)}>Ваш путь начинается здесь</h2>
        <p {...sx(s.muted)}>
          Вход через локальный сервис авторизации Keycloak. Новый аккаунт
          получает роль кандидата; доступ работодателя выдаётся администратором.
        </p>
        <div {...sx(s.inline)}>
          <button
            {...sx(s.button)}
            onclick={() => auth.login({ redirectUri: window.location.href })}
            >Войти <ActionIcon name="login" /></button
          >
          <button
            {...sx(s.outline)}
            onclick={() => auth.register({ redirectUri: window.location.href })}
            >Создать аккаунт <ActionIcon name="signup" /></button
          >
        </div>
      </div>
    </main>
  </div>
{:else}
  <div {...sx(s.shell)}>
    <DashboardNavigation {role} {section} {identity} {openSection} />
    <main {...sx(s.main)}>
      <div {...sx(s.top)}>
        <div>
          <div {...sx(s.kicker)}>
            ФСП КАРЬЕРА / {role === "candidate" ? "СПЕЦИАЛИСТ" : "РАБОТОДАТЕЛЬ"}
          </div>
          <h1 {...sx(s.title)}>
            {section === "overview"
              ? "Главное"
              : section === "profile"
                ? "Мой профиль"
                : section === "assessment"
                  ? "Оценка навыков"
                  : section === "company"
                    ? "Моя компания"
                    : section === "needs"
                      ? "Потребность команды"
                      : section === "matches"
                        ? "Подбор кандидатов"
                        : "Приглашения"}
          </h1>
          <p {...sx(s.subtitle)}>
            {role === "candidate"
              ? "Ваши навыки превращаются в новые возможности."
              : "Найдите специалистов по направлению и результату оценки."}
          </p>
        </div>
      </div>
      {#if error}<div role="alert" {...sx(s.error)}>{error}</div>{/if}
      {#if success}<div role="status" {...sx(s.success)}>{success}</div>{/if}
      {#if section === "overview"}
        <Overview
          {role}
          {profile}
          {company}
          {need}
          {invitations}
          {openSection}
        />
      {:else if section === "profile" && role === "candidate"}
        <CandidateProfile
          bind:profile
          {resumeDraft}
          bind:techStackText
          bind:softSkillsText
          bind:targetRolesText
          {busy}
          {uploadResume}
          {confirmResume}
          {saveProfile}
          {downloadResume}
          {revokeConsent}
        />
      {:else if section === "assessment" && role === "candidate"}
        <CandidateAssessment {profile} refreshProfile={refresh} />
      {:else if section === "company" && role === "employer"}
        <EmployerCompany bind:company {busy} {saveCompany} />
      {:else if section === "needs" && role === "employer"}
        <EmployerNeeds bind:need {busy} {saveNeed} />
      {:else if section === "matches" && role === "employer"}
        <EmployerMatches {matches} {busy} {findMatches} {sendInvitation} />
      {:else if section === "invitations"}
        <Invitations {invitations} {role} {busy} {updateInvitation} />
      {/if}
    </main>
  </div>
{/if}
