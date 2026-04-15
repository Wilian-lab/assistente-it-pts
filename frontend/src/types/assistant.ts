export interface AssistantChatTurn {
  role: 'user' | 'assistant'
  content: string
}

export interface AssistantRequest {
  itId: string
  message: string
  documentCode?: string
  documentTitle?: string
  fileUrl?: string
  setorAtivo?: string
  selectedStep?: number | null
  selectedPage?: number | null
  selectedOptionTitle?: string
  history?: AssistantChatTurn[]
}

export interface AssistantEvidenceItem {
  passo?: number | null
  pagina?: number | null
  entryType?: string
  sectionNumber?: number | null
  sectionTitle?: string
  what?: string
  how?: string
  care?: string
  possibleCauses?: string
  actionText?: string
}

export interface AssistantResponse {
  message: string
  sourceType?: string
  documento?: string
  titulo?: string
  revisao?: string
  downloadUrl?: string
  previewUrl?: string
  warnings?: string[]
  evidence?: AssistantEvidenceItem[]
  metadata?: Record<string, unknown>
}


export interface AssistantMessage {
  role: 'user' | 'assistant'
  content: string
  timestamp?: string
  itActionDoc?: string
  docDownloadUrl?: string
  docFileName?: string
  sourceType?: string
  metadata?: Record<string, unknown>
  evidence?: AssistantEvidenceItem[]
  apiItOptions?: Array<{
    id: string
    title: string
    code: string
  }>
  apiSelectionQuery?: string
}

export interface AssistantOptionItem {
  passo?: number | null
  pagina?: number | null
  titulo: string
  origin?: 'backend' | 'canonical'
}

export interface AssistantOptionsResponse {
  documento?: string
  titulo?: string
  opcoes: AssistantOptionItem[]
}

export interface AssistantContextResponse {
  itId: string
  documento?: string
  titulo?: string
  revisao?: string
  status?: string
  setor?: string
  downloadUrl?: string
  previewUrl?: string
  conversationId?: string
  documentVersion?: string
  stepCount?: number
  anomalyCount?: number
  opcoes: AssistantOptionItem[]
  sampleQuestions?: string[]
  metadata?: Record<string, unknown>
}

