import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'

import { useAuth } from '../../hooks/useAuth'
import { useIts } from '../../hooks/useIts'
import { userService } from '../../services/user/userService'
import { normalizeItStatus } from '../../types/it'
import {
  buildTrainingStatusDescription,
  formatDateLabel,
  getTrainingStatusMeta,
  getUserTrainingAlerts,
} from '../../features/panel/panelUtils'

export function NotificationsPage() {
  const { user } = useAuth()
  const activeSector = String(user?.setorAtivo ?? '').trim()
  const isAdmin = ['ADMIN', 'SUPER_ADMIN'].includes(String(user?.role ?? '').toUpperCase())
  const usersQuery = useQuery({
    queryKey: ['notifications-users', activeSector],
    queryFn: () => userService.listUsers(),
    enabled: isAdmin && Boolean(activeSector),
  })
  const itsQuery = useIts()

  const alerts = useMemo(() => {
    if (isAdmin) {
      const trainingAlerts = getUserTrainingAlerts(usersQuery.data ?? []).map((alert) => ({
        title:
          alert.statusTone === 'danger'
            ? 'Treinamento vencido'
            : alert.statusTone === 'warning'
              ? 'Treinamento proximo de vencer'
              : 'Treinamento pendente',
        description: `${alert.name}: ${alert.description}`,
        statusLabel: alert.statusLabel,
        statusTone: alert.statusTone ?? 'pending',
      }))

      const pendingIts = (itsQuery.data ?? [])
        .filter((it) => normalizeItStatus(it.status).label === 'Pendente')
        .map((it) => ({
          title: 'Pendencia de documentacao',
          description: `O documento ${it.documento} esta pendente de revisao ou liberacao no setor.`,
          statusLabel: 'Pendente',
          statusTone: 'pending' as const,
        }))

      return [...trainingAlerts, ...pendingIts]
    }

    const meta = getTrainingStatusMeta(user?.nextTrainingDate, user?.trainingStatus)
    return [
      {
        title: meta.title,
        description: buildTrainingStatusDescription({
          lastTrainedIt: user?.lastTrainedIt ?? null,
          nextTrainingDate: user?.nextTrainingDate ?? null,
          trainingStatus: user?.trainingStatus ?? null,
        }),
        statusLabel: meta.label,
        statusTone: meta.tone,
      },
      {
        title: 'Ultima IT treinada',
        description: user?.lastTrainedIt
          ? `${user.lastTrainedIt} registrada em ${formatDateLabel(user.lastTrainingDate)}.`
          : 'Nenhuma IT treinada foi registrada ate o momento.',
        statusLabel: user?.lastTrainedIt ? 'Treinado' : 'Pendente',
        statusTone: user?.lastTrainedIt ? ('ok' as const) : ('pending' as const),
      },
      {
        title: 'Proximo vencimento',
        description: `Proxima data de vencimento: ${formatDateLabel(user?.nextTrainingDate)}.`,
        statusLabel: meta.label,
        statusTone: meta.tone,
      },
    ]
  }, [isAdmin, itsQuery.data, user, usersQuery.data])

  return (
    <section className="page-section">
      <div className="dashboard-section-title">Notificacoes</div>
      <div className="dashboard-section-subtitle">Alertas operacionais e avisos relacionados a treinamento e documentos.</div>

      <div className="streamlit-card panel-block">
        <div className="panel-title">Alertas</div>
        {usersQuery.isLoading || itsQuery.isLoading ? <p className="helper-text">Carregando alertas...</p> : null}
        {usersQuery.isError || itsQuery.isError ? <p className="error-text">Nao foi possivel carregar os alertas.</p> : null}
        {!usersQuery.isLoading && !itsQuery.isLoading ? (
          <div className="activity-list training-alert-list">
            {alerts.map((alert) => (
              <div key={`${alert.title}-${alert.description}`} className="activity-item training-alert-item">
                <div className="activity-content">
                  <strong>{alert.title}</strong>
                  <div className="panel-muted">{alert.description}</div>
                </div>
                <div className={`activity-date training-alert-status tone-${alert.statusTone}`}>{alert.statusLabel}</div>
              </div>
            ))}
          </div>
        ) : null}
      </div>
    </section>
  )
}
