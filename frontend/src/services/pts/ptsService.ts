import { apiClient } from '../http/apiClient'

export interface PtsItemData {
  produto: string
  etapa: string
  item: string
  variavel: string
  classificacao: string
  unidade: string
  limiteInf: string
  limiteSup: string
  respColeta: string
  respAnalise: string
  frequencia: string
  pontoColeta: string
  amostra: string
  metodoAnalise: string
  tag: string
  tagAspen: string
  acaoAbaixo: string
  acaoAcima: string
  fca: string
  vaiNoApp: string
  documentoReferencia: string
}

export interface PtsFileInfo {
  setor: string
  fileName: string
  size: number
  lastModified: string
  recordsCount: number
}

function parseItemCode(value: string) {
  return String(value)
    .trim()
    .split('.')
    .map((part) => Number.parseInt(part, 10))
}

export function comparePtsItemCodes(left: string, right: string) {
  const leftParts = parseItemCode(left)
  const rightParts = parseItemCode(right)
  const max = Math.max(leftParts.length, rightParts.length)

  for (let index = 0; index < max; index += 1) {
    const leftValue = Number.isFinite(leftParts[index]) ? leftParts[index] : -1
    const rightValue = Number.isFinite(rightParts[index]) ? rightParts[index] : -1
    if (leftValue !== rightValue) return leftValue - rightValue
  }

  return left.localeCompare(right, 'pt-BR', { sensitivity: 'base', numeric: true })
}

export const ptsService = {
  async getProducts() {
    return apiClient.get<string[]>('/api/pts/products')
  },

  async getItems(product: string) {
    const items = await apiClient.get<string[]>(`/api/pts/items?product=${encodeURIComponent(product)}`)
    return [...items].sort(comparePtsItemCodes)
  },

  getData(product: string, item?: string) {
    const params = new URLSearchParams({ product })
    if (item && item !== 'Selecione um item') params.set('item', item)
    return apiClient.get<PtsItemData[]>(`/api/pts/data?${params}`)
  },

  getFiles() {
    return apiClient.get<PtsFileInfo[]>('/api/pts/files')
  },

  deleteCurrentFile() {
    return apiClient.delete<{ message: string }>('/api/pts/files/current')
  },
}
