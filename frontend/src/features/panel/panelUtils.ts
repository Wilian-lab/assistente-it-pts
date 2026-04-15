import type { ManagedIt, TrainingAlert } from '../../types/it'
import type { UserProfile } from '../../types/auth'

export const IT_STATUS_OPTIONS = ['Atualizada', 'Pendente', 'Copia nao controlada'] as const

type TrainingTone = 'pending' | 'danger' | 'warning' | 'ok'

export interface TrainingStatusMeta {
  label: string
  icon: string
  tone: TrainingTone
  priority: number
  title: string
}

function parseIsoDate(value: string | null | undefined): Date | null {
  const text = String(value ?? '').trim()
  if (!text) return null

  const parsed = new Date(text)
  return Number.isNaN(parsed.getTime()) ? null : parsed
}

function startOfToday(): Date {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return today
}

function getDaysUntilTraining(nextTrainingDate: string | null | undefined): number | null {
  const nextTraining = parseIsoDate(nextTrainingDate)
  if (!nextTraining) return null

  const today = startOfToday()
  const diffMs = nextTraining.getTime() - today.getTime()
  return Math.ceil(diffMs / (1000 * 60 * 60 * 24))
}

export function formatDateLabel(value: string | Date | null | undefined): string {
  const date = value instanceof Date ? value : parseIsoDate(value)
  if (!date) return 'Sem data'
  return date.toLocaleDateString('pt-BR')
}

