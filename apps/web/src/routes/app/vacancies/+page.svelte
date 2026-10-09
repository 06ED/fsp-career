<script lang="ts">
  import { CandidateVacancies } from "$lib/features/browse-vacancies";
  import { EmployerVacancies } from "$lib/features/manage-vacancies";
  import { getDashboard } from "$lib/pages/dashboard";

  const dashboard = getDashboard();
</script>

{#if dashboard.role === "candidate"}
  <CandidateVacancies profile={dashboard.profile} busy={dashboard.busy} run={dashboard.action} />
{:else if dashboard.role === "employer"}
  <EmployerVacancies busy={dashboard.busy} run={dashboard.action} />
{/if}
