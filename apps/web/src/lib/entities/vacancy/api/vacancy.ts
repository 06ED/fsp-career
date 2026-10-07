import { api, save } from "$lib/shared/api";
import type {
  Applicant,
  ApplicationStatus,
  CandidateApplication,
  EmployerVacancy,
  VacancyCard,
  VacancyFilters,
  VacancyInput,
} from "..";

// ---- Vacancies and applications ----
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
  ["Vacancy not found", "Вакансия не найдена."],
  ["Application not found", "Отклик не найден."],
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
