export type Role = "candidate" | "employer";
export type Section =
  | "overview"
  | "profile"
  | "assessment"
  | "company"
  | "needs"
  | "matches"
  | "invitations";

export type CvEntry = {
  company: string;
  role: string;
  startDate: string;
  endDate: string;
  description: string;
};
export type Profile = {
  fullName: string;
  email: string;
  phone: string;
  processingConsent: boolean;
  consentPublication: boolean;
  specialization: string;
  industry: string;
  desiredGrade: string;
  techStack: string[];
  experience: string;
  softSkills: string[];
  targetRoles: string[];
  cvHistory: CvEntry[];
  grade?: string;
  score?: number;
  category?: string;
};
export type ResumeDraft = {
  draftId: string;
  parsedFields: Partial<Profile>;
  reviewed: boolean;
};
export type Question = {
  id: number;
  prompt: string;
  options: { id: string; label: string }[];
};
export type Assessment = {
  attemptId: string;
  questions: Question[];
  expiresAt: string;
};
export type Result = {
  grade: string;
  score: number;
  specialization: string;
  submittedAt: string;
};
export type Company = { name: string; description: string; website: string };
export type Need = {
  specialization: string;
  industry: string;
  minGrade: string;
  description: string;
};
export type Match = {
  candidateId: string;
  fullName: string;
  specialization: string;
  grade: string;
  score: number;
  achievementScore: number;
  totalScore: number;
  explanation: string;
  achievements: { title: string; issuer: string; points: number }[];
  techStack: string[];
};
export type Offer = {
  description: string;
  salaryMinRub: number;
  salaryMaxRub: number;
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
  email?: string;
  phone?: string;
  createdAt?: string;
};
export type InvitationStatusAction = "view" | "accept" | "reject";
export type RunAction = (
  job: () => Promise<void>,
  confirmation: string,
) => Promise<void>;
