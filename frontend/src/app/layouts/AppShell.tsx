import { useCallback, useEffect, useRef, useState } from 'react'
import type { MouseEvent as ReactMouseEvent } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'

import { ChatPanel } from '../../components/chat/ChatPanel'
import { useAuth } from '../../hooks/useAuth'
import { fetchAuthorizedBlobUrl } from '../../services/http/apiClient'
import { authService } from '../../services/auth/authService'
import { getFirstName } from '../../types/auth'
import { getSetorBrandName, getSetorLabel } from '../../types/setor'
import { OPEN_ASSISTANT_PANEL_EVENT } from '../../utils/assistantPanel'

const BASE_NAV_ITEMS = [
  { label: 'Painel do Usuario', to: '/' },
  { label: 'Minhas ITs', to: '/minhas-its' },
  { label: 'Documentacao', to: '/documentation' },
  { label: 'Arquivos', to: '/files' },
  { label: 'Historico de Conversas', to: '/conversations' },
  { label: 'Notificacoes', to: '/notifications' },
] as const

const CHAT_COLLAPSED_KEY = 'pts.chatFloatingCollapsed'
const CHAT_HEIGHT_KEY = 'pts.chatFloatingHeight'

function AssistantLauncherGlyph() {
  return (
    <svg viewBox="0 0 32 32" aria-hidden="true" focusable="false" className="floating-chat-launcher-glyph">
      <defs>
        <linearGradient id="assistantLauncherGradient" x1="6" y1="4" x2="26" y2="28" gradientUnits="userSpaceOnUse">
          <stop stopColor="#86efac" />
          <stop offset="0.52" stopColor="#7dd3fc" />
          <stop offset="1" stopColor="#60a5fa" />
        </linearGradient>
      </defs>
      <rect x="7" y="9" width="18" height="14" rx="6" fill="url(#assistantLauncherGradient)" />
      <path d="M12 23.5h8" stroke="#dbeafe" strokeWidth="1.8" strokeLinecap="round" />
      <path d="M16 7V4.5" stroke="#bfdbfe" strokeWidth="1.8" strokeLinecap="round" />
      <circle cx="16" cy="4" r="1.6" fill="#dbeafe" />
      <circle cx="13" cy="15" r="1.5" fill="#082f49" />
      <circle cx="19" cy="15" r="1.5" fill="#082f49" />
      <path d="M13 19c.8.9 1.8 1.3 3 1.3s2.2-.4 3-1.3" stroke="#082f49" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" fill="none" />
    </svg>
  )
}

function loadStoredCollapsed(): boolean {
  const raw = window.localStorage.getItem(CHAT_COLLAPSED_KEY)
  return raw !== 'false'
}

function loadStoredHeight(): number {
  const raw = Number(window.localStorage.getItem(CHAT_HEIGHT_KEY))
  return Number.isFinite(raw) && raw >= 560 ? raw : 760
}

