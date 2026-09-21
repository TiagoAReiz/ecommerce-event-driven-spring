/**
 * Guarda do token da sessao.
 *
 * O token vem no fragmento da URL depois do login no Google e vive no
 * sessionStorage: sobrevive ao F5 da aba, some quando a aba fecha e nunca vai
 * para outra aba ou janela. localStorage duraria alem da sessao sem motivo.
 */

const KEY = 'loja.token'

let memoria: string | null = null

export function getToken(): string | null {
  if (memoria) return memoria
  try {
    memoria = sessionStorage.getItem(KEY)
  } catch {
    // Aba anonima ou storage bloqueado: a sessao vale so enquanto a pagina viver.
    memoria = null
  }
  return memoria
}

export function setToken(token: string): void {
  memoria = token
  try {
    sessionStorage.setItem(KEY, token)
  } catch {
    // Sem storage, o token fica so em memoria.
  }
}

export function clearToken(): void {
  memoria = null
  try {
    sessionStorage.removeItem(KEY)
  } catch {
    // nada a limpar
  }
}

/** Claims que o front le do token. A validacao de verdade e do backend. */
export type TokenClaims = { sub?: string; roles?: string[]; exp?: number; auth_time?: number }

export function readClaims(token: string): TokenClaims | null {
  try {
    const payload = token.split('.')[1]
    const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'))
    return JSON.parse(decodeURIComponent(escape(json))) as TokenClaims
  } catch {
    return null
  }
}
