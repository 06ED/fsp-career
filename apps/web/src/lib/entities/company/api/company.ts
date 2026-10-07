import { api, save } from "$lib/shared/api";
import type { Company, Need } from "..";
export const companyApi = {
  company: () => api<Company>("/api/employers/company"),
  saveCompany: (company: Company) =>
    save<Company>("/api/employers/company", company),
  need: () => api<Need>("/api/employers/needs"),
  saveNeed: (need: Need) => save<Need>("/api/employers/needs", need),
};