export function formatDateTimeLabel(value: string | null | undefined): string {
  const date = parseIsoDate(value)
  if (!date) return '-'
  return date.toLocaleString('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function normalizeTrainingStatus(value: string | null | undefined): string {
  return String(value ?? '').trim().toUpperCase().replaceAll(' ', '_')
}

export function getTrainingStatusMeta(
  nextTrainingDate: string | null | undefined,
  trainingStatus?: string | null,
): TrainingStatusMeta {
  const normalizedStatus = normalizeTrainingStatus(trainingStatus)
  if (normalizedStatus === 'TREINADO') {
    return { label: 'Treinado', icon: '●', tone: 'ok', priority: 1, title: 'Treinamento em dia' }
  }
  if (normalizedStatus === 'NAO_TREINADO') {
    return { label: 'Nao treinado', icon: '●', tone: 'danger', priority: 3, title: 'Treinamento nao realizado' }
  }
  if (normalizedStatus === 'PENDENTE') {
    return { label: 'Pendente', icon: '○', tone: 'pending', priority: 0, title: 'Treinamento pendente' }
  }
  if (normalizedStatus === 'ATRASADO') {
    return { label: 'Atrasado', icon: '●', tone: 'danger', priority: 3, title: 'Treinamento atrasado' }
  }

  const nextTraining = parseIsoDate(nextTrainingDate)
  if (!nextTraining) {
    return { label: 'Pendente', icon: '○', tone: 'pending', priority: 0, title: 'Treinamento pendente' }
  }

  const today = startOfToday()
  const warningLimit = new Date(today)
  warningLimit.setDate(warningLimit.getDate() + 7)

  if (nextTraining < today) {
    return { label: 'Vencido', icon: '●', tone: 'danger', priority: 3, title: 'Treinamento vencido' }
  }

  if (nextTraining <= warningLimit) {
    return { label: 'Proximo de vencer', icon: '●', tone: 'warning', priority: 2, title: 'Treinamento proximo de vencer' }
  }

  return { label: 'Treinado', icon: '●', tone: 'ok', priority: 1, title: 'Treinamento em dia' }
}

export function buildTrainingStatusDescription(user: Pick<UserProfile, 'lastTrainedIt' | 'nextTrainingDate' | 'trainingStatus'>): string {
  const meta = getTrainingStatusMeta(user.nextTrainingDate, user.trainingStatus)
  const targetDocument = String(user.lastTrainedIt ?? '').trim()
  const nextDateLabel = formatDateLabel(user.nextTrainingDate)
  const daysUntil = getDaysUntilTraining(user.nextTrainingDate)

  if (normalizeTrainingStatus(user.trainingStatus) === 'NAO_TREINADO') {
    return targetDocument
      ? `O usuario ainda nao foi treinado para o documento ${targetDocument}.`
      : 'O usuario ainda nao foi treinado.'
  }

  if (normalizeTrainingStatus(user.trainingStatus) === 'ATRASADO') {
    return targetDocument
      ? `O treinamento do documento ${targetDocument} esta atrasado.`
      : 'O treinamento do usuario esta atrasado.'
  }

  if (meta.tone === 'pending') {
    return targetDocument
      ? `Treinamento pendente para o documento ${targetDocument}.`
      : 'Nenhum treinamento registrado para este usuario.'
  }

  if (meta.tone === 'danger') {
    return targetDocument
      ? `O documento ${targetDocument} venceu em ${nextDateLabel}.`
      : `Treinamento vencido em ${nextDateLabel}.`
  }

  if (meta.tone === 'warning') {
    if (daysUntil === 0) {
      return targetDocument
        ? `O documento ${targetDocument} vence hoje.`
        : 'O treinamento vence hoje.'
    }

    return targetDocument
      ? `O documento ${targetDocument} vence em ${daysUntil} dia(s), na data ${nextDateLabel}.`
      : `O treinamento vence em ${daysUntil} dia(s), na data ${nextDateLabel}.`
  }

  return targetDocument
    ? `Ultimo treinamento registrado no documento ${targetDocument}. Proximo vencimento em ${nextDateLabel}.`
    : `Treinamento em dia. Proximo vencimento em ${nextDateLabel}.`
}

export function getUserTrainingAlerts(users: UserProfile[]): TrainingAlert[] {
  return users
    .map((user) => {
      const meta = getTrainingStatusMeta(user.nextTrainingDate, user.trainingStatus)
      return {
        name: user.name?.trim() || '-',
        email: user.email?.trim() || '-',
        statusLabel: meta.label,
        statusIcon: meta.icon,
        statusTone: meta.tone,
        nextTrainingDate: user.nextTrainingDate,
        lastTrainedIt: user.lastTrainedIt,
        description: buildTrainingStatusDescription(user),
        priority: meta.priority,
      }
    })
    .filter((item) => item.priority === 0 || item.priority >= 2)
    .sort((a, b) => {
      const priorityDiff = (b.priority ?? 0) - (a.priority ?? 0)
      if (priorityDiff !== 0) return priorityDiff

      const aDate = parseIsoDate(a.nextTrainingDate)
      const bDate = parseIsoDate(b.nextTrainingDate)
      if (!aDate && !bDate) return a.name.localeCompare(b.name, 'pt-BR')
      if (!aDate) return -1
      if (!bDate) return 1
      return aDate.getTime() - bDate.getTime()
    })
}

export function getRecentIts(its: ManagedIt[], limit = 2): ManagedIt[] {
  return [...its]
    .sort((a, b) => {
      const aDate = parseIsoDate(a.dataPublicacao)
      const bDate = parseIsoDate(b.dataPublicacao)
      if (!aDate && !bDate) return a.documento.localeCompare(b.documento, 'pt-BR')
      if (!aDate) return 1
      if (!bDate) return -1
      return bDate.getTime() - aDate.getTime()
    })
    .slice(0, limit)
}

export function getMostRecentIt(its: ManagedIt[]): ManagedIt | null {
  return getRecentIts(its, 1)[0] ?? null
}

export function buildManagedItPayload(input: {
  documento: string
  revisao: string
  status: string
  setor: string
  dataPublicacao: string
  paginaAtual: number
  totalPaginas: number
  prazoTreinamentoDias: number
}) {
  return {
    documento: input.documento.trim(),
    revisao: input.revisao.trim(),
    status: input.status.trim(),
    setor: input.setor.trim(),
    dataPublicacao: input.dataPublicacao,
    paginaAtual: Number(input.paginaAtual),
    totalPaginas: Number(input.totalPaginas),
    prazoTreinamentoDias: Number(input.prazoTreinamentoDias),
  }
}

export function getManagedItTitle(it: ManagedIt): string {
  return it.titulo?.trim() || it.documentTitle?.trim() || it.documento
}
