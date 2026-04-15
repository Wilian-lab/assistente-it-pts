import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'

import { useAuth } from '../../hooks/useAuth'
import { authService } from '../../services/auth/authService'
import { ApiClientError } from '../../services/http/apiClient'
import { getSetorBrandName, getSetorLabel } from '../../types/setor'

export function LoginPage() {
  const navigate = useNavigate()
  const { login } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [setor, setSetor] = useState<string>('')
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const setoresQuery = useQuery({
    queryKey: ['auth-setores'],
    queryFn: authService.listSetores,
  })
  const setores = setoresQuery.data ?? []
  const selectedSetorLabel = getSetorBrandName(setor)

  useEffect(() => {
    if (!setor && setores.length > 0) {
      setSetor(setores[0]?.codigo ?? '')
    }
  }, [setor, setores])

  useEffect(() => {
    document.title = selectedSetorLabel ? `PTS ${selectedSetorLabel}` : 'PTS'
  }, [selectedSetorLabel])

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setIsSubmitting(true)

    try {
      await login({ email, password, setor })
      navigate('/')
    } catch (caughtError) {
      if (caughtError instanceof ApiClientError) {
        setError(caughtError.message)
      } else {
        setError('Nao foi possivel autenticar. Verifique suas credenciais.')
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="auth-page-wrapper">
      <div className="auth-card-outer">
        <div className="auth-brand-block">
          <div className="auth-logo-mark">PTS</div>
          <div className="auth-brand-name">Sistema de controle de ITs e PTS</div>
        </div>
        <div className="auth-helper-text">Sistema de gestao e treinamento</div>

        <form className="auth-form" onSubmit={handleSubmit}>
          <div className="auth-form-title">Acessar o sistema</div>

          <label className="auth-field">
            <span>Email</span>
            <input
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              placeholder="seu@email.com"
              required
              autoComplete="email"
            />
          </label>

          <label className="auth-field">
            <span>Senha</span>
            <input
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              placeholder="********"
              required
              autoComplete="current-password"
            />
          </label>

          <label className="auth-field">
            <span>Setor</span>
            <select
              value={setor}
              onChange={(event) => setSetor(event.target.value)}
              required
              disabled={setoresQuery.isLoading || setores.length === 0}
            >
              <option value="">
                {setoresQuery.isLoading ? 'Carregando setores...' : 'Selecione o setor'}
              </option>
              {setores.map((item) => (
                <option key={item.codigo} value={item.codigo}>
                  {getSetorLabel(item.codigo)}
                </option>
              ))}
            </select>
          </label>

          {setoresQuery.error instanceof ApiClientError ? (
            <div className="auth-error">{setoresQuery.error.message}</div>
          ) : null}
          {error ? <div className="auth-error">{error}</div> : null}

          <button type="submit" className="auth-submit-btn" disabled={isSubmitting}>
            {isSubmitting ? 'Entrando...' : 'Entrar'}
          </button>

          <div className="auth-helper-text" style={{ marginTop: 12 }}>
            <Link to="/forgot-password">Esqueci minha senha</Link>
          </div>
        </form>

        <div className="auth-footer-note">
          Sistema de consulta de ITs PTS
        </div>
      </div>
    </div>
  )
}
