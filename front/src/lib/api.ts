import type { Problem } from '../types/api'
import { clearToken, getToken } from './session'

/** Base do gateway. Em produção vem de VITE_API_URL; em dev, o padrão local. */
export const API_URL = (import.meta.env.VITE_API_URL ?? 'http://localhost:8080').replace(/\/$/, '')

/** Prefixo publico de tudo que o cliente consome. */
const PREFIX = '/api/v1'

/** Erro de API com o `code` do contrato a mao, para a tela ramificar sem parsear texto. */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly problem: Problem
  constructor(problem: Problem) {
    super(problem.detail ?? problem.title ?? `Erro ${problem.status}`)
    this.name = 'ApiError'
    this.status = problem.status
    this.code = problem.code ?? ''
    this.problem = problem
  }
  /** Erros de campo de 400/422, prontos para marcar o formulario. */
  get fieldErrors(): Record<string, string> {
    const out: Record<string, string> = {}
    for (const e of this.problem.errors ?? []) out[e.field] = e.message
    return out
  }
}

/** Sessao expirada: quem estiver ouvindo manda o usuario para o login. */
export const UNAUTHORIZED_EVENT = 'loja:unauthorized'

type RequestOptions = {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  body?: unknown
  query?: Record<string, string | number | boolean | undefined | null>
  /** UUID v4 reaproveitado no retry; so nas rotas que o contrato marca. */
  idempotencyKey?: string
  signal?: AbortSignal
  /** Rota publica: nao manda Authorization mesmo havendo token. */
  anonymous?: boolean
}

function buildUrl(path: string, query?: RequestOptions['query']): string {
  const url = new URL(API_URL + PREFIX + path)
  for (const [key, value] of Object.entries(query ?? {})) {
    if (value === undefined || value === null || value === '') continue
    url.searchParams.set(key, String(value))
  }
  return url.toString()
}

/**
 * Uma porta de entrada para a API inteira.
 *
 * Centraliza o Bearer, o envelope de erro e o 401 porque espalhar isso por tela
 * garante que uma delas esqueca — e um 401 silencioso vira tela quebrada.
 */
export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, query, idempotencyKey, signal, anonymous } = options

  const headers: Record<string, string> = { Accept: 'application/json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (idempotencyKey) headers['Idempotency-Key'] = idempotencyKey

  const token = anonymous ? null : getToken()
  if (token) headers.Authorization = `Bearer ${token}`

  let response: Response
  try {
    response = await fetch(buildUrl(path, query), {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal,
    })
  } catch (cause) {
    if (signal?.aborted) throw cause
    // Rede fora, gateway fora, CORS: para a tela e tudo "nao deu para falar com o servidor".
    throw new ApiError({ status: 0, code: 'NETWORK_ERROR', title: 'Sem conexão com o servidor' })
  }

  if (response.status === 401 && !anonymous) {
    clearToken()
    window.dispatchEvent(new CustomEvent(UNAUTHORIZED_EVENT))
  }

  if (response.status === 204 || response.status === 304) return undefined as T

  const text = await response.text()
  const payload = text ? safeJson(text) : null

  if (!response.ok) {
    const problem = (payload ?? {}) as Partial<Problem>
    throw new ApiError({ ...problem, status: problem.status ?? response.status })
  }

  return payload as T
}

function safeJson(text: string): unknown {
  try {
    return JSON.parse(text)
  } catch {
    return null
  }
}

export const api = {
  get: <T>(path: string, options?: Omit<RequestOptions, 'method' | 'body'>) =>
    request<T>(path, { ...options, method: 'GET' }),
  post: <T>(path: string, body?: unknown, options?: Omit<RequestOptions, 'method' | 'body'>) =>
    request<T>(path, { ...options, method: 'POST', body }),
  put: <T>(path: string, body?: unknown, options?: Omit<RequestOptions, 'method' | 'body'>) =>
    request<T>(path, { ...options, method: 'PUT', body }),
  patch: <T>(path: string, body?: unknown, options?: Omit<RequestOptions, 'method' | 'body'>) =>
    request<T>(path, { ...options, method: 'PATCH', body }),
  delete: <T>(path: string, options?: Omit<RequestOptions, 'method' | 'body'>) =>
    request<T>(path, { ...options, method: 'DELETE' }),
}

/** Chave de idempotencia: gerada uma vez por intencao e reusada no retry. */
export function newIdempotencyKey(): string {
  return crypto.randomUUID()
}

/** Endereco para onde mandar o navegador para logar com o Google. */
export function googleLoginUrl(): string {
  return `${API_URL}/oauth2/authorization/google`
}
