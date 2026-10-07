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
export {
  applicationStatusLabels,
  gradeName,
  grades,
  salaryRange,
  shortDate,
  specializationName,
  splitList,
  workFormatName,
  workFormats,
} from "./model/labels";
export { vacancyApi } from "./api/vacancy";
