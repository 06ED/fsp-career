import { createContext } from "svelte";
import { candidateApi } from "$lib/entities/candidate";
import { companyApi } from "$lib/entities/company";
import { invitationApi } from "$lib/entities/invitation";
import { splitList } from "$lib/entities/vacancy";
import { downloadResumePdf } from "$lib/features/edit-candidate-profile";
import type { Me } from "$lib/entities/account";
import type { Profile, ResumeDraft } from "$lib/entities/candidate";
import type { Company, Need } from "$lib/entities/company";
import type {
  Invitation,
  InvitationStatusAction,
  Offer,
} from "$lib/entities/invitation";

export const [getDashboard, setDashboard] =
  createContext<ReturnType<typeof createDashboard>>();

export function createDashboard(account: Me) {
  const state = $state({
    role: account.role,
    identity: account.email || "Аккаунт VK ID",
    error: "",
    success: "",
    busy: false,
    profile: {
      fullName: "",
      email: account.email || "",
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
    } as Profile,
    company: {
      name: "",
      description: "",
      website: "",
      industry: "",
      contactEmail: "",
      contactPhone: "",
      contactTelegram: "",
      contactNote: "",
    } as Company,
    need: {
      specialization: "",
      industry: "",
      minGrade: "junior",
      description: "",
      techStack: [],
    } as Need,
    invitations: [] as Invitation[],
    invitationsLoaded: false,
    resumeDraft: null as ResumeDraft | null,
    techStackText: "",
    softSkillsText: "",
    targetRolesText: "",
  });

  function syncTags() {
    state.techStackText = state.profile.techStack.join(", ");
    state.softSkillsText = state.profile.softSkills.join(", ");
    state.targetRolesText = state.profile.targetRoles.join(", ");
  }

  function applyTags() {
    state.profile.techStack = splitList(state.techStackText);
    state.profile.softSkills = splitList(state.softSkillsText);
    state.profile.targetRoles = splitList(state.targetRolesText);
  }

  async function refresh() {
    if (state.role === "candidate") {
      const [profile, invitations] = await Promise.all([
        candidateApi.profile(),
        invitationApi.invitations(),
      ]);
      state.profile = {
        ...state.profile,
        ...profile,
        email: profile.email || account.email || "",
      };
      state.invitations = invitations;
      syncTags();
    } else if (state.role === "employer") {
      const [company, need, invitations] = await Promise.all([
        companyApi.company(),
        companyApi.need(),
        invitationApi.invitations(),
      ]);
      state.company = { ...state.company, ...company };
      state.need = { ...state.need, ...need };
      state.invitations = invitations;
    }
    state.invitationsLoaded = true;
  }

  async function action(job: () => Promise<void>, confirmation: string) {
    state.error = "";
    state.success = "";
    state.busy = true;
    try {
      await job();
      state.success = confirmation;
    } catch (error) {
      state.error = error instanceof Error ? error.message : "Произошла ошибка";
    } finally {
      state.busy = false;
    }
  }

  async function loadInvitations() {
    state.invitationsLoaded = false;
    await action(async () => {
      state.invitations = await invitationApi.invitations();
      state.invitationsLoaded = true;
    }, "");
  }

  async function saveProfile() {
    await action(async () => {
      applyTags();
      state.profile = await candidateApi.saveProfile(state.profile);
    }, "Профиль сохранён.");
  }

  async function downloadResume() {
    await action(async () => {
      await downloadResumePdf(await candidateApi.profile());
    }, "Резюме готово к скачиванию.");
  }

  async function revokeConsent() {
    await action(async () => {
      state.profile = await candidateApi.revokeConsent();
      state.invitations = [];
      state.resumeDraft = null;
      syncTags();
    }, "Данные удалены. Для продолжения потребуется новое согласие.");
  }

  async function uploadResume(event: Event) {
    const file = (event.currentTarget as HTMLInputElement).files?.[0];
    if (!file) return;
    if (!state.profile.processingConsent) {
      state.error =
        "Сначала сохраните профиль с согласием на обработку данных.";
      return;
    }
    if (file.type !== "application/pdf" || file.size > 5 * 1024 * 1024) {
      state.error = "Выберите PDF-файл размером не более 5 МБ.";
      return;
    }
    await action(async () => {
      state.resumeDraft = await candidateApi.uploadResume(file);
      state.profile = {
        ...state.profile,
        ...state.resumeDraft.parsedFields,
        email: state.profile.email,
        processingConsent: state.profile.processingConsent,
        consentPublication: state.profile.consentPublication,
      };
      syncTags();
    }, "Черновик распознан. Проверьте каждое поле и подтвердите перенос данных.");
  }

  async function confirmResume() {
    const draft = state.resumeDraft;
    if (!draft) return;
    await action(async () => {
      applyTags();
      state.profile = await candidateApi.confirmResume(
        draft.draftId,
        state.profile,
      );
      state.resumeDraft = null;
    }, "Проверенные данные из резюме сохранены. Публикация профиля не менялась.");
  }

  async function saveCompany() {
    await action(async () => {
      state.company = await companyApi.saveCompany(state.company);
    }, "Данные компании сохранены.");
  }

  async function saveNeed() {
    await action(async () => {
      state.need = await companyApi.saveNeed(state.need);
    }, "Потребность сохранена. Теперь можно открыть подборку.");
  }

  async function sendInvitation(
    candidateId: string,
    offer: Offer,
  ): Promise<boolean> {
    let sent = false;
    await action(async () => {
      await invitationApi.sendInvitation(candidateId, offer);
      sent = true;
      await refresh();
    }, "Приглашение отправлено кандидату. Контакты появятся после принятия.");
    return sent;
  }

  async function updateInvitation(
    invitation: Invitation,
    status: InvitationStatusAction,
  ) {
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

  return Object.assign(state, {
    refresh,
    action,
    loadInvitations,
    saveProfile,
    downloadResume,
    revokeConsent,
    uploadResume,
    confirmResume,
    saveCompany,
    saveNeed,
    sendInvitation,
    updateInvitation,
  });
}
