import { apiClient } from '../http/apiClient'
import type { ItPayload, ManagedIt } from '../../types/it'

export const itService = {
  list: () => apiClient.get<ManagedIt[]>('/it'),
  getById: (id: string) => apiClient.get<ManagedIt>(`/it/${id}`),
  create: (payload: ItPayload) => apiClient.post<ManagedIt>('/it', payload),
  update: (id: string, payload: ItPayload) => apiClient.put<ManagedIt>(`/it/${id}`, payload),
  remove: (id: string) => apiClient.delete<void>(`/it/${id}`),
  syncFiles: () => apiClient.post<{ message: string }>('/it/sync'),
}
