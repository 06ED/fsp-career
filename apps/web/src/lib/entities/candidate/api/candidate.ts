import { api, save } from "$lib/shared/api";
import type { CandidatePrivacy, Profile, ResumeDraft } from "..";
export const candidateApi = {
  profile: () => api<Profile>("/api/candidates/me"),
  saveProfile: (profile: Profile) =>
    save<Profile>("/api/candidates/me", profile),
  revokeConsent: () =>
    api<Profile>("/api/candidates/me/consent", { method: "DELETE" }),
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
  privacy: () => api<CandidatePrivacy>("/api/candidates/me/privacy"),
  savePrivacy: (privacy: CandidatePrivacy) =>
    save<CandidatePrivacy>("/api/candidates/me/privacy", privacy),
};
