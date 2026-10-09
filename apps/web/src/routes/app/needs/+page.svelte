<script lang="ts">
  import { EmployerNeeds } from "$lib/features/edit-need";
  import { getDashboard } from "$lib/pages/dashboard";
  const dashboard = getDashboard();
</script>

<EmployerNeeds bind:need={dashboard.need} busy={dashboard.busy} saveNeed={dashboard.saveNeed} />
