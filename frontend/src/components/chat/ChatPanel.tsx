import { Fragment, useEffect, useMemo, useRef, useState } from 'react'
import type { CSSProperties, FormEvent, MouseEvent, ReactNode, WheelEvent } from 'react'

import { useChat } from '../../hooks/useChat'
import { useAuth } from '../../hooks/useAuth'
import { useIts } from '../../hooks/useIts'
import { assistantService } from '../../services/assistant/assistantService'
import { canonicalAssistantOptionsService } from '../../services/assistant/canonicalAssistantOptionsService'
import { downloadProtectedItFile, getApiBaseUrl, openProtectedItFile } from '../../services/http/apiClient'
import type { AssistantOptionItem } from '../../types/assistant'
import { getFirstName } from '../../types/auth'
import { getItDisplayCode, getItDisplayTitle } from '../../types/it'

interface ChatPanelProps {
  collapsed?: boolean
  width?: number
  onToggleCollapse?: () => void
  onStartVerticalResize?: (event: MouseEvent<HTMLDivElement>) => void
  onWidthChange?: (value: number) => void
  variant?: 'sidebar' | 'stage' | 'floating'
}

interface ChatItOption {
  id: string
  label: string
  title: string
  code: string
  setor?: string
}

interface ParsedAssistantSection {
  title: string
  lines: string[]
}

interface ParsedAssistantDocument {
  lines: string[]
}

const CHAT_MESSAGE_SCALE_KEY = 'pts.chatMessageScale'
const CHAT_MESSAGE_SCALE_MIN = 0.9
const CHAT_MESSAGE_SCALE_MAX = 1.18
const CHAT_MESSAGE_SCALE_STEP = 0.08

function clampChatMessageScale(value: number): number {
  return Math.min(CHAT_MESSAGE_SCALE_MAX, Math.max(CHAT_MESSAGE_SCALE_MIN, Number(value.toFixed(2))))
}

function loadStoredChatMessageScale(): number {
  const raw = Number(window.localStorage.getItem(CHAT_MESSAGE_SCALE_KEY))
  return Number.isFinite(raw) ? clampChatMessageScale(raw) : 1
}

function AssistantGlyph() {
  return (
    <svg viewBox="0 0 32 32" aria-hidden="true" focusable="false" className="chat-panel-bot-icon">
      <defs>
        <linearGradient id="assistantGlyphGradient" x1="6" y1="4" x2="26" y2="28" gradientUnits="userSpaceOnUse">
          <stop stopColor="#7dd3fc" />
          <stop offset="1" stopColor="#60a5fa" />
        </linearGradient>
      </defs>
      <rect x="7" y="9" width="18" height="14" rx="6" fill="url(#assistantGlyphGradient)" />
      <path d="M12 23.5h8" stroke="#dbeafe" strokeWidth="1.8" strokeLinecap="round" />
      <path d="M16 7V4.5" stroke="#93c5fd" strokeWidth="1.8" strokeLinecap="round" />
      <circle cx="16" cy="4" r="1.6" fill="#bfdbfe" />
      <circle cx="13" cy="15" r="1.5" fill="#0f172a" />
      <circle cx="19" cy="15" r="1.5" fill="#0f172a" />
      <path d="M13 19c.8.9 1.8 1.3 3 1.3s2.2-.4 3-1.3" stroke="#0f172a" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" fill="none" />
    </svg>
  )
}

function resolveFileUrl(url: string | undefined): string {
  if (!url) return ''
  if (url.startsWith('http')) return url
  if (url.startsWith('/docs/')) return url
  return `${getApiBaseUrl()}${url}`
}

async function openDocument(url: string, fileName: string): Promise<void> {
  if (url.startsWith('/docs/')) {
    window.open(url, '_blank', 'noopener,noreferrer')
    return
  }

  void fileName
  await openProtectedItFile(url)
}

async function downloadDocument(url: string, fileName: string): Promise<void> {
  if (url.startsWith('/docs/')) {
    const link = document.createElement('a')
    link.href = url
    link.download = fileName
    document.body.appendChild(link)
    link.click()
    link.remove()
    return
  }

  await downloadProtectedItFile(url, fileName)
}

