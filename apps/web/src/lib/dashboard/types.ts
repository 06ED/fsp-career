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
// Account role chosen once after the first login (null until then).
export type Me = { role: Role | null; email: string; emailVerified: boolean };

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
