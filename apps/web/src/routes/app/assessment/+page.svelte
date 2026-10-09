<script lang="ts">
  import { CandidateAssessment } from "$lib/features/take-assessment";
  import { getDashboard } from "$lib/pages/dashboard";

  const dashboard = getDashboard();
</script>

<CandidateAssessment profile={dashboard.profile} refreshProfile={dashboard.refresh} />
