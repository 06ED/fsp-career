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
export {
  companyContactLine,
  stackSuggestions,
  splitTags,
} from "./model/helpers";
export { companyApi } from "./api/company";
