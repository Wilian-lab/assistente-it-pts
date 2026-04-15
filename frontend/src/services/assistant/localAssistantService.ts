import type { AssistantRequest, AssistantResponse } from '../../types/assistant'

interface ItDocMeta {
  document_code: string
  document_title: string
  author?: string
  authorizer?: string
  print_date?: string
  create_date?: string
  file_path?: string
  products?: string[]
}

interface ItIndexEntry extends ItDocMeta {
  page?: number
  step?: number
  section_number?: number
  section_title?: string
  entry_type?: 'step' | 'section' | 'anomaly'
  what?: string
  how?: string
  care?: string
  anomaly_title?: string
  possible_causes?: string
  action_text?: string
  normalized?: string
  normalized_what?: string
  normalized_how?: string
  normalized_care?: string
  score?: number
  matched_terms?: number
  matched_in_body?: number
  matched_in_what?: number
  query_norm?: string
  related_anomalies?: ItIndexEntry[]
}

interface ItIndex {
  docs: ItDocMeta[]
  entries: ItIndexEntry[]
}

interface LocalAssistantRequest extends AssistantRequest {
  documentCode?: string
  documentTitle?: string
  fileUrl?: string
}

const IT_MAX_RESULTS = 3
const IT_SCORE_WHAT_KEYWORD = 9.0
const IT_SCORE_TITLE_KEYWORD = 7.0
const IT_SCORE_CODE_KEYWORD = 4.0
const IT_SCORE_HOW_KEYWORD = 3.5
const IT_SCORE_CARE_KEYWORD = 2.5
const IT_SCORE_TEXT_KEYWORD = 3.0
const IT_SCORE_PHRASE_WHAT_LONG = 14.0
const IT_SCORE_PHRASE_WHAT_SHORT = 9.0
const IT_SCORE_PHRASE_TITLE_LONG = 10.0
const IT_SCORE_PHRASE_TITLE_SHORT = 6.0
const IT_SCORE_PHRASE_TEXT_LONG = 6.0
const IT_SCORE_PHRASE_TEXT_SHORT = 3.5
const IT_SCORE_QUERY_IN_WHAT = 20.0
const IT_SCORE_QUERY_IN_TEXT = 8.0
const IT_SCORE_SECTION_CONTEXT = 8.0
const IT_SCORE_STEP_BASE = 2.0
const IT_SCORE_STEP_ACTION_QUERY = 8.0
const IT_SCORE_STEP_ACTION_MATCH = 12.0
const IT_SCORE_MATCHED_TERM = 2.0
const STEP_LIMIT = 20
const ACTION_TERMS = ['monitorar', 'ajustar', 'passar', 'ligar', 'iniciar', 'operar']
const CARE_FIELD_CUES = [
  'em caso',
  'nao esquecer',
  'cuidar',
  'atencao',
  'seguranca',
  'bloqueio',
  'loto',
  'epi',
  'evitar',
  'sempre utilizar',
  'rampa de aquecimento',
  'vazamento',
  'rompimento',
  'durante a parada',
  'qualquer duvida',
]
const STOP_WORDS = new Set([
  'a', 'o', 'e', 'de', 'do', 'da', 'dos', 'das', 'para', 'por', 'com', 'sem', 'em', 'no', 'na', 'nos', 'nas',
  'uma', 'um', 'as', 'os', 'que', 'qual', 'quais', 'como', 'sobre', 'me', 'traga', 'mostrar', 'mostre', 'favor',
])

let itIndexPromise: Promise<ItIndex> | null = null

