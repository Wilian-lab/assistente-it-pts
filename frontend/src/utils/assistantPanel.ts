export const OPEN_ASSISTANT_PANEL_EVENT = 'pts.assistant.open'

export function openAssistantPanel(): void {
  window.dispatchEvent(new CustomEvent(OPEN_ASSISTANT_PANEL_EVENT))
}
