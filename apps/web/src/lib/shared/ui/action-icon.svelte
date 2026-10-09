<script lang="ts">
  import {
    ArrowUpRight,
    Check,
    CircleX,
    ClipboardCheck,
    Download,
    Eye,
    FileCheck,
    LayoutDashboard,
    LogIn,
    LogOut,
    Plus,
    RefreshCw,
    Search,
    Send,
    SendHorizontal,
    Trash2,
    UserRound,
    X,
  } from "@jis3r/icons";
  // Vacancies, applications and short tasks screens.
  import { Archive, Briefcase, ClipboardList, Lightbulb, Pencil, Star, Undo } from "@jis3r/icons";
  // Moving Icons doesn't currently include these four shapes.
  import { Building2, Save, Target, UserPlus } from "@lucide/svelte";

  const movingIcons = {
    arrow: ArrowUpRight,
    login: LogIn,
    logout: LogOut,
    overview: LayoutDashboard,
    profile: UserRound,
    assessment: ClipboardCheck,
    invitations: SendHorizontal,
    matches: Search,
    refresh: RefreshCw,
    send: Send,
    close: X,
    accept: Check,
    reject: CircleX,
    view: Eye,
    download: Download,
    remove: Trash2,
    add: Plus,
    confirm: FileCheck,
    vacancies: Briefcase,
    applications: ClipboardList,
    tasks: Lightbulb,
    edit: Pencil,
    archive: Archive,
    undo: Undo,
    star: Star,
  } as const;
  const fallbackIcons = {
    signup: UserPlus,
    company: Building2,
    need: Target,
    save: Save,
  } as const;

  let {
    name,
    size = 17,
    placement = "trailing",
  }: {
    name: keyof typeof movingIcons | keyof typeof fallbackIcons;
    size?: number;
    placement?: "leading" | "trailing" | "solo";
  } = $props();

  let playing = $state(false);
  let MovingIcon = $derived(movingIcons[name as keyof typeof movingIcons]);
  let FallbackIcon = $derived(fallbackIcons[name as keyof typeof fallbackIcons]);

  function bindParentHover(node: HTMLElement) {
    const parent = node.closest("button, a[data-interactive]");
    if (!parent) return;
    const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)");
    const start = () => (playing = !reducedMotion.matches);
    const stop = () => (playing = false);
    parent.addEventListener("pointerenter", start);
    parent.addEventListener("pointerleave", stop);
    parent.addEventListener("focusin", start);
    parent.addEventListener("focusout", stop);
    return {
      destroy() {
        parent.removeEventListener("pointerenter", start);
        parent.removeEventListener("pointerleave", stop);
        parent.removeEventListener("focusin", start);
        parent.removeEventListener("focusout", stop);
      },
    };
  }
</script>

<span use:bindParentHover data-action-icon data-position={placement} aria-hidden="true">
  {#if name in movingIcons}
    <MovingIcon {size} strokeWidth={2} animate={playing} />
  {:else}
    <FallbackIcon {size} strokeWidth={2} />
  {/if}
</span>
