<script lang="ts">
  import "$lib/shared/styles/theme.css";
  import "$lib/shared/styles/base.css";
  import "$lib/shared/styles/dashboard.css";
  import "$lib/shared/styles/vacancy.css";
  import "$lib/pages/landing/ui/landing.css";
  import "$lib/features/take-assessment/ui/assessment.css";
  import "$lib/features/find-candidates/ui/matching.css";

  let { children } = $props();
</script>

{@render children()}
