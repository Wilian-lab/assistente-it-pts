export type UserRole = 'SUPER_ADMIN' | 'ADMIN' | 'USER'

export interface UserProfile {
  id: string | null
  name: string
  email: string
  role: UserRole | string | null
  cargo: string | null
  setores?: string | null
  setorAtivo?: string | null
  profileImageUrl?: string | null
  lastTrainedIt: string | null
  trainingStatus?: string | null
  lastTrainingDate: string | null
  retrainingIntervalDays: number | null
  nextTrainingDate: string | null
}

export function getFirstName(user: UserProfile | null): string {
  const raw = user?.name?.trim() || user?.email?.split('@', 1)[0] || 'Usuario'
  return raw.split(' ')[0] || 'Usuario'
}

export interface LoginResponse {
  accessToken: string
  expiresIn: number
  user: UserProfile
}

export interface LoginPayload {
  email: string
  setor: string
  password: string
}

export interface SwitchSectorPayload {
  setor: string
}

export interface SetorOption {
  codigo: string
}

export interface ForgotPasswordPayload {
  email: string
}

export interface ResetPasswordPayload {
  token: string
  newPassword: string
}

export interface RecoveryCodeResetPayload {
  email: string
  recoveryCode: string
  newPassword: string
}

export interface UpdateProfilePayload {
  name: string
}

export interface ChangePasswordPayload {
  currentPassword: string
  newPassword: string
}
