import type { AssistantOptionItem } from '../../types/assistant'

interface LegacyItIndexEntry {
  document_code?: string
  document_title?: string
  page?: number
  step?: number
  entry_type?: string
  what?: string
}

interface LegacyItIndexPayload {
  entries?: LegacyItIndexEntry[]
}

let cachedIndexPromise: Promise<LegacyItIndexPayload | null> | null = null

function normalize(value: string | undefined): string {
  return String(value ?? '')
    .normalize('NFD')
    .replace(/\p{M}+/gu, '')
    .toLowerCase()
    .trim()
}

function compact(value: string | undefined): string {
  return normalize(value).replace(/[^a-z0-9]/g, '')
}

function cleanTitle(value: string | undefined): string {
  return String(value ?? '')
    .replace(/\s+/g, ' ')
    .trim()
    .replace(/[.:;-]+$/, '')
}

async function loadIndex(): Promise<LegacyItIndexPayload | null> {
  if (!cachedIndexPromise) {
    cachedIndexPromise = fetch('/data/it_index.json')
      .then(async (response) => {
        if (!response.ok) return null
        return (await response.json()) as LegacyItIndexPayload
      })
      .catch(() => null)
  }

  return cachedIndexPromise
}

function matchesDocument(entry: LegacyItIndexEntry, documentCode?: string, documentTitle?: string): boolean {
  const entryCode = normalize(entry.document_code)
  const entryCompactCode = compact(entry.document_code)
  const targetCode = normalize(documentCode)
  const targetCompactCode = compact(documentCode)

  if (targetCode && (entryCode === targetCode || entryCompactCode === targetCompactCode)) {
    return true
  }

  const entryTitle = normalize(entry.document_title)
  const targetTitle = normalize(documentTitle)
  return Boolean(targetTitle) && entryTitle === targetTitle
}

export const canonicalAssistantOptionsService = {
  async getOptions(documentCode?: string, documentTitle?: string): Promise<AssistantOptionItem[]> {
    const index = await loadIndex()
    const entries = index?.entries ?? []
    const byStep = new Map<number, AssistantOptionItem>()

    for (const entry of entries) {
      if (normalize(entry.entry_type) !== 'step' || typeof entry.step !== 'number') continue
      if (!matchesDocument(entry, documentCode, documentTitle)) continue

      const title = cleanTitle(entry.what)
      if (!title) continue

      const candidate: AssistantOptionItem = {
        passo: entry.step,
        pagina: typeof entry.page === 'number' ? entry.page : null,
        titulo: title,
        origin: 'canonical',
      }

      const current = byStep.get(entry.step)
      if (!current) {
        byStep.set(entry.step, candidate)
        continue
      }

      const currentLength = String(current.titulo ?? '').length
      const candidateLength = title.length
      const shouldReplace =
        candidateLength < currentLength ||
        (candidateLength === currentLength && (candidate.pagina ?? 999) < (current.pagina ?? 999))

      if (shouldReplace) {
        byStep.set(entry.step, candidate)
      }
    }

    return [...byStep.values()].sort((left, right) => (left.passo ?? 999) - (right.passo ?? 999))
  },
}
