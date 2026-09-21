import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { ApiError, api, googleLoginUrl, UNAUTHORIZED_EVENT } from './api'
import { clearToken, getToken, readClaims, setToken } from './session'

export type SessionUser = {
  id: number
  name: string
  email: string
  photoUrl: string | null
  roles: string[]
  expiresAt: string
}

type RefreshResponse = { accessToken: string; expiresIn: number; expiresAt: string }

type AuthState = {
  user: SessionUser | null
  /** `loading` enquanto o token guardado ainda nao foi conferido com o gateway. */
  status: 'loading' | 'authenticated' | 'anonymous'
  isOwner: boolean
  login: (redirectTo?: string) => void
  logout: () => Promise<void>
  /** Rele a sessao depois de algo que muda o perfil (ex.: editar nome). */
  reload: () => Promise<void>
}

const AuthContext = createContext<AuthState | null>(null)

/** Onde o usuario estava quando bateu no login, para voltar depois. */
const REDIRECT_KEY = 'loja.redirect'

/** O contrato manda renovar quando faltarem ~5 minutos do TTL de 1 h. */
const RENEW_MARGIN_MS = 5 * 60 * 1000

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<SessionUser | null>(null)
  const [status, setStatus] = useState<AuthState['status']>(() => (getToken() ? 'loading' : 'anonymous'))
  const renewTimer = useRef<number | null>(null)

  const loadSession = useCallback(async () => {
    if (!getToken()) {
      setUser(null)
      setStatus('anonymous')
      return
    }
    try {
      const session = await api.get<SessionUser>('/auth/session')
      setUser(session)
      setStatus('authenticated')
    } catch (error) {
      // 401 (token morto) e 404 (conta removida com token vivo) terminam igual: anonimo.
      if (error instanceof ApiError && (error.status === 401 || error.status === 404)) {
        clearToken()
        setUser(null)
        setStatus('anonymous')
        return
      }
      // Gateway fora: nao desloga quem tem token valido, so nao sabe quem e ainda.
      setStatus(getToken() ? 'authenticated' : 'anonymous')
    }
  }, [])

  /** Renovacao deslizante: cada token renova uma vez so, entao agenda pelo `exp`. */
  const scheduleRenew = useCallback(() => {
    if (renewTimer.current) window.clearTimeout(renewTimer.current)
    const token = getToken()
    if (!token) return
    const claims = readClaims(token)
    if (!claims?.exp) return

    const delay = claims.exp * 1000 - Date.now() - RENEW_MARGIN_MS
    renewTimer.current = window.setTimeout(async () => {
      try {
        const renewed = await api.post<RefreshResponse>('/auth/refresh')
        setToken(renewed.accessToken)
        scheduleRenew()
      } catch {
        // Teto de 7 dias estourado ou token ja usado: a sessao acabou de verdade.
        clearToken()
        setUser(null)
        setStatus('anonymous')
      }
    }, Math.max(delay, 1000))
  }, [])

  useEffect(() => {
    void loadSession().then(scheduleRenew)
    return () => {
      if (renewTimer.current) window.clearTimeout(renewTimer.current)
    }
  }, [loadSession, scheduleRenew])

  // Qualquer 401 vindo de qualquer tela derruba a sessao num lugar so.
  useEffect(() => {
    const onUnauthorized = () => {
      setUser(null)
      setStatus('anonymous')
    }
    window.addEventListener(UNAUTHORIZED_EVENT, onUnauthorized)
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, onUnauthorized)
  }, [])

  const login = useCallback((redirectTo?: string) => {
    try {
      sessionStorage.setItem(REDIRECT_KEY, redirectTo ?? window.location.pathname + window.location.search)
    } catch {
      // Sem storage, volta para a home depois do login.
    }
    window.location.assign(googleLoginUrl())
  }, [])

  const logout = useCallback(async () => {
    try {
      await api.post('/auth/logout')
    } catch {
      // Mesmo que o gateway recuse, a sessao local tem que sair.
    }
    clearToken()
    setUser(null)
    setStatus('anonymous')
  }, [])

  const value = useMemo<AuthState>(
    () => ({
      user,
      status,
      isOwner: Boolean(user?.roles?.includes('owner')),
      login,
      logout,
      reload: loadSession,
    }),
    [user, status, login, logout, loadSession],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthState {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth precisa estar dentro de AuthProvider')
  return context
}

/** Guarda o token que chegou no fragmento e devolve para onde voltar. */
export function consumeTokenFromFragment(): { ok: boolean; redirectTo: string } {
  const hash = window.location.hash.startsWith('#') ? window.location.hash.slice(1) : ''
  const token = new URLSearchParams(hash).get('token')

  let redirectTo = '/'
  try {
    redirectTo = sessionStorage.getItem(REDIRECT_KEY) ?? '/'
    sessionStorage.removeItem(REDIRECT_KEY)
  } catch {
    redirectTo = '/'
  }

  if (!token) return { ok: false, redirectTo }

  setToken(token)
  // Tira o token da barra de enderecos antes de qualquer navegacao: nao fica no
  // historico e nao vaza em print de tela nem em link copiado.
  window.history.replaceState(null, '', window.location.pathname + window.location.search)
  return { ok: true, redirectTo }
}
