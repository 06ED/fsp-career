<script lang="ts">
  import { goto } from "$app/navigation";
  import { accountApi } from "$lib/entities/account";
  import type { Role } from "$lib/entities/account";
  import { RoleChoice } from "$lib/features/choose-role";
  import { getDashboard } from "$lib/pages/dashboard";

  let { data } = $props();
  const dashboard = getDashboard();

  async function chooseRole(role: Role) {
    await dashboard.action(async () => {
      const account = await accountApi.chooseRole(role);
      dashboard.role = account.role;
      await dashboard.refresh();
      await goto("/app", { replaceState: true, invalidateAll: true });
    }, "");
  }
</script>

<RoleChoice suggested={data.desiredRole} busy={dashboard.busy} {chooseRole} />
