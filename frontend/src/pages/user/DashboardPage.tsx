import { useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'

import { useAuth } from '../../hooks/useAuth'
import { useChat } from '../../hooks/useChat'
import { useIts } from '../../hooks/useIts'
import { userService } from '../../services/user/userService'
import { openProtectedItFile } from '../../services/http/apiClient'
import { getItDisplayCode, getItDisplayTitle, normalizeItStatus } from '../../types/it'
import { getSetorLabel } from '../../types/setor'
import { formatDateLabel, getRecentIts, getUserTrainingAlerts } from '../../features/panel/panelUtils'

export function DashboardPage() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const activeSector = String(user?.setorAtivo ?? '').trim()
  const itsQuery = useIts()
  const { recentActivities, accessedItDocs, interactionCount } = useChat()

  const isAdmin = ['ADMIN', 'SUPER_ADMIN'].includes(String(user?.role ?? '').toUpperCase())
  const usersQuery = useQuery({
    queryKey: ['admin-users', activeSector],
    queryFn: () => userService.listUsers(),
    enabled: isAdmin && Boolean(activeSector),
  })

  const its = itsQuery.data ?? []
  const recentIts = useMemo(() => getRecentIts(its, 3), [its])
  const trainingAlerts = useMemo(() => getUserTrainingAlerts(usersQuery.data ?? []).slice(0, 4), [usersQuery.data])
  const recentLog = recentActivities.slice(0, 4)

  const metrics = useMemo(
    () => [
      { title: 'ITs acessadas', value: accessedItDocs.length, subtitle: 'Este mes', tone: 'tone-blue' },
      { title: 'Conversas no assistente', value: interactionCount, subtitle: 'Este mes', tone: 'tone-purple' },
      { title: 'Arquivos enviados', value: its.length, subtitle: 'Este mes', tone: 'tone-violet' },
    ],
    [accessedItDocs.length, interactionCount, its.length],
  )

  return (
    <section className="page-section dashboard-overview-page">
      <div className="dashboard-overview-layout">
        <div className="dashboard-overview-main">
          <section className="dashboard-overview-hero">
            <div className="dashboard-overview-copy">
              <span className="dashboard-context-kicker">Painel do usuario</span>
              <div className="dashboard-overview-sector-badge">{getSetorLabel(user?.setorAtivo ?? '')}</div>
              <h1>Ola, {user?.name?.split(' ')[0] ?? 'Usuario'}.</h1>
            </div>
            <div className="dashboard-overview-actions">
              <button type="button" className="outline-button dashboard-pill-button" onClick={() => navigate('/minhas-its')}>
                Buscar uma IT
              </button>
              <button type="button" className="outline-button dashboard-pill-button" onClick={() => navigate('/documentation')}>
                Abrir documentacao
              </button>
              {isAdmin ? (
                <button type="button" className="outline-button dashboard-pill-button" onClick={() => navigate('/admin/users')}>
                  Painel administrativo
                </button>
              ) : null}
            </div>
          </section>

          <section className="dashboard-overview-card">
            <div className="panel-row-between">
              <div className="panel-title">Minhas ITs</div>
              <button type="button" className="panel-link-button" onClick={() => navigate('/minhas-its')}>
                Ver todas
              </button>
            </div>

            {itsQuery.isLoading ? <p className="helper-text">Carregando ITs...</p> : null}
            {itsQuery.isError ? <p className="error-text">Nao foi possivel carregar as ITs.</p> : null}
            {!itsQuery.isLoading && !itsQuery.isError ? (
              recentIts.length > 0 ? (
                <div className="dashboard-main-it-grid">
                  {recentIts.map((it) => {
                    const status = normalizeItStatus(it.status)
                    return (
                      <article key={it.id} className="dashboard-context-it-item dashboard-main-it-item">
                        <div className="dashboard-context-it-top">
                          <div className="dashboard-context-it-ident">
                            <div className={`it-icon tone-${status.tone}`}>IT</div>
                            <div>
                              <div className="it-card-title">{getItDisplayTitle(it)}</div>
                              <div className="it-card-code">{getItDisplayCode(it)}</div>
                            </div>
                          </div>
                          <span className={`status-chip tone-${status.tone}`}>{status.label}</span>
                        </div>
                        <button
                          type="button"
                          className="outline-button small"
                          onClick={() => {
                            void openProtectedItFile(it.id)
                          }}
                        >
                          Visualizar
                        </button>
                      </article>
                    )
                  })}
                </div>
              ) : (
                <p className="helper-text">Nenhuma IT disponivel ainda.</p>
              )
            ) : null}
          </section>
        </div>

        <aside className="dashboard-overview-side">
          <section className="dashboard-overview-card">
            <div className="panel-title">Treinamentos com atencao</div>
            {usersQuery.isLoading ? <p className="helper-text">Carregando alertas...</p> : null}
            {usersQuery.isError ? <p className="error-text">Nao foi possivel carregar os alertas.</p> : null}
            {!usersQuery.isLoading && !usersQuery.isError ? (
              trainingAlerts.length > 0 ? (
                <div className="dashboard-compact-list">
                  {trainingAlerts.map((alert) => (
                    <div key={`${alert.email}-${alert.nextTrainingDate ?? 'sem-data'}`} className="dashboard-compact-item">
                      <div>
                        <strong>{alert.name}</strong>
                        <div className="panel-muted">Vencimento: {formatDateLabel(alert.nextTrainingDate)}</div>
                      </div>
                      <span className="dashboard-compact-status">
                        {alert.statusIcon} {alert.statusLabel}
                      </span>
                    </div>
                  ))}
                </div>
              ) : (
                <p className="helper-text">Nenhum treinamento com atencao no momento.</p>
              )
            ) : null}
          </section>

          <section className="dashboard-overview-metrics dashboard-overview-metrics-side">
            {metrics.map((metric) => (
              <article key={metric.title} className={`metric-card ${metric.tone}`}>
                <div className="metric-title">{metric.title}</div>
                <div className="metric-value">{metric.value}</div>
                <div className="metric-subtitle">{metric.subtitle}</div>
              </article>
            ))}
          </section>

          <section className="dashboard-overview-card dashboard-overview-card-compact">
            <div className="panel-row-between">
              <div className="panel-title">Atividades recentes</div>
              <button type="button" className="panel-link-button" onClick={() => navigate('/conversations')}>
                Ver historico
              </button>
            </div>
            <div className="dashboard-compact-list dashboard-activity-list">
              {recentLog.length > 0 ? (
                recentLog.map((activity, index) => (
                  <div key={`${activity.descricao}-${index}`} className="dashboard-compact-item dashboard-activity-item">
                    <div className="dashboard-activity-copy">
                      <span className="dashboard-activity-icon">{activity.icone}</span>
                      <div className="dashboard-activity-text">
                        <strong>{activity.descricao}</strong>
                      </div>
                    </div>
                    <span className="dashboard-compact-date">{activity.data}</span>
                  </div>
                ))
              ) : (
                <div className="dashboard-compact-item dashboard-activity-item">
                  <div className="dashboard-activity-copy">
                    <span className="dashboard-activity-icon">Chat</span>
                    <div className="dashboard-activity-text">
                      <strong>Nenhuma atividade recente registrada.</strong>
                    </div>
                  </div>
                  <span className="dashboard-compact-date">Agora</span>
                </div>
              )}
            </div>
          </section>
        </aside>
      </div>
    </section>
  )
}
