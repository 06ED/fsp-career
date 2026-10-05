import { api, apiBlob, save } from "$lib/client";
import type {
  Assessment,
  Company,
  Invitation,
  InvitationStatusAction,
  Match,
  Need,
  Offer,
  Profile,
  Result,
  ResumeDraft,
} from "./types";

export const dashboardApi = {
  profile: () => api<Profile>("/api/candidates/me"),
  saveProfile: (profile: Profile) =>
    save<Profile>("/api/candidates/me", profile),
  revokeConsent: () =>
    api<Profile>("/api/candidates/me/consent", { method: "DELETE" }),
  exportResume: () => apiBlob("/api/candidates/me/resume.pdf"),
  uploadResume: (file: File) => {
    const body = new FormData();
    body.append("file", file);
    return api<ResumeDraft>("/api/candidates/me/resume-upload", {
      method: "POST",
      body,
    });
  },
  confirmResume: (draftId: string, profile: Profile) =>
    save<Profile>(
      "/api/candidates/me/resume-confirm",
      {
        draftId,
        fullName: profile.fullName,
        phone: profile.phone,
        experience: profile.experience,
        techStack: profile.techStack,
        softSkills: profile.softSkills,
        targetRoles: profile.targetRoles,
        cvHistory: profile.cvHistory,
      },
      "POST",
    ),
  questions: () => api<Assessment>("/api/assessments/questions"),
  assessmentHistory: async () =>
    (await api<{ results: Result[] }>("/api/assessments/history")).results,
  submitAssessment: (attemptId: string, answers: Record<string, string>) =>
    save<Result>(
      "/api/assessments",
      {
        attemptId,
        answers: Object.entries(answers).map(([questionId, optionId]) => ({
          questionId: Number(questionId),
          optionId,
        })),
      },
      "POST",
    ),
  company: () => api<Company>("/api/employers/company"),
  saveCompany: (company: Company) =>
    save<Company>("/api/employers/company", company),
  need: () => api<Need>("/api/employers/needs"),
  saveNeed: (need: Need) => save<Need>("/api/employers/needs", need),
  matches: async () =>
    (await api<{ matches: Match[] }>("/api/matches")).matches,
  invitations: async () => {
    const response = await api<Invitation[] | { invitations: Invitation[] }>(
      "/api/invitations",
    );
    return Array.isArray(response) ? response : response.invitations;
  },
  sendInvitation: (candidateId: string, offer: Offer) =>
    save<unknown>("/api/invitations", { candidateId, ...offer }, "POST"),
  updateInvitation: (id: string, status: InvitationStatusAction) =>
    api<unknown>(`/api/invitations/${id}/${status}`, { method: "POST" }),
};
