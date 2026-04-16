import { useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { useAuth } from '../../hooks/useAuth'
import { useIts } from '../../hooks/useIts'
import { openProtectedItFile } from '../../services/http/apiClient'
import { itService } from '../../services/its/itService'
import { ptsService } from '../../services/pts/ptsService'
import { uploadService } from '../../services/upload/uploadService'
import { getItDisplayCode, getItDisplayTitle, normalizeItStatus } from '../../types/it'
import { getSetorLabel } from '../../types/setor'

const STATUS_OPTIONS = ['Atualizada', 'Pendente', 'Cópia não controlada'] as const

export function FilesPage() {
  const { user } = useAuth()
  const queryClient = useQueryClient()
  const isAdmin = String(user?.role ?? '').toUpperCase() === 'ADMIN'
  const activeSector = String(user?.setorAtivo ?? '').trim()
  const itsQuery = useIts()
  const ptsFilesQuery = useQuery({
    queryKey: ['pts-files', activeSector],
    queryFn: ptsService.getFiles,
    staleTime: 15_000,
    refetchOnWindowFocus: true,
    refetchInterval: 20_000,
  })

  const [deleteMsg, setDeleteMsg] = useState<{ ok: boolean; text: string } | null>(null)
  const [ptsDeleteMsg, setPtsDeleteMsg] = useState<{ ok: boolean; text: string } | null>(null)

  const deleteMutation = useMutation({
    mutationFn: (id: string) => itService.remove(id),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['its'] })
      setDeleteMsg({ ok: true, text: 'IT excluída com sucesso.' })
    },
    onError: () => setDeleteMsg({ ok: false, text: 'Erro ao excluir IT.' }),
  })

  const deletePtsMutation = useMutation({
    mutationFn: () => ptsService.deleteCurrentFile(),
    onSuccess: async (result) => {
      setPtsDeleteMsg({ ok: true, text: result.message || 'Arquivo PTS excluído com sucesso.' })
      await queryClient.invalidateQueries({ queryKey: ['pts-files'] })
      await queryClient.invalidateQueries({ queryKey: ['pts-products'] })
      await queryClient.invalidateQueries({ queryKey: ['pts-items'] })
      await queryClient.invalidateQueries({ queryKey: ['pts-data'] })
    },
    onError: (error: unknown) => {
      const message = error instanceof Error ? error.message : 'Erro ao excluir arquivo PTS.'
      setPtsDeleteMsg({ ok: false, text: message })
    },
  })

  const [ptsFile, setPtsFile] = useState<File | null>(null)
  const [ptsUploading, setPtsUploading] = useState(false)
  const [ptsResult, setPtsResult] = useState<{ ok: boolean; message: string } | null>(null)
  const ptsInputRef = useRef<HTMLInputElement>(null)

  const [pdfFiles, setPdfFiles] = useState<File[]>([])
  const [pdfStatus, setPdfStatus] = useState<(typeof STATUS_OPTIONS)[number]>('Atualizada')
  const [pdfUploading, setPdfUploading] = useState(false)
  const [pdfResults, setPdfResults] = useState<Array<{ name: string; ok: boolean; message: string }>>([])
  const pdfInputRef = useRef<HTMLInputElement>(null)

  async function handlePtsUpload() {
    if (!ptsFile) return
    setPtsUploading(true)
    setPtsResult(null)
    setPtsDeleteMsg(null)
    try {
      const res = await uploadService.uploadPtsExcel(ptsFile, activeSector)
      setPtsResult({ ok: true, message: res.message || 'Planilha enviada com sucesso.' })
      await queryClient.invalidateQueries({ queryKey: ['pts-files'] })
      await queryClient.invalidateQueries({ queryKey: ['pts-products'] })
      await queryClient.invalidateQueries({ queryKey: ['pts-items'] })
      await queryClient.invalidateQueries({ queryKey: ['pts-data'] })
      setPtsFile(null)
      if (ptsInputRef.current) ptsInputRef.current.value = ''
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Erro ao enviar planilha.'
      setPtsResult({ ok: false, message: msg })
    } finally {
      setPtsUploading(false)
    }
  }

  async function handlePdfUpload() {
    if (pdfFiles.length === 0) return
    setPdfUploading(true)
    setPdfResults([])
    const results: Array<{ name: string; ok: boolean; message: string }> = []

    for (const file of pdfFiles) {
      try {
        const res = await uploadService.uploadItPdf(file, pdfStatus, activeSector)
        results.push({ name: file.name, ok: true, message: res.message || 'Enviado com sucesso.' })
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : 'Erro ao enviar.'
        results.push({ name: file.name, ok: false, message: msg })
      }
    }

    setPdfResults(results)
    setPdfUploading(false)

    if (results.some((result) => result.ok)) {
      await queryClient.invalidateQueries({ queryKey: ['its'] })
      setPdfFiles([])
      if (pdfInputRef.current) pdfInputRef.current.value = ''
    }
  }

  if (!isAdmin) {
    return (
      <section className="page-section">
        <div className="dashboard-section-title">Arquivos</div>
        <div className="dashboard-section-subtitle">O upload de arquivos é uma funcionalidade exclusiva do administrador.</div>
        <div className="panel-block streamlit-card">
          <p>Para enviar planilhas PTS ou documentos IT, entre com uma conta de administrador.</p>
        </div>
      </section>
    )
  }

  const ptsFiles = ptsFilesQuery.data ?? []

  return (
    <section className="page-section">
      <div className="dashboard-section-title">Arquivos</div>
      <div className="dashboard-section-subtitle">
        Envie a planilha PTS (.xlsx) e os documentos IT em PDF. As ITs e os PTS ficam disponíveis para consulta após o
        upload.
      </div>
      <div className="dashboard-section-subtitle">Setor ativo: {getSetorLabel(activeSector)}</div>

      <div className="upload-cards-grid">
        <div className="upload-dropzone panel-block">
          <div className="panel-title small-title">Planilha PTS (.xlsx)</div>
          <p className="helper-text">
            Envie o arquivo PTS_Agriproducts.xlsx para o setor ativo. Os dados serão importados automaticamente para a
            Documentação desse setor.
          </p>
          <input
            ref={ptsInputRef}
            className="native-file-input"
            type="file"
            accept=".xlsx"
            onChange={(event) => setPtsFile(event.target.files?.[0] ?? null)}
          />
          {ptsFile ? (
            <div className="upload-file-name">
              Selecionado: <strong>{ptsFile.name}</strong>
            </div>
          ) : null}
          <button
            type="button"
            className="outline-button upload-send-btn"
            onClick={handlePtsUpload}
            disabled={!ptsFile || ptsUploading}
          >
            {ptsUploading ? 'Enviando...' : 'Enviar planilha'}
          </button>
          {ptsResult ? <div className={ptsResult.ok ? 'upload-success' : 'upload-error'}>{ptsResult.message}</div> : null}
          {ptsDeleteMsg ? <div className={ptsDeleteMsg.ok ? 'upload-success' : 'upload-error'}>{ptsDeleteMsg.text}</div> : null}
        </div>

        <div className="upload-dropzone panel-block">
          <div className="panel-title small-title">Documentos IT em PDF</div>
          <p className="helper-text">
            Selecione um ou mais arquivos PDF. O nome do arquivo será usado como código do documento. As ITs aparecerão
            automaticamente em &quot;Minhas ITs&quot;.
          </p>
          <input
            ref={pdfInputRef}
            className="native-file-input"
            type="file"
            accept=".pdf"
            multiple
            onChange={(event) => setPdfFiles(Array.from(event.target.files ?? []))}
          />
          {pdfFiles.length > 0 ? (
            <div className="upload-file-name">
              {pdfFiles.length === 1 ? `Selecionado: ${pdfFiles[0].name}` : `${pdfFiles.length} arquivos selecionados`}
            </div>
          ) : null}
          <label className="auth-field">
            <span>Status dos documentos</span>
            <select value={pdfStatus} onChange={(event) => setPdfStatus(event.target.value as typeof pdfStatus)}>
              {STATUS_OPTIONS.map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </select>
          </label>
          <button
            type="button"
            className="outline-button upload-send-btn"
            onClick={handlePdfUpload}
            disabled={pdfFiles.length === 0 || pdfUploading}
          >
            {pdfUploading ? 'Enviando...' : `Enviar PDF${pdfFiles.length > 1 ? 's' : ''}`}
          </button>
          {pdfResults.length > 0 ? (
            <div className="upload-results-list">
              {pdfResults.map((result) => (
                <div key={result.name} className={result.ok ? 'upload-success' : 'upload-error'}>
                  <strong>{result.name}</strong>: {result.message}
                </div>
              ))}
            </div>
          ) : null}
        </div>
      </div>

      <div className="panel-block streamlit-card" style={{ marginTop: 20 }}>
        <div className="panel-row-between files-table-head" style={{ marginBottom: 12 }}>
          <div className="panel-title">PTS cadastrados</div>
          <div className="helper-text">{ptsFiles.length} arquivo(s)</div>
        </div>
        {ptsFilesQuery.isLoading ? <p className="helper-text">Carregando...</p> : null}
        <div className="table-shell">
          <table className="streamlit-table">
            <thead>
              <tr>
                <th>Setor</th>
                <th>Arquivo</th>
                <th>Última atualização</th>
                <th>Registros</th>
                <th>Ações</th>
              </tr>
            </thead>
            <tbody>
              {ptsFiles.map((file) => (
                <tr key={`${file.setor}-${file.fileName}`}>
                  <td>{file.setor}</td>
                  <td>{file.fileName}</td>
                  <td>{file.lastModified}</td>
                  <td>{file.recordsCount}</td>
                  <td>
                    <div className="table-actions">
                      <button
                        type="button"
                        className="outline-button small danger-button"
                        onClick={() => {
                          if (window.confirm(`Excluir o PTS ${file.fileName}?`)) {
                            deletePtsMutation.mutate()
                          }
                        }}
                        disabled={deletePtsMutation.isPending}
                      >
                        Excluir
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
              {!ptsFilesQuery.isLoading && ptsFiles.length === 0 ? (
                <tr>
                  <td colSpan={5} style={{ textAlign: 'center', color: '#6b7fa3' }}>
                    Nenhum PTS cadastrado. Faça upload da planilha acima.
                  </td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </div>
      </div>

      <div className="panel-block streamlit-card" style={{ marginTop: 20 }}>
        <div className="panel-row-between files-table-head" style={{ marginBottom: 12 }}>
          <div className="panel-title">ITs cadastradas</div>
          <div className="helper-text">{itsQuery.data?.length ?? 0} documentos</div>
        </div>
        {itsQuery.isLoading ? <p className="helper-text">Carregando...</p> : null}
        {deleteMsg ? (
          <div className={deleteMsg.ok ? 'upload-success' : 'upload-error'} style={{ marginBottom: 10 }}>
            {deleteMsg.text}
          </div>
        ) : null}
        <div className="table-shell">
          <table className="streamlit-table">
            <thead>
              <tr>
                <th>Código</th>
                <th>Título</th>
                <th>Revisão</th>
                <th>Status</th>
                <th>Ações</th>
              </tr>
            </thead>
            <tbody>
              {(itsQuery.data ?? []).map((it) => {
                const status = normalizeItStatus(it.status)
                return (
                  <tr key={it.id}>
                    <td>{getItDisplayCode(it)}</td>
                    <td>{getItDisplayTitle(it)}</td>
                    <td>{it.revisao}</td>
                    <td>
                      <span className={`status-chip tone-${status.tone}`}>{status.label}</span>
                    </td>
                    <td>
                      <div className="table-actions">
                        <button
                          type="button"
                          className="outline-button small"
                          onClick={() => {
                            void openProtectedItFile(it.id)
                          }}
                        >
                          Abrir PDF
                        </button>
                        <button
                          type="button"
                          className="outline-button small danger-button"
                          onClick={() => {
                            if (window.confirm(`Excluir a IT ${getItDisplayCode(it)}?`)) {
                              setDeleteMsg(null)
                              deleteMutation.mutate(it.id)
                            }
                          }}
                          disabled={deleteMutation.isPending}
                        >
                          Excluir
                        </button>
                      </div>
                    </td>
                  </tr>
                )
              })}
              {!itsQuery.isLoading && (itsQuery.data?.length ?? 0) === 0 ? (
                <tr>
                  <td colSpan={5} style={{ textAlign: 'center', color: '#6b7fa3' }}>
                    Nenhuma IT cadastrada. Faça upload dos PDFs acima.
                  </td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </div>
      </div>
    </section>
  )
}

