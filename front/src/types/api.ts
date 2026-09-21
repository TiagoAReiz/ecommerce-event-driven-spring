/** Tipos que valem para toda a API. O que e de uma area so mora na area. */

/** Envelope de erro da API (RFC 9457). `code` e estavel e serve para ramificar. */
export type Problem = {
  type?: string
  title?: string
  status: number
  detail?: string
  instance?: string
  code?: string
  requestId?: string
  timestamp?: string
  errors?: { field: string; message: string }[]
}

/** Paginacao do backend: `content` + metadados em `page`. */
export type Page<T> = {
  content: T[]
  page: { number: number; size: number; totalElements: number; totalPages: number }
}

/**
 * Dinheiro chega como string ("349.90") e assim permanece.
 * Converter para number perderia centavos em somas; quem exibe formata.
 */
export type Money = string
