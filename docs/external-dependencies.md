# External inputs needed for an official FSP release

The local Compose prototype deliberately does **not** assert that a candidate owns
an FSP ID, achieved an FSP result or has a validated hiring grade. The supplied
brand assets and specification do not include an issuer API contract or an
independent labeled evaluation dataset. Do not replace these missing proofs with
self-reported IDs, client-submitted points or synthetic test fixtures.

## FSP identity and achievements

Provide an authorized sandbox/API agreement with:

- Candidate-to-FSP-account binding protocol: OAuth/OIDC issuer and scopes, signed
  assertion or other issuer-owned proof, required consent, expected subject ID and
  account recovery/rebinding rules. A typed ID entered in a profile is **not** proof.
- Achievement query or signed-event schema, stable event IDs, award type/level,
  timestamps, issuer signature/authentication, revocation/correction semantics,
  paging and rate limits. Specify whether participation, prize and qualification
  may be displayed or used for ranking and approved language for brand claims.
- Sandbox URL, test identities/events, scoped **non-production** credentials
  supplied through a secure secret channel (never committed to this repository),
  webhook verification keys if applicable, and whether synchronization is allowed.
- Data-processing agreement, retention/deletion and provenance/audit requirements.
  A server-owned connector must bind the verified external subject to the
  consented Keycloak identity, support replay-safe imports and remove/recompute
  ranking on revocation and consent withdrawal.

## Assessment and matching validation

To claim a meaningful grade or recruitment fit, obtain independent subject-matter
expert ratings and a consented, de-identified pilot response matrix per specialty
(including question IDs, answer choices, timestamp and form). Pre-register
sample sizes, grade boundaries, pass criteria and intended use before inspecting
outcomes. For ranking, supply independently judged relevance and pairwise
preferences for held-out employer needs, including assessed *and* unretrieved
eligible candidates; freeze k and baselines before evaluation. Report uncertainty,
coverage and subgroup risks. See [validation.md](<validation.md>) for the
measurement protocol. No such samples or scores are contained in this project.

Until these inputs arrive, use [README.md](<../README.md>) to run the **synthetic
local prototype**. Do not deploy with the example credentials, development
Keycloak mode, Mailpit or HTTP for real applicant data.
