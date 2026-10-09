<script lang="ts">
  import { onMount, tick } from "svelte";
  import { goto } from "$app/navigation";
  import { auth, initAuth } from "$lib/shared/auth";
  import { ActionIcon } from "$lib/shared/ui";
  import { accountApi } from "$lib/entities/account";
  import { candidateApi } from "$lib/entities/candidate";
  import { companyApi } from "$lib/entities/company";
  import { invitationApi } from "$lib/entities/invitation";
  import type { Role, Section } from "$lib/entities/account";
  import type { Profile, ResumeDraft } from "$lib/entities/candidate";
  import type { Company, Need } from "$lib/entities/company";
  import type { Invitation, InvitationStatusAction, Offer } from "$lib/entities/invitation";
  import { DashboardNavigation } from "$lib/widgets/dashboard-navigation";
  import { Overview } from "$lib/widgets/overview";
  import {
    CandidateProfile,
    CandidateSettings,
    downloadResumePdf,
  } from "$lib/features/edit-candidate-profile";
  import { CandidateAssessment } from "$lib/features/take-assessment";
  import { EmployerCompany } from "$lib/features/edit-company";
  import { EmployerNeeds } from "$lib/features/edit-need";
  import { EmployerMatches } from "$lib/features/find-candidates";
  import { Invitations } from "$lib/features/manage-invitations";
  import { RoleChoice } from "$lib/features/choose-role";
  import { CandidateVacancies } from "$lib/features/browse-vacancies";
  import { CandidateApplications } from "$lib/features/manage-applications";
  import { CandidateTasks } from "$lib/features/answer-task";
  import { EmployerVacancies } from "$lib/features/manage-vacancies";
  import { EmployerTasks } from "$lib/features/manage-tasks";
  const vacancySectionTitles: Record<string, string> = {
    vacancies: "Вакансии",
    applications: "Мои отклики",
    tasks: "Регулярные задания",
  };

  let ready = $state(false);
  let initError = $state("");
  let authenticated = $state(false);
  let role = $state<Role>("candidate");
  let section = $state<Section>("overview");
  let mainElement = $state<HTMLElement>();
  let error = $state("");
  let success = $state("");
  let busy = $state(false);
  let identity = $state("");
  let desiredRole = $state<Role>("candidate");
  let needsRole = $state(false);
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
  let company = $state<Company>({
    name: "",
    description: "",
    website: "",
    industry: "",
    contactEmail: "",
    contactPhone: "",
    contactTelegram: "",
    contactNote: "",
  });
  let need = $state<Need>({
    specialization: "",
    industry: "",
    minGrade: "junior",
    description: "",
    techStack: [],
  });
  let invitations = $state<Invitation[]>([]);
  let invitationsLoaded = $state(false);
  let resumeDraft = $state<ResumeDraft | null>(null);
  let techStackText = $state("");
  let softSkillsText = $state("");
  let targetRolesText = $state("");

  onMount(() => {
    const url = new URL(window.location.href);
    if (url.searchParams.get("section") === "profile") section = "profile";
    desiredRole = url.searchParams.get("role") === "employer" ? "employer" : "candidate";
    if (url.searchParams.has("authError")) {
      error = "Не удалось войти через внешний сервис. Попробуйте ещё раз.";
      url.searchParams.delete("authError");
      window.history.replaceState(null, "", url);
    }
    if (url.searchParams.has("providerLinkError")) {
      error =
        "Не удалось привязать аккаунт. Возможно, он уже связан с другим профилем. Попробуйте ещё раз.";
      url.searchParams.delete("providerLinkError");
      window.history.replaceState(null, "", url);
    }
    connect();
  });

  async function connect() {
    ready = false;
    initError = "";
    try {
      await initAuth();
    } catch (e) {
      initError = message(e);
      ready = true;
      return;
    }
    authenticated = auth.authenticated;
    if (!authenticated) {
      const authError = error ? "&authError=failed" : "";
      await goto(`/login?role=${desiredRole}${authError}`, {
        replaceState: true,
      });
      return;
    }
    try {
      if (authenticated) {
        identity = auth.email || "Аккаунт VK ID";
        // The role is bound to the account once, in the app; a fresh account chooses it first.
        const me = await accountApi.me();
        needsRole = !me.role;
        if (me.role) {
          role = me.role;
          await refresh();
        } else {
          needsRole = true;
        }
      }
    } catch (e) {
      error = message(e);
    } finally {
      ready = true;
    }
  }

  async function refresh() {
    try {
      if (role === "candidate") {
        const [p, i] = await Promise.all([candidateApi.profile(), invitationApi.invitations()]);
        profile = {
          ...profile,
          ...p,
          email: p.email || "",
        };
        invitations = i;
        syncTags();
        invitationsLoaded = true;
      } else {
        const [c, n, i] = await Promise.all([
          companyApi.company(),
          companyApi.need(),
          invitationApi.invitations(),
        ]);
        company = { ...company, ...c };
        need = { ...need, ...n };
        invitations = i;
        invitationsLoaded = true;
      }
    } catch (e) {
      error = message(e);
    }
  }

  async function chooseRole(choice: Role) {
    await action(async () => {
      const me = await accountApi.chooseRole(choice);
      role = me.role ?? choice;
      needsRole = false;
      section = "overview";
      await refresh();
    }, "");
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
    if (section !== id) {
      section = id;
      await tick();
      if (mainElement) mainElement.scrollTop = 0;
      window.scrollTo(0, 0);
    }
    error = "";
    success = "";
    if (id === "invitations")
      await action(async () => {
        invitations = await invitationApi.invitations();
        invitationsLoaded = true;
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
      profile = await candidateApi.saveProfile(profile);
    }, "Профиль сохранён.");
  }
  async function downloadResume() {
    await action(async () => {
      const savedProfile = await candidateApi.profile();
      await downloadResumePdf(savedProfile);
    }, "Резюме готово к скачиванию.");
  }
  async function revokeConsent() {
    await action(async () => {
      profile = await candidateApi.revokeConsent();
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
      resumeDraft = await candidateApi.uploadResume(file);
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
      profile = await candidateApi.confirmResume(resumeDraft!.draftId, profile);
      resumeDraft = null;
    }, "Проверенные данные из резюме сохранены. Публикация профиля не менялась.");
  }
  async function saveCompany() {
    await action(async () => {
      company = await companyApi.saveCompany(company);
    }, "Данные компании сохранены.");
  }
  async function saveNeed() {
    await action(async () => {
      need = await companyApi.saveNeed(need);
    }, "Потребность сохранена. Теперь можно открыть подборку.");
  }
  async function sendInvitation(candidateId: string, offer: Offer): Promise<boolean> {
    let sent = false;
    await action(async () => {
      await invitationApi.sendInvitation(candidateId, offer);
      sent = true;
      await refresh();
    }, "Приглашение отправлено кандидату. Контакты появятся после принятия.");
    return sent;
  }
  async function updateInvitation(invitation: Invitation, status: InvitationStatusAction) {
    await action(
      async () => {
        await invitationApi.updateInvitation(invitation.id, status);
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
  <div class="d-ui-main"><p class="d-ui-muted">Подключаем платформу…</p></div>
{:else if initError}
  <main class="d-ui-main">
    <div role="alert" class="d-ui-error">
      Не удалось подключить авторизацию: {initError}
    </div>
    <button class="d-ui-button" onclick={connect}>
      Повторить подключение <ActionIcon name="refresh" />
    </button>
  </main>
{:else if needsRole}
  <div class="d-ui-shell">
    <aside class="d-ui-sidebar">
      <a href="/" class="d-ui-brand">
        <img src="/fsp-mark.svg" alt="" class="d-ui-emblem" />
        ФСП / КАРЬЕРА
      </a>
    </aside>
    <main class="d-ui-main">
      <div class="d-ui-kicker">ПЕРВЫЙ ВХОД / {identity}</div>
      <h1 class="d-ui-title">Выберите роль</h1>
      {#if error}
        <p role="alert" class="d-ui-error">{error}</p>
      {/if}
      <RoleChoice suggested={desiredRole} {busy} {chooseRole} />
    </main>
  </div>
{:else}
  <div class="d-ui-shell">
    <DashboardNavigation {role} {section} {identity} {openSection} />
    <main bind:this={mainElement} class="d-ui-main">
      <div class="d-ui-top">
        <div>
          <div class="d-ui-kicker">
            ФСП КАРЬЕРА / {role === "candidate" ? "СПЕЦИАЛИСТ" : "РАБОТОДАТЕЛЬ"}
          </div>
          <h1 class="d-ui-title">
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
                        : section === "invitations"
                          ? "Приглашения"
                          : (vacancySectionTitles[section] ?? "")}
          </h1>
          <p class="d-ui-subtitle">
            {role === "candidate"
              ? "Ваши навыки превращаются в новые возможности."
              : "Найдите специалистов по направлению и результату оценки."}
          </p>
        </div>
      </div>
      {#if error}
        <div role="alert" class="d-ui-error">{error}</div>
      {/if}
      {#if success}
        <div role="status" class="d-ui-success">{success}</div>
      {/if}
      {#if section === "overview"}
        <Overview {role} {profile} {company} {need} {invitations} {openSection} />
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
        <CandidateSettings processingConsent={profile.processingConsent} {busy} run={action} />
      {:else if section === "assessment" && role === "candidate"}
        <CandidateAssessment {profile} refreshProfile={refresh} />
      {:else if section === "company" && role === "employer"}
        <EmployerCompany bind:company {busy} {saveCompany} />
      {:else if section === "needs" && role === "employer"}
        <EmployerNeeds bind:need {busy} {saveNeed} />
      {:else if section === "matches" && role === "employer"}
        <EmployerMatches
          {need}
          {company}
          {busy}
          run={action}
          {sendInvitation}
          openNeeds={() => openSection("needs")}
        />
      {:else if section === "invitations"}
        {#if error || !invitationsLoaded}
          <div class="d-ui-panel">
            <p class="d-ui-muted">Не удалось загрузить приглашения.</p>
            <button class="d-ui-button" disabled={busy} onclick={() => openSection("invitations")}>
              Повторить загрузку <ActionIcon name="refresh" />
            </button>
          </div>
        {:else}
          <Invitations {invitations} {role} {busy} {updateInvitation} />
        {/if}
      {:else if section === "vacancies" && role === "candidate"}
        <CandidateVacancies {profile} {busy} run={action} {openSection} />
      {:else if section === "applications" && role === "candidate"}
        <CandidateApplications {busy} run={action} {openSection} />
      {:else if section === "tasks" && role === "candidate"}
        <CandidateTasks {profile} {busy} run={action} {openSection} />
      {:else if section === "vacancies" && role === "employer"}
        <EmployerVacancies {busy} run={action} />
      {:else if section === "tasks" && role === "employer"}
        <EmployerTasks {busy} run={action} />
      {/if}
    </main>
  </div>
{/if}