function parseAssistantContent(content: string): { document: ParsedAssistantDocument | null; sections: ParsedAssistantSection[] } | null {
  const normalized = String(content ?? '').trim()
  if (!normalized.includes('**') && !normalized.includes('- ') && !normalized.includes('* ')) return null

  const lines = normalized.split('\n').map((line) => line.trim())
  const sections: ParsedAssistantSection[] = []
  let current: ParsedAssistantSection | null = null
  const documentLines: string[] = []
  let isDocument = false

  for (const line of lines) {
    if (!line) continue

    const headingMatch = line.match(/^\*\*(.+?)\*\*(?::\s*(.*))?$/)
    if (headingMatch) {
      const title = headingMatch[1].trim()
      const inlineContent = headingMatch[2]?.trim()
      if (title.toLowerCase() === 'documento') {
        current = null
        isDocument = true
        if (inlineContent) documentLines.push(inlineContent)
        continue
      }

      isDocument = false
      current = { title, lines: inlineContent ? [inlineContent] : [] }
      sections.push(current)
      continue
    }

    if (isDocument) {
      documentLines.push(line)
      continue
    }

    if (current) {
      current.lines.push(line)
    }
  }

  if (!sections.length && !documentLines.length) return null
  return {
    document: documentLines.length ? { lines: documentLines } : null,
    sections,
  }
}

function renderAssistantLine(line: string): ReactNode {
  if (line.startsWith('- ') || line.startsWith('* ')) {
    return <li>{line.slice(2).trim()}</li>
  }

  const [label, ...rest] = line.split(':')
  if (rest.length > 0 && label.length < 28) {
    return (
      <div className="chat-doc-meta-line">
        <span>{label.trim()}</span>
        <strong>{rest.join(':').trim()}</strong>
      </div>
    )
  }

  return <p>{line}</p>
}

function renderAssistantContent(content: string): ReactNode {
  const parsed = parseAssistantContent(content)
  if (!parsed) {
    return content.split('\n').map((line, lineIndex, allLines) => (
      <span key={lineIndex}>
        {line}
        {lineIndex < allLines.length - 1 ? <br /> : null}
      </span>
    ))
  }

  return (
    <div className="chat-assistant-rich">
      {parsed.document ? (
        <section className="chat-assistant-doc-card">
          <div className="chat-assistant-doc-eyebrow">Documento Base</div>
          <div className="chat-assistant-doc-grid">
            {parsed.document.lines.map((line, index) => (
              <Fragment key={`${line}-${index}`}>{renderAssistantLine(line)}</Fragment>
            ))}
          </div>
        </section>
      ) : null}

      {parsed.sections.map((section, index) => {
        const bulletLines = section.lines.filter((line) => line.startsWith('- ') || line.startsWith('* '))
        const plainLines = section.lines.filter((line) => !line.startsWith('- ') && !line.startsWith('* '))
        return (
          <section key={`${section.title}-${index}`} className="chat-assistant-section-card">
            <div className="chat-assistant-section-title">{section.title}</div>
            {plainLines.map((line, lineIndex) => (
              <Fragment key={`${section.title}-plain-${lineIndex}`}>{renderAssistantLine(line)}</Fragment>
            ))}
            {bulletLines.length > 0 ? (
              <ul className="chat-assistant-list">
                {bulletLines.map((line, lineIndex) => (
                  <Fragment key={`${section.title}-bullet-${lineIndex}`}>{renderAssistantLine(line)}</Fragment>
                ))}
              </ul>
            ) : null}
          </section>
        )
      })}
    </div>
  )
}

function resolveAssistantSourceLabel(sourceType?: string, metadata?: Record<string, unknown>): string | null {
  if (sourceType === 'pts_direct_lookup' || metadata?.source === 'pts_system') {
    return 'Consulta direta do sistema'
  }

  if (sourceType === 'assistant_cache' || metadata?.cacheHit === true) {
    return 'Consumindo do banco de dados'
  }

  if (sourceType === 'it_grounded_fast' || sourceType === 'it_structured' || sourceType === 'openrouter_it' || sourceType === 'gemini_it') {
    return 'Consulta nova na IT'
  }

  return null
}
function buildSectorScopedItOptions(rawIts: NonNullable<ReturnType<typeof useIts>['data']>, setorAtivo: string): ChatItOption[] {
  const normalizedSetorAtivo = String(setorAtivo ?? '').trim().toLowerCase()
  const seen = new Set<string>()

  return (rawIts ?? []).flatMap((it) => {
    const normalizedItSetor = String(it.setor ?? '').trim().toLowerCase()

    if (normalizedSetorAtivo && normalizedItSetor && normalizedItSetor !== normalizedSetorAtivo) {
      return []
    }

    const title = getItDisplayTitle(it)
    const code = getItDisplayCode(it)
    const key = `${title}::${code}`.trim().toLowerCase()

    if (!key || seen.has(key)) return []
    seen.add(key)

    return [
      {
        id: it.id,
        label: `${title} (${code})`,
        title,
        code,
        setor: it.setor,
      },
    ]
  })
}

