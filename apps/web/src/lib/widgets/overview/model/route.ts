import type { Profile } from "$lib/entities/candidate";
import type { Invitation } from "$lib/entities/invitation";

export function routeProgress(profile: Profile, invitations: Invitation[]) {
  const completed = [
    Boolean(
      profile.fullName.trim() &&
      profile.processingConsent &&
      profile.consentPublication,
    ),
    Boolean(profile.specialization.trim() && profile.desiredGrade),
    Boolean(profile.grade),
    invitations.some((invitation) =>
      ["viewed", "accepted", "rejected"].includes(invitation.status),
    ),
  ];
  const current = completed.indexOf(false);
  return completed.map((done, index) =>
    done ? "done" : index === current ? "current" : "upcoming",
  );
}
