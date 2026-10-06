import { api, apiBlob, save } from "$lib/client";
import type {
  CandidateCard,
  CandidatePrivacy,
  Company,
  FspStatus,
  Invitation,
  InvitationStatusAction,
  Match,
  MatchQuery,
  MatchResponse,
  Me,
  Need,
  Offer,
  Profile,
  ResumeDraft,
  Role,
} from "./types";
import type {
  Applicant,
  ApplicationStatus,
  CandidateApplication,
  CandidateTaskSubmission,
  CurrentShortTask,
  EmployerShortTask,
  EmployerTaskSubmission,
  EmployerVacancy,
  ShortTaskInput,
  SubmissionKind,
  VacancyCard,
  VacancyFilters,
  VacancyInput,
} from "./types";

export const dashboardApi = {
  me: () => api<Me>("/api/me"),
  chooseRole: (role: Role) => save<Me>("/api/me/role", { role }, "POST"),
  profile: () => api<Profile>("/api/candidates/me"),
  saveProfile: (profile: Profile) =>
    save<Profile>("/api/candidates/me", profile),
  revokeConsent: () =>
    api<Profile>("/api/candidates/me/consent", { method: "DELETE" }),
  exportResume: () => apiBlob("/api/candidates/me/resume.pdf"),
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
  company: () => api<Company>("/api/employers/company"),
  saveCompany: (company: Company) =>
    save<Company>("/api/employers/company", company),
  need: () => api<Need>("/api/employers/needs"),
  saveNeed: (need: Need) => save<Need>("/api/employers/needs", need),
  matches: (query: MatchQuery = {}) =>
    api<MatchResponse>("/api/matches" + matchQueryString(query)),
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
  // --- Matching, candidate bank, privacy and FSP ID link ---
  bank: (query: MatchQuery = {}) =>
    api<MatchResponse>("/api/bank/candidates" + matchQueryString(query)),
  candidateCard: (candidateId: string) =>
    api<CandidateCard>(
      `/api/bank/candidates/${encodeURIComponent(candidateId)}`,
    ),
  privacy: () => api<CandidatePrivacy>("/api/candidates/me/privacy"),
  savePrivacy: (privacy: CandidatePrivacy) =>
    save<CandidatePrivacy>("/api/candidates/me/privacy", privacy),
  fsp: () => api<FspStatus>("/api/candidates/me/fsp"),
  linkFsp: (fspId: string) =>
    save<FspStatus>("/api/candidates/me/fsp", { fspId }, "POST"),
  unlinkFsp: () =>
    api<FspStatus>("/api/candidates/me/fsp", { method: "DELETE" }),
};

// ---- Vacancies, applications and regular short tasks ----
// API errors are English like the rest of the API; the known ones are shown in Russian.
const vacancyErrors: [string, string][] = [
  [
    "Grant processing consent before applying",
    "Дайте согласие на обработку данных в профиле, чтобы откликаться.",
  ],
  [
    "Fill in full name and specialization",
    "Заполните имя и специализацию в профиле.",
  ],
  [
    "Add a phone number or verify email",
    "Укажите телефон или подтвердите email, чтобы компания могла с вами связаться.",
  ],
  [
    "Complete the skills assessment",
    "Сначала пройдите оценку навыков: компания видит подтверждённую категорию.",
  ],
  ["Vacancy is closed", "Вакансия закрыта."],
  ["You have already applied", "Вы уже откликнулись на эту вакансию."],
  [
    "Only sent or viewed applications",
    "Отозвать можно только отклик, по которому ещё нет решения.",
  ],
  [
    "Invalid application transition",
    "Статус этого отклика уже нельзя изменить.",
  ],
  [
    "Set company name first",
    "Сначала укажите название компании в разделе «Компания».",
  ],
  ["City is required", "Укажите город для офисного или гибридного формата."],
  [
    "Salary range in RUB",
    "Зарплатная вилка обязательна: «от» больше нуля и не больше «до».",
  ],
  [
    "Salary must not exceed",
    "Проверьте зарплату: не более 10 000 000 ₽ в месяц.",
  ],
  ["Vacancy is already", "Статус вакансии уже изменён — обновите список."],
  ["Task is already", "Статус задания уже изменён — обновите список."],
  [
    "Grant processing consent before short tasks",
    "Дайте согласие на обработку данных в профиле, чтобы получать задания.",
  ],
  [
    "Set specialization before short tasks",
    "Укажите специализацию в профиле, чтобы получать задания.",
  ],
  [
    "This week's task is already answered",
    "Задание этой недели уже выполнено.",
  ],
  [
    "This task is not your current weekly task",
    "Задание недели сменилось — обновите раздел.",
  ],
  ["rating must be between", "Оценка — от 1 до 5."],
  ["Vacancy not found", "Вакансия не найдена."],
  ["Application not found", "Отклик не найден."],
  ["Task not found", "Задание не найдено."],
  ["Submission not found", "Ответ не найден."],
];
async function ru<T>(request: Promise<T>): Promise<T> {
  try {
    return await request;
  } catch (e) {
    const text = e instanceof Error ? e.message : String(e);
    const known = vacancyErrors.find(([prefix]) => text.startsWith(prefix));
    throw new Error(known ? known[1] : text);
  }
}
function vacancyQuery(filters: VacancyFilters): string {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(filters))
    if (String(value ?? "").trim()) params.set(key, String(value).trim());
  const query = params.toString();
  return query ? `?${query}` : "";
}

