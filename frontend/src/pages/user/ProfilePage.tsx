import { useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'

import { useAuth } from '../../hooks/useAuth'
import { ApiClientError } from '../../services/http/apiClient'
import { profileService } from '../../services/user/profileService'
import { getSetorLabel } from '../../types/setor'

interface FeedbackState {
  ok: boolean
  text: string
}

function splitNameParts(name: string | null | undefined) {
  const trimmed = String(name ?? '').trim()
  if (!trimmed) return { firstName: '', lastName: '' }
  const parts = trimmed.split(/\s+/)
  return {
    firstName: parts[0] ?? '',
    lastName: parts.slice(1).join(' '),
  }
}

export function ProfilePage() {
  const { user, refreshUser } = useAuth()
  const nameParts = useMemo(() => splitNameParts(user?.name), [user?.name])
  const [firstName, setFirstName] = useState(nameParts.firstName)
  const [lastName, setLastName] = useState(nameParts.lastName)
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [avatarUrl, setAvatarUrl] = useState<string | null>(null)
  const [profileMsg, setProfileMsg] = useState<FeedbackState | null>(null)
  const [passwordMsg, setPasswordMsg] = useState<FeedbackState | null>(null)
  const [avatarMsg, setAvatarMsg] = useState<FeedbackState | null>(null)
  const [isSavingProfile, setIsSavingProfile] = useState(false)
  const [isSavingPassword, setIsSavingPassword] = useState(false)
  const [isUploadingAvatar, setIsUploadingAvatar] = useState(false)

  useEffect(() => {
    setFirstName(nameParts.firstName)
    setLastName(nameParts.lastName)
  }, [nameParts.firstName, nameParts.lastName])

  useEffect(() => {
    let currentUrl: string | null = null

    async function loadAvatar() {
      if (!user?.profileImageUrl) {
        setAvatarUrl(null)
        return
      }

      try {
        const objectUrl = await profileService.getAvatarUrl()
        currentUrl = objectUrl
        setAvatarUrl(objectUrl)
      } catch {
        setAvatarUrl(null)
      }
    }

    void loadAvatar()

    return () => {
      if (currentUrl) {
        URL.revokeObjectURL(currentUrl)
      }
    }
  }, [user?.profileImageUrl])

  async function handleProfileSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setProfileMsg(null)

    if (!firstName.trim() || !lastName.trim()) {
      setProfileMsg({ ok: false, text: 'Informe nome e sobrenome.' })
      return
    }

    setIsSavingProfile(true)
    try {
      await profileService.updateProfile({ name: `${firstName.trim()} ${lastName.trim()}` })
      await refreshUser()
      setProfileMsg({ ok: true, text: 'Perfil atualizado com sucesso.' })
    } catch (error) {
      setProfileMsg({
        ok: false,
        text: error instanceof ApiClientError ? error.message : 'Nao foi possivel atualizar o perfil.',
      })
    } finally {
      setIsSavingProfile(false)
    }
  }

  async function handlePasswordSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setPasswordMsg(null)

    if (newPassword.length < 8) {
      setPasswordMsg({ ok: false, text: 'A nova senha deve conter no minimo 8 caracteres.' })
      return
    }
    if (newPassword !== confirmPassword) {
      setPasswordMsg({ ok: false, text: 'A confirmacao da nova senha nao confere.' })
      return
    }

    setIsSavingPassword(true)
    try {
      await profileService.changePassword({ currentPassword, newPassword })
      setCurrentPassword('')
      setNewPassword('')
      setConfirmPassword('')
      setPasswordMsg({ ok: true, text: 'Senha alterada com sucesso.' })
    } catch (error) {
      setPasswordMsg({
        ok: false,
        text: error instanceof ApiClientError ? error.message : 'Nao foi possivel alterar a senha.',
      })
    } finally {
      setIsSavingPassword(false)
    }
  }

  async function handleAvatarChange(file: File | null) {
    setAvatarMsg(null)
    if (!file) return

    setIsUploadingAvatar(true)
    try {
      await profileService.uploadAvatar(file)
      await refreshUser()
      const objectUrl = await profileService.getAvatarUrl()
      setAvatarUrl((current) => {
        if (current) URL.revokeObjectURL(current)
        return objectUrl
      })
      setAvatarMsg({ ok: true, text: 'Foto de perfil atualizada com sucesso.' })
    } catch (error) {
      setAvatarMsg({
        ok: false,
        text: error instanceof ApiClientError ? error.message : 'Nao foi possivel atualizar a foto de perfil.',
      })
    } finally {
      setIsUploadingAvatar(false)
    }
  }

  async function handleAvatarRemove() {
    setAvatarMsg(null)
    setIsUploadingAvatar(true)
    try {
      await profileService.removeAvatar()
      await refreshUser()
      setAvatarUrl((current) => {
        if (current) URL.revokeObjectURL(current)
        return null
      })
      setAvatarMsg({ ok: true, text: 'Foto de perfil removida com sucesso.' })
    } catch (error) {
      setAvatarMsg({
        ok: false,
        text: error instanceof ApiClientError ? error.message : 'Nao foi possivel remover a foto de perfil.',
      })
    } finally {
      setIsUploadingAvatar(false)
    }
  }

  return (
    <section className="page-section profile-page">
      <div className="dashboard-section-title">Meu perfil</div>
      <div className="dashboard-section-subtitle">Atualize seus dados, foto de perfil e senha sem sair da conta.</div>

      <div className="profile-layout">
        <section className="streamlit-card panel-block profile-summary-card">
          {avatarUrl ? (
            <div
              className="profile-summary-backdrop"
              style={{ backgroundImage: `url(${avatarUrl})` }}
              aria-hidden="true"
            />
          ) : null}
          <div className="profile-avatar-shell">
            {avatarUrl ? (
              <img src={avatarUrl} alt={`Foto de perfil de ${user?.name ?? 'usuario'}`} className="profile-avatar-image" />
            ) : (
              <div className="profile-avatar-fallback">{String(firstName || user?.name || 'U').charAt(0).toUpperCase()}</div>
            )}
          </div>
          <div className="profile-summary-copy">
            <div className="panel-title">{user?.name ?? 'Usuario'}</div>
            <div className="panel-muted">{user?.email ?? '-'}</div>
            <div className="panel-muted">Setor ativo: {getSetorLabel(user?.setorAtivo ?? '')}</div>
            <div className="panel-muted">Perfil: {String(user?.role ?? 'USER')}</div>
          </div>
          <div className="profile-avatar-actions">
            <label className="outline-button profile-upload-button">
              {isUploadingAvatar ? 'Enviando foto...' : 'Trocar foto'}
              <input type="file" accept="image/*" hidden onChange={(event) => void handleAvatarChange(event.target.files?.[0] ?? null)} />
            </label>
            <button type="button" className="outline-button danger-button" onClick={() => void handleAvatarRemove()} disabled={isUploadingAvatar || !user?.profileImageUrl}>
              Remover foto
            </button>
          </div>
          {avatarMsg ? <div className={avatarMsg.ok ? 'upload-success' : 'upload-error'}>{avatarMsg.text}</div> : null}
        </section>

        <form className="streamlit-card panel-block" onSubmit={handleProfileSubmit}>
          <div className="panel-title">Dados pessoais</div>
          <div className="form-grid-two">
            <label className="field-stack">
              <span>Nome</span>
              <input value={firstName} onChange={(event) => setFirstName(event.target.value)} required />
            </label>
            <label className="field-stack">
              <span>Sobrenome</span>
              <input value={lastName} onChange={(event) => setLastName(event.target.value)} required />
            </label>
            <label className="field-stack">
              <span>Email</span>
              <input value={user?.email ?? ''} readOnly disabled />
            </label>
            <label className="field-stack">
              <span>Setor ativo</span>
              <input value={getSetorLabel(user?.setorAtivo ?? '')} readOnly disabled />
            </label>
          </div>
          <button type="submit" className="outline-button" disabled={isSavingProfile}>
            {isSavingProfile ? 'Salvando...' : 'Salvar perfil'}
          </button>
          {profileMsg ? <div className={profileMsg.ok ? 'upload-success' : 'upload-error'}>{profileMsg.text}</div> : null}
        </form>

        <form className="streamlit-card panel-block" onSubmit={handlePasswordSubmit}>
          <div className="panel-title">Alterar senha</div>
          <div className="form-grid-two">
            <label className="field-stack">
              <span>Senha atual</span>
              <input type="password" value={currentPassword} onChange={(event) => setCurrentPassword(event.target.value)} autoComplete="current-password" required />
            </label>
            <label className="field-stack">
              <span>Nova senha</span>
              <input type="password" value={newPassword} onChange={(event) => setNewPassword(event.target.value)} autoComplete="new-password" minLength={8} required />
            </label>
            <label className="field-stack">
              <span>Confirmar nova senha</span>
              <input type="password" value={confirmPassword} onChange={(event) => setConfirmPassword(event.target.value)} autoComplete="new-password" minLength={8} required />
            </label>
          </div>
          <button type="submit" className="outline-button" disabled={isSavingPassword}>
            {isSavingPassword ? 'Salvando...' : 'Alterar senha'}
          </button>
          {passwordMsg ? <div className={passwordMsg.ok ? 'upload-success' : 'upload-error'}>{passwordMsg.text}</div> : null}
        </form>
      </div>
    </section>
  )
}
