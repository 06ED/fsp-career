export function modal(dialog: HTMLDialogElement, onClose: () => void) {
  const opener =
    document.activeElement instanceof HTMLElement
      ? document.activeElement
      : null;
  const cancel = (event: Event) => {
    event.preventDefault();
    onClose();
  };
  dialog.addEventListener("cancel", cancel);
  dialog.showModal();
  return {
    destroy() {
      dialog.removeEventListener("cancel", cancel);
      dialog.close();
      opener?.focus();
    },
  };
}
