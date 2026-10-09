import { api, save } from "$lib/shared/api";
import type {
  CandidateTaskSubmission,
  CurrentShortTask,
  EmployerShortTask,
  EmployerTaskSubmission,
  ShortTaskInput,
  SubmissionKind,
} from "..";

const errors: Record<string, string> = {
  "Task is already": "Статус задания уже изменён — обновите список.",
  "Grant processing consent before short tasks":
    "Дайте согласие на обработку данных в профиле, чтобы получать задания.",
  "Set specialization before short tasks":
    "Укажите специализацию в профиле, чтобы получать задания.",
  "This week's task is already answered": "Задание этой недели уже выполнено.",
  "This task is not your current weekly task":
    "Задание недели сменилось — обновите раздел.",
  "rating must be between": "Оценка — от 1 до 5.",
  "Task not found": "Задание не найдено.",
  "Submission not found": "Ответ не найден.",
};

async function ru<T>(request: Promise<T>): Promise<T> {
  try {
    return await request;
  } catch (e) {
    const text = e instanceof Error ? e.message : String(e);
    throw new Error(
      Object.entries(errors).find(([prefix]) => text.startsWith(prefix))?.[1] ??
        text,
      { cause: e },
    );
  }
}

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
