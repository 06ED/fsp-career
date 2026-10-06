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

// --- Adaptive assessment (survey → declared grade → adaptive test) ---
export type GradeId = "junior" | "middle" | "senior";
export type CatalogEntry = { id: string; label: string; description: string };
export type AssessmentGrade = CatalogEntry & {
  thetaCut: number;
  scoreCut: number;
};
export type AssessmentVacancy = {
  id: string;
  title: string;
  company: string;
  specialization: string;
  industry: string;
  grade: GradeId;
  text: string;
};
export type AssessmentCatalog = {
  specializations: CatalogEntry[];
  industries: CatalogEntry[];
  teamRoles: CatalogEntry[];
  workFormats: CatalogEntry[];
  grades: AssessmentGrade[];
  topics: CatalogEntry[];
  vacancies: AssessmentVacancy[];
  testLength: number;
  timeLimitMinutes: number;
  cooldownDays: number;
  decisionMargin: number;
  familyCount: number;
};
export type AssessmentSurvey = {
  industry: string;
  specialization: string;
  experienceYears: number;
  teamRoles: string[];
  workFormat: string;
};
export type AssessmentItem = {
  topic: string;
  topicLabel: string;
  prompt: string;
  code?: string | null;
  language?: string | null;
  format: "choice" | "number";
  options: { id: string; label: string }[];
  unit?: string | null;
};
export type TopicQuota = {
  topic: string;
  label: string;
  weight: number;
  items: number;
};
export type AssessmentAttempt = {
  attemptId: string;
  specialization: string;
  industry: string;
  targetGrade: GradeId;
  vacancyId?: string | null;
  vacancyTitle?: string | null;
  startedAt: string;
  expiresAt: string;
  position: number;
  total: number;
  item?: AssessmentItem | null;
  blueprint: TopicQuota[];
};
export type GradeOption = {
  grade: GradeId;
  allowed: boolean;
  reason: string;
  message: string;
  availableAt?: string | null;
};
export type AssessmentResult = {
  attemptId: string;
  specialization: string;
  industry: string;
  targetGrade?: GradeId | null;
  passed?: boolean | null;
  confident?: boolean | null;
  theta?: number | null;
  thetaSe?: number | null;
  score: number;
  grade: GradeId;
  previousGrade?: GradeId | null;
  answered: number;
  correct: number;
  total: number;
  expired: boolean;
  startedAt: string;
  submittedAt: string;
  survey?: AssessmentSurvey | null;
  vacancyId?: string | null;
  vacancyTitle?: string | null;
  method: string;
  summary: string;
  nextOptions: GradeOption[];
};
export type AssessmentEligibility = {
  specialization: string;
  confirmedGrade?: GradeId | null;
  score?: number | null;
  lastAttemptAt?: string | null;
  options: GradeOption[];
  openAttemptId?: string | null;
  cooldownDays: number;
};
export type AnswerOutcome = {
  done: boolean;
  attempt?: AssessmentAttempt | null;
  result?: AssessmentResult | null;
};
export type CurrentAssessment = {
  attempt?: AssessmentAttempt | null;
  closed?: AssessmentResult | null;
};
export type DetectedSkill = {
  id: string;
  label: string;
  topics: string[];
  hits: number;
  matched: string[];
};
export type BlueprintPreview = {
  specialization: string;
  detectedSpecialization?: string | null;
  vacancyId?: string | null;
  vacancyTitle?: string | null;
  targetGrade: GradeId;
  skills: DetectedSkill[];
  uncoveredSkills: DetectedSkill[];
  topics: TopicQuota[];
  sampleItems: AssessmentItem[];
  poolSize: number;
  testLength: number;
};
export type StartAssessment = {
  survey: AssessmentSurvey;
  targetGrade: GradeId;
  vacancyId?: string;
  vacancyText?: string;
};
