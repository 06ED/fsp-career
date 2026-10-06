export type Role = "candidate" | "employer";
export type Section =
  | "overview"
  | "profile"
  | "assessment"
  | "company"
  | "needs"
  | "matches"
  | "invitations"
  | "vacancies"
  | "applications"
  | "tasks";

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

// ---- Vacancies, applications and regular short tasks (VacancyRoutes.kt / TaskRoutes.kt) ----
export type WorkFormat = "office" | "remote" | "hybrid";
export type ApplicationStatus = "sent" | "viewed" | "accepted" | "rejected";
export type VacancyInput = {
  title: string;
  description: string;
  specialization: string;
  grade: string;
  techStack: string[];
  salaryMinRub: number;
  salaryMaxRub: number;
  workFormat: WorkFormat;
  city: string;
};
export type ApplicationCounts = {
  total: number;
  sent: number;
  viewed: number;
  accepted: number;
  rejected: number;
};
export type EmployerVacancy = VacancyInput & {
  id: string;
  status: "open" | "closed";
  createdAt: string;
  updatedAt: string;
  applications: ApplicationCounts;
};
export type VacancyCard = VacancyInput & {
  id: string;
  companyName: string;
  companyDescription: string;
  companyWebsite: string;
  createdAt: string;
  categoryMatch: boolean;
  stackOverlap: string[];
  fit: string;
  myApplicationId?: string | null;
  myApplicationStatus?: ApplicationStatus | null;
};
export type VacancyFilters = {
  specialization: string;
  grade: string;
  techStack: string;
  salaryFrom: string | number | null;
  workFormat: string;
  q: string;
};
export type CandidateApplication = {
  id: string;
  vacancyId: string;
  vacancyTitle: string;
  vacancyStatus: "open" | "closed";
  companyName: string;
  specialization: string;
  grade: string;
  salaryMinRub: number;
  salaryMaxRub: number;
  workFormat: WorkFormat;
  city: string;
  coverNote: string;
  status: ApplicationStatus;
  createdAt: string;
  updatedAt: string;
};
export type Applicant = {
  id: string;
  vacancyId: string;
  candidateId: string;
  fullName: string;
  specialization: string;
  assessedGrade?: string | null;
  score?: number | null;
  techStack: string[];
  experience: string;
  coverNote: string;
  status: ApplicationStatus;
  createdAt: string;
  updatedAt: string;
  contactEmail: string;
  contactPhone: string;
  categoryMatch: boolean;
  stackOverlap: string[];
  fit: string;
  taskSubmissions: number;
  taskAverageRating?: number | null;
  lastTaskActivityAt?: string | null;
};
export type ShortTaskInput = {
  title: string;
  body: string;
  specialization: string;
  grade: string | null;
};
export type EmployerShortTask = ShortTaskInput & {
  id: string;
  active: boolean;
  createdAt: string;
  submissions: number;
  unrated: number;
};
export type SubmissionKind = "solution" | "approach";
export type CandidateTaskSubmission = {
  id: string;
  taskId: string;
  taskTitle: string;
  companyName: string;
  kind: SubmissionKind;
  answer: string;
  submittedAt: string;
  rating?: number | null;
  ratingComment: string;
  ratedAt?: string | null;
};
export type OfferedTask = {
  id: string;
  title: string;
  body: string;
  specialization: string;
  grade?: string | null;
  companyName: string;
};
export type CurrentShortTask = {
  periodStart: string;
  nextRotationAt: string;
  state: "offered" | "answered" | "none";
  task?: OfferedTask | null;
  submission?: CandidateTaskSubmission | null;
  lastActivityAt?: string | null;
};
export type EmployerTaskSubmission = {
  id: string;
  taskId: string;
  candidateId: string;
  candidateName: string;
  specialization: string;
  assessedGrade?: string | null;
  score?: number | null;
  kind: SubmissionKind;
  answer: string;
  submittedAt: string;
  rating?: number | null;
  ratingComment: string;
  ratedAt?: string | null;
};
