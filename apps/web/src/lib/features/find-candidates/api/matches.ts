import { api } from "$lib/shared/api";
import type { CandidateCard, MatchQuery, MatchResponse } from "../model/types";
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
export const matchApi = {
  matches: (query: MatchQuery = {}) =>
    api<MatchResponse>("/api/matches" + matchQueryString(query)),
  bank: (query: MatchQuery = {}) =>
    api<MatchResponse>("/api/bank/candidates" + matchQueryString(query)),
  candidateCard: (candidateId: string) =>
    api<CandidateCard>(
      `/api/bank/candidates/${encodeURIComponent(candidateId)}`,
    ),
};
