export interface ManagedIt {
  id: string
  documento: string
  titulo?: string
  documentTitle?: string
  codigo?: string
  revisao: string
  status: string
  setor?: string
  dataPublicacao: string
  paginaAtual: number
  totalPaginas: number
  prazoTreinamentoDias: number
  fileUrl?: string
  downloadUrl?: string
}

export interface RecentActivity {
  icone: string
  descricao: string
  data: string
}

export interface ItPayload {
  documento: string
  revisao: string
  status: string
  setor: string
  dataPublicacao: string
  paginaAtual: number
  totalPaginas: number
  prazoTreinamentoDias: number
}

export type UserPanelNavItem =
  | 'Painel do Usuario'
  | 'Minhas ITs'
  | 'Assistente'
  | 'Documentacao'
  | 'Arquivos'
  | 'Historico de Conversas'
  | 'Notificacoes'

export interface MetricCard {
  titulo: string
  valor: number | string
  subtitulo: string
}

export interface ItDocumentOption {
  id: string
  title: string
  code: string
}

export interface AssistantDocumentCard {
  documentCode?: string
  documentTitle?: string
  downloadUrl?: string
  fileName?: string
}

export interface TrainingAlert {
  name: string
  email?: string
  statusLabel: string
  statusIcon: string
  statusTone?: 'pending' | 'danger' | 'warning' | 'ok'
  nextTrainingDate?: string | null
  lastTrainedIt?: string | null
  description?: string
  priority?: number
}

export interface DashboardSummary {
  itsAcessadas: number
  conversas: number
  arquivos: number
}

export interface LastTrainedItSummary {
  documento: string
  titulo: string
  revisao: string
  nextTrainingDate?: string | null
}

export interface UserPanelSnapshot {
  recentIts: ManagedIt[]
  recentActivities: RecentActivity[]
  metrics: MetricCard[]
}

export function getItDisplayTitle(it: ManagedIt): string {
  return it.titulo ?? it.documentTitle ?? it.documento
}

export function getItDisplayCode(it: ManagedIt): string {
  return it.codigo ?? it.documento
}

export function normalizeItStatus(status: string): { label: string; tone: 'green' | 'purple' | 'draft' | 'orange' } {
  const value = (status || 'Rascunho').trim().toLowerCase()

  if (value.includes('atual')) {
    return { label: 'Atualizada', tone: 'green' }
  }

  if (value.includes('pend')) {
    return { label: 'Pendente', tone: 'purple' }
  }

  if (value.includes('copia') || value.includes('cópia') || value.includes('nao controlada') || value.includes('não controlada')) {
    return { label: 'Copia nao controlada', tone: 'orange' }
  }

  if (value.includes('analise') || value.includes('análise')) {
    return { label: 'Em analise', tone: 'purple' }
  }

  return { label: 'Rascunho', tone: 'draft' }
}

export function sortItemCode(value: string): Array<number | string> {
  return String(value)
    .split('.')
    .map((part) => (/^\d+$/.test(part) ? Number(part) : part.toLowerCase()))
}
