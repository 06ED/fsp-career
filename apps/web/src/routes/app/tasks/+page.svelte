<script lang="ts">
  import { CandidateTasks } from "$lib/features/answer-task";
  import { EmployerTasks } from "$lib/features/manage-tasks";
  import { getDashboard } from "$lib/pages/dashboard";

  const dashboard = getDashboard();
</script>

{#if dashboard.role === "candidate"}
  <CandidateTasks profile={dashboard.profile} busy={dashboard.busy} run={dashboard.action} />
{:else if dashboard.role === "employer"}
  <EmployerTasks busy={dashboard.busy} run={dashboard.action} />
{/if}
