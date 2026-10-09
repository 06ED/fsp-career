<script lang="ts">
  import { CandidateApplications } from "$lib/features/manage-applications";
  import { getDashboard } from "$lib/pages/dashboard";
  const dashboard = getDashboard();
</script>

<CandidateApplications busy={dashboard.busy} run={dashboard.action} />
