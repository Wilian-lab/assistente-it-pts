import { apiClient } from '../http/apiClient'
import type {
  ForgotPasswordPayload,
  LoginPayload,
  LoginResponse,
  RecoveryCodeResetPayload,
  ResetPasswordPayload,
  SetorOption,
  SwitchSectorPayload,
  UserProfile,
} from '../../types/auth'

export const authService = {
  login: (payload: LoginPayload) => apiClient.post<LoginResponse>('/auth/login', payload),
  switchSector: (payload: SwitchSectorPayload) => apiClient.post<LoginResponse>('/auth/switch-sector', payload),
  listSetores: () => apiClient.get<SetorOption[]>('/auth/setores'),
  listManageableSetores: () => apiClient.get<SetorOption[]>('/api/admin/setores'),
  getCurrentUser: () => apiClient.get<UserProfile>('/usuario/me'),
  forgotPassword: (payload: ForgotPasswordPayload) => apiClient.post<{ message: string }>('/auth/forgot-password', payload),
  resetPassword: (payload: ResetPasswordPayload) => apiClient.post<{ message: string }>('/auth/reset-password', payload),
  resetPasswordWithRecoveryCode: (payload: RecoveryCodeResetPayload) =>
    apiClient.post<{ message: string }>('/auth/reset-password/recovery-code', payload),
}
