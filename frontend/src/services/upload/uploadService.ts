import { getAccessToken, invalidateAuthSession } from '../../utils/storage'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

class UploadError extends Error {
  status: number
  constructor(message: string, status: number) {
    super(message)
    this.name = 'UploadError'
    this.status = status
  }
}

interface UploadItPdfOptions {
  existingItId?: string
  documento?: string
  revisao?: string
  dataPublicacao?: string
  paginaAtual?: number
  totalPaginas?: number
  prazoTreinamentoDias?: number
}

async function uploadMultipart(path: string, formData: FormData): Promise<{ message: string }> {
  const token = getAccessToken()
  const headers = new Headers()
  if (token) headers.set('Authorization', `Bearer ${token}`)

  let response: Response
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      method: 'POST',
      headers,
      body: formData,
    })
  } catch {
    throw new UploadError('Nao foi possivel conectar ao backend.', 0)
  }

  if (!response.ok) {
    let message = `Erro ${response.status}`
    try {
      const body = (await response.json()) as { message?: string }
      if (body.message) message = body.message
    } catch {
      // ignore
    }
    if (response.status === 401) {
      const reason = message.toLowerCase().includes('expirou') ? 'expired' : 'unauthorized'
      invalidateAuthSession(reason)
    }
    throw new UploadError(message, response.status)
  }

  return response.json() as Promise<{ message: string }>
}

export const uploadService = {
  uploadPtsExcel: (file: File, setor: string) => {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('setor', setor)
    return uploadMultipart('/it/upload/pts', formData)
  },

  uploadItPdf: (file: File, status: string, setor: string, options: UploadItPdfOptions = {}) => {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('status', status)
    formData.append('setor', setor)

    if (options.existingItId) formData.append('existingItId', options.existingItId)
    if (options.documento) formData.append('documento', options.documento)
    if (options.revisao) formData.append('revisao', options.revisao)
    if (options.dataPublicacao) formData.append('dataPublicacao', options.dataPublicacao)
    if (typeof options.paginaAtual === 'number') formData.append('paginaAtual', String(options.paginaAtual))
    if (typeof options.totalPaginas === 'number') formData.append('totalPaginas', String(options.totalPaginas))
    if (typeof options.prazoTreinamentoDias === 'number') {
      formData.append('prazoTreinamentoDias', String(options.prazoTreinamentoDias))
    }

    return uploadMultipart('/it/upload/pdf', formData)
  },
}
