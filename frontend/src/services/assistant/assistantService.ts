import type { AssistantContextResponse, AssistantOptionsResponse, AssistantRequest, AssistantResponse } from '../../types/assistant'
import { backendAssistantService } from './backendAssistantService'
import { localAssistantService } from './localAssistantService'

const provider = (import.meta.env.VITE_ASSISTANT_PROVIDER ?? 'backend').trim().toLowerCase()

export const assistantService = {
  async ask(payload: AssistantRequest): Promise<AssistantResponse> {
    if (provider === 'local') {
      return localAssistantService.ask(payload)
    }

    return backendAssistantService.ask(payload)
  },
  async getContext(itId: string, setorAtivo?: string): Promise<AssistantContextResponse> {
    if (provider === 'local') {
      return {
        itId,
        opcoes: [],
      }
    }

    return backendAssistantService.getContext(itId, setorAtivo)
  },
  async getOptions(itId: string, setorAtivo?: string): Promise<AssistantOptionsResponse> {
    if (provider === 'local') {
      return { opcoes: [] }
    }

    return backendAssistantService.getOptions(itId, setorAtivo)
  },
}