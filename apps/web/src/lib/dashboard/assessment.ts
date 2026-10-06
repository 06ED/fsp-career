import { api, save } from "$lib/client";
import type {
  AnswerOutcome,
  AssessmentAttempt,
  AssessmentCatalog,
  AssessmentEligibility,
  AssessmentResult,
  BlueprintPreview,
  CatalogEntry,
  CurrentAssessment,
  StartAssessment,
} from "./types";

/** Adaptive assessment API (survey → declared grade → adaptive test). */
export const assessmentApi = {
  catalog: () => api<AssessmentCatalog>("/api/assessments/catalog"),
  blueprint: (request: {
    specialization?: string;
    vacancyId?: string;
    vacancyText?: string;
    targetGrade?: string;
  }) => save<BlueprintPreview>("/api/assessments/blueprint", request, "POST"),
  eligibility: (specialization: string) =>
    api<AssessmentEligibility>(
      `/api/assessments/eligibility?specialization=${encodeURIComponent(specialization)}`,
    ),
  history: async () =>
    (await api<{ results: AssessmentResult[] }>("/api/assessments/history"))
      .results,
  current: () => api<CurrentAssessment>("/api/assessments/attempts/current"),
  start: (request: StartAssessment) =>
    save<AssessmentAttempt>("/api/assessments/attempts", request, "POST"),
  answer: (attemptId: string, position: number, answer: string) =>
    save<AnswerOutcome>(
      `/api/assessments/attempts/${attemptId}/answers`,
      { position, answer },
      "POST",
    ),
};

export const gradeLabels: Record<string, string> = {
  junior: "Junior",
  middle: "Middle",
  senior: "Senior",
};

export function gradeLabel(id?: string | null): string {
  return id ? (gradeLabels[id] ?? id) : "—";
}

export function entryLabel(
  list: CatalogEntry[] | undefined,
  id?: string | null,
) {
  if (!id) return "—";
  return list?.find((entry) => entry.id === id)?.label ?? id;
}

export function formatDate(iso?: string | null): string {
  return iso ? new Date(iso).toLocaleDateString("ru-RU") : "—";
}

export function formatDateTime(iso?: string | null): string {
  return iso
    ? new Date(iso).toLocaleString("ru-RU", {
        day: "numeric",
        month: "long",
        hour: "2-digit",
        minute: "2-digit",
      })
    : "—";
}

export function formatNumber(value?: number | null, digits = 2): string {
  return value === null || value === undefined
    ? "—"
    : value.toFixed(digits).replace(".", ",").replace("-", "−");
}
