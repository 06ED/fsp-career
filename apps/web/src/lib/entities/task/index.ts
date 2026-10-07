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
export { submissionKindLabels } from "./model/labels";
export { taskApi } from "./api/task";
