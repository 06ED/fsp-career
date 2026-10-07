// Page-level action runner shared by feature callbacks; not a business DTO.
export type RunAction = (
  job: () => Promise<void>,
  confirmation: string,
) => Promise<void>;
