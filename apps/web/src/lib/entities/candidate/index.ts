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
export type CandidatePrivacy = {
  fullName: boolean;
  techStack: boolean;
  experience: boolean;
  cvHistory: boolean;
  softSkills: boolean;
  fspAchievements: boolean;
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
export { industries, industryLabel } from "./model/industries";
export { specializations } from "./model/specializations";
export { candidateApi } from "./api/candidate";
export { achievementDetails, achievementLabel } from "./model/achievements";
