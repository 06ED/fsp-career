<script lang="ts">
  import { getDashboard } from "$lib/pages/dashboard";
  import { Overview } from "$lib/widgets/overview";
  const dashboard = getDashboard();
</script>

{#if dashboard.role}
  <Overview
    role={dashboard.role}
    profile={dashboard.profile}
    company={dashboard.company}
    need={dashboard.need}
    invitations={dashboard.invitations}
  />
{/if}
