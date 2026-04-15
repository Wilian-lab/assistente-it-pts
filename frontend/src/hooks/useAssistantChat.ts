import { useMutation } from '@tanstack/react-query'

import { assistantService } from '../services/assistant/assistantService'

export function useAssistantChat() {
  return useMutation({
    mutationFn: assistantService.ask,
  })
}
