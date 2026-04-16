import { useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'

import { useAuth } from '../../hooks/useAuth'
import { useIts } from '../../hooks/useIts'
import { assistantService } from '../../services/assistant/assistantService'
import { openProtectedItFile } from '../../services/http/apiClient'
import type { AssistantContextResponse, AssistantMessage } from '../../types/assistant'
import { getItDisplayCode, getItDisplayTitle } from '../../types/it'

type ItConversationMap = Record<string, AssistantMessage[]>

export function AssistantPage() {
  const { user } = useAuth()
  const itsQuery = useIts()
  const [selectedItId, setSelectedItId] = useState('')
  const [message, setMessage] = useState('')
  const [conversationsByItId, setConversationsByItId] = useState<ItConversationMap>({})
  const [activeContext, setActiveContext] = useState<AssistantContextResponse | null>(null)
  const [isContextLoading, setIsContextLoading] = useState(false)
  const [isSending, setIsSending] = useState(false)

  const activeSector = String(user?.setorAtivo ?? '').trim().toLowerCase()

  const itOptions = useMemo(
    () =>
      (itsQuery.data ?? [])
        .filter((it) => {
          const itSector = String(it.setor ?? '').trim().toLowerCase()
          if (!activeSector || !itSector) return true
          return itSector === activeSector
        })
        .map((it) => ({
          id: it.id,
          code: getItDisplayCode(it),
          title: getItDisplayTitle(it),
          setor: it.setor,
          label: `${getItDisplayTitle(it)} (${getItDisplayCode(it)})`,
        })),
    [activeSector, itsQuery.data],
  )

  const effectiveItId = selectedItId || itOptions[0]?.id || ''
  const selectedIt = itOptions.find((option) => option.id === effectiveItId)
  const messages = conversationsByItId[effectiveItId] ?? []

  useEffect(() => {
    async function loadContext() {
      if (!effectiveItId) {
        setActiveContext(null)
        return
      }

      setIsContextLoading(true)
      try {
        const context = await assistantService.getContext(effectiveItId, String(selectedIt?.setor ?? user?.setorAtivo ?? '').trim())
        setActiveContext(context)
      } catch {
        setActiveContext(null)
      } finally {
        setIsContextLoading(false)
      }
    }

    void loadContext()
  }, [effectiveItId, selectedIt?.setor, user?.setorAtivo])

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    const trimmedMessage = message.trim()
    const trimmedItId = effectiveItId.trim()
    if (!trimmedMessage || isSending) return

    const timestamp = new Date().toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })
    const userMessage: AssistantMessage = { role: 'user', content: trimmedMessage, timestamp }

    if (!trimmedItId) {
      setConversationsByItId((current) => ({
        ...current,
        [trimmedItId]: [...(current[trimmedItId] ?? []), userMessage, { role: 'assistant', content: 'Selecione uma IT antes de enviar a pergunta.', timestamp }],
      }))
      setMessage('')
      return
    }

    setConversationsByItId((current) => ({ ...current, [trimmedItId]: [...(current[trimmedItId] ?? []), userMessage] }))
    setIsSending(true)

    try {
      const history = (conversationsByItId[trimmedItId] ?? []).slice(-4).map((item) => ({ role: item.role, content: item.content }))
      const response = await assistantService.ask({
        itId: trimmedItId,
        message: trimmedMessage,
        documentCode: selectedIt?.code,
        documentTitle: selectedIt?.title,
        setorAtivo: String(selectedIt?.setor ?? user?.setorAtivo ?? '').trim(),
        history,
      })

      setConversationsByItId((current) => ({
        ...current,
        [trimmedItId]: [...(current[trimmedItId] ?? []), { role: 'assistant', content: response.message || 'Sem resposta.', timestamp: new Date().toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' }), itActionDoc: response.documento, docDownloadUrl: response.downloadUrl, docFileName: response.titulo, evidence: response.evidence }],
      }))
    } catch {
      setConversationsByItId((current) => ({
        ...current,
        [trimmedItId]: [...(current[trimmedItId] ?? []), { role: 'assistant', content: 'Nao foi possivel concluir a consulta agora.', timestamp: new Date().toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' }) }],
      }))
    } finally {
      setIsSending(false)
      setMessage('')
    }
  }

  function handleClearConversation() {
    if (!effectiveItId) return
    setConversationsByItId((current) => ({ ...current, [effectiveItId]: [] }))
  }

  function handleItChange(nextItId: string) {
    setSelectedItId(nextItId)
    setMessage('')
    setConversationsByItId(nextItId ? { [nextItId]: [] } : {})
  }

  return (
    <section className="page-section assistant-streamlit-page">
      <div className="assistant-header-card">
        <div className="assistant-avatar">IA</div>
        <div>
          <div className="assistant-title">Assistente de ITs</div>
          <div className="assistant-online">Online</div>
        </div>
      </div>

      <div className="assistant-panel-card">
        <label className="assistant-select-label">
          Selecione a IT para consultar no chat
          <select value={effectiveItId} onChange={(event) => handleItChange(event.target.value)}>
            {itOptions.length === 0 ? <option value="">Nenhuma IT disponivel</option> : null}
            {itOptions.map((option) => (
              <option key={option.id} value={option.id}>{option.label}</option>
            ))}
          </select>
        </label>

        {selectedIt ? (
          <>
            <div className="assistant-doc-row">
              <button type="button" className="outline-button dashboard-pill-button">Conversa ativa nesta IT</button>
              <button type="button" className="outline-button dashboard-pill-button" onClick={() => { void openProtectedItFile(selectedIt.id) }}>Visualizar IT</button>
            </div>

            <div className="panel-block streamlit-card compact-block" style={{ marginBottom: 16 }}>
              <div className="panel-title">Contexto ativo da conversa</div>
              {isContextLoading ? <p className="helper-text">Carregando contexto da IT...</p> : null}
              {!isContextLoading && activeContext ? (
                <>
                  <div className="helper-text"><strong>{activeContext.titulo || selectedIt.title}</strong>{activeContext.documento ? ` (${activeContext.documento})` : ''}{activeContext.revisao ? ` • Revisao ${activeContext.revisao}` : ''}</div>
                  <div className="helper-text">{activeContext.stepCount ?? 0} passos estruturados • {activeContext.anomalyCount ?? 0} anomalias indexadas</div>
                  {(activeContext.sampleQuestions ?? []).length > 0 ? <div className="helper-text" style={{ marginTop: 8 }}>Exemplos: {(activeContext.sampleQuestions ?? []).join(' • ')}</div> : null}
                </>
              ) : null}
            </div>
          </>
        ) : null}

        <div className="assistant-messages-box">
          {messages.length === 0 ? (
            <div className="assistant-bubble-group bot">
              <div className="assistant-bubble bot">
                Ola! Sou o assistente tecnico.
                <br />
                Ao trocar de IT, a conversa reinicia para evitar contexto misturado.
                <br />
                <br />
                <strong>Qual informacao voce precisa nesta IT?</strong>
              </div>
              <div className="assistant-meta">Assistente agora</div>
            </div>
          ) : null}

          {messages.map((item, index) => (
            <div key={`${item.role}-${index}-${item.timestamp ?? ''}`} className={`assistant-bubble-group ${item.role === 'user' ? 'user' : 'bot'}`}>
              <div className={`assistant-bubble ${item.role === 'user' ? 'user' : 'bot'}`}>{item.content}</div>
              <div className="assistant-meta">{item.role === 'user' ? 'Voce' : 'Assistente'} {item.timestamp ?? ''}</div>
            </div>
          ))}
        </div>

        <form className="assistant-form" onSubmit={handleSubmit}>
          <input value={message} onChange={(event) => setMessage(event.target.value)} placeholder="Digite sua mensagem sobre a IT selecionada..." />
          <button type="submit" disabled={isSending || isContextLoading}>{isSending ? 'Enviando...' : 'Enviar'}</button>
        </form>

        <div className="assistant-footer-row">
          <div className="assistant-footnote">O assistente responde sempre dentro da IT ativa e reinicia a conversa quando voce troca de documento.</div>
          <button type="button" className="outline-button small" onClick={handleClearConversation}>Limpar conversa</button>
        </div>

        {itsQuery.isLoading ? <p className="helper-text">Carregando lista de ITs...</p> : null}
        {!itsQuery.isLoading && itOptions.length === 0 ? <p className="helper-text">Nenhuma IT disponivel para o setor atual.</p> : null}
      </div>
    </section>
  )
}

