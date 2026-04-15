import { apiClient } from '../http/apiClient'
import type { UserProfile } from '../../types/auth'

interface CreateUserPayload {
  name: string
  email: string
  password: string
  role: string
  cargo: string
  setores: string
}

interface UpdateTrainingPayload {
  lastTrainedIt: string
  trainingStatus: string
  lastTrainingDate: string
  retrainingIntervalDays: number
}

interface UpdateUserSetoresPayload {
  setores: string
}

interface CreateUserResponse {
  message: string
  recoveryCode: string
  emailSent: boolean
  user: UserProfile
}

interface UpdateRecoveryCodeResponse {
  message: string
  recoveryCode: string
  emailSent: boolean
}

export interface SetorRecord {
  id: string
  codigo: string
}

export const userService = {
  listUsers: (setor?: string) =>
    apiClient.get<UserProfile[]>(`/api/admin/users${setor ? `?setor=${encodeURIComponent(setor)}` : ''}`),
  createUser: (payload: CreateUserPayload) => apiClient.post<CreateUserResponse>('/api/admin/users', payload),
  deleteUser: (id: string) => apiClient.delete<void>(`/api/admin/users/${id}`),
  updateTraining: (id: string, payload: UpdateTrainingPayload) =>
    apiClient.put<UserProfile>(`/api/admin/users/${id}/training`, payload),
  updateRecoveryCode: (id: string) =>
    apiClient.put<UpdateRecoveryCodeResponse>(`/api/admin/users/${id}/recovery-code`),
  updateUserSetores: (id: string, payload: UpdateUserSetoresPayload) =>
    apiClient.put<UserProfile>(`/api/admin/users/${id}/setores`, payload),
  listSetores: () => apiClient.get<SetorRecord[]>('/api/admin/setores'),
  createSetor: (codigo: string) => apiClient.post<SetorRecord>('/api/admin/setores', { codigo }),
}
