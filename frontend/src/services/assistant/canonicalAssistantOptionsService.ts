import type { AssistantOptionItem } from '../../types/assistant'

export const canonicalAssistantOptionsService = {
  async getOptions(_documentCode?: string, _documentTitle?: string): Promise<AssistantOptionItem[]> {
    return []
  },
}
