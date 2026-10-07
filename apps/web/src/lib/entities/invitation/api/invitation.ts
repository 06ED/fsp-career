import { api, save } from "$lib/shared/api";
import type { Invitation, InvitationStatusAction, Offer } from "..";
export const invitationApi = {
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