export function ChatPanel({
  collapsed = false,
  width = 380,
  onToggleCollapse,
  onStartVerticalResize,
  onWidthChange,
  variant = 'sidebar',
}: ChatPanelProps) {
  const isStageVariant = variant === 'stage'
  const isFloatingVariant = variant === 'floating'
  const { user } = useAuth()
  const {
    messages,
    selectedItId,
    addMessage,
    clearMessages,
    addActivity,
    trackItAccess,
    setSelectedItId,
    incrementInteraction,
  } = useChat()

  const itsQuery = useIts()
  const [inputValue, setInputValue] = useState('')
  const [isSending, setIsSending] = useState(false)
  const [guidedOptions, setGuidedOptions] = useState<AssistantOptionItem[]>([])
  const [isLoadingOptions, setIsLoadingOptions] = useState(false)
  const [guidedMode, setGuidedMode] = useState<'initial' | 'continue'>('initial')
  const [guidedPickerVisible, setGuidedPickerVisible] = useState(true)
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const [messageScale, setMessageScale] = useState<number>(() => loadStoredChatMessageScale())

  const activeSector = String(user?.setorAtivo ?? '').trim()
  const itOptions = buildSectorScopedItOptions(itsQuery.data ?? [], activeSector)
  const effectiveItId = selectedItId || ''
  const selectedIt = itOptions.find((option) => option.id === effectiveItId)

  useEffect(() => {
    window.localStorage.setItem(CHAT_MESSAGE_SCALE_KEY, String(messageScale))
  }, [messageScale])

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  useEffect(() => {
    setGuidedMode('initial')
    setGuidedPickerVisible(true)
  }, [effectiveItId])

  useEffect(() => {
    if (!effectiveItId) {
      setGuidedOptions([])
      return
    }

    let active = true
    setIsLoadingOptions(true)
    Promise.all([
      assistantService.getOptions(effectiveItId, String(selectedIt?.setor ?? activeSector).trim()).catch(() => ({ opcoes: [] })),
      canonicalAssistantOptionsService.getOptions(selectedIt?.code, selectedIt?.title),
    ])
      .then(([response, canonicalOptions]) => {
        if (!active) return

        const backendOptions = (response.opcoes ?? []).map((option) => ({ ...option, origin: 'backend' as const }))
        setGuidedOptions(backendOptions.length > 0 ? backendOptions : canonicalOptions)
      })
      .catch(() => {
        if (!active) return
        setGuidedOptions([])
      })
      .finally(() => {
        if (active) setIsLoadingOptions(false)
      })

    return () => {
      active = false
    }
  }, [effectiveItId, selectedIt?.setor, activeSector])

  async function sendAssistantQuery(text: string, option?: AssistantOptionItem) {
    const timestamp = new Date().toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })

    if (!effectiveItId) {
      addMessage({ role: 'assistant', content: 'Selecione uma IT antes de enviar a pergunta.', timestamp })
      return
    }

    incrementInteraction()
    addActivity({ icone: 'Chat', descricao: 'Nova mensagem no assistente.', data: 'Hoje' })
    addMessage({ role: 'user', content: text, timestamp })
    setInputValue('')
    setIsSending(true)
    setGuidedMode('continue')
    setGuidedPickerVisible(false)

    try {
      const response = await assistantService.ask({
        itId: effectiveItId,
        message: text,
        documentCode: selectedIt?.code,
        documentTitle: selectedIt?.title,
        setorAtivo: String(selectedIt?.setor ?? activeSector).trim(),
        selectedStep: option?.passo ?? null,
        selectedPage: option?.pagina ?? null,
        selectedOptionTitle: option?.titulo,
        history: messages
          .slice(-6)
          .filter((message) => !!message.content?.trim())
          .map((message) => ({ role: message.role, content: message.content })),
      })

      addMessage({
        role: 'assistant',
        content: response.message || 'Sem resposta.',
        timestamp: new Date().toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' }),
        itActionDoc: response.documento,
        docDownloadUrl: resolveFileUrl(response.downloadUrl),
        docFileName: response.titulo,
        sourceType: response.sourceType,
        metadata: response.metadata,
      })

      if (response.documento) {
        trackItAccess(response.documento, `Voce consultou a IT ${response.documento}.`)
      }
    } catch {
      addMessage({
        role: 'assistant',
        content: 'Nao foi possivel processar a consulta da IT selecionada.',
        timestamp: new Date().toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' }),
      })
    } finally {
      setIsSending(false)
    }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    const text = inputValue.trim()
    if (!text || isSending) return

    setInputValue('')
    await sendAssistantQuery(text)
  }

  function updateMessageScale(nextValue: number) {
    setMessageScale(clampChatMessageScale(nextValue))
  }

  function handleMessageZoom(direction: 'in' | 'out') {
    updateMessageScale(messageScale + (direction === 'in' ? CHAT_MESSAGE_SCALE_STEP : -CHAT_MESSAGE_SCALE_STEP))
  }

  function handleMessagesWheel(event: WheelEvent<HTMLDivElement>) {
    if (!(event.ctrlKey || event.metaKey)) {
      return
    }
    event.preventDefault()
    handleMessageZoom(event.deltaY < 0 ? 'in' : 'out')
  }

  function handleItChange(nextItId: string) {
    if (nextItId === effectiveItId) return
    clearMessages()
    setInputValue('')
    setGuidedMode('initial')
    setGuidedPickerVisible(true)
    setSelectedItId(nextItId)
  }

  const latestDoc = [...messages].reverse().find((message) => message.itActionDoc || message.docDownloadUrl)
  const latestFileName = `${latestDoc?.docFileName ?? latestDoc?.itActionDoc ?? 'Documento IT'}.pdf`
  const typingTimestamp = useMemo(() => new Date().toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' }), [isSending])
  const firstName = useMemo(() => getFirstName(user), [user])
  const initialAssistantGreeting = useMemo(() => {
    if (effectiveItId && selectedIt) {
      return `Ola, ${firstName}. Estou pronto para te ajudar com a IT "${selectedIt.title}".`
    }

    return `Ola, ${firstName}. Selecione uma IT para eu consultar com voce.`
  }, [effectiveItId, firstName, selectedIt])
  const initialAssistantHint = useMemo(() => {
    if (effectiveItId && selectedIt) {
      return user?.role === 'ADMIN'
        ? 'Voce pode escolher um passo acima ou mandar uma pergunta direta sobre essa IT. Se precisar de gestao, usuarios e treinamentos seguem no painel administrativo.'
        : 'Voce pode escolher um passo acima ou mandar uma pergunta direta sobre essa IT.'
    }

    return 'Assim que voce selecionar a IT correta, eu sigo com a consulta dentro do contexto dela.'
  }, [effectiveItId, selectedIt, user?.role])
  const hasGuidedOptions = Boolean(effectiveItId) && !isLoadingOptions && guidedOptions.length > 0
  const shouldRenderStepDock = Boolean(effectiveItId)
  const showGuidedSelector = hasGuidedOptions && guidedPickerVisible
  const guidedHeading = guidedMode === 'initial' ? 'Passos disponiveis nesta IT' : 'Consultar outro passo'
  const chatScaleStyle = useMemo(() => ({ ['--chat-message-scale' as any]: String(messageScale) } as CSSProperties), [messageScale])

  return (
    <div className={`chat-panel-root ${collapsed ? 'is-collapsed' : ''} ${isStageVariant ? 'is-stage' : ''} ${isFloatingVariant ? 'is-floating' : ''}`} style={chatScaleStyle}>
      <div className="chat-panel-header">
        {isFloatingVariant && onStartVerticalResize ? (
          <div className="chat-panel-resize-grip" onMouseDown={onStartVerticalResize} role="presentation" aria-hidden="true">
            <span />
          </div>
        ) : null}
        <div className="chat-panel-title-row">
          <div className="chat-panel-bot-avatar" aria-hidden="true"><AssistantGlyph /></div>
          <div className="chat-panel-header-copy">
            <div className="chat-panel-title">Assistente de ITs</div>
            <div className="chat-panel-status">
              <span className="chat-dot" />
              Online
            </div>
          </div>
          <div className="chat-panel-header-actions">
            {!isStageVariant && !isFloatingVariant && onWidthChange ? (
              <label className="chat-panel-size-control" title="Tamanho do painel">
                <span>Tamanho</span>
                <input type="range" min={320} max={620} step={20} value={width} onChange={(event) => onWidthChange(Number(event.target.value))} />
              </label>
            ) : null}
            <div className="chat-panel-zoom-control" aria-label="Ajustar leitura das mensagens">
              <button
                type="button"
                className="chat-panel-zoom-button"
                onClick={() => handleMessageZoom('out')}
                disabled={messageScale <= CHAT_MESSAGE_SCALE_MIN}
                title="Diminuir leitura"
                aria-label="Diminuir leitura das mensagens"
              >
                A-
              </button>
              <span className="chat-panel-zoom-indicator">{Math.round(messageScale * 100)}%</span>
              <button
                type="button"
                className="chat-panel-zoom-button"
                onClick={() => handleMessageZoom('in')}
                disabled={messageScale >= CHAT_MESSAGE_SCALE_MAX}
                title="Aumentar leitura"
                aria-label="Aumentar leitura das mensagens"
              >
                A+
              </button>
            </div>
            {!isStageVariant && onToggleCollapse ? (
              <button type="button" className="outline-button small chat-toggle-button chat-action-button" onClick={onToggleCollapse}>
                {isFloatingVariant ? 'Fechar' : collapsed ? 'Abrir' : 'Fechar'}
              </button>
            ) : null}
          </div>
        </div>
      </div>

      {itOptions.length > 0 ? (
        <div className="chat-panel-select-row">
          <label className="chat-panel-select-label">
            Selecione a IT para consultar
            <select value={effectiveItId} onChange={(event) => handleItChange(event.target.value)} className="chat-panel-select">
              <option value="">Selecione a IT que voce quer trabalhar</option>
              {itOptions.map((option) => (
                <option key={option.id} value={option.id}>
                  {option.label}
                </option>
              ))}
            </select>
          </label>
</div>
      ) : null}

      {itsQuery.isLoading ? <div className="chat-panel-helper">Carregando ITs...</div> : null}
      {!itsQuery.isLoading && itOptions.length === 0 ? <div className="chat-panel-helper">Nenhuma IT disponivel para o setor atual.</div> : null}

      {shouldRenderStepDock ? (
        <div className={`chat-step-dock ${guidedPickerVisible ? 'is-open' : ''} ${isStageVariant ? 'is-stage' : ''}`}>
          <div className="chat-step-dock-header">
            <div className="chat-step-dock-copy">
              <span className="chat-step-dock-eyebrow">Consulta guiada</span>
              <strong>{isLoadingOptions ? 'Carregando os passos desta IT' : hasGuidedOptions ? guidedHeading : 'Nenhum passo estruturado disponivel nesta IT'}</strong>
            </div>
            {hasGuidedOptions ? (
              <button type="button" className="outline-button small chat-step-dock-toggle chat-action-button" onClick={() => setGuidedPickerVisible((current) => !current)}>
                {guidedPickerVisible ? 'Ocultar passos' : `Ver passos (${guidedOptions.length})`}
              </button>
            ) : null}
          </div>

          {isLoadingOptions ? <div className="chat-step-dock-empty">Lendo a estrutura da IT selecionada...</div> : null}
          {!isLoadingOptions && !hasGuidedOptions ? (
            <div className="chat-step-dock-empty">Nao encontrei passos estruturados para esta IT. Voce ainda pode enviar uma pergunta livre abaixo.</div>
          ) : null}

          {showGuidedSelector ? (
            <div className="chat-step-dock-list">
              {guidedOptions.map((option, index) => {
                const optionLabel = option.passo != null
                  ? `Passo ${option.passo}: ${option.titulo}`
                  : option.titulo

                return (
                  <button
                    key={`${option.passo ?? 'x'}-${option.pagina ?? 'x'}-${index}`}
                    type="button"
                    className="chat-step-dock-option"
                    disabled={isSending}
                    onClick={() => { void sendAssistantQuery(optionLabel, option) }}
                  >
                    <span className="chat-step-dock-badge">Passo {option.passo ?? '-'}</span>
                    <strong>{option.titulo}</strong>
                    <small>Pagina {option.pagina ?? '-'}</small>
                  </button>
                )
              })}
            </div>
          ) : null}
        </div>
      ) : null}
      <div className="chat-panel-messages" onWheel={handleMessagesWheel}>
        {messages.length === 0 ? (
          <div className="chat-bubble-group bot initial">
            <div className="chat-bubble bot chat-initial-bubble">
              <strong className="chat-initial-title">{initialAssistantGreeting}</strong>
              <p className="chat-initial-text">{initialAssistantHint}</p>
            </div>
            <div className="chat-meta-row">
              <div className="chat-meta">Assistente</div>
              <span className="chat-source-badge">Assistente do sistema</span>
            </div>
          </div>
        ) : null}
        {messages.map((message, index) => {
          const hasStructuredContent = message.role === 'assistant' && Boolean(parseAssistantContent(message.content))
          const sourceLabel = message.role === 'assistant' ? resolveAssistantSourceLabel(message.sourceType, message.metadata) : null
          return (
            <div key={`${message.role}-${index}-${message.timestamp ?? ''}`} className={`chat-bubble-group ${message.role === 'user' ? 'user' : 'bot'}`}>
              <div className={`chat-bubble ${message.role === 'user' ? 'user' : 'bot'} ${hasStructuredContent ? 'has-structured-content' : ''}`}>
                {message.role === 'assistant' ? renderAssistantContent(message.content) : message.content}
              </div>
              <div className="chat-meta-row">
                <div className="chat-meta">{message.role === 'user' ? 'Voce' : 'Assistente'}{message.timestamp ? ` ${message.timestamp}` : ''}</div>
                {sourceLabel ? <span className="chat-source-badge">{sourceLabel}</span> : null}
              </div>
            </div>
          )
        })}
        {isSending ? (
          <div className="chat-bubble-group bot typing">
            <div className="chat-bubble bot chat-typing-bubble" aria-live="polite" aria-label="Assistente digitando">
              <span className="chat-typing-label">Assistente esta digitando</span>
              <span className="chat-typing-dots" aria-hidden="true">
                <span />
                <span />
                <span />
              </span>
            </div>
            <div className="chat-meta-row">
              <div className="chat-meta">Assistente {typingTimestamp}</div>
            </div>
          </div>
        ) : null}
        <div ref={messagesEndRef} />
      </div>



      <form className="chat-panel-form" onSubmit={handleSubmit}>
        <div className="chat-panel-composer-shell">
          <div className="chat-panel-composer-field">
            <span className="chat-panel-composer-label">Mensagem</span>
            <input value={inputValue} onChange={(event) => setInputValue(event.target.value)} placeholder={effectiveItId ? 'Digite sua mensagem sobre a IT selecionada...' : 'Selecione uma IT para comecar'} disabled={isSending || !effectiveItId} />
          </div>
          <button type="submit" className="chat-panel-composer-submit" disabled={isSending || !inputValue.trim() || !effectiveItId}>{isSending ? 'Enviando...' : 'Enviar'}</button>
        </div>
        <div className="chat-panel-composer-tip">{effectiveItId ? 'Dica: pergunte de forma direta. Ex.: como fazer, o que monitorar, quais cuidados, o que fazer em caso de falha. Use Ctrl + scroll aqui nas mensagens para ajustar a leitura.' : 'Selecione uma IT para habilitar a conversa.'}</div>
      </form>

      <div className="chat-panel-footer-row">
        <div className="chat-panel-footer-actions">
          {latestDoc?.docDownloadUrl ? (
            <>
              <button type="button" className="outline-button small chat-action-button" onClick={() => { void openDocument(latestDoc.docDownloadUrl!, latestFileName) }}>Visualizar PDF</button>
              <button type="button" className="outline-button small chat-action-button" onClick={() => { void downloadDocument(latestDoc.docDownloadUrl!, latestFileName) }}>Baixar IT</button>
            </>
          ) : null}
          <button type="button" className="outline-button small chat-action-button" onClick={clearMessages}>Limpar conversa</button>
        </div>
      </div>
    </div>
  )
}