export function AppShell() {
  const navigate = useNavigate()
  const location = useLocation()
  const queryClient = useQueryClient()
  const { logout, switchSector, user } = useAuth()
  const firstName = getFirstName(user)
  const role = String(user?.role ?? '').toUpperCase()
  const isAdmin = role === 'ADMIN' || role === 'SUPER_ADMIN'
  const activeSetorCode = String(user?.setorAtivo ?? '').trim()
  const activeSetorLabel = getSetorBrandName(activeSetorCode)
  const [sidebarAvatarUrl, setSidebarAvatarUrl] = useState<string | null>(null)
  const [chatCollapsed, setChatCollapsed] = useState<boolean>(() => loadStoredCollapsed())
  const [chatHeight, setChatHeight] = useState<number>(() => loadStoredHeight())
  const [chatOverlayMounted, setChatOverlayMounted] = useState<boolean>(() => !loadStoredCollapsed())
  const [chatOverlayVisible, setChatOverlayVisible] = useState<boolean>(() => !loadStoredCollapsed())
  const [sectorSwitcherOpen, setSectorSwitcherOpen] = useState(false)
  const [sectorSwitcherLoading, setSectorSwitcherLoading] = useState(false)
  const [sectorSwitcherOptions, setSectorSwitcherOptions] = useState<string[]>([])
  const [sectorSwitcherSelection, setSectorSwitcherSelection] = useState('')
  const [sectorSwitcherFeedback, setSectorSwitcherFeedback] = useState<string | null>(null)
  const [isSwitchingSector, setIsSwitchingSector] = useState(false)
  const closeTimerRef = useRef<number | null>(null)
  const resizeStateRef = useRef<{ startY: number; startHeight: number } | null>(null)

  useEffect(() => {
    window.localStorage.setItem(CHAT_COLLAPSED_KEY, String(chatCollapsed))
  }, [chatCollapsed])

  useEffect(() => {
    window.localStorage.setItem(CHAT_HEIGHT_KEY, String(chatHeight))
  }, [chatHeight])

  useEffect(() => {
    return () => {
      if (closeTimerRef.current) {
        window.clearTimeout(closeTimerRef.current)
      }
    }
  }, [])

  const loadSectorSwitcherOptions = useCallback(async () => {
    if (!isAdmin) {
      setSectorSwitcherOptions([])
      setSectorSwitcherSelection('')
      return
    }

    setSectorSwitcherLoading(true)
    setSectorSwitcherFeedback(null)

    try {
      const response = await authService.listManageableSetores()
      const nextOptions = Array.from(
        new Set(
          response
            .map((item) => String(item.codigo ?? '').trim())
            .filter(Boolean),
        ),
      )

      setSectorSwitcherOptions(nextOptions)
      setSectorSwitcherSelection((current) => {
        if (current && nextOptions.includes(current)) return current
        if (activeSetorCode && nextOptions.includes(activeSetorCode)) return activeSetorCode
        return nextOptions[0] ?? ''
      })
    } catch {
      const fallbackOptions = Array.from(new Set([activeSetorCode].filter(Boolean)))
      setSectorSwitcherOptions(fallbackOptions)
      setSectorSwitcherSelection(fallbackOptions[0] ?? '')
      setSectorSwitcherFeedback('Nao foi possivel atualizar a lista de setores agora.')
    } finally {
      setSectorSwitcherLoading(false)
    }
  }, [activeSetorCode, isAdmin])

  useEffect(() => {
    if (!isAdmin) return
    void loadSectorSwitcherOptions()
  }, [isAdmin, loadSectorSwitcherOptions])

  function openChatPanel() {
    if (closeTimerRef.current) {
      window.clearTimeout(closeTimerRef.current)
      closeTimerRef.current = null
    }

    setChatOverlayMounted(true)
    setChatCollapsed(false)
    window.requestAnimationFrame(() => {
      setChatOverlayVisible(true)
    })
  }

  function closeChatPanel() {
    setChatOverlayVisible(false)
    setChatCollapsed(true)

    if (closeTimerRef.current) {
      window.clearTimeout(closeTimerRef.current)
    }

    closeTimerRef.current = window.setTimeout(() => {
      setChatOverlayMounted(false)
      closeTimerRef.current = null
    }, 190)
  }

  useEffect(() => {
    function handleMouseMove(event: globalThis.MouseEvent) {
      const state = resizeStateRef.current
      if (!state) return

      const viewportMax = Math.max(620, window.innerHeight - 32)
      const nextHeight = Math.min(viewportMax, Math.max(560, state.startHeight + (state.startY - event.clientY)))
      setChatHeight(nextHeight)
    }

    function handleMouseUp() {
      resizeStateRef.current = null
      document.body.style.userSelect = ''
      document.body.style.cursor = ''
    }

    window.addEventListener('mousemove', handleMouseMove)
    window.addEventListener('mouseup', handleMouseUp)

    return () => {
      window.removeEventListener('mousemove', handleMouseMove)
      window.removeEventListener('mouseup', handleMouseUp)
    }
  }, [])

  useEffect(() => {
    function handleOpenAssistantPanel() {
      openChatPanel()
    }

    window.addEventListener(OPEN_ASSISTANT_PANEL_EVENT, handleOpenAssistantPanel)
    return () => {
      window.removeEventListener(OPEN_ASSISTANT_PANEL_EVENT, handleOpenAssistantPanel)
    }
  }, [])

  useEffect(() => {
    if (chatCollapsed) return

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        closeChatPanel()
      }
    }

    window.addEventListener('keydown', handleKeyDown)
    return () => {
      window.removeEventListener('keydown', handleKeyDown)
    }
  }, [chatCollapsed])

  useEffect(() => {
    document.title = activeSetorLabel ? `PTS ${activeSetorLabel}` : 'PTS'
  }, [activeSetorLabel])

  useEffect(() => {
    let currentUrl: string | null = null

    async function loadSidebarAvatar() {
      if (!user?.profileImageUrl) {
        setSidebarAvatarUrl(null)
        return
      }

      try {
        const objectUrl = await fetchAuthorizedBlobUrl(user.profileImageUrl)
        currentUrl = objectUrl
        setSidebarAvatarUrl(objectUrl)
      } catch {
        setSidebarAvatarUrl(null)
      }
    }

    void loadSidebarAvatar()

    return () => {
      if (currentUrl) {
        URL.revokeObjectURL(currentUrl)
      }
    }
  }, [user?.profileImageUrl])

  function handleLogout() {
    logout()
    navigate('/login', { replace: true })
  }

  async function handleOpenSectorSwitcher() {
    setSectorSwitcherOpen(true)
    setSectorSwitcherFeedback(null)
    await loadSectorSwitcherOptions()
  }

  function handleCloseSectorSwitcher() {
    setSectorSwitcherOpen(false)
    setSectorSwitcherFeedback(null)
    setSectorSwitcherSelection(activeSetorCode)
  }

  async function handleApplySectorSwitch() {
    if (!sectorSwitcherSelection) {
      setSectorSwitcherFeedback('Selecione um setor para continuar.')
      return
    }

    if (sectorSwitcherSelection === activeSetorCode) {
      setSectorSwitcherOpen(false)
      return
    }

    setIsSwitchingSector(true)
    setSectorSwitcherFeedback(null)

    try {
      await switchSector(sectorSwitcherSelection)
      await queryClient.invalidateQueries()
      await queryClient.refetchQueries({ type: 'active' })
      setSectorSwitcherOpen(false)
      navigate(`${location.pathname}${location.search}`, { replace: true })
    } catch (error) {
      setSectorSwitcherFeedback(error instanceof Error ? error.message : 'Nao foi possivel trocar o setor agora.')
    } finally {
      setIsSwitchingSector(false)
    }
  }

  function handleStartVerticalResize(event: ReactMouseEvent<HTMLDivElement>) {
    resizeStateRef.current = {
      startY: event.clientY,
      startHeight: chatHeight,
    }

    document.body.style.userSelect = 'none'
    document.body.style.cursor = 'ns-resize'
  }

  return (
    <div className="app-shell workspace-2col">
      <aside className="sidebar chatbot-sidebar">
        <div className="sidebar-brand-block">
          <div className="sidebar-brand-mark">PTS</div>
          <div>
            <div className="sidebar-brand-title">{activeSetorLabel ? `PTS ${activeSetorLabel}` : 'PTS'}</div>
            <div className="sidebar-brand-subtitle">{activeSetorLabel || 'Centro Operacional'}</div>
          </div>
        </div>

        <button type="button" className="sidebar-user-card" onClick={() => navigate('/profile')}>
          <div className="sidebar-user-avatar">
            {sidebarAvatarUrl ? (
              <img src={sidebarAvatarUrl} alt={`Foto de perfil de ${user?.name ?? firstName}`} className="sidebar-user-avatar-image" />
            ) : (
              <span className="sidebar-user-avatar-fallback">{String(firstName || 'U').charAt(0).toUpperCase()}</span>
            )}
          </div>
          <div className="sidebar-user-copy">
            <div className="sidebar-user-name">{user?.name ?? firstName}</div>
            <div className="sidebar-user-role">{role || 'USER'}</div>
          </div>
        </button>

        <nav className="nav-list">
          {[...BASE_NAV_ITEMS, ...(isAdmin ? [{ label: 'Painel Administrativo', to: '/admin/users' as const }] : [])].map((item) => (
            <NavLink key={item.to} to={item.to} end={item.to === '/'} className="sidebar-link">
              {item.label}
            </NavLink>
          ))}
        </nav>

        <div className="sidebar-footer-block">
          <button type="button" className="sidebar-logout-btn" onClick={handleLogout}>
            Sair
          </button>
        </div>
      </aside>

      <div className="workspace-content-area">
        <header className="workspace-toolbar">
          <div className="workspace-toolbar-copy">
            <div className="workspace-title">{activeSetorLabel ? `Centro Operacional ${activeSetorLabel}` : 'Centro Operacional PTS'}</div>
            <div className="workspace-toolbar-subtitle-row">
              <div className="workspace-toolbar-subtitle">Setor ativo: {getSetorLabel(activeSetorCode)}</div>
              {isAdmin ? (
                <button type="button" className="workspace-sector-switch-trigger" onClick={() => void handleOpenSectorSwitcher()}>
                  Trocar setor
                </button>
              ) : null}
            </div>
          </div>
        </header>
        <main className="page-content">
          <Outlet />
        </main>
      </div>

      <button
        type="button"
        className={`floating-chat-launcher ${chatCollapsed ? 'is-visible' : 'is-hidden'}`}
        onClick={openChatPanel}
        aria-label="Abrir assistente"
        aria-hidden={!chatCollapsed}
        tabIndex={chatCollapsed ? 0 : -1}
      >
        <span className="floating-chat-launcher-icon" aria-hidden="true"><AssistantLauncherGlyph /></span>
        <span className="floating-chat-launcher-text">Assistente</span>
      </button>

      {chatOverlayMounted ? (
        <>
          <button
            type="button"
            className={`floating-chat-backdrop ${chatOverlayVisible ? 'is-visible' : 'is-hidden'}`}
            aria-label="Fechar assistente"
            onClick={closeChatPanel}
          />
          <div className={`floating-chat-shell ${chatOverlayVisible ? 'is-open' : 'is-closing'}`} style={{ height: `${chatHeight}px` }}>
            <ChatPanel
              collapsed={false}
              onToggleCollapse={closeChatPanel}
              onStartVerticalResize={handleStartVerticalResize}
              variant="floating"
            />
          </div>
        </>
      ) : null}

      {sectorSwitcherOpen ? (
        <div className="active-sector-modal-backdrop" onClick={handleCloseSectorSwitcher}>
          <div className="active-sector-modal" onClick={(event) => event.stopPropagation()}>
            <div className="active-sector-modal-header">
              <div>
                <div className="panel-title">Trocar setor ativo</div>
                <div className="panel-muted">Escolha o setor que voce quer administrar agora.</div>
              </div>
              <button type="button" className="active-sector-modal-close" onClick={handleCloseSectorSwitcher}>
                Fechar
              </button>
            </div>

            <div className="active-sector-modal-copy">
              O sistema atualiza seu setor ativo sem precisar sair da conta. Novos setores liberados para voce aparecem aqui automaticamente.
            </div>

            {sectorSwitcherLoading ? (
              <p className="helper-text">Carregando setores disponiveis...</p>
            ) : sectorSwitcherOptions.length > 0 ? (
              <div className="active-sector-modal-grid">
                {sectorSwitcherOptions.map((codigo) => {
                  const isSelected = sectorSwitcherSelection === codigo
                  return (
                    <button
                      key={`switch-${codigo}`}
                      type="button"
                      className={`active-sector-option ${isSelected ? 'is-selected' : ''}`}
                      onClick={() => setSectorSwitcherSelection(codigo)}
                    >
                      <span className="active-sector-option-label">{getSetorLabel(codigo)}</span>
                      <span className="active-sector-option-meta">{codigo}</span>
                    </button>
                  )
                })}
              </div>
            ) : (
              <div className="active-sector-empty">Nenhum setor disponivel para troca no momento.</div>
            )}

            <div className="active-sector-modal-actions">
              <button
                type="button"
                className="outline-button"
                onClick={handleApplySectorSwitch}
                disabled={isSwitchingSector || !sectorSwitcherSelection}
              >
                {isSwitchingSector ? 'Atualizando...' : 'Ir para o setor'}
              </button>
              <button
                type="button"
                className="outline-button small"
                onClick={() => setSectorSwitcherSelection(activeSetorCode)}
                disabled={isSwitchingSector}
              >
                Voltar ao atual
              </button>
            </div>

            {sectorSwitcherFeedback ? (
              <div className="upload-error">{sectorSwitcherFeedback}</div>
            ) : null}
          </div>
        </div>
      ) : null}
    </div>
  )
}



