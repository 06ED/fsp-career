<script lang="ts">
  import { EmployerCompany } from "$lib/features/edit-company";
  import { getDashboard } from "$lib/pages/dashboard";

  const dashboard = getDashboard();
</script>

<EmployerCompany
  bind:company={dashboard.company}
  busy={dashboard.busy}
  saveCompany={dashboard.saveCompany}
/>
