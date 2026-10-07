export type Offer = {
  description: string;
  salaryMinRub: number;
  salaryMaxRub: number;
  contactMethod: string;
};
export type Invitation = {
  id: string;
  candidateId: string;
  candidateName?: string;
  companyName?: string;
  description: string;
  salaryMinRub: number;
  salaryMaxRub: number;
  status: string;
  contactEmail?: string;
  contactPhone?: string;
  contactMethod?: string;
  email?: string;
  phone?: string;
  createdAt?: string;
};
export type InvitationStatusAction = "view" | "accept" | "reject";
export { invitationStatusLabels } from "./model/labels";
export { invitationApi } from "./api/invitation";
