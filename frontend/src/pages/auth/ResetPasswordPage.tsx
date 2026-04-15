import { useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'

import { authService } from '../../services/auth/authService'
import { ApiClientError } from '../../services/http/apiClient'

export function ResetPasswordPage() {
  const [searchParams] = useSearchParams()
  const token = useMemo(() => searchParams.get('token') ?? '', [searchParams])
  const [newPassword, setNewPassword] = useState('')
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setMessage(null)
    setIsSubmitting(true)

    try {
      const response = await authService.resetPassword({ token, newPassword })
      setMessage(response.message)
      setNewPassword('')
    } catch (caughtError) {
      setError(caughtError instanceof ApiClientError ? caughtError.message : 'Nao foi possivel redefinir a senha.')
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="auth-page-wrapper">
      <div className="auth-card-outer">
        <div className="auth-form-title">Nova senha</div>
        <div className="auth-helper-text">Use o link recebido por e-mail para definir sua nova senha.</div>

        {!token ? <div className="auth-error">Link invalido ou ausente.</div> : null}

        <form className="auth-form" onSubmit={handleSubmit}>
          <label className="auth-field">
            <span>Nova senha</span>
            <input
              type="password"
              value={newPassword}
              onChange={(event) => setNewPassword(event.target.value)}
              placeholder="Digite sua nova senha"
              required
              minLength={8}
              autoComplete="new-password"
              disabled={!token}
            />
          </label>

          {error ? <div className="auth-error">{error}</div> : null}
          {message ? <div className="upload-success">{message}</div> : null}

          <button type="submit" className="auth-submit-btn" disabled={isSubmitting || !token}>
            {isSubmitting ? 'Salvando...' : 'Salvar nova senha'}
          </button>
        </form>

        <div className="auth-helper-text" style={{ marginTop: 12 }}>
          <Link to="/login">Voltar para o login</Link>
        </div>
      </div>
    </div>
  )
}
