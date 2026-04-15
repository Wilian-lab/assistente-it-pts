import { useMemo, useState } from 'react'

import { useChat } from '../../hooks/useChat'
import { useIts } from '../../hooks/useIts'
import { ApiClientError, openProtectedItFile } from '../../services/http/apiClient'
import { getItDisplayCode, getItDisplayTitle, normalizeItStatus } from '../../types/it'
import { openAssistantPanel } from '../../utils/assistantPanel'

const STATUS_OPTIONS = ['Todas', 'Atualizada', 'Pendente', 'Cópia não controlada'] as const

export function MyItsPage() {
  const itsQuery = useIts()
  const { setSelectedItId, trackItAccess } = useChat()
  const [status, setStatus] = useState<(typeof STATUS_OPTIONS)[number]>('Todas')

  const filteredIts = useMemo(() => {
    const list = itsQuery.data ?? []
    return list.filter((it) => {
      const normalizedStatus = normalizeItStatus(it.status).label
      return status === 'Todas' || normalizedStatus === status
    })
  }, [itsQuery.data, status])

  const errorMessage = itsQuery.error instanceof ApiClientError ? itsQuery.error.message : 'Falha ao carregar ITs.'

  return (
    <section className="page-section">
      <div className="dashboard-section-title">Minhas ITs</div>
      <div className="dashboard-section-subtitle">Selecione uma IT para consultar no assistente ou abra o PDF.</div>

      <div className="filters-row panel-block compact-block its-filter-row">
        <label className="field-stack its-status-filter">
          <span>Filtrar por status</span>
          <select value={status} onChange={(event) => setStatus(event.target.value as typeof status)}>
            {STATUS_OPTIONS.map((option) => (
              <option key={option} value={option}>
                {option}
              </option>
            ))}
          </select>
        </label>
        <div className="its-count-label">
          {filteredIts.length} {filteredIts.length === 1 ? 'documento' : 'documentos'}
        </div>
      </div>

      {itsQuery.isLoading ? <p>Carregando ITs...</p> : null}
      {itsQuery.isError ? <p className="error-text">{errorMessage}</p> : null}
      {!itsQuery.isLoading && !itsQuery.isError && filteredIts.length === 0 ? (
        <p className="helper-text">Nenhuma IT com o status selecionado.</p>
      ) : null}

      <div className="its-list-grid its-card-grid-dense">
        {filteredIts.map((it) => {
          const statusMeta = normalizeItStatus(it.status)
          const code = getItDisplayCode(it)
          return (
            <article key={it.id} className="streamlit-card its-list-item-card its-visual-card">
              <div className="its-card-top-row">
                <div className={`it-icon tone-${statusMeta.tone}`}>IT</div>
                <span className={`status-chip tone-${statusMeta.tone}`}>{statusMeta.label}</span>
              </div>
              <div className="it-card-title">{getItDisplayTitle(it)}</div>
              <div className="it-card-code">{code}</div>
              <div className="its-meta-lines">
                <span>Revisão: {it.revisao}</span>
                <span>Páginas: {it.paginaAtual}/{it.totalPaginas}</span>
              </div>
              <div className="its-actions-row">
                <button
                  type="button"
                  className="outline-button small its-select-btn"
                  onClick={() => {
                    setSelectedItId(it.id)
                    openAssistantPanel()
                    trackItAccess(code, `Você selecionou a IT ${code} para consulta no assistente.`)
                  }}
                >
                  Selecionar no chat
                </button>
                <button
                  type="button"
                  className="outline-button small"
                  onClick={() => {
                    void openProtectedItFile(it.id)
                  }}
                >
                  Abrir PDF
                </button>
              </div>
            </article>
          )
        })}
      </div>
    </section>
  )
}
