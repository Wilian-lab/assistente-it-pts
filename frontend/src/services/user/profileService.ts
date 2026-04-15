import type { ChangePasswordPayload, UpdateProfilePayload, UserProfile } from '../../types/auth'
import { apiClient, fetchAuthorizedBlobUrl, uploadAuthorizedFile } from '../http/apiClient'

export const profileService = {
  updateProfile: (payload: UpdateProfilePayload) => apiClient.put<UserProfile>('/usuario/me/profile', payload),
  changePassword: (payload: ChangePasswordPayload) => apiClient.put<void>('/usuario/me/password', payload),
  uploadAvatar: (file: File) => uploadAuthorizedFile<UserProfile>('/usuario/me/avatar', file),
  removeAvatar: () => apiClient.delete<UserProfile>('/usuario/me/avatar'),
  getAvatarUrl: () => fetchAuthorizedBlobUrl('/usuario/me/avatar'),
}