export const vacancyApi = {
  vacancies: async (filters: VacancyFilters) =>
    (
      await ru(
        api<{ vacancies: VacancyCard[] }>(
          `/api/vacancies${vacancyQuery(filters)}`,
        ),
      )
    ).vacancies,
  apply: (vacancyId: string, coverNote: string) =>
    ru(
      save<CandidateApplication>(
        `/api/vacancies/${vacancyId}/applications`,
        { coverNote },
        "POST",
      ),
    ),
  myApplications: async () =>
    (
      await ru(
        api<{ applications: CandidateApplication[] }>(
          "/api/candidates/me/applications",
        ),
      )
    ).applications,
  withdraw: (applicationId: string) =>
    ru(
      api<void>(`/api/candidates/me/applications/${applicationId}`, {
        method: "DELETE",
      }),
    ),
  employerVacancies: async () =>
    (
      await ru(
        api<{ vacancies: EmployerVacancy[] }>("/api/employers/vacancies"),
      )
    ).vacancies,
  createVacancy: (vacancy: VacancyInput) =>
    ru(save<EmployerVacancy>("/api/employers/vacancies", vacancy, "POST")),
  updateVacancy: (id: string, vacancy: VacancyInput) =>
    ru(save<EmployerVacancy>(`/api/employers/vacancies/${id}`, vacancy)),
  setVacancyOpen: (id: string, open: boolean) =>
    ru(
      api<EmployerVacancy>(
        `/api/employers/vacancies/${id}/${open ? "reopen" : "close"}`,
        { method: "POST" },
      ),
    ),
  applicants: (vacancyId: string) =>
    ru(
      api<{ vacancy: EmployerVacancy; applications: Applicant[] }>(
        `/api/employers/vacancies/${vacancyId}/applications`,
      ),
    ),
  setApplicationStatus: (
    applicationId: string,
    status: Exclude<ApplicationStatus, "sent">,
  ) =>
    ru(
      save<Applicant>(`/api/employers/applications/${applicationId}/status`, {
        status,
      }),
    ),
};

export const taskApi = {
  current: () => ru(api<CurrentShortTask>("/api/candidates/me/tasks/current")),
  submit: (taskId: string, kind: SubmissionKind, answer: string) =>
    ru(
      save<CandidateTaskSubmission>(
        "/api/candidates/me/task-submissions",
        { taskId, kind, answer },
        "POST",
      ),
    ),
  mySubmissions: async () =>
    (
      await ru(
        api<{ submissions: CandidateTaskSubmission[] }>(
          "/api/candidates/me/task-submissions",
        ),
      )
    ).submissions,
  employerTasks: async () =>
    (await ru(api<{ tasks: EmployerShortTask[] }>("/api/employers/tasks")))
      .tasks,
  createTask: (task: ShortTaskInput) =>
    ru(save<EmployerShortTask>("/api/employers/tasks", task, "POST")),
  setTaskActive: (id: string, active: boolean) =>
    ru(
      api<EmployerShortTask>(
        `/api/employers/tasks/${id}/${active ? "activate" : "deactivate"}`,
        { method: "POST" },
      ),
    ),
  submissions: (taskId: string) =>
    ru(
      api<{ task: EmployerShortTask; submissions: EmployerTaskSubmission[] }>(
        `/api/employers/tasks/${taskId}/submissions`,
      ),
    ),
  rate: (submissionId: string, rating: number, comment: string) =>
    ru(
      save<EmployerTaskSubmission>(
        `/api/employers/task-submissions/${submissionId}/rating`,
        { rating, comment },
      ),
    ),
};
function matchQueryString(query: MatchQuery): string {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (
      value === undefined ||
      value === null ||
      value === "" ||
      value === false
    )
      continue;
    if (Array.isArray(value)) {
      if (value.length) params.set(key, value.join(","));
    } else params.set(key, String(value));
  }
  const text = params.toString();
  return text ? `?${text}` : "";
}
