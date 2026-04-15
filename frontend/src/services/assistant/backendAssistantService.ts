import type {
  AssistantContextResponse,
  AssistantOptionsResponse,
  AssistantRequest,
  AssistantResponse,
} from '../../types/assistant'
import { apiClient } from '../http/apiClient'

export const backendAssistantService = {
  ask: (payload: AssistantRequest) => apiClient.post<AssistantResponse>('/assistant/ask', payload),
  getContext: (itId: string, setorAtivo?: string) =>
    apiClient.get<AssistantContextResponse>(
      `/assistant/context?itId=${encodeURIComponent(itId)}${setorAtivo ? `&setorAtivo=${encodeURIComponent(setorAtivo)}` : ''}`,
    ),
  getOptions: (itId: string, setorAtivo?: string) =>
    apiClient.get<AssistantOptionsResponse>(
      `/assistant/options?itId=${encodeURIComponent(itId)}${setorAtivo ? `&setorAtivo=${encodeURIComponent(setorAtivo)}` : ''}`,
    ),
}