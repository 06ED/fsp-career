<script lang="ts">
  import { CandidateProfile, CandidateSettings } from "$lib/features/edit-candidate-profile";
  import { getDashboard } from "$lib/pages/dashboard";

  const dashboard = getDashboard();
</script>

<CandidateProfile
  bind:profile={dashboard.profile}
  resumeDraft={dashboard.resumeDraft}
  bind:techStackText={dashboard.techStackText}
  bind:softSkillsText={dashboard.softSkillsText}
  bind:targetRolesText={dashboard.targetRolesText}
  busy={dashboard.busy}
  uploadResume={dashboard.uploadResume}
  confirmResume={dashboard.confirmResume}
  saveProfile={dashboard.saveProfile}
  downloadResume={dashboard.downloadResume}
  revokeConsent={dashboard.revokeConsent}
/>

<CandidateSettings
  processingConsent={dashboard.profile.processingConsent}
  busy={dashboard.busy}
  run={dashboard.action}
/>
