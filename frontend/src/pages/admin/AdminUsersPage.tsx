import { useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { useAuth } from '../../hooks/useAuth'
import { useIts } from '../../hooks/useIts'
import { ApiClientError, openProtectedItFile } from '../../services/http/apiClient'
import { itService } from '../../services/its/itService'
import { userService } from '../../services/user/userService'
import { uploadService } from '../../services/upload/uploadService'
import { getItDisplayCode, normalizeItStatus } from '../../types/it'
import type { UserProfile } from '../../types/auth'
import { getSetorLabel, parseSetorCodes } from '../../types/setor'
import {
  buildTrainingStatusDescription,
  buildManagedItPayload,
  formatDateLabel,
  formatDateTimeLabel,
  getTrainingStatusMeta,
  getMostRecentIt,
  getUserTrainingAlerts,
  IT_STATUS_OPTIONS,
} from '../../features/panel/panelUtils'

const CARGO_OPTIONS = ['OPERADOR', 'OPERADOR_ESPECIALIZADO', 'AUXILIAR_DE_PRODUCAO']
const ROLE_OPTIONS = ['USER', 'ADMIN'] as const
const TRAINING_STATUS_OPTIONS = ['TREINADO', 'NAO_TREINADO', 'PENDENTE', 'ATRASADO'] as const

interface FeedbackState {
  ok: boolean
  text: string
}

type ItFormState = {
  documento: string
  revisao: string
  status: (typeof IT_STATUS_OPTIONS)[number]
  dataPublicacao: string
  paginaAtual: number
  totalPaginas: number
  prazoTreinamentoDias: number
}

type ItAdminMode = 'create' | 'update' | 'attach'

const initialItForm: ItFormState = {
  documento: '',
  revisao: '',
  status: IT_STATUS_OPTIONS[0],
  dataPublicacao: '',
  paginaAtual: 1,
  totalPaginas: 1,
  prazoTreinamentoDias: 0,
}

export function AdminUsersPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { user } = useAuth()
  const activeSector = String(user?.setorAtivo ?? '').trim()
  const isSuperAdmin = String(user?.role ?? '').toUpperCase() === 'SUPER_ADMIN'
  const [userFilterSector, setUserFilterSector] = useState('')

  const usersQuery = useQuery({
    queryKey: ['admin-users', isSuperAdmin ? userFilterSector : userFilterSector || activeSector],
    queryFn: () => userService.listUsers(userFilterSector || undefined),
  })
  const setoresQuery = useQuery({ queryKey: ['admin-setores'], queryFn: userService.listSetores })
  const itsQuery = useIts()
  const users = usersQuery.data ?? []
  const its = itsQuery.data ?? []
  const setores = setoresQuery.data ?? []
  const availableSectorCodes = useMemo(() => setores.map((item) => item.codigo), [setores])
  const visibleUsers = useMemo(
    () => users.filter((currentUser) => String(currentUser.role ?? '').toUpperCase() !== 'SUPER_ADMIN'),
    [users],
  )
  const operationalUsers = useMemo(
    () => visibleUsers.filter((currentUser) => !['ADMIN', 'SUPER_ADMIN'].includes(String(currentUser.role ?? '').toUpperCase())),
    [visibleUsers],
  )
  const managedAdmins = useMemo(
    () => visibleUsers.filter((currentUser) => String(currentUser.role ?? '').toUpperCase() === 'ADMIN'),
    [visibleUsers],
  )
  const trainingAlerts = useMemo(() => getUserTrainingAlerts(operationalUsers).slice(0, 6), [operationalUsers])
  const mostRecentIt = useMemo(() => getMostRecentIt(its), [its])

  const [itMode, setItMode] = useState<ItAdminMode>('attach')
  const [itForm, setItForm] = useState<ItFormState>(initialItForm)
  const [editingItId, setEditingItId] = useState('')
  const [selectedUploadFile, setSelectedUploadFile] = useState<File | null>(null)
  const [attachStatus, setAttachStatus] = useState<(typeof IT_STATUS_OPTIONS)[number]>(IT_STATUS_OPTIONS[0])
  const [itMsg, setItMsg] = useState<FeedbackState | null>(null)

  const [newName, setNewName] = useState('')
  const [newEmail, setNewEmail] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [newRole, setNewRole] = useState<(typeof ROLE_OPTIONS)[number]>('USER')
  const [newCargo, setNewCargo] = useState(CARGO_OPTIONS[0])
  const [newSectorCode, setNewSectorCode] = useState('')
  const [newSectorTouched, setNewSectorTouched] = useState(false)
  const [createUserMsg, setCreateUserMsg] = useState<FeedbackState | null>(null)

  const [selectedUserIdForRecovery, setSelectedUserIdForRecovery] = useState('')
  const [recoveryCodeMsg, setRecoveryCodeMsg] = useState<FeedbackState | null>(null)

  const [trainingUserId, setTrainingUserId] = useState('')
  const [trainingItId, setTrainingItId] = useState('')
  const [trainingStatus, setTrainingStatus] = useState<(typeof TRAINING_STATUS_OPTIONS)[number]>('TREINADO')
  const [trainingDate, setTrainingDate] = useState('')
  const [trainingDays, setTrainingDays] = useState(180)
  const [trainingMsg, setTrainingMsg] = useState<FeedbackState | null>(null)
  const selectedTrainingUser = useMemo(
    () => operationalUsers.find((currentUser) => String(currentUser.id ?? '') === trainingUserId) ?? null,
    [operationalUsers, trainingUserId],
  )
  const [syncMsg, setSyncMsg] = useState<FeedbackState | null>(null)
  const [deleteUserMsg, setDeleteUserMsg] = useState<FeedbackState | null>(null)
  const [sectorEditorUserId, setSectorEditorUserId] = useState('')
  const [sectorEditorSelection, setSectorEditorSelection] = useState<string[]>([])
  const [sectorEditorCustom, setSectorEditorCustom] = useState('')
  const [updateSetoresMsg, setUpdateSetoresMsg] = useState<FeedbackState | null>(null)
  const selectedManagedAdmin = useMemo(
    () => managedAdmins.find((currentUser) => String(currentUser.id ?? '').trim() === sectorEditorUserId) ?? null,
    [managedAdmins, sectorEditorUserId],
  )

  const createUserMutation = useMutation({
    mutationFn: userService.createUser,
    onSuccess: (result) => {
      void queryClient.invalidateQueries({ queryKey: ['admin-users'] })
      void queryClient.invalidateQueries({ queryKey: ['admin-setores'] })
      setCreateUserMsg({ ok: true, text: result.message })
      setNewName('')
      setNewEmail('')
      setNewPassword('')
      setNewRole('USER')
      setNewCargo(CARGO_OPTIONS[0])
      setNewSectorCode(isSuperAdmin ? '' : activeSector)
      setNewSectorTouched(false)
    },
    onError: (error) => {
      setCreateUserMsg({
        ok: false,
        text: error instanceof ApiClientError ? error.message : 'Erro ao criar usuario.',
      })
    },
  })

  const updateRecoveryCodeMutation = useMutation({
    mutationFn: (userId: string) => userService.updateRecoveryCode(userId),
    onSuccess: (result) => {
      setRecoveryCodeMsg({ ok: true, text: result.message })
    },
    onError: (error) => {
      setRecoveryCodeMsg({
        ok: false,
        text: error instanceof ApiClientError ? error.message : 'Erro ao atualizar codigo de recuperacao.',
      })
    },
  })

  const updateUserSetoresMutation = useMutation({
    mutationFn: ({ userId, setores }: { userId: string; setores: string }) =>
      userService.updateUserSetores(userId, { setores }),
    onSuccess: (result) => {
      void queryClient.invalidateQueries({ queryKey: ['admin-users'] })
      void queryClient.invalidateQueries({ queryKey: ['admin-setores'] })
      setSectorEditorSelection(parseSetorCodes(result.setores))
      setSectorEditorCustom('')
      setUpdateSetoresMsg({ ok: true, text: 'Setores atualizados com sucesso.' })
    },
    onError: (error) => {
      setUpdateSetoresMsg({
        ok: false,
        text: error instanceof ApiClientError ? error.message : 'Erro ao atualizar setores do usuario.',
      })
    },
  })

  const deleteUserMutation = useMutation({
    mutationFn: (id: string) => userService.deleteUser(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['admin-users'] })
      setDeleteUserMsg({ ok: true, text: 'Usuario excluido com sucesso.' })
    },
    onError: (error) => {
      setDeleteUserMsg({
        ok: false,
        text: error instanceof ApiClientError ? error.message : 'Erro ao excluir usuario.',
      })
    },
  })

  const deleteItMutation = useMutation({
    mutationFn: (id: string) => itService.remove(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['its'] })
      setItMsg({ ok: true, text: 'IT excluida com sucesso.' })
      if (editingItId) {
        setEditingItId('')
        setItForm(initialItForm)
      }
    },
    onError: (error) => {
      setItMsg({
        ok: false,
        text: error instanceof ApiClientError ? error.message : 'Erro ao excluir IT.',
      })
    },
  })

  const updateTrainingMutation = useMutation({
    mutationFn: ({
      userId,
      itCode,
      status,
      date,
      days,
    }: {
      userId: string
      itCode: string
      status: string
      date: string
      days: number
    }) =>
      userService.updateTraining(userId, {
        lastTrainedIt: itCode,
        trainingStatus: status,
        lastTrainingDate: date,
        retrainingIntervalDays: days,
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['admin-users'] })
      setTrainingMsg({ ok: true, text: 'Treinamento salvo com sucesso.' })
    },
    onError: (error) => {
      setTrainingMsg({
        ok: false,
        text: error instanceof ApiClientError ? error.message : 'Erro ao salvar treinamento.',
      })
    },
  })

  const syncMutation = useMutation({
    mutationFn: itService.syncFiles,
    onSuccess: (result) => {
      void queryClient.invalidateQueries({ queryKey: ['its'] })
      void queryClient.invalidateQueries({ queryKey: ['pts-products'] })
      void queryClient.invalidateQueries({ queryKey: ['pts-data'] })
      setSyncMsg({ ok: true, text: result.message ?? 'Sincronizacao concluida para o setor ativo.' })
    },
    onError: (error) => {
      setSyncMsg({
        ok: false,
        text: error instanceof ApiClientError ? error.message : 'Erro ao sincronizar PDFs do setor.',
      })
    },
  })

  const uploadItMutation = useMutation({
    mutationFn: ({
      file,
      status,
      options,
    }: {
      file: File
      status: string
      options?: {
        existingItId?: string
        documento?: string
        revisao?: string
        dataPublicacao?: string
        paginaAtual?: number
        totalPaginas?: number
        prazoTreinamentoDias?: number
      }
    }) => uploadService.uploadItPdf(file, status, activeSector, options),
    onSuccess: (result, variables) => {
      void queryClient.invalidateQueries({ queryKey: ['its'] })
      setItMsg({
        ok: true,
        text: variables.options?.existingItId
          ? 'IT atualizada com o PDF revisado com sucesso.'
          : result.message ?? 'IT anexada com sucesso.',
      })
      setSelectedUploadFile(null)
      setAttachStatus(IT_STATUS_OPTIONS[0])
      if (variables.options?.existingItId) {
        setEditingItId('')
        setItForm(initialItForm)
      }
    },
    onError: (error) => {
      const message = error instanceof Error ? error.message : 'Erro ao anexar IT.'
      setItMsg({ ok: false, text: message })
    },
  })

  const overdueCount = useMemo(() => {
    const today = new Date()
    today.setHours(0, 0, 0, 0)
    return users.filter((currentUser) => {
      if (['ADMIN', 'SUPER_ADMIN'].includes(String(currentUser.role ?? '').toUpperCase())) return false
      if (!currentUser.nextTrainingDate) return false
      const next = new Date(currentUser.nextTrainingDate)
      return !Number.isNaN(next.getTime()) && next < today
    }).length
  }, [users])

  const pendingCount = useMemo(
    () => operationalUsers.filter((currentUser) => !String(currentUser.nextTrainingDate ?? '').trim()).length,
    [operationalUsers],
  )

  const usersError = usersQuery.error instanceof ApiClientError ? usersQuery.error.message : null
  const itsError = itsQuery.error instanceof ApiClientError ? itsQuery.error.message : null
  const safeRoleOptions = isSuperAdmin ? ROLE_OPTIONS : (['USER'] as const)

  useEffect(() => {
    if (activeSector) {
      setUserFilterSector(activeSector)
    }
  }, [activeSector])

  useEffect(() => {
    if (!isSuperAdmin && !newSectorTouched && !newSectorCode && activeSector) {
      setNewSectorCode(activeSector)
    }
  }, [activeSector, isSuperAdmin, newSectorCode, newSectorTouched])

  useEffect(() => {
    if (!selectedManagedAdmin) return
    setSectorEditorSelection(parseSetorCodes(selectedManagedAdmin.setores))
  }, [selectedManagedAdmin])

  function applyItToForm(id: string) {
    const selected = its.find((it) => it.id === id)
    if (!selected) return

    setEditingItId(selected.id)
    setSelectedUploadFile(null)
    setItMsg(null)
    setItForm({
      documento: selected.documento ?? '',
      revisao: selected.revisao ?? '',
      status: (selected.status as (typeof IT_STATUS_OPTIONS)[number]) || IT_STATUS_OPTIONS[0],
      dataPublicacao: String(selected.dataPublicacao ?? '').slice(0, 16),
      paginaAtual: selected.paginaAtual ?? 1,
      totalPaginas: selected.totalPaginas ?? 1,
      prazoTreinamentoDias: selected.prazoTreinamentoDias ?? 0,
    })
  }

  function switchItMode(nextMode: ItAdminMode) {
    setItMode(nextMode)
    setItMsg(null)
    setSelectedUploadFile(null)

    if (nextMode === 'create') {
      setEditingItId('')
      setItForm(initialItForm)
    }
  }

  function handleCreateUser(event: FormEvent) {
    event.preventDefault()
    setCreateUserMsg(null)

    const trimmedName = newName.trim()
    const trimmedEmail = newEmail.trim().toLowerCase()
    const normalizedPassword = newPassword
    const normalizedSector = newSectorCode.trim().toUpperCase()

    if (!trimmedName || !trimmedEmail || !normalizedPassword) {
      setCreateUserMsg({ ok: false, text: 'Preencha nome, email e senha.' })
      return
    }

    if (!trimmedName.includes(' ')) {
      setCreateUserMsg({ ok: false, text: 'Informe nome e sobrenome para o novo usuario.' })
      return
    }

    if (normalizedPassword.length < 8) {
      setCreateUserMsg({ ok: false, text: 'A senha deve conter no minimo 8 caracteres.' })
      return
    }

    if (!normalizedSector) {
      setCreateUserMsg({ ok: false, text: 'Informe o setor do novo usuario.' })
      return
    }

    createUserMutation.mutate({
      name: trimmedName,
      email: trimmedEmail,
      password: normalizedPassword,
      role: newRole,
      cargo: newCargo,
      setores: normalizedSector,
    })
  }

  function handleSaveTraining(event: FormEvent) {
    event.preventDefault()
    setTrainingMsg(null)
    const itCode = its.find((it) => it.id === trainingItId)?.documento ?? trainingItId
    updateTrainingMutation.mutate({
      userId: trainingUserId,
      itCode,
      status: trainingStatus,
      date: trainingDate,
      days: trainingDays,
    })
  }

  function handleSaveIt(event: FormEvent) {
    event.preventDefault()
    setItMsg(null)

    const payload = buildManagedItPayload({
      ...itForm,
      setor: activeSector,
      paginaAtual: Number(itForm.paginaAtual),
      totalPaginas: Number(itForm.totalPaginas),
      prazoTreinamentoDias: Number(itForm.prazoTreinamentoDias),
    })

    if (itMode === 'update') {
      if (!editingItId) {
        setItMsg({ ok: false, text: 'Selecione a IT que voce quer atualizar.' })
        return
      }

      if (!selectedUploadFile) {
        setItMsg({ ok: false, text: 'Anexe o PDF revisado antes de atualizar a IT.' })
        return
      }

      uploadItMutation.mutate({
        file: selectedUploadFile,
        status: payload.status,
        options: {
          existingItId: editingItId,
          documento: payload.documento,
          revisao: payload.revisao,
          dataPublicacao: payload.dataPublicacao,
          paginaAtual: payload.paginaAtual,
          totalPaginas: payload.totalPaginas,
          prazoTreinamentoDias: payload.prazoTreinamentoDias,
        },
      })
      return
    }

    if (!selectedUploadFile) {
      setItMsg({ ok: false, text: 'Anexe o PDF da nova IT antes de criar o cadastro.' })
      return
    }

    uploadItMutation.mutate({
      file: selectedUploadFile,
      status: payload.status,
      options: {
        documento: payload.documento,
        revisao: payload.revisao,
        dataPublicacao: payload.dataPublicacao,
        paginaAtual: payload.paginaAtual,
        totalPaginas: payload.totalPaginas,
        prazoTreinamentoDias: payload.prazoTreinamentoDias,
      },
    })
  }

  function handleAttachIt(event: FormEvent) {
    event.preventDefault()
    setItMsg(null)

    if (!selectedUploadFile) {
      setItMsg({ ok: false, text: 'Selecione o PDF da IT para anexar.' })
      return
    }

    uploadItMutation.mutate({ file: selectedUploadFile, status: attachStatus })
  }

  function handleUpdateRecoveryCode(event: FormEvent) {
    event.preventDefault()
    setRecoveryCodeMsg(null)
    if (!selectedUserIdForRecovery) {
      setRecoveryCodeMsg({ ok: false, text: 'Selecione um usuario para gerar um novo codigo.' })
      return
    }
    updateRecoveryCodeMutation.mutate(selectedUserIdForRecovery)
  }

  function startEditIt(id: string) {
    setItMode('update')
    applyItToForm(id)
  }

  function resetItForm() {
    setEditingItId('')
    setSelectedUploadFile(null)
    setItForm(initialItForm)
    setItMsg(null)
  }

  function openSectorEditor(targetUser: UserProfile) {
    const targetId = String(targetUser.id ?? '').trim()
    if (!targetId) return
    setUpdateSetoresMsg(null)
    setSectorEditorUserId(targetId)
    setSectorEditorSelection(parseSetorCodes(targetUser.setores))
    setSectorEditorCustom('')
  }

  function closeSectorEditor() {
    setSectorEditorUserId('')
    setSectorEditorSelection([])
    setSectorEditorCustom('')
    setUpdateSetoresMsg(null)
  }

  function toggleSectorSelection(code: string) {
    setSectorEditorSelection((current) =>
      current.includes(code) ? current.filter((item) => item !== code) : [...current, code],
    )
  }

  function applySectorUpdate(userId: string) {
    setUpdateSetoresMsg(null)
    const merged = Array.from(new Set([...sectorEditorSelection, ...parseSetorCodes(sectorEditorCustom)]))
    if (merged.length === 0) {
      setUpdateSetoresMsg({ ok: false, text: 'Selecione ou informe ao menos um setor.' })
      return
    }
    updateUserSetoresMutation.mutate({ userId, setores: merged.join(',') })
  }

  return (
    <section className="page-section admin-page-shell">
      <div className="dashboard-section-title">Painel Administrativo</div>
      <div className="dashboard-section-subtitle">
        Gerencie ITs, revisoes, anexos PDF, usuarios, treinamentos e sincronizacao da base documental do setor ativo.
      </div>
      <div className="dashboard-section-subtitle">Setor ativo: {getSetorLabel(activeSector)}</div>

      <div className="admin-tab-grid">
        <section className="panel-block streamlit-card">
          <div className="panel-title">Dashboard</div>
          <div className="metrics-grid admin-metrics-grid">
            <article className="metric-card tone-blue">
              <div className="metric-title">Total de ITs</div>
              <div className="metric-value">{its.length}</div>
            </article>
            <article className="metric-card tone-purple">
              <div className="metric-title">Treinamentos vencidos</div>
              <div className="metric-value">{overdueCount}</div>
            </article>
            <article className="metric-card tone-violet">
              <div className="metric-title">Usuarios cadastrados</div>
              <div className="metric-value">{users.length}</div>
            </article>
          </div>

          <div className="admin-dashboard-grid">
            <div className="nested-panel">
              <div className="panel-title">IT mais recente</div>
              <div className="panel-strong">{mostRecentIt ? mostRecentIt.documento : '-'}</div>
              <div className="panel-muted">
                {mostRecentIt ? formatDateTimeLabel(mostRecentIt.dataPublicacao) : 'Nenhuma IT publicada'}
              </div>
            </div>
            <div className="nested-panel">
              <div className="panel-title">Usuarios pendentes</div>
              <div className="panel-strong">{pendingCount}</div>
              <div className="panel-muted">Sem proximo treinamento informado</div>
            </div>
          </div>
        </section>

        <section className="panel-block streamlit-card">
          <div className="panel-row-between">
            <div className="panel-title">Gestao de ITs</div>
            <div className="admin-sync-row">
              <button
                type="button"
                className="outline-button small"
                onClick={() => {
                  setSyncMsg(null)
                  syncMutation.mutate()
                }}
                disabled={syncMutation.isPending}
              >
                {syncMutation.isPending ? 'Sincronizando...' : 'Sincronizar PDFs do setor'}
              </button>
              <button type="button" className="outline-button small" onClick={() => navigate('/files')}>
                Ir para Arquivos/PDFs
              </button>
            </div>
          </div>

          {syncMsg ? <div className={syncMsg.ok ? 'upload-success' : 'upload-error'}>{syncMsg.text}</div> : null}
          {itsError ? <p className="error-text">{itsError}</p> : null}

          <div className="admin-it-mode-switch" role="tablist" aria-label="Fluxos de gestao de ITs">
            <button
              type="button"
              className={`admin-it-mode-button ${itMode === 'create' ? 'is-active' : ''}`}
              onClick={() => switchItMode('create')}
            >
              Criar IT
            </button>
            <button
              type="button"
              className={`admin-it-mode-button ${itMode === 'update' ? 'is-active' : ''}`}
              onClick={() => switchItMode('update')}
            >
              Atualizar IT
            </button>
            <button
              type="button"
              className={`admin-it-mode-button ${itMode === 'attach' ? 'is-active' : ''}`}
              onClick={() => switchItMode('attach')}
            >
              Anexar IT
            </button>
          </div>

          <div className="admin-it-mode-helper">
            {itMode === 'create'
              ? 'Use este fluxo quando a IT for nova no setor. O cadastro so entra no sistema junto com o PDF anexado.'
              : itMode === 'update'
                ? 'Use este fluxo quando existir uma IT no sistema e voce precisar subir o PDF revisado junto com os dados novos. A atualizacao so entra no sistema com o anexo da revisao.'
                : 'Use este fluxo para anexar o PDF da IT. Se o documento ja existir no setor, a revisao anexada atualiza a IT atual para usuarios e administradores.'}
          </div>

          {itMode === 'attach' ? (
            <form className="panel-block nested-panel admin-it-upload-panel" onSubmit={handleAttachIt}>
              <div className="panel-row-between">
                <div>
                  <div className="panel-title">Anexar IT revisada</div>
                  <div className="panel-muted">O sistema vai ler o PDF, identificar documento e revisao e atualizar a IT atual do mesmo documento no setor ativo.</div>
                </div>
                <button type="button" className="panel-link-button" onClick={resetItForm}>
                  Limpar selecao
                </button>
              </div>

              <div className="form-grid-two">
                <label className="field-stack">
                  <span>Status da IT</span>
                  <select value={attachStatus} onChange={(event) => setAttachStatus(event.target.value as (typeof IT_STATUS_OPTIONS)[number])}>
                    {IT_STATUS_OPTIONS.map((option) => (
                      <option key={option} value={option}>
                        {option}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="field-stack">
                  <span>Arquivo PDF da IT</span>
                  <input
                    type="file"
                    accept="application/pdf,.pdf"
                    onChange={(event) => setSelectedUploadFile(event.target.files?.[0] ?? null)}
                    required
                  />
                </label>
              </div>

              <div className="admin-it-inline-note">
                {selectedUploadFile
                  ? `Arquivo selecionado: ${selectedUploadFile.name}`
                  : 'Selecione o PDF revisado da IT. O documento antigo sera substituido logicamente pela nova revisao no setor atual.'}
              </div>

              <button type="submit" className="outline-button" disabled={uploadItMutation.isPending || !selectedUploadFile}>
                {uploadItMutation.isPending ? 'Anexando...' : 'Anexar IT'}
              </button>
              {itMsg ? <div className={itMsg.ok ? 'upload-success' : 'upload-error'}>{itMsg.text}</div> : null}
            </form>
          ) : (
            <form className="panel-block nested-panel" onSubmit={handleSaveIt}>
              <div className="panel-row-between">
                <div className="panel-title">{itMode === 'update' ? 'Atualizar IT com PDF revisado' : 'Criar IT do zero'}</div>
                {(editingItId || itMode === 'update') ? (
                  <button type="button" className="panel-link-button" onClick={resetItForm}>
                    Cancelar edicao
                  </button>
                ) : null}
              </div>

              {itMode === 'update' ? (
                <label className="field-stack admin-it-picker">
                  <span>IT existente</span>
                  <select
                    value={editingItId}
                    onChange={(event) => {
                      const nextId = event.target.value
                      if (!nextId) {
                        resetItForm()
                        setItMode('update')
                        return
                      }
                      applyItToForm(nextId)
                    }}
                    required
                  >
                    <option value="">Selecione a IT que voce quer atualizar</option>
                    {its.map((it) => (
                      <option key={it.id} value={it.id}>
                        {it.documento} - Rev. {it.revisao}
                      </option>
                    ))}
                  </select>
                </label>
              ) : null}

              <div className="form-grid-two">
                <label className="field-stack">
                  <span>Documento</span>
                  <input
                    value={itForm.documento}
                    onChange={(event) => setItForm((current) => ({ ...current, documento: event.target.value }))}
                    required
                  />
                </label>
                <label className="field-stack">
                  <span>Revisao</span>
                  <input
                    value={itForm.revisao}
                    onChange={(event) => setItForm((current) => ({ ...current, revisao: event.target.value }))}
                    required
                  />
                </label>
                <label className="field-stack">
                  <span>Status</span>
                  <select
                    value={itForm.status}
                    onChange={(event) =>
                      setItForm((current) => ({
                        ...current,
                        status: event.target.value as (typeof IT_STATUS_OPTIONS)[number],
                      }))
                    }
                  >
                    {IT_STATUS_OPTIONS.map((option) => (
                      <option key={option} value={option}>
                        {option}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="field-stack">
                  <span>Data de publicacao</span>
                  <input
                    type="datetime-local"
                    value={itForm.dataPublicacao}
                    onChange={(event) => setItForm((current) => ({ ...current, dataPublicacao: event.target.value }))}
                    required
                  />
                </label>
                <label className="field-stack">
                  <span>Pagina atual</span>
                  <input
                    type="number"
                    min={1}
                    value={itForm.paginaAtual}
                    onChange={(event) =>
                      setItForm((current) => ({ ...current, paginaAtual: Number(event.target.value) || 1 }))
                    }
                    required
                  />
                </label>
                <label className="field-stack">
                  <span>Total de paginas</span>
                  <input
                    type="number"
                    min={1}
                    value={itForm.totalPaginas}
                    onChange={(event) =>
                      setItForm((current) => ({ ...current, totalPaginas: Number(event.target.value) || 1 }))
                    }
                    required
                  />
                </label>
                <label className="field-stack">
                  <span>Prazo de treinamento (dias)</span>
                  <input
                    type="number"
                    min={0}
                    value={itForm.prazoTreinamentoDias}
                    onChange={(event) =>
                      setItForm((current) => ({
                        ...current,
                        prazoTreinamentoDias: Number(event.target.value) || 0,
                      }))
                    }
                    required
                  />
                </label>
              </div>

              <label className="field-stack" style={{ marginTop: 16 }}>
                <span>{itMode === 'update' ? 'PDF revisado da IT' : 'PDF da nova IT'}</span>
                <input
                  type="file"
                  accept="application/pdf,.pdf"
                  onChange={(event) => setSelectedUploadFile(event.target.files?.[0] ?? null)}
                  required
                />
              </label>
              <div className="admin-it-inline-note">
                {selectedUploadFile
                  ? `${itMode === 'update' ? 'PDF revisado' : 'PDF da IT'} selecionado: ${selectedUploadFile.name}`
                  : itMode === 'update'
                    ? 'A atualizacao so entra no sistema quando o PDF revisado for anexado. Sem esse anexo, o cadastro poderia mudar, mas o documento continuaria antigo.'
                    : 'A criacao da IT so entra no sistema com o PDF anexado. Sem o documento, o cadastro ficaria inconsistente para usuarios e administradores.'}
              </div>

              <button
                type="submit"
                className="outline-button"
                disabled={uploadItMutation.isPending || !selectedUploadFile}
              >
                {uploadItMutation.isPending
                  ? 'Salvando...'
                  : itMode === 'update'
                    ? 'Anexar PDF revisado e atualizar IT'
                    : 'Anexar PDF e criar IT'}
              </button>
              {itMsg ? <div className={itMsg.ok ? 'upload-success' : 'upload-error'}>{itMsg.text}</div> : null}
            </form>
          )}

          <div className="table-shell compact-table-shell">
            <table className="streamlit-table">
              <thead>
                <tr>
                  <th>Documento</th>
                  <th>Revisao</th>
                  <th>Status</th>
                  <th>Publicacao</th>
                  <th>Paginas</th>
                  <th>Acoes</th>
                </tr>
              </thead>
              <tbody>
                {its.map((it) => {
                  const status = normalizeItStatus(it.status)
                  return (
                    <tr key={it.id}>
                      <td>{it.documento}</td>
                      <td>{it.revisao}</td>
                      <td>
                        <span className={`status-chip tone-${status.tone}`}>{status.label}</span>
                      </td>
                      <td>{formatDateTimeLabel(it.dataPublicacao)}</td>
                      <td>
                        {it.paginaAtual}/{it.totalPaginas}
                      </td>
                      <td>
                        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                          <button
                            type="button"
                            className="outline-button small"
                            onClick={() => startEditIt(it.id)}
                          >
                            Atualizar
                          </button>
                          <button
                            type="button"
                            className="outline-button small"
                            onClick={() => {
                              void openProtectedItFile(it.id)
                            }}
                          >
                            PDF
                          </button>
                          <button
                            type="button"
                            className="outline-button small danger-button"
                            onClick={() => {
                              if (window.confirm(`Excluir a IT ${it.documento}?`)) {
                                setItMsg(null)
                                deleteItMutation.mutate(it.id)
                              }
                            }}
                            disabled={deleteItMutation.isPending}
                          >
                            Excluir
                          </button>
                        </div>
                      </td>
                    </tr>
                  )
                })}
                {!itsQuery.isLoading && its.length === 0 ? (
                  <tr>
                    <td colSpan={6} style={{ color: '#6b7fa3', textAlign: 'center' }}>
                      Nenhuma IT cadastrada. Use o painel de arquivos para subir PDFs ou crie uma IT manualmente.
                    </td>
                  </tr>
                ) : null}
              </tbody>
            </table>
          </div>
        </section>

        <section className="panel-block streamlit-card">
          <div className="panel-row-between">
            <div className="panel-title">Usuarios</div>
            {setores.length > 1 || isSuperAdmin ? (
              <label className="field-stack" style={{ maxWidth: 240 }}>
                <span>Filtrar por setor</span>
                <select
                  value={userFilterSector}
                  onChange={(event) => setUserFilterSector(event.target.value)}
                >
                  {isSuperAdmin ? <option value="">Todos os setores</option> : null}
                  {setores.map((item) => (
                    <option key={item.codigo} value={item.codigo}>
                      {getSetorLabel(item.codigo)}
                    </option>
                  ))}
                </select>
              </label>
            ) : null}
          </div>
          {usersQuery.isLoading ? <p className="helper-text">Carregando usuarios...</p> : null}
          {usersError ? <p className="error-text">{usersError}</p> : null}
          {deleteUserMsg ? (
            <div className={deleteUserMsg.ok ? 'upload-success' : 'upload-error'}>{deleteUserMsg.text}</div>
          ) : null}
          {updateSetoresMsg && !sectorEditorUserId ? (
            <div className={updateSetoresMsg.ok ? 'upload-success' : 'upload-error'}>{updateSetoresMsg.text}</div>
          ) : null}

          <div className="table-shell compact-table-shell">
            <table className="streamlit-table">
              <thead>
                <tr>
                  <th>Nome</th>
                  <th>Email</th>
                  <th>Perfil</th>
                  <th>Cargo</th>
                  <th>Setores</th>
                  <th>Status do treinamento</th>
                  <th>Ultima IT treinada</th>
                  <th>Proximo treinamento</th>
                  <th>Acoes</th>
                </tr>
              </thead>
              <tbody>
                {visibleUsers.map((user) => {
                  const userId = String(user.id ?? '').trim()
                  const canManageSectors = isSuperAdmin && String(user.role ?? '').toUpperCase() === 'ADMIN' && Boolean(userId)
                  const userSectorCodes = parseSetorCodes(user.setores)
                  const isEditingSectors = sectorEditorUserId === userId

                  return (
                    <tr key={user.id ?? user.email}>
                      <td>{user.name}</td>
                      <td>{user.email}</td>
                      <td>{String(user.role ?? '-')}</td>
                      <td>{String(user.cargo ?? '-').replaceAll('_', ' ')}</td>
                      <td>
                        <div className="admin-sector-cell">
                          <div className="admin-sector-chip-row">
                            {userSectorCodes.length > 0 ? (
                              userSectorCodes.map((sector) => (
                                <span key={`${userId}-${sector}`} className="admin-sector-chip">
                                  {getSetorLabel(sector)}
                                </span>
                              ))
                            ) : (
                              <span className="admin-sector-chip is-empty">Sem setor</span>
                            )}
                          </div>
                          {canManageSectors ? (
                            <button
                              type="button"
                              className="admin-sector-trigger"
                              onClick={() => openSectorEditor(user)}
                            >
                              {isEditingSectors ? 'Editando setores' : 'Editar setores'}
                            </button>
                          ) : null}
                        </div>
                      </td>
                      <td>
                        <span className={`status-chip tone-${getTrainingStatusMeta(user.nextTrainingDate, user.trainingStatus).tone}`}>
                          {getTrainingStatusMeta(user.nextTrainingDate, user.trainingStatus).label}
                        </span>
                      </td>
                      <td>{user.lastTrainedIt ?? '-'}</td>
                      <td>{formatDateLabel(user.nextTrainingDate)}</td>
                      <td>
                        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                          <button
                            type="button"
                            className="outline-button small danger-button"
                            onClick={() => {
                              if (!userId) return
                              if (window.confirm(`Excluir o usuario ${user.email}?`)) {
                                setDeleteUserMsg(null)
                                deleteUserMutation.mutate(userId)
                              }
                            }}
                            disabled={deleteUserMutation.isPending || !user.id}
                          >
                            Excluir
                          </button>
                        </div>
                      </td>
                    </tr>
                  )
                })}
                {!usersQuery.isLoading && visibleUsers.length === 0 ? (
                  <tr>
                    <td colSpan={9} style={{ color: '#6b7fa3', textAlign: 'center' }}>
                      Nenhum usuario cadastrado ainda.
                    </td>
                  </tr>
                ) : null}
              </tbody>
            </table>
          </div>

          <div className="admin-dashboard-grid">
            <div className="nested-panel">
              <div className="panel-title">Treinamentos com atencao</div>
              {trainingAlerts.length > 0 ? (
                <div className="activity-list training-alert-list">
                  {trainingAlerts.map((alert) => (
                    <div key={`${alert.email}-${alert.nextTrainingDate ?? 'sem-data'}`} className="activity-item training-alert-item">
                      <div className="activity-content">
                        <strong>{alert.name}</strong>
                        <div className="panel-muted">{alert.description}</div>
                      </div>
                      <div className={`activity-date training-alert-status tone-${alert.statusTone ?? 'pending'}`}>
                        {alert.statusLabel}
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <p className="helper-text">Nenhum usuario com treinamento pendente ou vencido.</p>
              )}
            </div>

            <form className="nested-panel" onSubmit={handleCreateUser}>
              <div className="panel-title">Criar usuario ou admin</div>
              <div className="form-grid-two">
                <input value={newName} onChange={(event) => setNewName(event.target.value)} placeholder="Nome" required />
                <input
                  value={newEmail}
                  onChange={(event) => setNewEmail(event.target.value)}
                  placeholder="Email"
                  type="email"
                  required
                />
                <input
                  value={newPassword}
                  onChange={(event) => setNewPassword(event.target.value)}
                  placeholder="Senha"
                  type="password"
                  required
                />
                <select value={newRole} onChange={(event) => setNewRole(event.target.value as (typeof ROLE_OPTIONS)[number])}>
                  {safeRoleOptions.map((option) => (
                    <option key={option} value={option}>
                      {option === 'ADMIN' ? 'Administrador' : 'Usuario'}
                    </option>
                  ))}
                </select>
                <input
                  value={newCargo}
                  onChange={(event) => setNewCargo(event.target.value)}
                  list="admin-cargo-options"
                  placeholder="Digite ou selecione o cargo"
                  required
                />
                <datalist id="admin-cargo-options">
                  {CARGO_OPTIONS.map((option) => (
                    <option key={option} value={option}>
                      {option.replaceAll('_', ' ')}
                    </option>
                  ))}
                </datalist>
                {isSuperAdmin ? (
                  <input
                    value={newSectorCode}
                    onChange={(event) => {
                      setNewSectorTouched(true)
                      setNewSectorCode(event.target.value)
                    }}
                    placeholder="Digite o setor do novo usuario"
                    required
                  />
                ) : (
                  <>
                  <input
                    value={newSectorCode}
                    onChange={(event) => {
                      setNewSectorTouched(true)
                      setNewSectorCode(event.target.value)
                    }}
                    list="admin-allowed-sector-options"
                    placeholder="Digite ou selecione o setor"
                    required
                  />
                  <datalist id="admin-allowed-sector-options">
                    {availableSectorCodes.map((codigo) => (
                      <option key={codigo} value={codigo}>
                        {getSetorLabel(codigo)}
                      </option>
                    ))}
                  </datalist>
                  </>
                )}
              </div>
              {isSuperAdmin ? (
                <div className="panel-muted" style={{ marginTop: 12 }}>
                  Digite o setor exatamente como ele deve nascer no banco. Se ainda nao existir, ele sera criado automaticamente.
                </div>
              ) : setores.length > 1 ? (
                <div className="panel-muted" style={{ marginTop: 12 }}>
                  Este administrador pode cadastrar usuarios nos setores vinculados a ele.
                </div>
              ) : null}
              <button type="submit" className="outline-button" disabled={createUserMutation.isPending}>
                {createUserMutation.isPending ? 'Criando...' : 'Criar usuario'}
              </button>
              {createUserMsg ? (
                <div className={createUserMsg.ok ? 'upload-success' : 'upload-error'}>{createUserMsg.text}</div>
              ) : null}
            </form>

            <form className="nested-panel" onSubmit={handleUpdateRecoveryCode}>
              <div className="panel-title">Atualizar codigo de recuperacao</div>
              <div className="form-grid-two">
                <label className="field-stack">
                  <span>Usuario</span>
                  <select value={selectedUserIdForRecovery} onChange={(event) => setSelectedUserIdForRecovery(event.target.value)} required>
                    <option value="">Selecionar usuario</option>
                    {visibleUsers.filter((currentUser) => currentUser.id).map((currentUser) => (
                      <option key={currentUser.id ?? currentUser.email} value={String(currentUser.id ?? '')}>
                        {currentUser.name} - {currentUser.email}
                      </option>
                    ))}
                  </select>
                </label>
              </div>
              <button type="submit" className="outline-button" disabled={updateRecoveryCodeMutation.isPending}>
                {updateRecoveryCodeMutation.isPending ? 'Gerando...' : 'Gerar novo codigo'}
              </button>
              {recoveryCodeMsg ? (
                <div className={recoveryCodeMsg.ok ? 'upload-success' : 'upload-error'}>{recoveryCodeMsg.text}</div>
              ) : null}
            </form>

            <form className="nested-panel" onSubmit={handleSaveTraining}>
              <div className="panel-title">Controle de treinamento</div>
              <div className="form-grid-two">
                <label className="field-stack">
                  <span>Usuario</span>
                  <select value={trainingUserId} onChange={(event) => setTrainingUserId(event.target.value)} required>
                    <option value="">Selecionar usuario</option>
                    {operationalUsers.map((user) => (
                      <option key={user.id ?? user.email} value={String(user.id ?? '')}>
                        {user.name} - {user.email}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="field-stack">
                  <span>IT treinada</span>
                  <select value={trainingItId} onChange={(event) => setTrainingItId(event.target.value)} required>
                    <option value="">Selecionar IT</option>
                    {its.map((it) => (
                      <option key={it.id} value={it.id}>
                        {getItDisplayCode(it)}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="field-stack">
                  <span>Status do treinamento</span>
                  <select
                    value={trainingStatus}
                    onChange={(event) => setTrainingStatus(event.target.value as (typeof TRAINING_STATUS_OPTIONS)[number])}
                    required
                  >
                    {TRAINING_STATUS_OPTIONS.map((statusOption) => (
                      <option key={statusOption} value={statusOption}>
                        {statusOption === 'TREINADO'
                          ? 'Treinado'
                          : statusOption === 'NAO_TREINADO'
                            ? 'Nao treinado'
                            : statusOption === 'ATRASADO'
                              ? 'Atrasado'
                              : 'Pendente'}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="field-stack">
                  <span>Data do treinamento</span>
                  <input type="date" value={trainingDate} onChange={(event) => setTrainingDate(event.target.value)} required />
                </label>
                <label className="field-stack">
                  <span>Prazo ate o proximo (dias)</span>
                  <input
                    type="number"
                    min={1}
                    value={trainingDays}
                    onChange={(event) => setTrainingDays(Number(event.target.value) || 1)}
                    required
                  />
                </label>
              </div>
              {selectedTrainingUser ? (
                <div className="admin-training-status-card">
                  <div className="admin-training-status-header">
                    <strong>{selectedTrainingUser.name}</strong>
                    <span className={`status-chip tone-${getTrainingStatusMeta(selectedTrainingUser.nextTrainingDate, selectedTrainingUser.trainingStatus).tone}`}>
                      {getTrainingStatusMeta(selectedTrainingUser.nextTrainingDate, selectedTrainingUser.trainingStatus).label}
                    </span>
                  </div>
                  <div className="panel-muted">{buildTrainingStatusDescription(selectedTrainingUser)}</div>
                  <div className="admin-training-status-meta">
                    <span>Ultima IT: {selectedTrainingUser.lastTrainedIt ?? 'Nao informada'}</span>
                    <span>Proximo vencimento: {formatDateLabel(selectedTrainingUser.nextTrainingDate)}</span>
                  </div>
                </div>
              ) : null}
              <button type="submit" className="outline-button" disabled={updateTrainingMutation.isPending}>
                {updateTrainingMutation.isPending ? 'Salvando...' : 'Salvar treinamento'}
              </button>
              {trainingMsg ? (
                <div className={trainingMsg.ok ? 'upload-success' : 'upload-error'}>{trainingMsg.text}</div>
              ) : null}
            </form>
          </div>
        </section>
      </div>

      {isSuperAdmin && sectorEditorUserId && selectedManagedAdmin ? (
        <div className="admin-sector-modal-backdrop" onClick={closeSectorEditor}>
          <div className="admin-sector-modal" onClick={(event) => event.stopPropagation()}>
            <div className="admin-sector-modal-header">
              <div>
                <div className="panel-title">Setores do administrador</div>
                <div className="panel-muted">
                  {selectedManagedAdmin.name} - {selectedManagedAdmin.email}
                </div>
              </div>
              <button type="button" className="admin-sector-modal-close" onClick={closeSectorEditor}>
                Fechar
              </button>
            </div>

            <div className="admin-sector-modal-copy">
              Marque os setores que esse administrador pode acessar. No pr?ximo login, ele poder? escolher qualquer um deles.
            </div>

            {availableSectorCodes.length > 0 ? (
              <div className="admin-sector-modal-grid">
                {availableSectorCodes.map((codigo) => {
                  const checked = sectorEditorSelection.includes(codigo)
                  return (
                    <label
                      key={`modal-${codigo}`}
                      className={`admin-sector-option ${checked ? 'is-selected' : ''}`}
                    >
                      <input
                        type="checkbox"
                        checked={checked}
                        onChange={() => toggleSectorSelection(codigo)}
                      />
                      <span>{getSetorLabel(codigo)}</span>
                    </label>
                  )
                })}
              </div>
            ) : null}

            <label className="field-stack" style={{ marginTop: 16 }}>
              <span>Adicionar setores novos</span>
              <input
                value={sectorEditorCustom}
                onChange={(event) => setSectorEditorCustom(event.target.value)}
                placeholder="Ex.: Refinaria, Edificio_28"
              />
            </label>

            <div className="admin-sector-modal-actions">
              <button
                type="button"
                className="outline-button"
                onClick={() => applySectorUpdate(sectorEditorUserId)}
                disabled={updateUserSetoresMutation.isPending}
              >
                {updateUserSetoresMutation.isPending ? 'Aplicando...' : 'Aplicar'}
              </button>
              <button
                type="button"
                className="outline-button small"
                onClick={() => {
                  setSectorEditorSelection([])
                  setSectorEditorCustom('')
                  setUpdateSetoresMsg(null)
                }}
              >
                Limpar
              </button>
            </div>

            {updateSetoresMsg ? (
              <div className={updateSetoresMsg.ok ? 'upload-success' : 'upload-error'}>{updateSetoresMsg.text}</div>
            ) : null}
          </div>
        </div>
      ) : null}
    </section>
  )
}