function normalize(text: string): string {
  return String(text ?? '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/\s+/g, ' ')
    .trim()
}

function tokenize(text: string): string[] {
  return normalize(text).split(' ').filter(Boolean)
}

function normalizeDocCode(value: string): string {
  return normalize(value).replace(/[^a-z0-9]/g, '')
}

function cleanItQuery(query: string): string {
  const cleaned = query
    .replace(/\b(pts|pps|it)\b/gi, ' ')
    .replace(/instru[cç][aã]o(?:es)?\s+t[eé]cnica(?:s)?/gi, ' ')
    .replace(/^\b(na|no|nas|nos)\b/gi, ' ')
    .replace(/\s+/g, ' ')
    .trim()
  return cleaned || query.trim()
}

function extractKeywords(query: string): string[] {
  const localStopWords = new Set([...STOP_WORDS, 'na', 'no', 'nas', 'nos', 'operar', 'operacao'])
  const keywords: string[] = []
  for (const token of tokenize(cleanItQuery(query))) {
    const normalized = normalize(token)
    if (localStopWords.has(normalized)) continue
    if (normalized.length <= 2 && normalized !== 'icm') continue
    keywords.push(normalized)
  }
  return [...new Set(keywords)]
}

function containsWholeTerm(text: string, term: string): boolean {
  if (!text || !term) return false
  return new RegExp(`\\b${term.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\b`).test(normalize(text))
}

function containsRelatedTerm(text: string, term: string): boolean {
  if (containsWholeTerm(text, term)) return true
  const normalizedTerm = normalize(term)
  if (normalizedTerm.length < 5) return false
  for (const token of tokenize(text)) {
    if (token.length < 5) continue
    if (token.startsWith(normalizedTerm.slice(0, 5)) || normalizedTerm.startsWith(token.slice(0, 5))) return true
  }
  return false
}

function extractStepHint(query: string): number | null {
  const match = normalize(query).match(/\bpasso\s*(\d{1,2})\b/)
  if (!match) return null
  const step = Number(match[1])
  return step >= 1 && step <= STEP_LIMIT ? step : null
}

async function loadItIndex(): Promise<ItIndex> {
  if (!itIndexPromise) {
    itIndexPromise = fetch('/data/it_index.json').then(async (response) => {
      if (!response.ok) throw new Error('Nao foi possivel carregar o indice local das ITs.')
      return response.json() as Promise<ItIndex>
    })
  }
  return itIndexPromise
}

function extractCanonicalDocCode(value: string): string {
  const match = String(value ?? '').match(/rp[-_ ]?it[-_ ]?[a-z]{0,4}\d+/i)
  return match ? match[0] : ''
}

function buildDocCandidates(documentCode?: string, documentTitle?: string, fileUrl?: string): string[] {
  const values = [documentCode, documentTitle, fileUrl].map((value) => String(value ?? '').trim()).filter(Boolean)
  const candidates = new Set<string>()

  for (const value of values) {
    candidates.add(normalize(value))
    candidates.add(normalizeDocCode(value))
    const canonical = extractCanonicalDocCode(value)
    if (canonical) {
      candidates.add(normalize(canonical))
      candidates.add(normalizeDocCode(canonical))
    }
    const fileName = value.split(/[\\/]/).pop() ?? ''
    if (fileName) {
      candidates.add(normalize(fileName))
      candidates.add(normalizeDocCode(fileName))
    }
  }

  return [...candidates].filter(Boolean)
}

function matchDoc(doc: ItDocMeta, candidates: string[]): boolean {
  const fileName = doc.file_path?.split(/[\\/]/).pop() ?? ''
  const fields = [
    normalize(doc.document_code),
    normalizeDocCode(doc.document_code),
    normalize(doc.document_title),
    normalizeDocCode(doc.document_title),
    normalize(fileName),
    normalizeDocCode(fileName),
  ].filter(Boolean)

  return candidates.some((candidate) => fields.some((field) => field === candidate || field.includes(candidate) || candidate.includes(field)))
}

function getDocMetadata(index: ItIndex, documentCode: string, documentTitle = '', fileUrl = ''): ItDocMeta | undefined {
  const candidates = buildDocCandidates(documentCode, documentTitle, fileUrl)
  return index.docs.find((doc) => matchDoc(doc, candidates))
}

function buildProtectedItFileUrl(itId?: string): string | undefined {
  const value = String(itId ?? '').trim()
  return value ? `/it/${encodeURIComponent(value)}/file` : undefined
}

function isStrongQueryMatch(entry: ItIndexEntry, keywords: string[]): boolean {
  if (!keywords.length) return false
  const contextText = [entry.what, entry.how, entry.care, entry.section_title, entry.anomaly_title].filter(Boolean).join(' ').trim()
  if (!contextText) return false
  const matchedKeywords = keywords.filter((keyword) => containsRelatedTerm(contextText, keyword))
  if (!matchedKeywords.length) return false
  const minRequired = Math.max(1, Math.min(keywords.length, 2))
  return matchedKeywords.length >= minRequired
}

function scoreItEntry(entry: ItIndexEntry, keywords: string[], queryNorm: string): [number, number, number, number] {
  const textNorm = entry.normalized ?? ''
  const whatNorm = entry.normalized_what ?? ''
  const howNorm = entry.normalized_how ?? ''
  const careNorm = entry.normalized_care ?? ''
  const titleNorm = normalize(entry.document_title ?? '')
  const codeNorm = normalize(entry.document_code ?? '')
  let matchedTerms = 0
  let matchedInBody = 0
  let matchedInWhat = 0
  let score = 0

  for (const keyword of keywords) {
    let hit = false
    if (whatNorm.includes(keyword)) {
      score += IT_SCORE_WHAT_KEYWORD
      hit = true
      matchedInWhat += 1
    }
    if (titleNorm.includes(keyword)) {
      score += IT_SCORE_TITLE_KEYWORD
      hit = true
    }
    if (codeNorm.includes(keyword)) {
      score += IT_SCORE_CODE_KEYWORD
      hit = true
    }
    if (howNorm.includes(keyword)) {
      score += IT_SCORE_HOW_KEYWORD
      hit = true
      matchedInBody += 1
    }
    if (careNorm.includes(keyword)) {
      score += IT_SCORE_CARE_KEYWORD
      hit = true
      matchedInBody += 1
    }
    if (textNorm.includes(keyword)) {
      score += IT_SCORE_TEXT_KEYWORD
      hit = true
    }
    if (hit) matchedTerms += 1
  }

  for (const size of [4, 3, 2]) {
    for (let index = 0; index <= keywords.length - size; index += 1) {
      const phrase = keywords.slice(index, index + size).join(' ')
      if (whatNorm.includes(phrase)) score += size >= 3 ? IT_SCORE_PHRASE_WHAT_LONG : IT_SCORE_PHRASE_WHAT_SHORT
      if (titleNorm.includes(phrase)) score += size >= 3 ? IT_SCORE_PHRASE_TITLE_LONG : IT_SCORE_PHRASE_TITLE_SHORT
      if (textNorm.includes(phrase)) score += size >= 3 ? IT_SCORE_PHRASE_TEXT_LONG : IT_SCORE_PHRASE_TEXT_SHORT
    }
  }

  if (queryNorm && whatNorm.includes(queryNorm)) score += IT_SCORE_QUERY_IN_WHAT
  if (queryNorm && textNorm.includes(queryNorm)) score += IT_SCORE_QUERY_IN_TEXT

  if (entry.entry_type === 'section' && /\b(resultado|resultados|referencia|referencias|anexo|anexos|definicao|definicoes|simbolo|simbolos|abreviatura|abreviaturas|recurso|recursos)\b/.test(queryNorm)) {
    score += IT_SCORE_SECTION_CONTEXT
  }
  if (entry.entry_type === 'anomaly') score += 6
  if (entry.entry_type === 'step') {
    score += IT_SCORE_STEP_BASE
    if (/\b(como|operar|operacao|passar|ligar|ajustar|iniciar|fazer|monitorar)\b/.test(queryNorm)) score += IT_SCORE_STEP_ACTION_QUERY
    for (const actionTerm of ACTION_TERMS) {
      const actionRegex = new RegExp(`\\b${actionTerm}\\b`)
      if (actionRegex.test(queryNorm) && (actionRegex.test(whatNorm) || actionRegex.test(howNorm))) score += IT_SCORE_STEP_ACTION_MATCH
    }
  }

  score += matchedTerms * IT_SCORE_MATCHED_TERM
  return [score, matchedTerms, matchedInBody, matchedInWhat]
}

function narrowSpecificResults(results: ItIndexEntry[], keywords: string[]): ItIndexEntry[] {
  if (results.length <= 1) return results
  const top = results[0]
  const second = results[1]
  const queryNorm = top.query_norm ?? ''
  if (queryNorm && (top.normalized_what ?? '').includes(queryNorm)) return [top]

  const topScore = Number(top.score ?? 0)
  const secondScore = Number(second?.score ?? 0)

  if ((top.matched_in_what ?? 0) >= 1 && (!second || topScore >= Math.max(6, secondScore * 1.8))) return [top]
  if ((top.matched_in_body ?? 0) >= 1 && (!second || topScore >= Math.max(8, secondScore * 2.5))) return [top]
  if (keywords.length < 2) return results
  if ((top.matched_in_what ?? 0) >= keywords.length) return [top]

  const allKeywordsInBody = (top.matched_in_body ?? 0) >= keywords.length
  const secondBodyMatches = second?.matched_in_body ?? 0

  if (top.entry_type === 'section' && second && topScore >= secondScore * 1.15) return [top]
  if (allKeywordsInBody && (secondBodyMatches < keywords.length || topScore >= secondScore * 1.2)) return [top]
  return results
}

function attachRelatedAnomalies(index: ItIndex, results: ItIndexEntry[]): ItIndexEntry[] {
  if (!results.length) return results
  const anomalyMap = new Map<string, ItIndexEntry[]>()
  for (const entry of index.entries) {
    if (entry.entry_type !== 'anomaly' || !entry.step) continue
    const key = `${entry.document_code}::${entry.step}`
    const current = anomalyMap.get(key) ?? []
    current.push(entry)
    anomalyMap.set(key, current)
  }

  return results.map((result) => {
    if (result.entry_type === 'step' && result.step) {
      const key = `${result.document_code}::${result.step}`
      return { ...result, related_anomalies: anomalyMap.get(key) ?? [] }
    }
    return { ...result, related_anomalies: [] }
  })
}

function dropStandaloneAnomaliesWhenStepExists(results: ItIndexEntry[]): ItIndexEntry[] {
  const stepKeys = new Set(
    results
      .filter((entry) => entry.entry_type === 'step' && entry.step)
      .map((entry) => `${entry.document_code}::${entry.step}`),
  )

  if (!stepKeys.size) return results

  return results.filter((entry) => {
    if (entry.entry_type !== 'anomaly' || !entry.step) return true
    return !stepKeys.has(`${entry.document_code}::${entry.step}`)
  })
}

function preferOperationalResults(results: ItIndexEntry[]): ItIndexEntry[] {
  const stepResults = results.filter((entry) => entry.entry_type === 'step')
  if (!stepResults.length) return results

  return [...stepResults]
    .sort((left, right) => {
      const stepDelta = Number(left.step ?? 0) - Number(right.step ?? 0)
      if (stepDelta !== 0) return stepDelta

      const pageDelta = Number(left.page ?? 0) - Number(right.page ?? 0)
      if (pageDelta !== 0) return pageDelta

      return Number(right.score ?? 0) - Number(left.score ?? 0)
    })
    .slice(0, IT_MAX_RESULTS)
}

function searchIt(index: ItIndex, query: string, documentCode: string, documentTitle = '', fileUrl = ''): ItIndexEntry[] {
  const keywords = extractKeywords(query)
  if (!keywords.length) return []

  const queryNorm = normalize(cleanItQuery(query))
  const stepHint = extractStepHint(query)
  const docMeta = getDocMetadata(index, documentCode, documentTitle, fileUrl)
  const wantedDoc = docMeta ? normalizeDocCode(docMeta.document_code) : normalizeDocCode(documentCode)
  const scored: ItIndexEntry[] = []

  for (const entry of index.entries) {
    if (wantedDoc && normalizeDocCode(entry.document_code ?? '') !== wantedDoc) continue
    const [score, matchedTerms, matchedInBody, matchedInWhat] = scoreItEntry(entry, keywords, queryNorm)
    let finalScore = score
    if (stepHint && entry.entry_type === 'step' && Number(entry.step ?? 0) === stepHint) finalScore += 30
    if (finalScore > 0) {
      scored.push({
        ...entry,
        score: finalScore,
        matched_terms: matchedTerms,
        matched_in_body: matchedInBody,
        matched_in_what: matchedInWhat,
        query_norm: queryNorm,
      })
    }
  }

  scored.sort((left, right) => {
    const scoreDelta = Number(right.score ?? 0) - Number(left.score ?? 0)
    if (scoreDelta !== 0) return scoreDelta
    return Number(left.page ?? 0) - Number(right.page ?? 0)
  })

  const results: ItIndexEntry[] = []
  const seen = new Set<string>()
  for (const entry of scored) {
    const key = [entry.document_code, entry.step ?? '', entry.section_number ?? '', entry.page ?? '', entry.entry_type ?? ''].join('::')
    if (seen.has(key)) continue
    seen.add(key)
    results.push(entry)
    if (results.length >= IT_MAX_RESULTS) break
  }

  let narrowed = narrowSpecificResults(results, keywords)
  if (wantedDoc) narrowed = narrowed.filter((entry) => isStrongQueryMatch(entry, keywords))
  narrowed = attachRelatedAnomalies(index, narrowed)
  narrowed = dropStandaloneAnomaliesWhenStepExists(narrowed)
  narrowed = preferOperationalResults(narrowed)
  return narrowed
}

function toBulletLines(text: string): string[] {
  const clean = String(text ?? '').replace(/\s+/g, ' ').trim()
  if (!clean) return []
  if (clean.includes('•')) {
    return clean.split('•').map((part) => part.trim().replace(/^[-;]+|[-;]+$/g, '')).filter(Boolean).map((part) => `- ${part}`)
  }
  const parts = clean.split(/\s+-\s+/).map((part) => part.trim().replace(/^[-;]+|[-;]+$/g, '')).filter(Boolean)
  if (parts.length > 1) return parts.map((part) => `- ${part}`)
  return [`- ${clean}`]
}

function normalizeStepBlocks(what: string, how: string, care: string): [string, string, string] {
  let nextWhat = String(what ?? '').trim()
  let nextHow = String(how ?? '').trim()
  let nextCare = String(care ?? '').trim()
  const careCues = ['no inicio', ...CARE_FIELD_CUES, 'importante', 'obrigatorio']
  const careLower = normalize(nextCare)

  let cuePos = -1
  for (const cue of careCues) {
    const position = careLower.indexOf(cue)
    if (position >= 0 && (cuePos < 0 || position < cuePos)) cuePos = position
  }

  if (cuePos > 0) {
    const prefix = nextCare.slice(0, cuePos).trim().replace(/^[-.;:]+|[-.;:]+$/g, '')
    const suffix = nextCare.slice(cuePos).trim().replace(/^[-.;:]+|[-.;:]+$/g, '')
    if (prefix) nextHow = `${nextHow} ${prefix}`.trim()
    nextCare = suffix
  }

  const howWords = tokenize(nextHow)
  const howTail = howWords.length ? howWords[howWords.length - 1] : ''
  if (['do', 'da', 'de', 'com', 'que', 'para', 'e', 'ou', 'no'].includes(howTail) && nextCare) {
    const parts = nextCare.split(/(?<=[.!?])\s+/, 2)
    const firstSentence = (parts[0] ?? '').trim().replace(/^[-.;:]+|[-.;:]+$/g, '')
    const rest = (parts[1] ?? '').trim().replace(/^[-.;:]+|[-.;:]+$/g, '')
    if (firstSentence && firstSentence.split(' ').length >= 3) {
      nextHow = `${nextHow} ${firstSentence}`.trim()
      nextCare = rest
    }
  }

  const splitSentences = (value: string) => value.split(/(?<=[.!?])\s+|\s*-\s+/).map((part) => part.trim().replace(/^[-.;:]+|[-.;:]+$/g, '')).filter(Boolean)
  const careScore = (sentence: string) => {
    const normalized = normalize(sentence)
    const cues = [...CARE_FIELD_CUES, 'solicitar auxilio', 'uniao rotativa']
    let score = 0
    for (const cue of cues) {
      if (normalized.includes(cue)) score += 2
    }
    if (normalized.includes('evitar')) score += 1
    if (normalized.includes('nao ')) score += 1
    return score
  }

  const howSentences = splitSentences(nextHow)
  if (howSentences.length >= 2) {
    let splitIndex = -1
    for (let index = howSentences.length - 1; index > 0; index -= 1) {
      if (careScore(howSentences[index]) >= 2) splitIndex = index
      else if (splitIndex > 0) break
    }
    if (splitIndex > 0) {
      const howHead = howSentences.slice(0, splitIndex).join(' - ').trim()
      const careTail = howSentences.slice(splitIndex).join(' - ').trim()
      if (howHead && careTail) {
        nextHow = howHead
        nextCare = nextCare ? `${nextCare} ${careTail}`.trim() : careTail
      }
    }
  }

  return [nextWhat, nextHow, nextCare]
}

function formatItResponse(results: ItIndexEntry[], query: string): string {
  const cleanedQuery = cleanItQuery(query)
  if (!extractKeywords(query).length) {
    return 'Nao consegui identificar o tema da IT que voce quer consultar.\n\nTente algo como: `Na IT, como operar o sistema de casca?`'
  }
  if (!results.length) {
    return `Nao encontrei trecho da IT relacionado a "${cleanedQuery}" nos PDFs carregados.\n\nTente informar a operacao, equipamento, linha ou numero do documento.`
  }

  const lines: string[] = [`Beleza, encontrei ${results.length} trechos relacionados a "${cleanedQuery}":`, '']

  results.forEach((match, index) => {
    lines.push(`Resultado ${index + 1}`)
    lines.push(`Documento: ${match.document_code ?? '-'}`)
    lines.push(`Titulo da operacao: ${match.document_title ?? '-'}`)
    lines.push(`Autor: ${match.author ?? '-'}`)
    lines.push(`Autorizador: ${match.authorizer ?? '-'}`)
    lines.push(`Data de criacao: ${match.create_date ?? '-'}`)
    lines.push(`Data de impressao: ${match.print_date ?? '-'}`)

    if (match.entry_type === 'section') lines.push(`Secao: ${match.section_number ?? '-'} . ${match.section_title ?? '-'}`)
    else if (match.entry_type === 'anomaly') {
      if (match.step) lines.push(`Passo relacionado: ${match.step}`)
      lines.push(`Anomalia: ${match.anomaly_title ?? match.what ?? '-'}`)
    } else if (match.step) lines.push(`Passo: ${match.step}`)
    lines.push('')

    const [what, how, care] = normalizeStepBlocks(match.what ?? '', match.how ?? '', match.care ?? '')
    const safeWhat = what || 'Nao identificado no trecho.'
    const safeHow = how || 'Nao identificado no trecho.'
    const safeCare = care || 'Nao identificado no trecho.'

    if (match.entry_type === 'section') {
      lines.push('Conteudo')
      lines.push(...toBulletLines(safeHow))
      lines.push('')
    } else if (match.entry_type === 'anomaly') {
      lines.push('Possiveis causas')
      lines.push(...toBulletLines(match.possible_causes?.trim() || 'Nao identificado no trecho.'))
      lines.push('')
      lines.push('Acao')
      lines.push(...toBulletLines(match.action_text?.trim() || 'Nao identificado no trecho.'))
      lines.push('')
      lines.push('Cuidados especiais')
      lines.push(...toBulletLines(safeCare))
      lines.push('')
    } else {
      lines.push('O que fazer')
      lines.push(...toBulletLines(safeWhat))
      lines.push('')
      lines.push('Como fazer')
      lines.push(...toBulletLines(safeHow))
      lines.push('')
      lines.push('Cuidados especiais')
      lines.push(...toBulletLines(safeCare))
      lines.push('')
      if (match.related_anomalies?.length) {
        lines.push('Anomalias relacionadas')
        for (const anomaly of match.related_anomalies) {
          lines.push(`- ${anomaly.anomaly_title ?? '-'}`)
          if (anomaly.possible_causes) lines.push(`  Possiveis causas: ${anomaly.possible_causes}`)
          if (anomaly.action_text) lines.push(`  Acao: ${anomaly.action_text}`)
          if (anomaly.care) lines.push(`  Cuidados: ${anomaly.care}`)
        }
        lines.push('')
      }
    }

    lines.push(`Fonte: ${match.document_code ?? '-'}, pagina ${match.page ?? '-'}`)
    lines.push('')
  })

  return lines.join('\n').trim()
}

export const localAssistantService = {
  async ask(payload: LocalAssistantRequest): Promise<AssistantResponse> {
    const index = await loadItIndex()
    const documentCode = String(payload.documentCode ?? '').trim()
    const itId = String(payload.itId ?? '').trim()
    if (!documentCode) throw new Error('Nao foi possivel identificar a IT selecionada para consulta.')
    if (!itId) throw new Error('Nao foi possivel identificar a IT selecionada para abrir o documento.')

    const docMeta = getDocMetadata(index, documentCode, payload.documentTitle, payload.fileUrl)
    const resolvedDocCode = docMeta?.document_code ?? documentCode
    const results = searchIt(index, payload.message, resolvedDocCode, payload.documentTitle, payload.fileUrl)
    const hasResults = results.length > 0
    const fileUrl = hasResults ? buildProtectedItFileUrl(itId) : undefined

    return {
      message: formatItResponse(results, payload.message),
      sourceType: 'it_local',
      documento: hasResults ? resolvedDocCode : undefined,
      titulo: hasResults ? (docMeta?.document_title ?? payload.documentTitle ?? resolvedDocCode) : undefined,
      revisao: '',
      downloadUrl: hasResults ? fileUrl : undefined,
      previewUrl: hasResults ? fileUrl : undefined,
      warnings: [],
      metadata: {
        mode: 'local_it_index',
        results: results.length,
      },
    }
  },
}






