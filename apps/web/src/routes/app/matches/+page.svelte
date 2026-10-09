<script lang="ts">
  import { EmployerMatches } from "$lib/features/find-candidates";
  import { getDashboard } from "$lib/pages/dashboard";
  const dashboard = getDashboard();
</script>

<EmployerMatches
  need={dashboard.need}
  company={dashboard.company}
  busy={dashboard.busy}
  run={dashboard.action}
  sendInvitation={dashboard.sendInvitation}
/>
