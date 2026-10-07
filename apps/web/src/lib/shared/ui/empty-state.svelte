<script lang="ts">
  import type { Snippet } from "svelte";
  import ActionIcon from "./action-icon.svelte";

  let {
    icon,
    title,
    description,
    action,
  }: {
    icon:
      | "invitations"
      | "vacancies"
      | "applications"
      | "tasks"
      | "assessment"
      | "matches";
    title: string;
    description: string;
    action?: Snippet;
  } = $props();
</script>

<div class="empty-state" role="status">
  <span class="empty-state-icon"
    ><ActionIcon name={icon} size={26} placement="solo" /></span
  >
  <h3>{title}</h3>
  <p>{description}</p>
  {#if action}<div class="d-ui-inline">{@render action()}</div>{/if}
</div>

<style>
  .empty-state {
    background: var(--surface-tint);
    border: 1px solid var(--border);
    border-radius: var(--radius-ui);
    padding: 28px;
    margin-bottom: 16px;
    text-align: center;
  }
  .empty-state-icon {
    display: inline-flex;
    color: var(--accent);
    background: var(--accent-soft);
    padding: 14px;
    border-radius: 50%;
  }
  h3 {
    font-size: 19px;
    margin: 12px 0 6px;
  }
  p {
    color: var(--text-muted);
    font-size: 13px;
    line-height: 1.55;
    margin: 0 auto 14px;
    max-width: 480px;
  }
  .d-ui-inline {
    justify-content: center;
  }
  @media (max-width: 600px) {
    .empty-state {
      padding: 20px 14px;
    }
  }
</style>
