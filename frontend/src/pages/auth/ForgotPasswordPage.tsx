import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'

import { authService } from '../../services/auth/authService'
import { ApiClientError } from '../../services/http/apiClient'

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [recoveryCode, setRecoveryCode] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [mailMessage, setMailMessage] = useState<string | null>(null)
  const [recoveryMessage, setRecoveryMessage] = useState<string | null>(null)
  const [mailError, setMailError] = useState<string | null>(null)
  const [recoveryError, setRecoveryError] = useState<string | null>(null)
  const [isSubmittingMail, setIsSubmittingMail] = useState(false)
  const [isSubmittingRecovery, setIsSubmittingRecovery] = useState(false)

  async function handleForgotPassword(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setMailError(null)
    setMailMessage(null)
    setIsSubmittingMail(true)

    try {
      const response = await authService.forgotPassword({ email })
      setMailMessage(response.message)
    } catch (error) {
      setMailError(error instanceof ApiClientError ? error.message : 'Nao foi possivel enviar o email.')
    } finally {
      setIsSubmittingMail(false)
    }
  }

  async function handleRecoveryCodeReset(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setRecoveryError(null)
    setRecoveryMessage(null)
    setIsSubmittingRecovery(true)

    try {
      const response = await authService.resetPasswordWithRecoveryCode({
        email,
        recoveryCode,
        newPassword,
      })
      setRecoveryMessage(response.message)
      setRecoveryCode('')
      setNewPassword('')
    } catch (error) {
      setRecoveryError(error instanceof ApiClientError ? error.message : 'Nao foi possivel redefinir a senha.')
    } finally {
      setIsSubmittingRecovery(false)
    }
  }

  return (
    <div className="auth-page-wrapper">
      <div className="auth-card-outer">
        <div className="auth-form-title">Recuperacao de acesso</div>
        <div className="auth-helper-text">
          Use o link por e-mail como forma principal de redefinicao. O codigo de recuperacao deve ser usado apenas se ele foi reenviado pelo administrador.
        </div>

        <form className="auth-form" onSubmit={handleForgotPassword}>
          <div className="auth-form-title" style={{ fontSize: '1rem' }}>Receber link por e-mail</div>
          <label className="auth-field">
            <span>Email corporativo</span>
            <input
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              placeholder="seu@email.com"
              required
              autoComplete="email"
            />
          </label>
          {mailError ? <div className="auth-error">{mailError}</div> : null}
          {mailMessage ? <div className="upload-success">{mailMessage}</div> : null}
          <button type="submit" className="auth-submit-btn" disabled={isSubmittingMail}>
            {isSubmittingMail ? 'Enviando...' : 'Enviar link por e-mail'}
          </button>
        </form>

        <form className="auth-form" onSubmit={handleRecoveryCodeReset}>
          <div className="auth-form-title" style={{ fontSize: '1rem' }}>Usar codigo fornecido pelo administrador</div>
          <div className="auth-helper-text" style={{ marginBottom: 0 }}>
            Use esta opcao somente se o administrador gerou e reenviou um novo codigo de recuperacao para voce.
          </div>
          <label className="auth-field">
            <span>Codigo de recuperacao</span>
            <input
              type="text"
              value={recoveryCode}
              onChange={(event) => setRecoveryCode(event.target.value.toUpperCase())}
              placeholder="Ex.: MASTER-RECOVERY-2026"
              required
            />
          </label>
          <label className="auth-field">
            <span>Nova senha</span>
            <input
              type="password"
              value={newPassword}
              onChange={(event) => setNewPassword(event.target.value)}
              placeholder="Digite a nova senha"
              required
              minLength={8}
              autoComplete="new-password"
            />
          </label>
          {recoveryError ? <div className="auth-error">{recoveryError}</div> : null}
          {recoveryMessage ? <div className="upload-success">{recoveryMessage}</div> : null}
          <button type="submit" className="auth-submit-btn" disabled={isSubmittingRecovery}>
            {isSubmittingRecovery ? 'Redefinindo...' : 'Redefinir com codigo'}
          </button>
        </form>

        <div className="auth-helper-text" style={{ marginTop: 12 }}>
          <Link to="/login">Voltar para o login</Link>
        </div>
      </div>
    </div>
  )
}
