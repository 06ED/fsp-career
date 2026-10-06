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
export type Company = {
  name: string;
  description: string;
  website: string;
  industry: string;
  contactEmail: string;
  contactPhone: string;
  contactTelegram: string;
  contactNote: string;
};
export type Need = {
  specialization: string;
  industry: string;
  minGrade: string;
  description: string;
  techStack: string[];
};
export type Match = {
  candidateId: string;
  fullName: string;
  specialization: string;
  grade: string;
  category: string;
  score: number;
  achievementScore: number;
  totalScore: number;
  relevance: number;
  strength: number;
  explanation: string;
  factors: MatchFactor[];
  flags: string[];
  achievements: VerifiedAchievement[];
  techStack: string[];
  industry: string;
  fspLinked: boolean;
  lastActiveAt?: string | null;
  assessedAt?: string | null;
  invitationStatus?: string | null;
};
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
export type RunAction = (
  job: () => Promise<void>,
  confirmation: string,
) => Promise<void>;

// --- Matching, candidate bank, privacy and FSP ID link ---
export type MatchFactor = {
  code: string;
  label: string;
  weight: number;
  value: number;
  points: number;
  text: string;
};
export type VerifiedAchievement = {
  title: string;
  issuer: string;
  points: number;
  source: string;
  eventName?: string | null;
  discipline?: string | null;
  eventDate?: string | null;
  level?: string | null;
  levelLabel?: string | null;
  place?: number | null;
  result?: string | null;
  resultLabel?: string | null;
};
export type MatchCategory = {
  key: string;
  specialization: string;
  grade: string;
  label: string;
  candidates: number;
  withFsp: number;
  avgTestScore: number;
  avgStrength: number;
  fit: "exact" | "higher" | "lower" | "adjacent" | "browse";
  recommended: boolean;
  reason: string;
  avgTextRelevance?: number | null;
};
export type MatchResponse = {
  mode: "need" | "bank";
  need?: {
    specialization: string;
    grade: string;
    industry: string;
    techStack: string[];
    description: string;
    query: string;
  } | null;
  total: number;
  categories: MatchCategory[];
  matches: Match[];
  formula: string[];
};
export type MatchQuery = {
  specialization?: string;
  grade?: string;
  category?: string;
  techStack?: string[];
  verifiedAchievements?: boolean;
  industry?: string;
  q?: string;
  allowLowerGrade?: boolean;
  limit?: number;
};
export type CandidateCard = {
  candidateId: string;
  fullName: string;
  specialization: string;
  grade?: string | null;
  score?: number | null;
  assessedAt?: string | null;
  category?: string | null;
  industry: string;
  techStack?: string[] | null;
  experience?: string | null;
  targetRoles?: string[] | null;
  cvHistory?: CvEntry[] | null;
  softSkills?: string[] | null;
  achievements?: VerifiedAchievement[] | null;
  fspLinked?: boolean | null;
  hiddenSections: string[];
  lastActiveAt?: string | null;
  contactsDisclosed: boolean;
  contactEmail?: string | null;
  contactPhone?: string | null;
  invitations: {
    id: string;
    status: string;
    createdAt: string;
    salaryMinRub: number;
    salaryMaxRub: number;
  }[];
  match?: Match | null;
};
export type CandidatePrivacy = {
  fullName: boolean;
  techStack: boolean;
  experience: boolean;
  cvHistory: boolean;
  softSkills: boolean;
  fspAchievements: boolean;
};
export type FspStatus = {
  linked: boolean;
  fspId?: string | null;
  linkedAt?: string | null;
  registry: string;
  achievements: VerifiedAchievement[];
  totalPoints: number;
  message: string;
};
