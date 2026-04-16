import type { AssistantRequest, AssistantResponse } from '../../types/assistant'

export const localAssistantService = {
  async ask(_payload: AssistantRequest): Promise<AssistantResponse> {
    throw new Error('O modo local do assistente foi desativado nesta build protegida.')
  },
}
