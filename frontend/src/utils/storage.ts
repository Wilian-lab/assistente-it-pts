const ACCESS_TOKEN_KEY = 'pts.accessToken'
const ACCESS_TOKEN_EXPIRES_AT_KEY = 'pts.accessTokenExpiresAt'

export const AUTH_SESSION_INVALIDATED_EVENT = 'pts.auth.sessionInvalidated'

export type AuthInvalidationReason = 'expired' | 'unauthorized'

function hasWindow() {
  return typeof window !== 'undefined'
}

function readExpiresAt(): number | null {
  if (!hasWindow()) return null
  const raw = window.localStorage.getItem(ACCESS_TOKEN_EXPIRES_AT_KEY)
  const value = Number(raw)
  return Number.isFinite(value) && value > 0 ? value : null
}

export function isAccessTokenExpired(): boolean {
  const expiresAt = readExpiresAt()
  return expiresAt !== null && Date.now() >= expiresAt
}

export function getAccessToken(): string | null {
  if (!hasWindow()) return null
  if (isAccessTokenExpired()) {
    invalidateAuthSession('expired')
    return null
  }
  return window.localStorage.getItem(ACCESS_TOKEN_KEY)
}

export function setAuthSession(token: string, expiresInSeconds?: number): void {
  if (!hasWindow()) return
  window.localStorage.setItem(ACCESS_TOKEN_KEY, token)
  if (typeof expiresInSeconds === 'number' && Number.isFinite(expiresInSeconds) && expiresInSeconds > 0) {
    window.localStorage.setItem(ACCESS_TOKEN_EXPIRES_AT_KEY, String(Date.now() + expiresInSeconds * 1000))
  } else {
    window.localStorage.removeItem(ACCESS_TOKEN_EXPIRES_AT_KEY)
  }
}

export function clearAccessToken(): void {
  if (!hasWindow()) return
  window.localStorage.removeItem(ACCESS_TOKEN_KEY)
  window.localStorage.removeItem(ACCESS_TOKEN_EXPIRES_AT_KEY)
}

export function invalidateAuthSession(reason: AuthInvalidationReason): void {
  clearAccessToken()
  if (!hasWindow()) return
  window.dispatchEvent(new CustomEvent<AuthInvalidationReason>(AUTH_SESSION_INVALIDATED_EVENT, { detail: reason }))
}
